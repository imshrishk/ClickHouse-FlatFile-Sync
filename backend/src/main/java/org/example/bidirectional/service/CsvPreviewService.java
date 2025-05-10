package org.example.bidirectional.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Specialized service for previewing CSV files, particularly focusing on tab-delimited
 * files with quoted fields. Provides robust handling for headerless files and complex
 * formats that standard parsers struggle with.
 */
@Service
public class CsvPreviewService {
    private static final Logger logger = LoggerFactory.getLogger(CsvPreviewService.class);
    private static final int MAX_PREVIEW_ROWS = 100;

    /**
     * Create a preview of a CSV file with options for headers and delimiter
     * 
     * @param inputStream The input stream containing the CSV data
     * @param delimiter The delimiter character 
     * @param hasHeader Whether to treat the first row as a header
     * @return A map containing headers, rows, and metadata for display
     * @throws IOException if there's an error reading the file
     */
    public Map<String, Object> createPreview(InputStream inputStream, char delimiter, boolean hasHeader) throws IOException {
        try {
            List<String[]> parsedData = parseDelimitedFile(inputStream, delimiter);
            return formatPreviewResponse(parsedData, hasHeader);
        } catch (Exception e) {
            logger.error("Error creating CSV preview: {}", e.getMessage(), e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", "Failed to parse CSV: " + e.getMessage());
            errorResponse.put("headers", List.of("Error"));
            errorResponse.put("rows", List.of(new String[]{"Failed to parse CSV file. Check the format and delimiter."}));
            errorResponse.put("hasHeader", hasHeader);
            return errorResponse;
        }
    }
    
    /**
     * Parse a delimited file handling quoted fields and complex formats
     * 
     * @param inputStream The input stream to read from
     * @param delimiter The delimiter character
     * @return List of String arrays containing the rows
     * @throws IOException if there's an error reading the file
     */
    private List<String[]> parseDelimitedFile(InputStream inputStream, char delimiter) throws IOException {
        List<String[]> parsedData = new ArrayList<>();
        
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String line;
            int lineCount = 0;
            
            while ((line = reader.readLine()) != null && lineCount < MAX_PREVIEW_ROWS) {
                lineCount++;
                try {
                    String[] fields = parseDelimitedLine(line, delimiter);
                    if (fields.length > 0) {
                        parsedData.add(fields);
                    }
                } catch (Exception e) {
                    logger.warn("Failed to parse line {}: {}", lineCount, e.getMessage());
                    // Skip problematic lines but continue parsing
                }
            }
        }
        
        logger.info("Successfully parsed {} rows from delimited file", parsedData.size());
        return parsedData;
    }
    
    /**
     * Parse a single delimited line handling quoted fields
     * 
     * @param line The line to parse
     * @param delimiter The delimiter character
     * @return Array of fields from the line
     */
    private String[] parseDelimitedLine(String line, char delimiter) {
        if (line == null || line.isEmpty()) {
            return new String[0];
        }
        
        List<String> fields = new ArrayList<>();
        StringBuilder fieldBuilder = new StringBuilder();
        boolean inQuotes = false;
        
        // Special handling for tab delimited files with quoted fields
        boolean isTabDelimited = delimiter == '\t';
        
        logger.debug("Parsing line with delimiter: '{}', isTab: {}", delimiter, isTabDelimited);
        
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            
            if (c == '"') {
                inQuotes = !inQuotes;
                // For better compatibility with various formats, add the quote to the field
                // This helps with values like "{GUID}" that shouldn't lose their quotes
                if (isTabDelimited) {
                    fieldBuilder.append(c);
                }
            } else if (c == delimiter && !inQuotes) {
                // End of field
                fields.add(cleanField(fieldBuilder.toString()));
                fieldBuilder = new StringBuilder();
            } else {
                fieldBuilder.append(c);
            }
        }
        
        // Add the last field
        fields.add(cleanField(fieldBuilder.toString()));
        
        // Log some debug info for tab-delimited files
        if (isTabDelimited && logger.isDebugEnabled()) {
            logger.debug("Tab-delimited parse result: {} fields from line: '{}'", 
                     fields.size(), line.length() > 50 ? line.substring(0, 50) + "..." : line);
        }
        
        return fields.toArray(new String[0]);
    }
    
    /**
     * Clean a field by trimming and handling quoted fields appropriately
     * 
     * @param field The field to clean
     * @return Cleaned field
     */
    private String cleanField(String field) {
        field = field.trim();
        // Only remove enclosing quotes if they're at the beginning and end
        // This preserves quotes in the middle of text, like "BISHOP'S STORTFORD"
        if (field.startsWith("\"") && field.endsWith("\"") && field.length() >= 2 
            // Make sure we're not dealing with a value that needs its quotes like "{GUID}"
            && !field.matches("\"\\{[^}]+\\}\"")) {
            field = field.substring(1, field.length() - 1);
        }
        return field;
    }
    
    /**
     * Format the parsed data into a response suitable for the frontend
     * 
     * @param parsedData The parsed CSV data
     * @param hasHeader Whether to treat the first row as a header
     * @return Map containing headers, rows, and metadata
     */
    private Map<String, Object> formatPreviewResponse(List<String[]> parsedData, boolean hasHeader) {
        Map<String, Object> response = new HashMap<>();
        
        if (parsedData.isEmpty()) {
            response.put("hasHeader", hasHeader);
            response.put("headers", List.of());
            response.put("rows", List.of());
            return response;
        }
        
        // Get the first row to determine column count
        String[] firstRow = parsedData.get(0);
        int columnCount = firstRow.length;
        
        if (hasHeader && parsedData.size() > 0) {
            // First row is headers - clean quotation marks from column names
            String[] headerRow = parsedData.get(0);
            for (int i = 0; i < headerRow.length; i++) {
                // Remove any quotation marks from column names
                headerRow[i] = headerRow[i].replaceAll("^\"|\"$", "");
            }
            
            // Get up to 100 data rows for preview
            List<String[]> dataRows = parsedData.size() > 1 ? 
                                     new ArrayList<>(parsedData.subList(1, Math.min(parsedData.size(), 101))) : 
                                     new ArrayList<>();
            
            // Ensure we have at least some data rows for preview
            if (dataRows.isEmpty() && headerRow.length > 0) {
                // If no data rows but we have headers, create a placeholder row
                logger.info("No data rows found, creating placeholder row");
                String[] placeholderRow = new String[headerRow.length];
                for (int i = 0; i < placeholderRow.length; i++) {
                    placeholderRow[i] = "(No data)";
                }
                dataRows.add(placeholderRow);
            }
            
            response.put("hasHeader", true);
            response.put("headers", headerRow);
            response.put("rows", dataRows);
        } else {
            // No header row, use all rows as data
            List<String[]> dataRows = new ArrayList<>(parsedData);
            String[] headers = new String[columnCount];
            for (int i = 0; i < columnCount; i++) {
                headers[i] = "Column" + (i + 1);
            }
            
            response.put("hasHeader", false);
            response.put("headers", headers);
            response.put("rows", dataRows);
        }
        
        return response;
    }
} 