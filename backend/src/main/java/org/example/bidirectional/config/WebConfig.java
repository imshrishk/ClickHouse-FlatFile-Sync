package org.example.bidirectional.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {
    @Value("${config.frontend}")
    private String frontendOrigin;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        String[] origins = frontendOrigin.split(",\\s*");
        registry.addMapping("/api/**")
                .allowedOrigins(origins) // Use the array of origins from application.properties
                .allowedMethods("GET", "POST", "PUT", "DELETE")
                .allowCredentials(true);
    }
}
