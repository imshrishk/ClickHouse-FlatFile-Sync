package org.example.bidirectional.service;

import com.clickhouse.client.api.Client;
import com.clickhouse.client.api.query.QueryResponse;
import com.clickhouse.client.api.insert.InsertSettings;
import com.clickhouse.data.ClickHouseFormat;
import org.example.bidirectional.config.SelectedColumnsQueryConfig;
import org.example.bidirectional.model.JoinTable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.clickhouse.client.api.insert.InsertResponse;
import com.clickhouse.client.api.DataStreamWriter;

/**
 * Unit tests for the IngestionService class.
 * Uses mocks to isolate the IngestionService from actual database operations.
 */
@ExtendWith(MockitoExtension.class)
class IngestionServiceTest {

    @Mock
    private ClickHouseService mockClickHouseService;

    @Mock
    private Client mockClient;

    @Mock
    private QueryResponse mockQueryResponse;

    @Mock
    private InsertResponse mockInsertResponse;

    private IngestionService ingestionService;
    private static final String CSV_CONTENT = "id,name,email\n1,John Doe,john@example.com\n2,Jane Smith,jane@example.com";

    @BeforeEach
    void setUp() {
        when(mockClickHouseService.getClient()).thenReturn(mockClient);
        ingestionService = new IngestionService(mockClickHouseService);
    }

    /**
     * Test constructor with null service
     */
    @Test
    void testConstructorWithNullService() {
        assertThrows(IllegalArgumentException.class, () -> new IngestionService(null),
                "Constructor should throw exception for null ClickHouseService");
    }

    /**
     * Test ingestDataFromStream with null table name
     */
    @Test
    void testIngestDataFromStreamWithNullTableName() {
        assertThrows(IllegalArgumentException.class, 
                () -> ingestionService.ingestDataFromStream(
                        3, null, Arrays.asList("id", "name", "email"), ",", 
                        new ByteArrayInputStream(CSV_CONTENT.getBytes(StandardCharsets.UTF_8))),
                "Should throw exception for null table name");
    }

    /**
     * Test ingestDataFromStream with null headers
     */
    @Test
    void testIngestDataFromStreamWithNullHeaders() {
        assertThrows(IllegalArgumentException.class, 
                () -> ingestionService.ingestDataFromStream(
                        3, "test_table", null, ",", 
                        new ByteArrayInputStream(CSV_CONTENT.getBytes(StandardCharsets.UTF_8))),
                "Should throw exception for null headers");
    }

    /**
     * Test ingestDataFromStream with empty headers
     */
    @Test
    void testIngestDataFromStreamWithEmptyHeaders() {
        assertThrows(IllegalArgumentException.class, 
                () -> ingestionService.ingestDataFromStream(
                        3, "test_table", Collections.emptyList(), ",", 
                        new ByteArrayInputStream(CSV_CONTENT.getBytes(StandardCharsets.UTF_8))),
                "Should throw exception for empty headers");
    }

    /**
     * Test ingestDataFromStream with null delimiter
     */
    @Test
    void testIngestDataFromStreamWithNullDelimiter() {
        assertThrows(IllegalArgumentException.class, 
                () -> ingestionService.ingestDataFromStream(
                        3, "test_table", Arrays.asList("id", "name", "email"), null, 
                        new ByteArrayInputStream(CSV_CONTENT.getBytes(StandardCharsets.UTF_8))),
                "Should throw exception for null delimiter");
    }

    /**
     * Test ingestDataFromStream with null input stream
     */
    @Test
    void testIngestDataFromStreamWithNullInputStream() {
        assertThrows(IllegalArgumentException.class, 
                () -> ingestionService.ingestDataFromStream(
                        3, "test_table", Arrays.asList("id", "name", "email"), ",", null),
                "Should throw exception for null input stream");
    }

    /**
     * Test ingestDataFromStream with invalid total columns
     */
    @Test
    void testIngestDataFromStreamWithInvalidTotalCols() throws Exception {
        // Given
        String tableName = "test_table";
        List<String> headers = Arrays.asList("id", "name", "email");
        ByteArrayInputStream inputStream = new ByteArrayInputStream(CSV_CONTENT.getBytes(StandardCharsets.UTF_8));
        
        when(mockClickHouseService.getTotalRows(tableName)).thenReturn(0L).thenReturn(2L);
        CompletableFuture<QueryResponse> future = CompletableFuture.completedFuture(mockQueryResponse);
        
        CompletableFuture<InsertResponse> insertFuture = CompletableFuture.completedFuture(mockInsertResponse);
        CompletableFuture<QueryResponse> queryFuture = CompletableFuture.completedFuture(mockQueryResponse);
        
        when(mockClient.insert(
                anyString(), any(InputStream.class), any(ClickHouseFormat.class), any(InsertSettings.class)))
                .thenReturn(insertFuture);
        when(mockClient.query(anyString())).thenReturn(queryFuture);
        
        // When
        long result = ingestionService.ingestDataFromStream(0, tableName, headers, ",", inputStream);
        
        // Then
        assertEquals(2, result, "Should return the correct row count");
        verify(mockClient).insert(anyString(), any(InputStream.class), eq(ClickHouseFormat.CSVWithNames), any(InsertSettings.class));
    }

    /**
     * Test direct ingestion with all columns selected
     */
    @Test
    void testDirectIngestion() throws Exception {
        // Given
        String tableName = "test_table";
        List<String> headers = Arrays.asList("id", "name", "email");
        ByteArrayInputStream inputStream = new ByteArrayInputStream(CSV_CONTENT.getBytes(StandardCharsets.UTF_8));
        
        when(mockClickHouseService.getTotalRows(tableName)).thenReturn(0L).thenReturn(2L);
        CompletableFuture<QueryResponse> future = CompletableFuture.completedFuture(mockQueryResponse);
        
        CompletableFuture<InsertResponse> insertFuture = CompletableFuture.completedFuture(mockInsertResponse);
        CompletableFuture<QueryResponse> queryFuture = CompletableFuture.completedFuture(mockQueryResponse);
        
        when(mockClient.insert(
                anyString(), any(InputStream.class), any(ClickHouseFormat.class), any(InsertSettings.class)))
                .thenReturn(insertFuture);
        when(mockClient.query(anyString())).thenReturn(queryFuture);
        
        // When
        long result = ingestionService.ingestDataFromStream(3, tableName, headers, ",", inputStream);
        
        // Then
        assertEquals(2, result, "Should return the correct row count");
        verify(mockClient).insert(anyString(), any(InputStream.class), eq(ClickHouseFormat.CSVWithNames), any(InsertSettings.class));
    }

    /**
     * Test selective ingestion with subset of columns
     */
    @Test
    void testSelectiveIngestion() throws Exception {
        // Given
        String tableName = "test_table";
        List<String> headers = Arrays.asList("id", "name"); // Only 2 out of 3 columns
        ByteArrayInputStream inputStream = new ByteArrayInputStream(CSV_CONTENT.getBytes(StandardCharsets.UTF_8));
        
        when(mockClickHouseService.getTotalRows(tableName)).thenReturn(0L).thenReturn(2L);
        CompletableFuture<QueryResponse> future = CompletableFuture.completedFuture(mockQueryResponse);
        
        CompletableFuture<InsertResponse> insertFuture = CompletableFuture.completedFuture(mockInsertResponse);
        CompletableFuture<QueryResponse> queryFuture = CompletableFuture.completedFuture(mockQueryResponse);
        
        when(mockClient.insert(
                anyString(), any(DataStreamWriter.class), any(ClickHouseFormat.class), any(InsertSettings.class)))
                .thenReturn(insertFuture);
        when(mockClient.query(anyString())).thenReturn(queryFuture);
        
        // When
        long result = ingestionService.ingestDataFromStream(3, tableName, headers, ",", inputStream);
        
        // Then
        assertEquals(2, result, "Should return the correct row count");
        verify(mockClient).insert(anyString(), any(DataStreamWriter.class), eq(ClickHouseFormat.CSVWithNames), any(InsertSettings.class));
    }

    /**
     * Test initial row count handling
     */
    @Test
    void testInitialRowCountHandling() throws Exception {
        // Given
        String tableName = "test_table";
        List<String> headers = Arrays.asList("id", "name", "email");
        ByteArrayInputStream inputStream = new ByteArrayInputStream(CSV_CONTENT.getBytes(StandardCharsets.UTF_8));
        
        when(mockClickHouseService.getTotalRows(tableName))
            .thenThrow(new RuntimeException("First call fails"))
            .thenReturn(2L);
        
        // When/Then
        Exception exception = assertThrows(Exception.class, 
                () -> ingestionService.ingestDataFromStream(3, tableName, headers, ",", inputStream),
                "Should throw exception when initial row count fails");
        
        assertTrue(exception.getMessage().contains("Failed to get initial row count"), 
                "Exception message should mention initial row count failure");
    }

    /**
     * Test streamDataToOutputStream with null config
     */
    @Test
    void testStreamDataToOutputStreamWithNullConfig() {
        assertThrows(IllegalArgumentException.class, 
                () -> ingestionService.streamDataToOutputStream(null, new ByteArrayOutputStream()),
                "Should throw exception for null config");
    }

    /**
     * Test streamDataToOutputStream with null output stream
     */
    @Test
    void testStreamDataToOutputStreamWithNullOutputStream() {
        SelectedColumnsQueryConfig config = new SelectedColumnsQueryConfig();
        config.setTableName("test_table");
        config.setColumns(Arrays.asList("id", "name"));
        
        assertThrows(IllegalArgumentException.class, 
                () -> ingestionService.streamDataToOutputStream(config, null),
                "Should throw exception for null output stream");
    }

    /**
     * Test streamDataToOutputStream with null table name
     */
    @Test
    void testStreamDataToOutputStreamWithNullTableName() {
        SelectedColumnsQueryConfig config = new SelectedColumnsQueryConfig();
        config.setTableName(null);
        config.setColumns(Arrays.asList("id", "name"));
        
        assertThrows(IllegalArgumentException.class, 
                () -> ingestionService.streamDataToOutputStream(config, new ByteArrayOutputStream()),
                "Should throw exception for null table name");
    }

    /**
     * Test streamDataToOutputStream with null columns
     */
    @Test
    void testStreamDataToOutputStreamWithNullColumns() {
        SelectedColumnsQueryConfig config = new SelectedColumnsQueryConfig();
        config.setTableName("test_table");
        config.setColumns(null);
        
        assertThrows(IllegalArgumentException.class, 
                () -> ingestionService.streamDataToOutputStream(config, new ByteArrayOutputStream()),
                "Should throw exception for null columns");
    }

    /**
     * Test streamDataToOutputStream with empty columns
     */
    @Test
    void testStreamDataToOutputStreamWithEmptyColumns() {
        SelectedColumnsQueryConfig config = new SelectedColumnsQueryConfig();
        config.setTableName("test_table");
        config.setColumns(Collections.emptyList());
        
        assertThrows(IllegalArgumentException.class, 
                () -> ingestionService.streamDataToOutputStream(config, new ByteArrayOutputStream()),
                "Should throw exception for empty columns");
    }

    /**
     * Test streamDataToOutputStream with null delimiter (should default to comma)
     */
    @Test
    void testStreamDataToOutputStreamWithNullDelimiter() throws Exception {
        // Given
        SelectedColumnsQueryConfig config = new SelectedColumnsQueryConfig();
        config.setTableName("test_table");
        config.setColumns(Arrays.asList("id", "name"));
        config.setDelimiter(null);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        
        when(mockClickHouseService.getTotalRows("test_table")).thenReturn(5L);
        when(mockClickHouseService.getJoinedQuery(eq("test_table"), anyList(), anyList()))
                .thenReturn("SELECT `test_table`.`id`,`test_table`.`name` FROM `test_table`");
        
        ByteArrayInputStream responseStream = new ByteArrayInputStream(CSV_CONTENT.getBytes(StandardCharsets.UTF_8));
        when(mockQueryResponse.getInputStream()).thenReturn(responseStream);
        CompletableFuture<QueryResponse> future = CompletableFuture.completedFuture(mockQueryResponse);
        when(mockClient.query(anyString())).thenReturn(future);
        
        // When
        long result = ingestionService.streamDataToOutputStream(config, outputStream);
        
        // Then
        assertEquals(6, result, "Should return header + row count");
        verify(mockClient).query(contains("FORMAT CSVWithNames"));
        verify(mockClient).query(contains("format_csv_delimiter = ','"));
        
        String output = outputStream.toString(StandardCharsets.UTF_8);
        assertEquals(CSV_CONTENT, output, "Output should match the input CSV");
    }

    /**
     * Test successful streaming of data
     */
    @Test
    void testSuccessfulDataStreaming() throws Exception {
        // Given
        SelectedColumnsQueryConfig config = new SelectedColumnsQueryConfig();
        config.setTableName("test_table");
        config.setColumns(Arrays.asList("id", "name"));
        config.setDelimiter("|");
        config.setJoinTables(Collections.singletonList(
                new JoinTable("other_table", "LEFT JOIN", "test_table.id = other_table.test_id")
        ));
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        
        String joinedQuery = "SELECT `test_table`.`id`,`test_table`.`name` FROM `test_table` " +
                "LEFT JOIN `other_table` ON test_table.id = other_table.test_id";
        
        when(mockClickHouseService.getTotalRows("test_table")).thenReturn(5L);
        when(mockClickHouseService.getJoinedQuery(eq("test_table"), anyList(), anyList()))
                .thenReturn(joinedQuery);
        
        String pipeContent = "id|name\n1|John Doe\n2|Jane Smith";
        ByteArrayInputStream responseStream = new ByteArrayInputStream(pipeContent.getBytes(StandardCharsets.UTF_8));
        when(mockQueryResponse.getInputStream()).thenReturn(responseStream);
        CompletableFuture<QueryResponse> future = CompletableFuture.completedFuture(mockQueryResponse);
        when(mockClient.query(anyString())).thenReturn(future);
        
        // When
        long result = ingestionService.streamDataToOutputStream(config, outputStream);
        
        // Then
        assertEquals(6, result, "Should return header + row count");
        verify(mockClient).query(contains(joinedQuery));
        verify(mockClient).query(contains("FORMAT CSVWithNames"));
        verify(mockClient).query(contains("format_csv_delimiter = '|'"));
        
        String output = outputStream.toString(StandardCharsets.UTF_8);
        assertEquals(pipeContent, output, "Output should match the input pipe-delimited content");
    }

    /**
     * Test handling of row count error during streaming
     */
    @Test
    void testRowCountErrorDuringStreaming() throws Exception {
        // Given
        SelectedColumnsQueryConfig config = new SelectedColumnsQueryConfig();
        config.setTableName("test_table");
        config.setColumns(Arrays.asList("id", "name"));
        config.setDelimiter(",");
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        
        when(mockClickHouseService.getTotalRows("test_table"))
                .thenThrow(new RuntimeException("Count error"));
        when(mockClickHouseService.getJoinedQuery(eq("test_table"), anyList(), anyList()))
                .thenReturn("SELECT `test_table`.`id`,`test_table`.`name` FROM `test_table`");
        
        ByteArrayInputStream responseStream = new ByteArrayInputStream(CSV_CONTENT.getBytes(StandardCharsets.UTF_8));
        when(mockQueryResponse.getInputStream()).thenReturn(responseStream);
        CompletableFuture<QueryResponse> future = CompletableFuture.completedFuture(mockQueryResponse);
        when(mockClient.query(anyString())).thenReturn(future);
        
        // When
        long result = ingestionService.streamDataToOutputStream(config, outputStream);
        
        // Then
        assertEquals(1, result, "Should return default count when row count fails");
        verify(mockClient).query(anyString());
        
        String output = outputStream.toString(StandardCharsets.UTF_8);
        assertEquals(CSV_CONTENT, output, "Output should still match the input CSV");
    }

    /**
     * Test query error during streaming
     */
    @Test
    void testQueryErrorDuringStreaming() throws Exception {
        // Given
        SelectedColumnsQueryConfig config = new SelectedColumnsQueryConfig();
        config.setTableName("test_table");
        config.setColumns(Arrays.asList("id", "name"));
        config.setDelimiter(",");
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        
        when(mockClickHouseService.getTotalRows("test_table")).thenReturn(5L);
        when(mockClickHouseService.getJoinedQuery(eq("test_table"), anyList(), anyList()))
                .thenReturn("SELECT `test_table`.`id`,`test_table`.`name` FROM `test_table`");
        
        when(mockClient.query(anyString()))
                .thenThrow(new RuntimeException("Query failed"));
        
        // When/Then
        Exception exception = assertThrows(Exception.class, 
                () -> ingestionService.streamDataToOutputStream(config, outputStream),
                "Should throw exception when query fails");
        
        assertTrue(exception.getMessage().contains("Failed to export data"), 
                "Exception message should indicate export failure");
        assertEquals(0, outputStream.size(), "No data should be written to output stream");
    }
} 