package com.talentus.scout.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Value("${talentus.video.upload-dir:uploads/videos}")
    private String uploadDir;

    @Value("${talentus.storage.base-dir:uploads}")
    private String baseStorageDir;

    @Value("${talentus.storage.provider:local}")
    private String storageProvider;

    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mapper.disable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES);
        return mapper;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        if ("r2".equalsIgnoreCase(storageProvider)) {
            // En producción con Cloudflare R2 no se exponen directorios locales por seguridad (LOPNNA)
            return;
        }

        Path uploadPath = Paths.get(uploadDir).toAbsolutePath().normalize();
        String uploadUri = uploadPath.toUri().toString();
        if (!uploadUri.endsWith("/")) {
            uploadUri += "/";
        }

        registry.addResourceHandler("/uploads/videos/**")
                .addResourceLocations(uploadUri)
                .setCacheControl(CacheControl.maxAge(Duration.ofDays(7)).cachePublic());

        Path basePath = Paths.get(baseStorageDir).toAbsolutePath().normalize();
        String baseUri = basePath.toUri().toString();
        if (!baseUri.endsWith("/")) {
            baseUri += "/";
        }

        registry.addResourceHandler("/uploads/**")
                .addResourceLocations(baseUri)
                .setCacheControl(CacheControl.maxAge(Duration.ofDays(7)).cachePublic());
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/uploads/**")
                .allowedOriginPatterns(
                        "http://localhost:*",
                        "http://127.0.0.1:*",
                        "http://localhost",
                        "http://127.0.0.1",
                        "https://talentusscout.com",
                        "https://www.talentusscout.com",
                        "https://*.talentusscout.com"
                )
                .allowedMethods("GET", "HEAD", "OPTIONS");
    }
}
