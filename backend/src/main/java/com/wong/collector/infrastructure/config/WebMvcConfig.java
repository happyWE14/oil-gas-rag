package com.wong.collector.infrastructure.config;

import java.nio.file.Paths;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

// config/WebMvcConfig.java
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final String storagePath;

    public WebMvcConfig(@Value("${paper.storage.path:./data/papers}") String storagePath) {
        this.storagePath = storagePath;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/api/file/**")
                .addResourceLocations(Paths.get(storagePath).toAbsolutePath().normalize().toUri().toString());
    }
}
