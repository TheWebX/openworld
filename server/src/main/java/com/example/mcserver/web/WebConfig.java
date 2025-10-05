package com.example.mcserver.web;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Paths;

@Configuration
public class WebConfig implements WebMvcConfigurer {
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String dist = Paths.get("client/dist").toAbsolutePath().toUri().toString();
        registry
            .addResourceHandler("/assets/**")
            .addResourceLocations(dist);
        registry
            .addResourceHandler("/index.html")
            .addResourceLocations(dist);
        registry
            .addResourceHandler("/favicon.ico")
            .addResourceLocations(dist);
    }
}
