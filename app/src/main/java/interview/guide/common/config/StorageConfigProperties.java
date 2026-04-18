package interview.guide.common.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Local file storage configuration.
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.storage")
public class StorageConfigProperties {

    private String baseDir = "./data";
    private String resumeDir = "./data/resume";
    private String knowledgeDir = "./data/knowledge";
    private String publicUrlPrefix = "/api/files";
}
