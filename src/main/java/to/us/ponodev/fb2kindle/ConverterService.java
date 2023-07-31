package to.us.ponodev.fb2kindle;

import com.google.gson.Gson;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import to.us.ponodev.fb2kindle.config.ConverterConfig;
import to.us.ponodev.fb2kindle.entity.User;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Slf4j
@AllArgsConstructor
@Service
public class ConverterService {

    private final ConverterConfig converterConfig;

    @Async
    public CompletableFuture<ConversionResult> convert(Path inputFile, long chatId, User user) throws InterruptedException, IOException {
        log.info("Converting started. File: " + inputFile.getFileName());

        String converterPath = converterConfig.getConverterPath();

        String profile = "profiles/default.css";
        if (!user.isEmbedFonts()) {
            profile = "profiles/default-no-fonts.css";
        }

        ProcessBuilder processBuilder = new ProcessBuilder();
        processBuilder.directory(new File(converterPath));
        processBuilder.command(converterPath + "/run.sh",
                String.valueOf(chatId),
                user.getEmail(),
                inputFile.toAbsolutePath().toString(),
                profile);
        processBuilder.redirectErrorStream(true);
        Process process;
        try {
            process = processBuilder.start();
        } catch (IOException e) {
            log.error(e.getMessage());
            return CompletableFuture.completedFuture(
                    new ConversionResult(1, "fb2c converter error!", null));
        }

        StringBuilder output = new StringBuilder();

        BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream()));

        String line;
        while ((line = reader.readLine()) != null) {
            log.info(line);
            output.append(line).append("\n");
        }

        int exitVal = process.waitFor();

        String result = "";
        for (String l : output.toString().split("\n")) {
            if (l.contains("Conversion completed") || l.contains("Command ended with error")) {
                result = l.substring(l.indexOf('{'), l.lastIndexOf('}') + 1);
                break;
            }
        }

        Gson gson = new Gson();
        Map jsonResultMap = gson.fromJson(result, Map.class);

        ConversionResult conversionResult;

        if (exitVal == 0) {
            log.info("Elapsed time: {}", jsonResultMap.get("elapsed"));
            Path resultPath = Path.of(String.valueOf(jsonResultMap.get("to")));
            log.info("Result file path: {}", resultPath);
            conversionResult = new ConversionResult(0, "Conversion successful", resultPath);
            if (converterConfig.isDeleteInputFile()) {
                inputFile.toFile().delete();
            }
            if (converterConfig.isDeleteOutputFile()) {
                resultPath.toFile().delete();
            }
        } else {
            Object errorMessage = jsonResultMap.get("error");
            log.info("Error detected: {}", errorMessage);
            conversionResult = new ConversionResult(1, String.valueOf(errorMessage), null);
        }

        return CompletableFuture.completedFuture(conversionResult);
    }

    @AllArgsConstructor
    @Getter
    public static class ConversionResult {
        int code;
        String message;
        Path path;
    }

}
