package org.example.bidirectional.controller;

import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;
import com.opencsv.CSVWriter;
import com.opencsv.exceptions.CsvException;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.example.bidirectional.config.*;
import org.example.bidirectional.exception.AuthenticationException;
import org.example.bidirectional.model.ColumnInfo;
import org.example.bidirectional.service.ClickHouseService;
import org.example.bidirectional.service.FileService;
import org.example.bidirectional.service.IngestionService;
import org.example.bidirectional.service.CsvPreviewService;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.Enumeration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestController
@RequestMapping("/clickhouse")
public class IngestionController {
    private static final Logger logger = LoggerFactory.getLogger(IngestionController.class);

    @Value("${config.frontend}")
    private String frontendUrl;

    @Autowired
    private CsvPreviewService csvPreviewService;

    @PostConstruct
    public void init() {
        logger.info("IngestionController initialized. Frontend URL: {}", frontendUrl);
    }

    // Add a simple health check endpoint for testing connectivity
    @GetMapping("/health-test")
    public ResponseEntity<Map<String, Object>> healthTest() {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "ok");
        response.put("timestamp", System.currentTimeMillis());
        response.put("message", "ClickHouse controller is responsive");
        
        logger.info("Health check endpoint called on IngestionController");
        return ResponseEntity.ok(response);
    }

    @PostMapping("/test-connection")
    public ResponseEntity<?> testConnection(@RequestBody ConnectionConfig props) {
        try {
            ClickHouseService clickHouseService = new ClickHouseService(props);

            return ResponseEntity.ok(Collections.singletonMap("success", clickHouseService.testConnection()));
        } catch (AuthenticationException e) {
            return ResponseEntity.status(401).body(false);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(false);
        }
    }

    @PostMapping("/tables")
    public ResponseEntity<Map<String, List<String>>> listTables(@RequestBody TablesConfig tablesConfig) {
        ClickHouseService clickHouseService = new ClickHouseService(tablesConfig.getConnection());
        return ResponseEntity.ok(Collections.singletonMap("tables", clickHouseService.listTables()));
    }

    @PostMapping("/columns")
    public ResponseEntity<Map<String, List<ColumnInfo>>> getColumns(@RequestBody ColumnConfig props) {
        ClickHouseService clickHouseService = new ClickHouseService(props.getConnection());
        return ResponseEntity.ok(Collections.singletonMap("columns", clickHouseService.getColumns(props.getTableName())));
    }

    /**
     * Extracts the header and data from the given list of rows
     */
    private ResponseEntity<Map<String, Object>> getHeadAndData(List<String[]> rows) {
        if (rows.isEmpty()) {
            return ResponseEntity.ok(Map.of("headers", List.of(), "rows", List.of()));
        }

        List<String> headers = List.of(rows.getFirst());
        List<String[]> data = rows.subList(1, rows.size());

        return ResponseEntity.ok(Map.of(
                "headers", headers,
                "rows", data
        ));
    }

    @PostMapping("/query-selected-columns")
    public ResponseEntity<Map<String, Object>> querySelectedColumns(@RequestBody SelectedColumnsQueryConfig config) throws Exception {
        try {
            ClickHouseService clickHouseService = new ClickHouseService(config.getConnection());

            // Add more detailed logging
            logger.info("Processing query-selected-columns request for table: {}, columns: {}, limit: {}",
                    config.getTableName(),
                    config.getColumns() != null ? config.getColumns().size() : 0,
                    config.getLimit());

            List<String[]> rows = clickHouseService.querySelectedColumns(
                    config.getTableName(),
                    config.getColumns(),
                    config.getJoinTables(),
                    config.getDelimiter(),
                    config.getLimit()
            );

            // Build response: first row is headers, remaining rows are data
            logger.info("Query completed successfully, returning {} rows", rows.size() - 1);
            return getHeadAndData(rows);
        } catch (Exception e) {
            logger.error("Failed to query selected columns: {}", e.getMessage(), e);
            throw new IllegalArgumentException("Failed to query selected columns: " + e.getMessage());
        }
    }

    /**
     * Endpoint for previewing CSV data with optimized large file handling
     * Uses stream-based processing and limits preview to a reasonable number of rows
     */
    @PostMapping(value = "/preview-csv", consumes = {"multipart/form-data"})
    public ResponseEntity<Map<String, Object>> previewCSV(
            @RequestPart("file") MultipartFile file,
            @RequestParam(value = "delimiter", defaultValue = ",") String delimiter,
            @RequestParam(value = "hasHeader", required = false, defaultValue = "true") boolean hasHeader
    ) throws Exception {
        logger.info("Preview CSV request received for file: {}, size: {}, delimiter: '{}', hasHeader: {}", 
                   file.getOriginalFilename(), formatFileSize(file.getSize()), delimiter, hasHeader);
        
        try {
            char delimiterChar = ClickHouseService.convertStringToChar(delimiter);
            
            // Try to show the first ~500 bytes of the file for debugging
            try (InputStream debugStream = file.getInputStream()) {
                byte[] debugBytes = new byte[500];
                int read = debugStream.read(debugBytes);
                if (read > 0) {
                    String preview = new String(debugBytes, 0, read, StandardCharsets.UTF_8);
                    logger.debug("File content preview: \n{}", preview);
                }
            } catch (Exception e) {
                logger.warn("Could not read debug preview: {}", e.getMessage());
            }
            
            // For very large files, create a temporary file
            if (file.getSize() > 10 * 1024 * 1024) { // 10MB threshold
                logger.info("Using large file optimization for file of size: {}", 
                           formatFileSize(file.getSize()));
                
                Path tempFile = Files.createTempFile("preview_", ".csv");
                try {
                    // Transfer only the first part of the file (max 1MB for preview)
                    try (InputStream in = file.getInputStream();
                         OutputStream out = Files.newOutputStream(tempFile)) {
                        byte[] buffer = new byte[8192];
                        int bytesRead;
                        long totalRead = 0;
                        long previewLimit = 1024 * 1024; // 1MB max
                        
                        while ((bytesRead = in.read(buffer)) != -1 && totalRead < previewLimit) {
                            out.write(buffer, 0, bytesRead);
                            totalRead += bytesRead;
                        }
                    }
                    
                    // Process the temp file with our specialized service
                    try (InputStream fileStream = Files.newInputStream(tempFile)) {
                        Map<String, Object> result = csvPreviewService.createPreview(fileStream, delimiterChar, hasHeader);
                        return ResponseEntity.ok(result);
                    }
                } finally {
                    try {
                        Files.deleteIfExists(tempFile);
                    } catch (Exception e) {
                        logger.warn("Failed to delete temp file: {}", tempFile);
                    }
                }
            } else {
                // For smaller files, process directly
                try (InputStream inputStream = file.getInputStream()) {
                    Map<String, Object> result = csvPreviewService.createPreview(inputStream, delimiterChar, hasHeader);
                    return ResponseEntity.ok(result);
                }
            }
        } catch (Exception e) {
            logger.error("Failed to preview CSV file: {}", e.getMessage(), e);
            
            // Provide a graceful error response rather than throwing
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", "Failed to parse CSV: " + e.getMessage());
            errorResponse.put("headers", List.of("Error"));
            errorResponse.put("rows", List.of(new String[]{"Failed to parse CSV file. Check the format and delimiter."}));
            errorResponse.put("hasHeader", hasHeader);
            
            return ResponseEntity.ok(errorResponse);
        }
    }

    /**
     * Direct download endpoint that streams data to the response
     */
    @PostMapping("/download")
    public void downloadData(@RequestBody SelectedColumnsQueryConfig request, HttpServletResponse response) throws IOException {
        long lineCount = 0;
        
        System.out.println("Download request received with " + 
                          (request != null ? "valid request for table: " + request.getTableName() : "null request"));
        
        try {
            if (request == null || request.getConnection() == null) {
                response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid request configuration");
                return;
            }
            
            ClickHouseService clickHouseService = new ClickHouseService(request.getConnection());
            IngestionService ingestionService = new IngestionService(clickHouseService);

            // Create a clean filename from the table name
            String cleanFilename = request.getTableName().replaceAll("[^a-zA-Z0-9-_]", "_") + ".csv";
            System.out.println("Preparing download for: " + cleanFilename);
            
            // Set CORS headers explicitly
            response.setHeader("Access-Control-Allow-Origin", "*");
            response.setHeader("Access-Control-Allow-Methods", "POST, GET, OPTIONS");
            response.setHeader("Access-Control-Allow-Headers", "Content-Type");
            response.setHeader("Access-Control-Expose-Headers", "Content-Disposition, Content-Length, X-Line-Count");
            
            // Set response headers for file download before writing to output stream
            response.setContentType("text/csv");
            response.setHeader("Content-Disposition", "attachment; filename=\"" + cleanFilename + "\"");
            response.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
            response.setHeader("Pragma", "no-cache");
            response.setHeader("Expires", "0");
            
            try {
                // Stream directly to the client response
                OutputStream outputStream = response.getOutputStream();
                System.out.println("Starting data stream to client...");
                
                try {
                    lineCount = ingestionService.streamDataToOutputStream(request, outputStream);
                    response.setHeader("X-Line-Count", String.valueOf(lineCount));
                    System.out.println("Completed streaming " + lineCount + " lines");
                } catch (Exception e) {
                    System.err.println("Error streaming data: " + e.getMessage());
                    e.printStackTrace();
                    throw new RuntimeException("Error streaming data: " + e.getMessage(), e);
                } finally {
                    // Ensure the stream is flushed even if an error occurs
                    try {
                        outputStream.flush();
                    } catch (Exception ignored) {}
                }
            } catch (Exception e) {
                System.err.println("Error getting output stream: " + e.getMessage());
                e.printStackTrace();
                throw new RuntimeException("Error getting output stream: " + e.getMessage(), e);
            }
        } catch (Exception e) {
            System.err.println("Download endpoint error: " + e.getMessage());
            e.printStackTrace();
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.setContentType("text/plain");
            response.getWriter().write("Failed to generate download: " + e.getMessage());
        }
    }

    // Keep the original method for compatibility but mark it as deprecated
    @Deprecated
    @PostMapping("/download-file")
    public ResponseEntity<FileSystemResource> ingestToFile(@RequestBody SelectedColumnsQueryConfig request) throws IOException {
        long lineCount = 0;
        try {
            ClickHouseService clickHouseService = new ClickHouseService(request.getConnection());
            IngestionService ingestionService = new IngestionService(clickHouseService);

            // Creating temp file and writing to it
            Path path = Files.createTempFile(request.getTableName() + "_export", ".csv");
            
            // Ensure the path is readable and writable
            File tempFile = path.toFile();
            tempFile.setReadable(true, false);
            tempFile.setWritable(true, false);
            
            try (OutputStream outStream = Files.newOutputStream(path)) {
                lineCount = ingestionService.streamDataToOutputStream(request, outStream);
            } catch (Exception e) {
                Files.deleteIfExists(path); // Clean up if streaming fails
                throw new RuntimeException("Error streaming data to file: " + e.getMessage(), e);
            }

            // Creating fileSystemResource for downloading
            FileSystemResource resource = getFileSystemResource(path);
            String cleanFilename = request.getTableName().replaceAll("[^a-zA-Z0-9-_]", "_") + ".csv";

            // Check if the file exists and has content
            if (!tempFile.exists() || tempFile.length() == 0) {
                throw new RuntimeException("Error creating download file or file is empty");
            }

            // Set headers to ensure proper file download
            HttpHeaders headers = new HttpHeaders();
            headers.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + cleanFilename + "\"");
            headers.add(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, "Content-Disposition, Content-Length, X-Line-Count");
            headers.add("X-Line-Count", String.valueOf(lineCount));
            headers.setContentType(MediaType.parseMediaType("text/csv"));
            headers.setContentLength(resource.contentLength());
            
            // Add cache control to prevent caching issues
            headers.setCacheControl("no-cache, no-store, must-revalidate");
            headers.setPragma("no-cache");
            headers.setExpires(0);
            
            return new ResponseEntity<>(resource, headers, HttpStatus.OK);
        } catch (Exception e) {
            e.printStackTrace();
            throw new IOException("Failed to generate download: " + e.getMessage(), e);
        }
    }

    private static FileSystemResource getFileSystemResource(Path path) {
        File file = path.toFile();
        return new FileSystemResource(file) {
            @Override
            public InputStream getInputStream() throws IOException {
                InputStream original = super.getInputStream();
                return new FilterInputStream(original) {
                    @Override
                    public void close() throws IOException {
                        super.close();

                        try { Files.deleteIfExists(file.toPath()); }
                        catch (IOException ignored) {}
                    }
                };
            }
        };
    }

    @PostMapping(value = "/upload", consumes = {"multipart/form-data"})
    public ResponseEntity<Map<String, Object>> ingestFromFile(
            @RequestPart("file") MultipartFile file,
            @RequestPart("config") String configJson,
            @RequestParam(value = "streaming", required = false, defaultValue = "false") boolean streaming
    ) {
        Map<String, Object> response = new HashMap<>();
        
        logger.info("Upload request received, file size: {} bytes, streaming mode: {}", 
                    file.getSize(), streaming ? "enabled" : "disabled");
        
        try {
            // Parse the config JSON string to UploadConfig POJO
            ObjectMapper mapper = new ObjectMapper();
            UploadConfig request = mapper.readValue(configJson, UploadConfig.class);

            // Setting up services
            ClickHouseService clickHouseService = new ClickHouseService(request.getConnection());
            IngestionService ingestionService = new IngestionService(clickHouseService);
            
            // Check if column types are provided in the config
            boolean hasColumnTypes = request.getColumnTypes() != null && !request.getColumnTypes().isEmpty();
            boolean needsColumnDetection = request.isCreateNewTable() && !hasColumnTypes;
            
            // If creating a new table and no column types provided, we need to detect them from the CSV
            if (needsColumnDetection) {
                logger.info("Auto-detecting columns for new table: {}", request.getTableName());
                
                // Parse the first part of the CSV to detect columns
                try (InputStream previewStream = file.getInputStream()) {
                    // Use CsvPreviewService to parse CSV headers and sample data
                    char delimiterChar = ClickHouseService.convertStringToChar(request.getDelimiter());
                    Map<String, Object> previewResult = csvPreviewService.createPreview(
                            previewStream, delimiterChar, true); // Assume headers for column detection
                    
                    if (previewResult.containsKey("error")) {
                        throw new Exception("Failed to extract column names: " + previewResult.get("error"));
                    }
                    
                    // Extract headers from the preview
                    Object headersObj = previewResult.get("headers");
                    if (headersObj == null) {
                        throw new Exception("No column headers detected in CSV");
                    }
                    
                    logger.info("Extracted headers: {}", headersObj);
                    
                    // Convert headers to column types map (defaulting all to String)
                    Map<String, String> columnTypes = new HashMap<>();
                    if (headersObj instanceof String[]) {
                        String[] headers = (String[]) headersObj;
                        for (String header : headers) {
                            columnTypes.put(header, "String"); // Default type for all columns
                        }
                    } else if (headersObj instanceof List) {
                        @SuppressWarnings("unchecked")
                        List<String> headers = (List<String>) headersObj;
                        for (String header : headers) {
                            columnTypes.put(header, "String"); // Default type for all columns
                        }
                    } else {
                        throw new Exception("Unexpected header format in CSV preview");
                    }
                    
                    // Set the detected column types in the request
                    request.setColumnTypes(columnTypes);
                    logger.info("Auto-detected {} columns for table {}", columnTypes.size(), request.getTableName());
                }
            }
            
            // Create the table if needed
            if (request.isCreateNewTable()) {
                logger.info("Creating new table: {}", request.getTableName());
                clickHouseService.createTable(request.getTableName(), request.getColumnTypes());
            }

            long lines;
            // For large files (over 1GB), use temp file to avoid memory issues
            if (streaming || file.getSize() > 1_073_741_824L) { // 1GB threshold
                logger.info("Using file-based streaming for large file ({})", 
                           formatFileSize(file.getSize()));
                
                // Create temporary file
                Path tempFile = Files.createTempFile("upload_", ".csv");
                try {
                    logger.debug("Created temp file: {}", tempFile);
                    
                    // Transfer the upload to the temp file
                    try (InputStream in = file.getInputStream();
                         OutputStream out = Files.newOutputStream(tempFile)) {
                        byte[] buffer = new byte[8192];
                        int bytesRead;
                        long totalRead = 0;
                        while ((bytesRead = in.read(buffer)) != -1) {
                            out.write(buffer, 0, bytesRead);
                            totalRead += bytesRead;
                            if (totalRead % 104_857_600 == 0) { // Log every 100MB
                                logger.debug("Wrote {}MB to temp file", totalRead / 1_048_576);
                            }
                        }
                    }
                    
                    // Process from the temp file
                    try (InputStream fileStream = Files.newInputStream(tempFile)) {
                        lines = ingestionService.ingestDataFromStream(
                                request.getTotalCols() != null ? request.getTotalCols() : request.getColumnTypes().size(),
                                request.getTableName(),
                                new ArrayList<>(request.getColumnTypes().keySet()),
                                request.getDelimiter(),
                                fileStream);
                    }
                } finally {
                    // Clean up the temp file
                    try {
                        Files.deleteIfExists(tempFile);
                        logger.debug("Deleted temp file: {}", tempFile);
                    } catch (Exception e) {
                        logger.warn("Failed to delete temp file: {}", tempFile, e);
                    }
                }
            } else {
                // For smaller files, stream directly from memory
                logger.info("Using direct streaming for smaller file ({})", 
                           formatFileSize(file.getSize()));
                           
                try (InputStream ingestionStream = file.getInputStream()) {
                    lines = ingestionService.ingestDataFromStream(
                            request.getTotalCols() != null ? request.getTotalCols() : request.getColumnTypes().size(),
                            request.getTableName(),
                            new ArrayList<>(request.getColumnTypes().keySet()),
                            request.getDelimiter(),
                            ingestionStream);
                }
            }

            response.put("lines", lines);
            response.put("success", true);
            response.put("message", "Upload successful");
            logger.info("Upload completed successfully, processed {} lines", lines);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Upload failed: {}", e.getMessage(), e);
            response.put("lines", 0);
            response.put("success", false);
            response.put("message", "Error: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
    
    /**
     * Helper method to format file size for logging
     */
    private String formatFileSize(long size) {
        if (size < 1024) return size + " B";
        int z = (63 - Long.numberOfLeadingZeros(size)) / 10;
        return String.format("%.1f %sB", (double)size / (1L << (z*10)), " KMGTPE".charAt(z));
    }

    @PostMapping("/types")
    public ResponseEntity<Map<String, ArrayList<String>>>  getTypes(@RequestBody TypesConfig typesConfig) {
        ClickHouseService clickHouseService = new ClickHouseService(typesConfig.getConnection());
        return ResponseEntity.ok(Collections.singletonMap("types", clickHouseService.getTypes()));
    }

    /**
     * Simplified GET endpoint for downloading data - easier for browsers to handle
     */
    @GetMapping("/simple-download")
    public void simpleDownload(HttpServletResponse response) throws IOException {
        System.out.println("Simple download endpoint called");
        
        try {
            // Create a simple sample file for testing downloads
            String csvContent = "id,name,value\n1,test1,100\n2,test2,200\n3,test3,300";
            String filename = "test_data.csv";
            System.out.println("Creating simple test file: " + filename);
            
            // Set CORS headers explicitly
            response.setHeader("Access-Control-Allow-Origin", "*");
            response.setHeader("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
            response.setHeader("Access-Control-Allow-Headers", "Content-Type");
            response.setHeader("Access-Control-Expose-Headers", "Content-Disposition, Content-Length");
            
            // Set response headers for file download
            response.setContentType("text/csv");
            response.setHeader("Content-Disposition", "attachment; filename=\"" + filename + "\"");
            response.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
            response.setHeader("Pragma", "no-cache");
            response.setHeader("Expires", "0");
            
            // Write directly to the response
            try (PrintWriter writer = response.getWriter()) {
                writer.write(csvContent);
                writer.flush();
                System.out.println("Wrote " + csvContent.length() + " bytes to response");
            } catch (Exception e) {
                System.err.println("Error writing to response: " + e.getMessage());
                e.printStackTrace();
            }
        } catch (Exception e) {
            System.err.println("Simple download error: " + e.getMessage());
            e.printStackTrace();
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.setContentType("text/plain");
            response.getWriter().write("Failed to generate download: " + e.getMessage());
        }
    }

    /**
     * Debug endpoint to show environment information and request details
     */
    @GetMapping("/env-test")
    public void envTest(HttpServletRequest request, HttpServletResponse response) throws IOException {
        System.out.println("Environment test endpoint called");
        
        response.setContentType("text/plain");
        PrintWriter writer = response.getWriter();
        
        writer.println("=== Environment Information ===");
        writer.println("Context Path: " + request.getContextPath());
        writer.println("Servlet Path: " + request.getServletPath());
        writer.println("Request URI: " + request.getRequestURI());
        writer.println("Request URL: " + request.getRequestURL());
        
        writer.println("\n=== Headers ===");
        Enumeration<String> headerNames = request.getHeaderNames();
        while (headerNames.hasMoreElements()) {
            String headerName = headerNames.nextElement();
            writer.println(headerName + ": " + request.getHeader(headerName));
        }
        
        writer.println("\n=== System Properties ===");
        writer.println("server.servlet.context-path: " + System.getProperty("server.servlet.context-path"));
        
        writer.flush();
        System.out.println("Sent environment information response");
    }

    /**
     * Simple direct download endpoint without the clickhouse prefix
     */
    @GetMapping("/direct-download")
    public void directDownload(HttpServletResponse response) throws IOException {
        System.out.println("Direct download endpoint called");
        
        try {
            // Create a simple test file
            String csvContent = "id,name,value\n1,test-direct,100\n2,test-direct,200\n3,test-direct,300";
            String filename = "direct_download.csv";
            System.out.println("Creating direct download test file: " + filename);
            
            // Set CORS headers explicitly
            response.setHeader("Access-Control-Allow-Origin", "*");
            response.setHeader("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
            response.setHeader("Access-Control-Allow-Headers", "Content-Type");
            response.setHeader("Access-Control-Expose-Headers", "Content-Disposition, Content-Length");
            
            // Set response headers for file download
            response.setContentType("text/csv");
            response.setHeader("Content-Disposition", "attachment; filename=\"" + filename + "\"");
            response.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
            response.setHeader("Pragma", "no-cache");
            response.setHeader("Expires", "0");
            
            // Write directly to the response
            try (PrintWriter writer = response.getWriter()) {
                writer.write(csvContent);
                writer.flush();
                System.out.println("Wrote " + csvContent.length() + " bytes to direct download response");
            } catch (Exception e) {
                System.err.println("Error writing to direct download response: " + e.getMessage());
                e.printStackTrace();
            }
        } catch (Exception e) {
            System.err.println("Direct download error: " + e.getMessage());
            e.printStackTrace();
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.setContentType("text/plain");
            response.getWriter().write("Failed to generate direct download: " + e.getMessage());
        }
    }

    // Add OPTIONS request handling for CORS preflight
    @RequestMapping(value = "/**", method = RequestMethod.OPTIONS)
    public ResponseEntity handleOptions() {
        return ResponseEntity
            .ok()
            .header("Access-Control-Allow-Origin", "*")
            .header("Access-Control-Allow-Methods", "POST, GET, PUT, OPTIONS, DELETE")
            .header("Access-Control-Allow-Headers", "Content-Type, Authorization, X-Requested-With")
            .header("Access-Control-Max-Age", "3600")
            .build();
    }
}
