package to.us.ponodev.fb2kindle;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.GetFile;
import org.telegram.telegrambots.meta.api.methods.send.SendDocument;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Document;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import to.us.ponodev.fb2kindle.config.BotConfig;
import to.us.ponodev.fb2kindle.config.ConverterConfig;
import to.us.ponodev.fb2kindle.entity.User;
import to.us.ponodev.fb2kindle.repository.UserRepository;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Slf4j
@AllArgsConstructor
@Component
public class TelegramBot extends TelegramLongPollingBot {

    private final BotConfig botConfig;
    private final ConverterConfig converterConfig;
    private final ConverterService converterService;
    private final UserRepository userRepository;
    private static final String START_COMMAND = "/start";
    private static final String EMAIL_COMMAND = "/email";
    private static final String FILE_COMMAND = "/file";
    private static final String FONT_COMMAND = "/font";
    private static final String MARGIN_COMMAND = "/margin";
    private static final String USERS_COMMAND = "/users";

    @Override
    public String getBotUsername() {
        return botConfig.getBotName();
    }

    @Override
    public String getBotToken() {
        return botConfig.getToken();
    }

    @Override
    public void onUpdateReceived(Update update) {
        if (update.hasMessage()) {
            long chatId = update.getMessage().getChatId();

            if (update.getMessage().hasText()) {
                String messageText = update.getMessage().getText();

                if (messageText.startsWith(START_COMMAND)) {
                    startCommandReceived(chatId, update.getMessage().getChat().getFirstName());
                } else if (messageText.startsWith(EMAIL_COMMAND)) {
                    emailCommandReceived(chatId, messageText);
                } else if (messageText.startsWith(FILE_COMMAND)) {
                    fileCommandReceived(chatId);
                } else if (messageText.startsWith(FONT_COMMAND)) {
                    fontCommandReceived(chatId);
                } else if (messageText.startsWith(MARGIN_COMMAND)) {
                    marginCommandReceived(chatId, messageText);
                } else if (messageText.startsWith(USERS_COMMAND)) {
                    usersCommandReceived(chatId);
                }

            } else if (update.getMessage().hasDocument()) {

                documentReceived(chatId, update);

            }
        }
    }

    private void startCommandReceived(Long chatId, String name) {
        String answer = "Hi, " + name + ", nice to meet you!" + "\n" +
                "This is fb2 to Kindle converter bot." + "\n" +
                "It receives .fb2 or .fb2.zip files, " + "\n" +
                "converts it to EPUB format and send to your Kindle." + "\n" +
                "\n" +
                "You need to setup your Kindle email using '" + EMAIL_COMMAND + "' command." + "\n" +
                "And also add fb2kindle.bot@gmail.com to your" + "\n" +
                "Approved Personal Document E-mail List in" + "\n" +
                "https://www.amazon.com/hz/mycd/digital-console/contentlist/pdocs/dateDsc" + "\n" +
                "Manage Your Content & Devices> Preferences > Personal Document Settings" + "\n" +
                "\n" +
                "By default there is an embedded Google Noto font in the book." + "\n" +
                "To switch this setting - use the '" + FONT_COMMAND + "' command." + "\n" +
                "\n" +
                "Also you can set your own margins for text." + "\n" +
                "To set - use the '" + MARGIN_COMMAND + "' command." + "\n" +
                "\n" +
                "By default converted file is sent by STK only. But it can also being downloaded." + "\n" +
                "To switch this setting - use the '" + FILE_COMMAND + "' command.";
        sendMessage(chatId, answer);
        userRepository
                .findById(chatId)
                .ifPresentOrElse(user -> {
                            String settings = "Your settings:" + "\n" +
                                    "chatID: " + user.getChatId() + "\n" +
                                    "Email: " + user.getEmail() + "\n" +
                                    "Embed fonts: " + user.isEmbedFonts() + "\n" +
                                    "Get converted file: " + user.isGetConvertedFile() + "\n" +
                                    "Margins: " + user.getMargins();
                            sendMessage(chatId, settings);
                        },
                        () -> registerNewUser(chatId, ""));
    }

    private void emailCommandReceived(long chatId, String message) {
        if (EMAIL_COMMAND.equals(message)) {
            userRepository
                    .findById(chatId)
                    .ifPresentOrElse(user -> sendMessage(chatId, "Your email is set to: " + user.getEmail()),
                            () -> sendMessage(chatId, "Your haven't set email yet!"));
        } else {
            String email = message.substring(EMAIL_COMMAND.length()).trim();
            if (email.equals("clear")) {
                userRepository
                        .findById(chatId)
                        .ifPresent(user -> {
                            user.setEmail("");
                            userRepository.save(user);
                        });
            } else if (email.endsWith("@kindle.com")) {
                userRepository
                        .findById(chatId)
                        .ifPresentOrElse(user -> {
                                    user.setEmail(email);
                                    userRepository.save(user);
                                },
                                () -> registerNewUser(chatId, email));
                sendMessage(chatId, "Email is set to: " + email);
            } else {
                sendMessage(chatId, "Only @kindle.com domain is accepted!");
            }
        }
    }

    private void marginCommandReceived(long chatId, String message) {
        if (MARGIN_COMMAND.equals(message)) {
            userRepository
                    .findById(chatId)
                    .ifPresentOrElse(user -> {
                                if (user.getMargins().isEmpty()) {
                                    String respondMessage =
                                            "You're using default margin: " + converterConfig.getDefaultMargin() + "\n" +
                                                    "You can set your own preferred margin using this command." + "\n" +
                                                    "Example: '" + MARGIN_COMMAND + " " + converterConfig.getDefaultMargin() + "'" + "\n" +
                                                    "Order of margins is: top right bottom left";
                                    sendMessage(chatId, respondMessage);
                                } else {
                                    sendMessage(chatId, "Your margin is set to: " + user.getMargins());
                                }
                            },
                            () -> registerNewUser(chatId, "")
                    );
        } else {
            String margin = message.substring(MARGIN_COMMAND.length()).trim();
            // Simple check for input string correctness
            // Like: 0pt -13pt 0pt -13pt
            if (margin.chars().filter(ch -> ch == ' ').count() == 3) {
                userRepository
                        .findById(chatId)
                        .ifPresentOrElse(user -> {
                                    user.setMargins(margin);
                                    userRepository.save(user);
                                },
                                () -> registerNewUser(chatId, "")
                        );
                sendMessage(chatId, "Margin is set to: " + margin);
            } else {
                sendMessage(chatId, "Wrong format of margin value!" + "\n" +
                        "Example: '" + MARGIN_COMMAND + " " + converterConfig.getDefaultMargin() + "'");
            }
        }
    }

    private void fileCommandReceived(long chatId) {
        userRepository
                .findById(chatId)
                .ifPresentOrElse(user -> {
                            user.setGetConvertedFile(!user.isGetConvertedFile());
                            userRepository.save(user);
                            sendMessage(chatId, "Get converted file is set to: " + user.isGetConvertedFile());
                        },
                        () -> registerNewUser(chatId, ""));
    }

    private void fontCommandReceived(long chatId) {
        userRepository
                .findById(chatId)
                .ifPresentOrElse(user -> {
                            user.setEmbedFonts(!user.isEmbedFonts());
                            userRepository.save(user);
                            sendMessage(chatId, "Embed fonts is set to: " + user.isEmbedFonts());
                        },
                        () -> registerNewUser(chatId, ""));
    }

    private void usersCommandReceived(long chatId) {
        if (String.valueOf(chatId).equals(botConfig.getAdminChatId())) {
            List<User> userList = userRepository.findAll();
            sendMessage(
                    chatId,
                    "List of all registered users (" + userList.size() + "):\n" +
                            "ChatId - E-mail\n" +
                            userList.stream()
                                    .map(user -> user.getChatId() + " - " + user.getEmail())
                                    .collect(Collectors.joining("\n"))
            );
        }
    }

    private void documentReceived(long chatId, Update update) {
        userRepository
                .findById(chatId)
                .ifPresentOrElse(user -> {
                            String docId = update.getMessage().getDocument().getFileId();
                            String docName = update.getMessage().getDocument().getFileName();
                            String docMime = update.getMessage().getDocument().getMimeType();
                            long docSize = update.getMessage().getDocument().getFileSize();
                            String getID = String.valueOf(update.getMessage().getFrom().getId());

                            Document document = new Document();
                            document.setMimeType(docMime);
                            document.setFileName(docName);
                            document.setFileSize(docSize);
                            document.setFileId(docId);

                            if (!(docName.contains(".fb2") || docName.contains(".zip"))) {
                                sendMessage(chatId, "Only .fb2 or .fb2.zip extensions are allowed!");
                                return;
                            }

                            GetFile getFile = new GetFile();
                            getFile.setFileId(document.getFileId());
                            try {
                                org.telegram.telegrambots.meta.api.objects.File telegramFile = execute(getFile);
                                docName = docName.replace(" ", "_");
                                Path inputFilePathString = Path.of("./data/userFiles/" + getID + "_" + docName);

                                sendMessage(chatId, "Conversion started. Please wait, it may take a while...");
                                File savedFile = downloadFile(telegramFile, inputFilePathString.toFile());
                                Path savedFilePath = Path.of(savedFile.getCanonicalPath());

                                CompletableFuture<ConverterService.ConversionResult> conversionResultCompletableFuture =
                                        converterService.convert(savedFilePath, chatId, user);

                                conversionResultCompletableFuture.thenApply(conversionResult -> {
                                    int exitValue = conversionResult.getCode();
                                    if (exitValue == 0) {
                                        Path resultPath = conversionResult.getPath();
                                        if (!user.getEmail().isEmpty()) {
                                            sendMessage(chatId,
                                                    "File " + resultPath.getFileName() +
                                                            " was converted and send to: " + user.getEmail());
                                        }
                                        if (user.isGetConvertedFile()) {
                                            sendFile(chatId, resultPath.toFile(), "");
                                        }
                                        if (user.getEmail().isEmpty() && !user.isGetConvertedFile()) {
                                            sendMessage(chatId,
                                                    "File was successfully converted, " +
                                                            "but you don't set your '/email' and " +
                                                            "not set '/file' option to get converted file.");
                                        }
                                        if (converterConfig.isDeleteOutputFile()) {
                                            resultPath.toFile().delete();
                                        }
                                    } else {
                                        sendMessage(chatId, conversionResult.getMessage());
                                    }
                                    return 0;
                                });

                            } catch (TelegramApiException | IOException e) {
                                e.printStackTrace();
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                        },
                        () -> registerNewUser(chatId, ""));
    }

    private void sendMessage(Long chatId, String textToSend) {
        SendMessage sendMessage = new SendMessage();
        sendMessage.setChatId(String.valueOf(chatId));
        sendMessage.setText(textToSend);
        try {
            execute(sendMessage);
        } catch (TelegramApiException ignored) {

        }
    }

    private void sendFile(Long chatId, File file, String caption) {
        SendDocument sendDocumentRequest = new SendDocument();
        sendDocumentRequest.setChatId(chatId);
        sendDocumentRequest.setDocument(new InputFile(file));
        sendDocumentRequest.setCaption(caption);
        try {
            execute(sendDocumentRequest);
        } catch (TelegramApiException ignored) {

        }
    }

    private void registerNewUser(long chatId, String email) {
        User user = User.builder()
                .chatId(chatId)
                .email(email)
                .embedFonts(true)
                .getConvertedFile(false)
                .margins("")
                .build();
        userRepository.save(user);
        sendMessage(chatId, "Now you are registered. You can start converting!");
    }
}

