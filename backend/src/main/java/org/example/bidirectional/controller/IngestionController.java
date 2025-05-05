package org.example.bidirectional.controller;

import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.example.bidirectional.config.*;
import org.example.bidirectional.exception.AuthenticationException;
import org.example.bidirectional.model.ColumnInfo;
import org.example.bidirectional.service.ClickHouseService;
import org.example.bidirectional.service.FileService;
import org.example.bidirectional.service.IngestionService;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.beans.factory.annotation.Value;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.Enumeration;

import com.fasterxml.jackson.databind.ObjectMapper;

@RestController
@RequestMapping("/clickhouse")
public class IngestionController {
    @Value("${config.frontend}")
    private String frontendUrl;

    @PostConstruct
    public void init() {
        System.out.println("Ensure FrontEnd is running on: " + frontendUrl);
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

            List<String[]> rows = clickHouseService.querySelectedColumns(
                    config.getTableName(),
                    config.getColumns(),
                    config.getJoinTables(),
                    config.getDelimiter()
            );

            // Build response: first row is headers, remaining rows are data
            return getHeadAndData(rows);
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to query selected columns.");
        }
    }

    @PostMapping(value = "/preview-csv", consumes = {"multipart/form-data"})
    public ResponseEntity<Map<String, Object>> previewCSV(
            @RequestPart("file") MultipartFile file,
            @RequestParam(value = "delimiter", defaultValue = ",") String delimiter
    ) throws Exception {
        try {
            List<String[]> rows = FileService.readCsvRows(file.getInputStream(), delimiter);
            return getHeadAndData(rows);
        } catch (Exception e) {
            throw new Exception("Failed to preview CSV. Please check if file exists.");
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
            @RequestPart("config") String configJson
    ) {
        Map<String, Object> response = new HashMap<>();
        try {
            // Parse the config JSON string to UploadConfig POJO
            ObjectMapper mapper = new ObjectMapper();
            UploadConfig request = mapper.readValue(configJson, UploadConfig.class);

            // Setting up services
            ClickHouseService clickHouseService = new ClickHouseService(request.getConnection());
            IngestionService ingestionService = new IngestionService(clickHouseService);

            if (request.isCreateNewTable())
                clickHouseService.createTable(request.getTableName(), request.getColumnTypes());

            long lines;
            // Ingest only selected columns from CSV stream
            try (InputStream ingestionStream = file.getInputStream()) {
                lines = ingestionService.ingestDataFromStream(
                        request.getTotalCols(),
                        request.getTableName(),
                        new ArrayList<>(request.getColumnTypes().keySet()),
                        request.getDelimiter(),
                        ingestionStream);
            }

            response.put("lines", lines);
            response.put("success", true);
            response.put("message", "Upload successful");

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("lines", 0);
            response.put("success", false);
            response.put("message", "Error: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
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
}
