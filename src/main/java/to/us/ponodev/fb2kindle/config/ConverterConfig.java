package to.us.ponodev.fb2kindle.config;

import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;


@Data
@PropertySource("application.properties")
@Configuration
public class ConverterConfig {

    @Value("${converter.path}")
    String converterPath;

    @Value("${converter.deleteInputFile}")
    boolean deleteInputFile;

    @Value("${converter.deleteOutputFile}")
    boolean deleteOutputFile;

    @Value("${converter.defaultMargin}")
    String defaultMargin;

}