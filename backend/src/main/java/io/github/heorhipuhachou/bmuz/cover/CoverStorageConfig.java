package io.github.heorhipuhachou.bmuz.cover;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** S3, калі зададзена bmuz.covers.bucket; інакш — лякальная тэчка, якую бэкэнд сам аддае па /covers/**. */
@Configuration
class CoverStorageConfig implements WebMvcConfigurer {

    private final String bucket;
    private final Path localDir;

    CoverStorageConfig(@Value("${bmuz.covers.bucket:}") String bucket,
                       @Value("${bmuz.covers.local-dir:covers-local}") String localDir) {
        this.bucket = bucket;
        this.localDir = Path.of(localDir).toAbsolutePath();
    }

    @Bean
    CoverStorage coverStorage() {
        if (!bucket.isBlank()) {
            S3Client s3 = S3Client.create();
            return new CoverStorage() {
                @Override
                public void put(String key, byte[] jpeg) {
                    s3.putObject(b -> b.bucket(bucket).key(key).contentType("image/jpeg")
                            .cacheControl("public, max-age=31536000, immutable"), RequestBody.fromBytes(jpeg));
                }

                @Override
                public void delete(String key) {
                    s3.deleteObject(b -> b.bucket(bucket).key(key));
                }
            };
        }
        return new CoverStorage() {
            @Override
            public void put(String key, byte[] jpeg) throws IOException {
                Path file = localDir.resolve(key).normalize();
                Files.createDirectories(file.getParent());
                Files.write(file, jpeg);
            }

            @Override
            public void delete(String key) throws IOException {
                Files.deleteIfExists(localDir.resolve(key).normalize());
            }
        };
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        if (bucket.isBlank()) {
            registry.addResourceHandler("/covers/**").addResourceLocations(localDir.resolve("covers").toUri().toString() + "/");
        }
    }
}
