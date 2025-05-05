package org.example.bidirectional.controller;

import org.example.bidirectional.config.ConnectionConfig;
import org.example.bidirectional.service.ClickHouseService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.beans.factory.annotation.Value;

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

    @Value("${config.frontend:http://localhost:5173}")
    private String frontendUrl;
    
    @PostConstruct
    public void init() {
        System.out.println("SimpleDownloadController initialized - Frontend URL: " + frontendUrl);
    }

    /**
     * Direct download endpoint at root level that connects to ClickHouse
     * Downloads data from the uk_price_paid table by default or a table specified in the request parameter
     */
    @GetMapping("/direct-download")
    public void directDownload(HttpServletRequest request, HttpServletResponse response) {
        System.out.println("Direct download endpoint called - Retrieving real data from ClickHouse");
        
        try {
            // Determine which table to download from based on request parameter or use default
            String tableName = request.getParameter("table");
            if (tableName == null || tableName.trim().isEmpty()) {
                tableName = "uk_price_paid";
            }
            
            // Create connection config
            ConnectionConfig connectionConfig = new ConnectionConfig();
            connectionConfig.setProtocol("http");
            connectionConfig.setHost("clickhouse");
            connectionConfig.setPort(8123);
            connectionConfig.setUsername("default");
            connectionConfig.setPassword("clickhouse");
            connectionConfig.setDatabase("uk");
            connectionConfig.setAuthType("password");
            
            // Initialize service
            ClickHouseService clickHouseService = new ClickHouseService(connectionConfig);
            
            // Set headers for the response
            setupResponseHeaders(response, tableName + "_data.csv");
            
            // Execute query and stream results
            try (OutputStream outputStream = response.getOutputStream()) {
                String query = "SELECT * FROM " + tableName + " FORMAT CSVWithNames";
                System.out.println("Executing query: " + query);
                clickHouseService.executeQueryToOutputStream(query, outputStream, "");
                
                outputStream.flush();
                System.out.println("Successfully sent real ClickHouse data from table: " + tableName);
            }
        } catch (Exception e) {
            System.err.println("Error in direct download: " + e.getMessage());
            e.printStackTrace();
            sendErrorResponse(response, e.getMessage());
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
