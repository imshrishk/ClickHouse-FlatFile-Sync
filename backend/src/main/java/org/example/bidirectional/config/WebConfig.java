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
        
        // Log the configured origins
        System.out.println("Configuring CORS for origins: " + String.join(", ", origins));
        
        registry.addMapping("/**")
                .allowedOrigins(origins) // Use the array of origins from application.properties
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .exposedHeaders("Content-Disposition", "Content-Length", "X-Line-Count")
                .allowCredentials(true)
                .maxAge(3600); // 1 hour
    }
}
