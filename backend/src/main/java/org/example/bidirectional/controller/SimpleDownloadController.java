package org.example.bidirectional.controller;

import org.example.bidirectional.config.ConnectionConfig;
import org.example.bidirectional.service.ClickHouseService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.beans.factory.annotation.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.*;
import java.util.*;

/**
 * Controller for direct downloads that connects to ClickHouse database
 * Based on implementation patterns from Kendo007/bidirectional
 */
@RestController
public class SimpleDownloadController {
    private static final Logger logger = LoggerFactory.getLogger(SimpleDownloadController.class);

    @Value("${config.frontend:http://localhost:5173}")
    private String frontendUrl;
    
    @PostConstruct
    public void init() {
        logger.info("SimpleDownloadController initialized - Frontend URL: {}", frontendUrl);
    }

    /**
     * Direct download endpoint at root level that connects to ClickHouse
     * Downloads data from the uk_price_paid table by default or a table specified in the request parameter
     */
    @GetMapping("/direct-download")
    public void directDownload(
            @RequestParam(value = "table", defaultValue = "uk_price_paid") String tableName,
            @RequestParam(value = "limit", defaultValue = "1000") int limit,
            HttpServletResponse response) throws IOException {
        
        logger.info("Direct download requested for table: {}, limit: {}", tableName, limit);
        
        try {
            // Configure connection
            ConnectionConfig config = new ConnectionConfig();
            config.setHost("host.docker.internal");
            config.setPort(8123);
            config.setProtocol("http");
            config.setDatabase("uk");
            config.setUsername("default");
            config.setPassword("default");
            config.setAuthType("password");
            
            // Create ClickHouse service
            ClickHouseService clickHouseService = new ClickHouseService(config);
            
            // Set response headers
            response.setContentType("text/csv");
            response.setHeader("Content-Disposition", "attachment; filename=" + tableName + ".csv");
            
            // Execute query and stream results
            String query = String.format("SELECT * FROM %s LIMIT %d FORMAT CSVWithNames", tableName, limit);
            logger.info("Executing query: {}", query);
            
            try (OutputStream out = response.getOutputStream()) {
                clickHouseService.executeQueryToOutputStream(query, out, "");
            }
            
            logger.info("Download completed successfully");
        } catch (Exception e) {
            logger.error("Download failed: {}", e.getMessage(), e);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.setContentType("application/json");
            try (PrintWriter writer = response.getWriter()) {
                writer.write("{\"error\": \"" + e.getMessage() + "\"}");
            }
        }
    }
    
    /**
     * Sets up the response headers for file download
     */
    private void setupResponseHeaders(HttpServletResponse response, String filename) {
        // Set CORS headers
        response.setHeader("Access-Control-Allow-Origin", "*");
        response.setHeader("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        response.setHeader("Access-Control-Allow-Headers", "Content-Type");
        response.setHeader("Access-Control-Expose-Headers", "Content-Disposition, Content-Length, X-Line-Count");
        
        // Set response headers for file download
        response.setContentType("text/csv");
        response.setHeader("Content-Disposition", "attachment; filename=\"" + filename + "\"");
        
        // Set cache control headers
        response.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
        response.setHeader("Pragma", "no-cache");
        response.setHeader("Expires", "0");
    }
    
    /**
     * Sends an error response in case of failure
     */
    private void sendErrorResponse(HttpServletResponse response, String message) {
        try {
            response.setStatus(HttpStatus.INTERNAL_SERVER_ERROR.value());
            response.setContentType("text/plain");
            response.getWriter().write("Failed to generate direct download: " + message);
        } catch (IOException ex) {
            System.err.println("Could not send error response: " + ex.getMessage());
        }
    }

    /**
     * Simple health check endpoint
     */
    @GetMapping("/health")
    public Map<String, String> health() {
        Map<String, String> status = new HashMap<>();
        status.put("status", "UP");
        status.put("timestamp", new Date().toString());
        status.put("database", "uk");
        status.put("table", "uk_price_paid");
        return status;
    }
}
