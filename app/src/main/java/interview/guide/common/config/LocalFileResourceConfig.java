package interview.guide.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Exposes locally stored files as static resources.
 */
@Configuration
public class LocalFileResourceConfig implements WebMvcConfigurer {

    private final StorageConfigProperties storageConfigProperties;

    public LocalFileResourceConfig(StorageConfigProperties storageConfigProperties) {
        this.storageConfigProperties = storageConfigProperties;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        Path baseDir = Paths.get(storageConfigProperties.getBaseDir()).toAbsolutePath().normalize();
        String location = baseDir.toUri().toString();
        registry.addResourceHandler(storageConfigProperties.getPublicUrlPrefix() + "/**")
            .addResourceLocations(location.endsWith("/") ? location : location + "/");
    }
}
