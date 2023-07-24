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
    private final ConverterService converterService;
    private final UserRepository userRepository;

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

                if (messageText.startsWith("/start")) {
                    startCommandReceived(chatId, update.getMessage().getChat().getFirstName());
                } else if (messageText.startsWith("/email")) {
                    emailCommandReceived(chatId, messageText);
                } else if (messageText.startsWith("/users")) {
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
                "You need to setup your Kindle email using '/email' command." + "\n" +
                "And also add fb2kindle.bot@gmail.com to your" + "\n" +
                "Approved Personal Document E-mail List in" + "\n" +
                "https://www.amazon.com/hz/mycd/digital-console/contentlist/pdocs/dateDsc" + "\n" +
                "Manage Your Content & Devices> Preferences > Personal Document Settings";
        sendMessage(chatId, answer);
    }

    private void emailCommandReceived(long chatId, String message) {
        if ("/email".equals(message)) {
            userRepository
                    .findById(chatId)
                    .ifPresentOrElse(user -> sendMessage(chatId, "Your email is set to: " + user.getEmail()),
                            () -> sendMessage(chatId, "Your haven't set email yet!"));
        } else {
            String email = message.substring(6).trim();
            if (email.endsWith("@kindle.com")) {
                User user = new User(chatId, email);
                userRepository.save(user);
                sendMessage(chatId, "Email is set to: " + email);
            } else {
                sendMessage(chatId, "Only @kindle.com domain is accepted!");
            }
        }
    }

    private void usersCommandReceived(long chatId) {
        if (String.valueOf(chatId).equals(botConfig.getAdminChatId())) {
            List<User> userList = userRepository.findAll();
            sendMessage(
                    chatId,
                    "List of all registered users:\n" +
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
                                Path inputFilePathString = Path.of("./data/userFiles/" + getID + "_" + docName);

                                sendMessage(chatId, "Conversion started. Please wait, it may take a while...");
                                File savedFile = downloadFile(telegramFile, inputFilePathString.toFile());
                                Path savedFilePath = Path.of(savedFile.getCanonicalPath());

                                CompletableFuture<ConverterService.ConversionResult> conversionResultCompletableFuture =
                                        converterService.convert(savedFilePath, chatId, user.getEmail());

                                conversionResultCompletableFuture.thenApply(conversionResult -> {
                                    int exitValue = conversionResult.getCode();
                                    if (exitValue == 0) {
                                        sendMessage(chatId,
                                                "File " + conversionResult.getPath().getFileName() +
                                                        " was converted and send to: " + user.getEmail());
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
                        () -> sendMessage(chatId, "Your need set your e-mail with '/email' command first!"));
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

    private void sendFile(Long chatId, File file, String caption) throws TelegramApiException {
        SendDocument sendDocumentRequest = new SendDocument();
        sendDocumentRequest.setChatId(chatId);
        sendDocumentRequest.setDocument(new InputFile(file));
        sendDocumentRequest.setCaption(caption);
        execute(sendDocumentRequest);
    }
}

