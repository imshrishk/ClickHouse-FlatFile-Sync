package org.example.bidirectional.service;

import com.clickhouse.client.api.DataStreamWriter;
import com.clickhouse.client.api.insert.InsertSettings;
import com.clickhouse.data.ClickHouseFormat;
import com.univocity.parsers.csv.CsvParser;
import com.univocity.parsers.csv.CsvParserSettings;
import com.univocity.parsers.csv.CsvWriter;
import com.univocity.parsers.csv.CsvWriterSettings;
import org.example.bidirectional.config.SelectedColumnsQueryConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.clickhouse.client.api.query.QueryResponse;
import com.opencsv.CSVReader;
import java.io.BufferedReader;
import java.io.InputStreamReader;

import java.io.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.example.bidirectional.service.ClickHouseService.quote;

/**
 * Service responsible for handling data ingestion operations between ClickHouse and files.
 * This service manages the bidirectional flow of data:
 * - From input streams (typically CSV files) to ClickHouse tables
 * - From ClickHouse tables to output streams (for file generation)
 * 
 * The service optimizes data transfer using buffered streams and efficient parsing/writing.
 */
public class IngestionService {
    private static final Logger logger = LoggerFactory.getLogger(IngestionService.class);
    private static final int DEFAULT_BUFFER_SIZE = 131072; // 128 KB buffer size

    private final ClickHouseService clickHouseService;

    /**
     * Constructs an IngestionService with the specified ClickHouse service.
     * 
     * @param clickHouseService the ClickHouse service to use for database operations
     */
    public IngestionService(ClickHouseService clickHouseService) {
        logger.debug("Initializing IngestionService");
        if (clickHouseService == null) {
            logger.error("ClickHouseService cannot be null");
            throw new IllegalArgumentException("ClickHouseService cannot be null");
        }
        this.clickHouseService = clickHouseService;
    }

    /**
     * Ingests data from an input stream to a ClickHouse table.
     * Supports selective ingestion of specific columns.
     * 
     * @param totalCols   Total number of columns in the source data
     * @param tableName   Target ClickHouse table name
     * @param headers     List of column names to ingest
     * @param delimiter   Delimiter string for CSV parsing
     * @param inputStream Input stream containing the data to ingest
     * @return Number of rows ingested
     * @throws Exception if ingestion fails
     */
    public long ingestDataFromStream(
            Integer totalCols,
            String tableName,
            List<String> headers,
            String delimiter,
            InputStream inputStream
    ) throws Exception {
        // Validate inputs
        if (tableName == null || tableName.trim().isEmpty()) {
            logger.error("Table name cannot be null or empty");
            throw new IllegalArgumentException("Table name cannot be null or empty");
        }
        
        if (headers == null || headers.isEmpty()) {
            logger.error("Headers list cannot be null or empty");
            throw new IllegalArgumentException("Headers list cannot be null or empty");
        }
        
        if (delimiter == null) {
            logger.error("Delimiter cannot be null");
            throw new IllegalArgumentException("Delimiter cannot be null");
        }
        
        if (inputStream == null) {
            logger.error("Input stream cannot be null");
            throw new IllegalArgumentException("Input stream cannot be null");
        }
        
        if (totalCols == null || totalCols <= 0) {
            logger.warn("Invalid total columns count ({}), defaulting to headers size: {}", totalCols, headers.size());
            totalCols = headers.size();
        }
        
        logger.info("Starting data ingestion to table '{}' with {} selected columns", tableName, headers.size());
        logger.debug("Using delimiter: '{}', total columns in source: {}", delimiter, totalCols);

        final char delimiterChar;
        try {
            delimiterChar = ClickHouseService.convertStringToChar(delimiter);
        } catch (IllegalArgumentException e) {
            logger.error("Invalid delimiter: {}", delimiter, e);
            throw new IllegalArgumentException("Invalid delimiter: " + e.getMessage(), e);
        }
        
        // Create a row counter to track progress
        final AtomicLong rowCounter = new AtomicLong(0);

        // Define the data stream writer for processing CSV data
        DataStreamWriter writer = outputStream -> {
            logger.debug("Initializing data stream writer with buffer size: {} bytes", DEFAULT_BUFFER_SIZE);
            
            // Configure CSV parser
            CsvParserSettings parserSettings = new CsvParserSettings();
            parserSettings.setHeaderExtractionEnabled(true);
            parserSettings.getFormat().setDelimiter(delimiterChar);
            parserSettings.selectFields(headers.toArray(new String[0]));
            parserSettings.setInputBufferSize(DEFAULT_BUFFER_SIZE);
            parserSettings.setMaxCharsPerColumn(100000); // Support for large fields (100K chars)
            parserSettings.setLineSeparatorDetectionEnabled(true);

            // Configure CSV writer
            CsvWriterSettings writerSettings = new CsvWriterSettings();
            writerSettings.getFormat().setDelimiter(delimiterChar);
            writerSettings.setQuoteAllFields(false);
            
            // Create buffered reader and writer
            BufferedReader bufferedReader = null;
            BufferedWriter bufferedWriter = null;
            CsvParser parser = null;
            CsvWriter csvWriter = null;
            
            try {
                bufferedReader = new BufferedReader(new InputStreamReader(inputStream), DEFAULT_BUFFER_SIZE);
                bufferedWriter = new BufferedWriter(new OutputStreamWriter(outputStream), DEFAULT_BUFFER_SIZE);
                
                parser = new CsvParser(parserSettings);
                parser.beginParsing(bufferedReader);
                
                csvWriter = new CsvWriter(bufferedWriter, writerSettings);
                
                // Write headers first
                String[] selectedHeaders = parser.getContext().selectedHeaders();
                if (selectedHeaders == null || selectedHeaders.length == 0) {
                    logger.warn("No headers found in input, using provided headers");
                    selectedHeaders = headers.toArray(new String[0]);
                }
                
                csvWriter.writeHeaders(selectedHeaders);
                logger.debug("Wrote headers: {}", String.join(", ", selectedHeaders));

                // Process and write each row
                String[] row;
                while ((row = parser.parseNext()) != null) {
                    csvWriter.writeRow((Object[]) row);
                    
                    long count = rowCounter.incrementAndGet();
                    if (count % 10000 == 0) {
                        logger.debug("Processed {} rows", count);
                    }
                }
                
                logger.info("Completed processing {} rows for ingestion", rowCounter.get());
            } catch (Exception e) {
                logger.error("Error during CSV processing: {}", e.getMessage(), e);
                throw new IOException("Failed to process CSV data: " + e.getMessage(), e);
            } finally {
                // Close resources in reverse order
                if (csvWriter != null) {
                    try {
                        csvWriter.close();
                    } catch (Exception e) {
                        logger.warn("Error closing CSV writer: {}", e.getMessage());
                    }
                }
                
                if (parser != null) {
                    try {
                        parser.stopParsing();
                    } catch (Exception e) {
                        logger.warn("Error stopping CSV parser: {}", e.getMessage());
                    }
                }
                
                if (bufferedWriter != null) {
                    try {
                        bufferedWriter.close();
                    } catch (Exception e) {
                        logger.warn("Error closing buffered writer: {}", e.getMessage());
                    }
                }
                
                // Note: We don't close bufferedReader here as it would close the input stream,
                // which is handled by the caller
            }
        };

        // Get initial row count for later calculation
        long beforeIngest;
        try {
            beforeIngest = clickHouseService.getTotalRows(tableName);
            if (beforeIngest == 0) {
                beforeIngest = -1; // To handle empty tables correctly
            }
            logger.debug("Table '{}' has {} rows before ingestion", tableName, beforeIngest);
        } catch (Exception e) {
            logger.error("Failed to get initial row count for table '{}': {}", tableName, e.getMessage(), e);
            throw new Exception("Failed to get initial row count: " + e.getMessage(), e);
        }

        // Configure insert settings
        InsertSettings settings = new InsertSettings()
                .serverSetting("input_format_with_names_use_header", "1")
                .serverSetting("input_format_skip_unknown_fields", "1")
                .serverSetting("input_format_allow_errors_ratio", "0.01") // Allow 1% errors
                .serverSetting("input_format_allow_errors_num", "10");    // Allow up to 10 errors

        try {
            // Determine the most efficient ingestion method
            if (headers.size() == totalCols && !Character.isWhitespace(delimiterChar)) {
                // Direct ingestion when all columns are selected and delimiter is valid
                logger.debug("Using direct ingestion method (all columns selected)");
                settings.serverSetting("format_csv_delimiter", delimiter);
                
                clickHouseService.getClient()
                        .insert(quote(tableName), inputStream, ClickHouseFormat.CSVWithNames, settings)
                        .get();
                
                logger.info("Completed direct ingestion to table '{}'", tableName);
            } else {
                // Selective ingestion when only specific columns are needed
                logger.debug("Using selective ingestion method ({} of {} columns)", headers.size(), totalCols);
                
                clickHouseService.getClient()
                        .insert(quote(tableName), writer, ClickHouseFormat.CSVWithNames, settings)
                        .get();
                
                logger.info("Completed selective ingestion to table '{}' with {} rows processed", 
                        tableName, rowCounter.get());
            }
        } catch (Exception e) {
            logger.error("Failed to ingest data to table '{}': {}", tableName, e.getMessage(), e);
            throw new Exception("Failed to ingest data: " + e.getMessage(), e);
        }

        // Calculate the number of rows ingested
        try {
            long afterIngest = clickHouseService.getTotalRows(tableName);
            long rowsIngested = afterIngest - beforeIngest;
            logger.info("Successfully ingested {} rows to table '{}'", rowsIngested, tableName);
            return rowsIngested;
        } catch (Exception e) {
            logger.error("Failed to get final row count for table '{}': {}", tableName, e.getMessage(), e);
            // If we can't get the final count but ingestion succeeded, return the processed count
            if (rowCounter.get() > 0) {
                logger.warn("Using processed row count ({}) as fallback", rowCounter.get());
                return rowCounter.get();
            }
            throw new Exception("Failed to calculate ingested rows: " + e.getMessage(), e);
        }
    }

    /**
     * Streams data from a ClickHouse query to an output stream.
     * Supports custom column selection and JOIN operations.
     * 
     * @param config       Configuration for the query (table, columns, joins, etc.)
     * @param outputStream The output stream to write data to
     * @return Number of rows written (including header)
     * @throws Exception if data streaming fails
     */
    public long streamDataToOutputStream(
            SelectedColumnsQueryConfig config,
            OutputStream outputStream) throws Exception {
        
        // Validate inputs
        if (config == null) {
            logger.error("Query configuration cannot be null");
            throw new IllegalArgumentException("Query configuration cannot be null");
        }
        
        if (config.getTableName() == null || config.getTableName().trim().isEmpty()) {
            logger.error("Table name cannot be null or empty");
            throw new IllegalArgumentException("Table name cannot be null or empty");
        }
        
        if (config.getColumns() == null || config.getColumns().isEmpty()) {
            logger.error("Columns list cannot be null or empty");
            throw new IllegalArgumentException("Columns list cannot be null or empty");
        }
        
        if (outputStream == null) {
            logger.error("Output stream cannot be null");
            throw new IllegalArgumentException("Output stream cannot be null");
        }
        
        if (config.getDelimiter() == null) {
            logger.warn("Delimiter is null, using comma as default");
            config.setDelimiter(",");
        }
        
        logger.info("Streaming data from table '{}' with {} columns to output stream", 
                config.getTableName(), config.getColumns().size());
        
        // Get the estimated row count (for logging)
        long estimatedRows;
        try {
            estimatedRows = clickHouseService.getTotalRows(config.getTableName());
            logger.debug("Estimated {} rows to export from table '{}'", estimatedRows, config.getTableName());
        } catch (Exception e) {
            logger.warn("Could not determine row count for table '{}': {}", config.getTableName(), e.getMessage());
            estimatedRows = -1;
        }

        // Build the SQL query with format specification and delimiter
        try {
            // Create the base query
            String sql = clickHouseService.getJoinedQuery(
                    config.getTableName(), 
                    config.getColumns(), 
                    config.getJoinTables()
                );
            
            // Get total row count first
            String countQuery = sql.replace("SELECT", "SELECT COUNT(*) as total_rows");
            long totalRows;
            try (QueryResponse qr = clickHouseService.getClient()
                    .query(countQuery)
                    .get();
                 BufferedReader reader = new BufferedReader(new InputStreamReader(qr.getInputStream()));
                 CSVReader csvReader = new CSVReader(reader)) {
                String[] row = csvReader.readNext();
                if (row != null && row.length > 0) {
                    totalRows = Long.parseLong(row[0]);
                } else {
                    totalRows = 0;
                }
            }
            
            // Add limit clause if specified
            if (config.getLimit() != null && config.getLimit() > 0) {
                sql += " LIMIT " + config.getLimit();
            }
            
            // Add format specification
            sql += " FORMAT CSVWithNames SETTINGS format_csv_delimiter = '"
                + ClickHouseService.convertStringToChar(config.getDelimiter()) + "';";
            
            logger.debug("Executing query for data export: {}", sql);
            
            // Execute the query and transfer the data to the output stream
            var response = clickHouseService.getClient()
                    .query(sql)
                    .get();
            
            // Use a buffer for better performance
            byte[] buffer = new byte[DEFAULT_BUFFER_SIZE];
            long totalBytes = 0;
            
            try (InputStream csvStream = response.getInputStream()) {
                int bytesRead;
                while ((bytesRead = csvStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                    totalBytes += bytesRead;
                    
                    // Log progress for large exports
                    if (totalBytes % (50 * DEFAULT_BUFFER_SIZE) == 0) {
                        logger.debug("Exported approximately {} MB of data", totalBytes / (1024 * 1024));
                    }
                }
                
                outputStream.flush();
                logger.info("Successfully exported {} MB of data from table '{}'", 
                        totalBytes / (1024 * 1024), config.getTableName());
            }
            
            // Return the header row (1) + the number of data rows
            return totalRows;
        } catch (Exception e) {
            logger.error("Failed to stream data from table '{}': {}", config.getTableName(), e.getMessage(), e);
            throw new Exception("Failed to export data: " + e.getMessage(), e);
        }
    }
}
