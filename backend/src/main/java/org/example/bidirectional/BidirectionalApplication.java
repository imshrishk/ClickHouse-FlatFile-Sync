package org.example.bidirectional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.Banner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.env.Environment;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;

/**
 * Main application class for the Bidirectional data transfer system.
 * This application enables seamless data transfer between ClickHouse databases
 * and flat files (primarily CSV) in both directions.
 */
@SpringBootApplication
public class BidirectionalApplication {
    private static final Logger logger = LoggerFactory.getLogger(BidirectionalApplication.class);

    /**
     * Main method to start the application. Configures the Spring Boot application
     * and adds additional startup logging.
     * 
     * @param args Command line arguments
     */
    public static void main(String[] args) {
        System.out.println("!!!!!!!!!! BIDIRECTIONAL APP MAIN START !!!!!!!!!!");
        System.out.println("!!!!!!!!!! java.io.tmpdir IS: " + System.getProperty("java.io.tmpdir") + " !!!!!!!!!!");
        try {
            System.out.println("!!!!!!!!!! CHECKPOINT 1 (Before logger.info) !!!!!!!!!!");
            logger.info("Starting ClickHouse-FlatFile-Sync application...");
            logger.info("Java version: {}", System.getProperty("java.version"));
            logger.info("Application arguments: {}", Arrays.toString(args));
            
            System.out.println("!!!!!!!!!! CHECKPOINT 2 (Before new SpringApplication) !!!!!!!!!!");
            SpringApplication app = new SpringApplication(BidirectionalApplication.class);
            System.out.println("!!!!!!!!!! CHECKPOINT 3 (After new SpringApplication, before setBannerMode) !!!!!!!!!!");
            app.setBannerMode(Banner.Mode.CONSOLE);
            app.addListeners(new ApplicationStartupListener());
            
            System.out.println("!!!!!!!!!! CHECKPOINT 4 (Before app.run) !!!!!!!!!!");
            app.run(args);
            System.out.println("!!!!!!!!!! CHECKPOINT 5 (After app.run) !!!!!!!!!!"); // This might not be reached if run() is blocking

            // Add this to keep the main thread's context alive if app.run() returns prematurely
            Thread keepAliveThread = new Thread(() -> {
                while (true) {
                    try {
                        Thread.sleep(Long.MAX_VALUE);
                    } catch (InterruptedException e) {
                        // Handle interruption if necessary
                        System.out.println("!!!!!!!!!! Keep-alive thread interrupted !!!!!!!!!!");
                        break;
                    }
                }
            });
            keepAliveThread.setDaemon(false); // Ensure it's a non-daemon thread
            keepAliveThread.setName("AppKeepAlive");
            keepAliveThread.start();
            System.out.println("!!!!!!!!!! Keep-alive thread started !!!!!!!!!!");
            
            logger.info("Application startup completed successfully");
        } catch (Exception e) {
            System.out.println("!!!!!!!!!! MAIN METHOD EXCEPTION: " + e.getMessage() + " !!!!!!!!!!");
            e.printStackTrace(System.out);
            logger.error("Application failed to start", e);
            System.exit(1);
        }
    }
    
    /**
     * Listener for application ready events that logs access URLs and
     * application configuration details when startup is complete.
     */
    static class ApplicationStartupListener implements ApplicationListener<ApplicationReadyEvent> {
        private final Logger startupLogger = LoggerFactory.getLogger(ApplicationStartupListener.class);
        
        @Override
        public void onApplicationEvent(ApplicationReadyEvent event) {
            System.out.println("!!!!!!!!!! CHECKPOINT 6 (ApplicationStartupListener.onApplicationEvent START) !!!!!!!!!!");
            try {
                Environment env = event.getApplicationContext().getEnvironment();
                String protocol = env.getProperty("server.ssl.key-store") != null ? "https" : "http";
                String serverPort = env.getProperty("server.port", "8080");
                String contextPath = env.getProperty("server.servlet.context-path", "/");
                if (!contextPath.endsWith("/")) {
                    contextPath += "/";
                }
                
                String hostAddress = "localhost";
                try {
                    hostAddress = InetAddress.getLocalHost().getHostAddress();
                } catch (UnknownHostException e) {
                    startupLogger.warn("Could not determine host address", e);
                }
                
                startupLogger.info("\n----------------------------------------------------------\n\t" +
                        "Application '{}' is running! Access URLs:\n\t" +
                        "Local: \t\t{}://localhost:{}{}\n\t" +
                        "External: \t{}://{}:{}{}\n\t" +
                        "API docs: \t{}://localhost:{}{}{}\n\t" +
                        "Profile(s): \t{}\n----------------------------------------------------------",
                        env.getProperty("spring.application.name"),
                        protocol, serverPort, contextPath,
                        protocol, hostAddress, serverPort, contextPath,
                        protocol, serverPort, contextPath, "swagger-ui/",
                        env.getActiveProfiles().length == 0 ? "default" : Arrays.toString(env.getActiveProfiles()));
                
                // Log application configuration settings
                logConfigurationSettings(env);
                System.out.println("!!!!!!!!!! CHECKPOINT 8 (ApplicationStartupListener.onApplicationEvent END) !!!!!!!!!!");
            } catch (Exception e) {
                System.out.println("!!!!!!!!!! ApplicationStartupListener EXCEPTION: " + e.getMessage() + " !!!!!!!!!!");
                e.printStackTrace(System.out);
                startupLogger.error("Error occurred while logging application startup information", e);
            }
        }
        
        /**
         * Logs important configuration settings for diagnostics.
         * 
         * @param env Spring environment
         */
        private void logConfigurationSettings(Environment env) {
            System.out.println("!!!!!!!!!! CHECKPOINT 7 (ApplicationStartupListener.logConfigurationSettings START) !!!!!!!!!!");
            startupLogger.info("Application Configuration Settings:");
            startupLogger.info("  Database configuration: {}", 
                    maskSensitiveConfig(env.getProperty("spring.datasource.url", "Not configured")));
            startupLogger.info("  Maximum file upload size: {}", 
                    env.getProperty("spring.servlet.multipart.max-file-size", "1MB"));
            startupLogger.info("  Maximum request size: {}", 
                    env.getProperty("spring.servlet.multipart.max-request-size", "10MB"));
            startupLogger.info("  Enable cross-origin requests: {}", 
                    env.getProperty("config.enableCors", "false"));
            startupLogger.info("  Frontend URL: {}", 
                    env.getProperty("config.frontend", "http://localhost:5173"));
            startupLogger.info("  Temporary file storage: {}", 
                    env.getProperty("java.io.tmpdir"));
        }
        
        /**
         * Masks sensitive information in configuration strings for logging.
         * 
         * @param config The configuration string to mask
         * @return The masked configuration string
         */
        private String maskSensitiveConfig(String config) {
            if (config == null) {
                return "null";
            }
            
            // Mask password in JDBC URL if present
            if (config.contains("password=")) {
                return config.replaceAll("password=([^&]*)", "password=*****");
            }
            
            return config;
        }
    }
}
