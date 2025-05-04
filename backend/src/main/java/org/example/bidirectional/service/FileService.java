package org.example.bidirectional.service;

import com.opencsv.CSVParserBuilder;
import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;
import com.opencsv.CSVWriter;
import com.opencsv.exceptions.CsvException;
import com.univocity.parsers.csv.CsvParser;
import com.univocity.parsers.csv.CsvParserSettings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Service class for handling file operations, specifically focusing on CSV file processing.
 * Provides functionality for reading from and writing to CSV files with different delimiting characters.
 * Supports both file-based and stream-based operations.
 */
@Service
public class FileService {
    private static final Logger logger = LoggerFactory.getLogger(FileService.class);
    private static final int DEFAULT_BUFFER_SIZE = 8192; // 8KB buffer

    /**
     * Reads a CSV file and returns its contents as a list of String arrays.
     * Each String array represents a row in the CSV file.
     *
     * @param filePath  the path to the CSV file
     * @param delimiter the character used to separate values in the CSV file
     * @return a list of String arrays representing the CSV data
     * @throws IOException   if an I/O error occurs
     * @throws CsvException if a CSV parsing error occurs
     */
    public List<String[]> readCsv(String filePath, char delimiter) throws IOException, CsvException {
        if (filePath == null || filePath.trim().isEmpty()) {
            logger.error("Cannot read CSV: File path is null or empty");
            throw new IllegalArgumentException("File path cannot be null or empty");
        }
        
        logger.debug("Reading CSV file: {} with delimiter: '{}'", filePath, delimiter);
        
        File file = new File(filePath);
        if (!file.exists()) {
            logger.error("CSV file does not exist: {}", filePath);
            throw new FileNotFoundException("CSV file not found: " + filePath);
        }
        
        if (!file.canRead()) {
            logger.error("Cannot read CSV file (permission denied): {}", filePath);
            throw new IOException("Cannot read file due to permissions: " + filePath);
        }
        
        try (CSVReader reader = new CSVReaderBuilder(new FileReader(filePath))
                .withCSVParser(new CSVParserBuilder().withSeparator(delimiter).build())
                .build()) {
            
            List<String[]> data = reader.readAll();
            logger.info("Successfully read {} rows from CSV file: {}", data.size(), filePath);
            return data;
        } catch (FileNotFoundException e) {
            logger.error("CSV file not found: {}", filePath, e);
            throw e;
        } catch (IOException e) {
            logger.error("IO error reading CSV file {}: {}", filePath, e.getMessage(), e);
            throw e;
        } catch (CsvException e) {
            logger.error("CSV parsing error for file {}: {}", filePath, e.getMessage(), e);
            throw e;
        } catch (Exception e) {
            logger.error("Unexpected error reading CSV file {}: {}", filePath, e.getMessage(), e);
            throw new IOException("Unexpected error reading CSV file: " + e.getMessage(), e);
        }
    }

    /**
     * Writes data to a CSV file with the specified delimiter.
     * Each String array in the data list represents a row to be written.
     *
     * @param filePath  the path where the CSV file will be created or overwritten
     * @param data      the data to write to the CSV file
     * @param delimiter the character to use as a delimiter in the CSV file
     * @throws IOException if an I/O error occurs
     */
    public void writeCsv(String filePath, List<String[]> data, char delimiter) throws IOException {
        if (filePath == null || filePath.trim().isEmpty()) {
            logger.error("Cannot write CSV: File path is null or empty");
            throw new IllegalArgumentException("File path cannot be null or empty");
        }
        
        if (data == null) {
            logger.error("Cannot write CSV: Data is null");
            throw new IllegalArgumentException("Data cannot be null");
        }
        
        logger.debug("Writing CSV file: {} with delimiter: '{}' and {} rows", filePath, delimiter, data.size());
        
        File parentDir = new File(filePath).getParentFile();
        if (parentDir != null && !parentDir.exists()) {
            logger.debug("Creating parent directories for file: {}", filePath);
            if (!parentDir.mkdirs()) {
                logger.warn("Failed to create parent directories for file: {}", filePath);
                // Continue anyway, the FileWriter will fail if the directories cannot be created
            }
        }
        
        try (CSVWriter writer = new CSVWriter(new FileWriter(filePath), delimiter,
                CSVWriter.DEFAULT_QUOTE_CHARACTER,  // enable proper quoting
                CSVWriter.DEFAULT_ESCAPE_CHARACTER,
                CSVWriter.DEFAULT_LINE_END)) {
            
            writer.writeAll(data);
            logger.info("Successfully wrote {} rows to CSV file: {}", data.size(), filePath);
        } catch (IOException e) {
            logger.error("IO error writing to CSV file {}: {}", filePath, e.getMessage(), e);
            throw e;
        } catch (Exception e) {
            logger.error("Unexpected error writing to CSV file {}: {}", filePath, e.getMessage(), e);
            throw new IOException("Unexpected error writing to CSV file: " + e.getMessage(), e);
        }
    }

    /**
     * Reads CSV data from an input stream and returns it as a list of String arrays.
     * The first array in the returned list contains the headers.
     * 
     * @param inputStream The input stream containing CSV data
     * @param delimiter The delimiter string (will be converted to char)
     * @return List of String arrays containing the CSV data (including headers)
     */
    public static List<String[]> readCsvRows(InputStream inputStream, String delimiter) {
        if (inputStream == null) {
            logger.error("Cannot read CSV: Input stream is null");
            throw new IllegalArgumentException("Input stream cannot be null");
        }
        
        if (delimiter == null) {
            logger.error("Cannot read CSV: Delimiter is null");
            throw new IllegalArgumentException("Delimiter cannot be null");
        }
        
        logger.debug("Reading CSV from input stream with delimiter: '{}'", delimiter);
        
        List<String[]> data = new ArrayList<>();
        char delimiterChar;
        
        try {
            delimiterChar = ClickHouseService.convertStringToChar(delimiter);
        } catch (IllegalArgumentException e) {
            logger.error("Invalid delimiter: {}", delimiter, e);
            throw e;
        }

        CsvParserSettings parserSettings = new CsvParserSettings();
        parserSettings.setHeaderExtractionEnabled(true);
        parserSettings.getFormat().setDelimiter(delimiterChar);
        parserSettings.setLineSeparatorDetectionEnabled(true);
        parserSettings.setMaxCharsPerColumn(100000); // Handle large fields (100K chars max)
        parserSettings.setInputBufferSize(DEFAULT_BUFFER_SIZE);

        CsvParser parser = new CsvParser(parserSettings);
        BufferedReader reader = null;
        
        try {
            reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8), DEFAULT_BUFFER_SIZE);
            parser.beginParsing(reader);
            
            String[] headers = parser.getContext().headers();
            if (headers == null || headers.length == 0) {
                logger.warn("No headers found in CSV input stream");
                // Create default header if not found
                headers = new String[]{"Column1"};
            }
            
            data.add(headers);
            logger.debug("Found {} headers in CSV input", headers.length);

            String[] row;
            int rowCount = 0;
            while ((row = parser.parseNext()) != null) {
                data.add(row);
                rowCount++;
                
                // Log progress periodically for large files
                if (rowCount % 10000 == 0) {
                    logger.debug("Processed {} rows from CSV input stream", rowCount);
                }
            }
            
            logger.info("Successfully read {} rows from CSV input stream", rowCount);
            return data;
        } catch (Exception e) {
            logger.error("Error parsing CSV from input stream: {}", e.getMessage(), e);
            throw new RuntimeException("Error parsing CSV data: " + e.getMessage(), e);
        } finally {
            if (parser != null) {
                try {
                    parser.stopParsing();
                } catch (Exception e) {
                    logger.warn("Error stopping CSV parser: {}", e.getMessage());
                }
            }
            
            // Note: We don't close the reader here as it would close the input stream,
            // which might be needed by the caller.
        }
    }
    
    /**
     * Validates a CSV file format by checking if it has a consistent number of columns across rows.
     * 
     * @param data The CSV data to validate
     * @return True if the CSV format is valid, false otherwise
     */
    public boolean validateCsvFormat(List<String[]> data) {
        if (data == null || data.isEmpty()) {
            logger.warn("Cannot validate empty CSV data");
            return false;
        }
        
        logger.debug("Validating CSV format for {} rows", data.size());
        
        // Get the number of columns in the first row (assumed to be the header)
        int headerColumnCount = data.get(0).length;
        
        if (headerColumnCount == 0) {
            logger.warn("CSV header has no columns");
            return false;
        }
        
        // Check if all rows have the same number of columns
        for (int i = 1; i < data.size(); i++) {
            if (data.get(i).length != headerColumnCount) {
                logger.warn("CSV row {} has inconsistent column count: expected {}, got {}", 
                    i, headerColumnCount, data.get(i).length);
                return false;
            }
        }
        
        logger.info("CSV format validation successful: {} rows with {} columns", data.size(), headerColumnCount);
        return true;
    }
}
