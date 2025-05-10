package org.example.bidirectional.service;

import com.clickhouse.client.api.Client;
import com.clickhouse.client.api.query.QueryResponse;
import com.clickhouse.client.api.query.QuerySettings;
import com.clickhouse.data.ClickHouseFormat;
import org.example.bidirectional.config.ConnectionConfig;
import org.example.bidirectional.exception.AuthenticationException;
import org.example.bidirectional.model.ColumnInfo;
import org.example.bidirectional.model.JoinTable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for the ClickHouseService class.
 * Uses mocks to isolate ClickHouseService from actual database connections.
 */
@ExtendWith(MockitoExtension.class)
class ClickHouseServiceTest {

    @Mock
    private Client mockClient;

    @Mock
    private Client.Builder mockBuilder;

    @Mock
    private QueryResponse mockQueryResponse;

    private ConnectionConfig validConfig;

    /**
     * Set up test fixtures before each test
     */
    @BeforeEach
    void setUp() {
        // Set up a valid connection configuration
        validConfig = new ConnectionConfig();
        validConfig.setHost("localhost");
        validConfig.setPort(8123);
        validConfig.setDatabase("default");
        validConfig.setUsername("default");
        validConfig.setPassword("password");
        validConfig.setAuthType("password");
        validConfig.setProtocol("http");
    }

    /**
     * Test convertStringToChar method with valid inputs
     */
    @ParameterizedTest
    @MethodSource("validCharConversionProvider")
    void testConvertStringToCharWithValidInput(String input, char expected) {
        assertEquals(expected, ClickHouseService.convertStringToChar(input));
    }

    /**
     * Provider for valid char conversion test cases
     */
    static Stream<Arguments> validCharConversionProvider() {
        return Stream.of(
            Arguments.of("\\n", '\n'),
            Arguments.of("\\t", '\t'),
            Arguments.of("\\r", '\r'),
            Arguments.of("\\b", '\b'),
            Arguments.of("\\f", '\f'),
            Arguments.of("\\'", '\''),
            Arguments.of("\\\"", '\"'),
            Arguments.of("\\\\", '\\'),
            Arguments.of(",", ','),
            Arguments.of(";", ';'),
            Arguments.of("|", '|')
        );
    }

    /**
     * Test convertStringToChar method with invalid inputs
     */
    @ParameterizedTest
    @ValueSource(strings = {"", "  "})
    void testConvertStringToCharWithInvalidInput(String input) {
        assertThrows(IllegalArgumentException.class, () -> ClickHouseService.convertStringToChar(input));
    }

    @Test
    void testConvertStringToCharWithNull() {
        assertThrows(IllegalArgumentException.class, () -> ClickHouseService.convertStringToChar(null));
    }

    /**
     * Test the constructor with valid password authentication
     */
    @Test
    void testConstructorWithPasswordAuth() throws Exception {
        // Given
        when(mockBuilder.setUsername(anyString())).thenReturn(mockBuilder);
        when(mockBuilder.compressServerResponse(anyBoolean())).thenReturn(mockBuilder);
        when(mockBuilder.setDefaultDatabase(anyString())).thenReturn(mockBuilder);
        when(mockBuilder.setConnectTimeout(anyLong())).thenReturn(mockBuilder);
        when(mockBuilder.setSocketTimeout(anyLong())).thenReturn(mockBuilder);
        when(mockBuilder.addEndpoint(anyString())).thenReturn(mockBuilder);
        when(mockBuilder.setPassword(anyString())).thenReturn(mockBuilder);
        when(mockBuilder.build()).thenReturn(mockClient);
        when(mockClient.ping()).thenReturn(true);

        // Use reflection to mock the Client.Builder static method
        try (var mockStatic = Mockito.mockStatic(Client.Builder.class)) {
            mockStatic.when(Client.Builder::new).thenReturn(mockBuilder);

            // When
            ClickHouseService service = new ClickHouseService(validConfig);

            // Then
            assertNotNull(service);
            verify(mockBuilder).setUsername(validConfig.getUsername());
            verify(mockBuilder).setPassword(validConfig.getPassword());
            verify(mockBuilder).addEndpoint("http://localhost:8123");
            verify(mockClient).ping();
        }
    }

    /**
     * Test the constructor with valid JWT authentication
     */
    @Test
    void testConstructorWithJwtAuth() throws Exception {
        // Given
        validConfig.setAuthType("jwt");
        validConfig.setJwt("mock.jwt.token");

        when(mockBuilder.setUsername(anyString())).thenReturn(mockBuilder);
        when(mockBuilder.compressServerResponse(anyBoolean())).thenReturn(mockBuilder);
        when(mockBuilder.setDefaultDatabase(anyString())).thenReturn(mockBuilder);
        when(mockBuilder.setConnectTimeout(anyLong())).thenReturn(mockBuilder);
        when(mockBuilder.setSocketTimeout(anyLong())).thenReturn(mockBuilder);
        when(mockBuilder.addEndpoint(anyString())).thenReturn(mockBuilder);
        when(mockBuilder.setAccessToken(anyString())).thenReturn(mockBuilder);
        when(mockBuilder.build()).thenReturn(mockClient);
        when(mockClient.ping()).thenReturn(true);

        // Use reflection to mock the Client.Builder static method
        try (var mockStatic = Mockito.mockStatic(Client.Builder.class)) {
            mockStatic.when(Client.Builder::new).thenReturn(mockBuilder);

            // When
            ClickHouseService service = new ClickHouseService(validConfig);

            // Then
            assertNotNull(service);
            verify(mockBuilder).setUsername(validConfig.getUsername());
            verify(mockBuilder).setAccessToken(validConfig.getJwt());
            verify(mockBuilder).addEndpoint("http://localhost:8123");
            verify(mockClient).ping();
        }
    }

    /**
     * Test the constructor with HTTPS protocol
     */
    @Test
    void testConstructorWithHttpsProtocol() throws Exception {
        // Given
        validConfig.setProtocol("https");

        when(mockBuilder.setUsername(anyString())).thenReturn(mockBuilder);
        when(mockBuilder.compressServerResponse(anyBoolean())).thenReturn(mockBuilder);
        when(mockBuilder.setDefaultDatabase(anyString())).thenReturn(mockBuilder);
        when(mockBuilder.setConnectTimeout(anyLong())).thenReturn(mockBuilder);
        when(mockBuilder.setSocketTimeout(anyLong())).thenReturn(mockBuilder);
        when(mockBuilder.addEndpoint(anyString())).thenReturn(mockBuilder);
        when(mockBuilder.setPassword(anyString())).thenReturn(mockBuilder);
        when(mockBuilder.build()).thenReturn(mockClient);
        when(mockClient.ping()).thenReturn(true);

        // Use reflection to mock the Client.Builder static method
        try (var mockStatic = Mockito.mockStatic(Client.Builder.class)) {
            mockStatic.when(Client.Builder::new).thenReturn(mockBuilder);

            // When
            ClickHouseService service = new ClickHouseService(validConfig);

            // Then
            assertNotNull(service);
            verify(mockBuilder).addEndpoint("https://localhost:8123");
        }
    }

    /**
     * Test constructor with failed ping
     */
    @Test
    void testConstructorWithFailedPing() throws Exception {
        // Given
        when(mockBuilder.setUsername(anyString())).thenReturn(mockBuilder);
        when(mockBuilder.compressServerResponse(anyBoolean())).thenReturn(mockBuilder);
        when(mockBuilder.setDefaultDatabase(anyString())).thenReturn(mockBuilder);
        when(mockBuilder.setConnectTimeout(anyLong())).thenReturn(mockBuilder);
        when(mockBuilder.setSocketTimeout(anyLong())).thenReturn(mockBuilder);
        when(mockBuilder.addEndpoint(anyString())).thenReturn(mockBuilder);
        when(mockBuilder.setPassword(anyString())).thenReturn(mockBuilder);
        when(mockBuilder.build()).thenReturn(mockClient);
        when(mockClient.ping()).thenReturn(false);

        // Use reflection to mock the Client.Builder static method
        try (var mockStatic = Mockito.mockStatic(Client.Builder.class)) {
            mockStatic.when(Client.Builder::new).thenReturn(mockBuilder);

            // When/Then
            assertThrows(AuthenticationException.class, () -> new ClickHouseService(validConfig));
        }
    }

    /**
     * Test constructor with invalid authentication type
     */
    @Test
    void testConstructorWithInvalidAuthType() {
        // Given
        validConfig.setAuthType("invalid");

        // Use reflection to mock the Client.Builder static method
        try (var mockStatic = Mockito.mockStatic(Client.Builder.class)) {
            mockStatic.when(Client.Builder::new).thenReturn(mockBuilder);

            // When/Then
            assertThrows(AuthenticationException.class, () -> new ClickHouseService(validConfig));
        }
    }

    /**
     * Test testConnection method
     */
    @Test
    void testTestConnection() throws Exception {
        // Given
        when(mockBuilder.setUsername(anyString())).thenReturn(mockBuilder);
        when(mockBuilder.compressServerResponse(anyBoolean())).thenReturn(mockBuilder);
        when(mockBuilder.setDefaultDatabase(anyString())).thenReturn(mockBuilder);
        when(mockBuilder.setConnectTimeout(anyLong())).thenReturn(mockBuilder);
        when(mockBuilder.setSocketTimeout(anyLong())).thenReturn(mockBuilder);
        when(mockBuilder.addEndpoint(anyString())).thenReturn(mockBuilder);
        when(mockBuilder.setPassword(anyString())).thenReturn(mockBuilder);
        when(mockBuilder.build()).thenReturn(mockClient);
        when(mockClient.ping()).thenReturn(true).thenReturn(false);

        // Use reflection to mock the Client.Builder static method
        try (var mockStatic = Mockito.mockStatic(Client.Builder.class)) {
            mockStatic.when(Client.Builder::new).thenReturn(mockBuilder);

            // When
            ClickHouseService service = new ClickHouseService(validConfig);
            boolean firstResult = service.testConnection();
            boolean secondResult = service.testConnection();

            // Then
            assertTrue(firstResult);
            assertFalse(secondResult);
        }
    }

    /**
     * Test getTotalRows method
     */
    @Test
    void testGetTotalRows() throws Exception {
        // Given
        String csvData = "100";
        InputStream inputStream = new ByteArrayInputStream(csvData.getBytes(StandardCharsets.UTF_8));
        
        when(mockClient.ping()).thenReturn(true);
        when(mockQueryResponse.getInputStream()).thenReturn(inputStream);
        
        CompletableFuture<QueryResponse> future = CompletableFuture.completedFuture(mockQueryResponse);
        when(mockClient.query(anyString(), any(QuerySettings.class))).thenReturn(future);

        // Set up for constructor
        when(mockBuilder.setUsername(anyString())).thenReturn(mockBuilder);
        when(mockBuilder.compressServerResponse(anyBoolean())).thenReturn(mockBuilder);
        when(mockBuilder.setDefaultDatabase(anyString())).thenReturn(mockBuilder);
        when(mockBuilder.setConnectTimeout(anyLong())).thenReturn(mockBuilder);
        when(mockBuilder.setSocketTimeout(anyLong())).thenReturn(mockBuilder);
        when(mockBuilder.addEndpoint(anyString())).thenReturn(mockBuilder);
        when(mockBuilder.setPassword(anyString())).thenReturn(mockBuilder);
        when(mockBuilder.build()).thenReturn(mockClient);
        
        // Use reflection to mock the Client.Builder static method
        try (var mockStatic = Mockito.mockStatic(Client.Builder.class)) {
            mockStatic.when(Client.Builder::new).thenReturn(mockBuilder);

            // When
            ClickHouseService service = new ClickHouseService(validConfig);
            long rowCount = service.getTotalRows("test_table");

            // Then
            assertEquals(100, rowCount);
            verify(mockClient).query(contains("SELECT COUNT(*) FROM"), any(QuerySettings.class));
        }
    }

    /**
     * Test listTables method
     */
    @Test
    void testListTables() throws Exception {
        // Given
        String csvData = "table1\ntable2\ntable3";
        InputStream inputStream = new ByteArrayInputStream(csvData.getBytes(StandardCharsets.UTF_8));
        
        when(mockClient.ping()).thenReturn(true);
        when(mockQueryResponse.getInputStream()).thenReturn(inputStream);
        
        CompletableFuture<QueryResponse> future = CompletableFuture.completedFuture(mockQueryResponse);
        when(mockClient.query(anyString(), any(QuerySettings.class))).thenReturn(future);

        // Set up for constructor
        when(mockBuilder.setUsername(anyString())).thenReturn(mockBuilder);
        when(mockBuilder.compressServerResponse(anyBoolean())).thenReturn(mockBuilder);
        when(mockBuilder.setDefaultDatabase(anyString())).thenReturn(mockBuilder);
        when(mockBuilder.setConnectTimeout(anyLong())).thenReturn(mockBuilder);
        when(mockBuilder.setSocketTimeout(anyLong())).thenReturn(mockBuilder);
        when(mockBuilder.addEndpoint(anyString())).thenReturn(mockBuilder);
        when(mockBuilder.setPassword(anyString())).thenReturn(mockBuilder);
        when(mockBuilder.build()).thenReturn(mockClient);
        
        // Use reflection to mock the Client.Builder static method
        try (var mockStatic = Mockito.mockStatic(Client.Builder.class)) {
            mockStatic.when(Client.Builder::new).thenReturn(mockBuilder);

            // When
            ClickHouseService service = new ClickHouseService(validConfig);
            List<String> tables = service.listTables();

            // Then
            assertEquals(3, tables.size());
            assertEquals("table1", tables.get(0));
            assertEquals("table2", tables.get(1));
            assertEquals("table3", tables.get(2));
            verify(mockClient).query(contains("SHOW TABLES FROM"), any(QuerySettings.class));
        }
    }

    /**
     * Test quote method
     */
    @Test
    void testQuote() {
        assertEquals("`test`", ClickHouseService.quote("test"));
        assertEquals("``", ClickHouseService.quote(null));
        assertEquals("````", ClickHouseService.quote("`"));
        assertEquals("`test``column`", ClickHouseService.quote("test`column"));
    }

    /**
     * Test getJoinedQuery method with simple columns
     */
    @Test
    void testGetJoinedQueryWithSimpleColumns() throws Exception {
        // Given
        String tableName = "users";
        List<String> columns = Arrays.asList("id", "name", "email");
        
        // Set up for constructor
        when(mockBuilder.setUsername(anyString())).thenReturn(mockBuilder);
        when(mockBuilder.compressServerResponse(anyBoolean())).thenReturn(mockBuilder);
        when(mockBuilder.setDefaultDatabase(anyString())).thenReturn(mockBuilder);
        when(mockBuilder.setConnectTimeout(anyLong())).thenReturn(mockBuilder);
        when(mockBuilder.setSocketTimeout(anyLong())).thenReturn(mockBuilder);
        when(mockBuilder.addEndpoint(anyString())).thenReturn(mockBuilder);
        when(mockBuilder.setPassword(anyString())).thenReturn(mockBuilder);
        when(mockBuilder.build()).thenReturn(mockClient);
        when(mockClient.ping()).thenReturn(true);
        
        // Use reflection to mock the Client.Builder static method
        try (var mockStatic = Mockito.mockStatic(Client.Builder.class)) {
            mockStatic.when(Client.Builder::new).thenReturn(mockBuilder);

            // When
            ClickHouseService service = new ClickHouseService(validConfig);
            String query = service.getJoinedQuery(tableName, columns, null);

            // Then
            String expected = "SELECT `users`.`id`,`users`.`name`,`users`.`email` FROM `users`";
            assertEquals(expected, query);
        }
    }

    /**
     * Test getJoinedQuery method with qualified columns and joins
     */
    @Test
    void testGetJoinedQueryWithQualifiedColumnsAndJoins() throws Exception {
        // Given
        String tableName = "users";
        List<String> columns = Arrays.asList("users.id", "users.name", "orders.order_id");
        List<JoinTable> joins = Collections.singletonList(
            new JoinTable("orders", "LEFT JOIN", "users.id = orders.user_id")
        );
        
        // Set up for constructor
        when(mockBuilder.setUsername(anyString())).thenReturn(mockBuilder);
        when(mockBuilder.compressServerResponse(anyBoolean())).thenReturn(mockBuilder);
        when(mockBuilder.setDefaultDatabase(anyString())).thenReturn(mockBuilder);
        when(mockBuilder.setConnectTimeout(anyLong())).thenReturn(mockBuilder);
        when(mockBuilder.setSocketTimeout(anyLong())).thenReturn(mockBuilder);
        when(mockBuilder.addEndpoint(anyString())).thenReturn(mockBuilder);
        when(mockBuilder.setPassword(anyString())).thenReturn(mockBuilder);
        when(mockBuilder.build()).thenReturn(mockClient);
        when(mockClient.ping()).thenReturn(true);
        
        // Use reflection to mock the Client.Builder static method
        try (var mockStatic = Mockito.mockStatic(Client.Builder.class)) {
            mockStatic.when(Client.Builder::new).thenReturn(mockBuilder);

            // When
            ClickHouseService service = new ClickHouseService(validConfig);
            String query = service.getJoinedQuery(tableName, columns, joins);

            // Then
            String expected = "SELECT `users`.`id`,`users`.`name`,`orders`.`order_id` FROM `users` LEFT JOIN `orders` ON users.id = orders.user_id";
            assertEquals(expected, query);
        }
    }

    /**
     * Test createTable method
     */
    @Test
    void testCreateTable() throws Exception {
        // Given
        String tableName = "new_table";
        Map<String, String> types = new LinkedHashMap<>();
        types.put("id", "UInt32");
        types.put("name", "String");
        types.put("created_at", "DateTime");
        
        CompletableFuture<QueryResponse> future = CompletableFuture.completedFuture(mockQueryResponse);
        when(mockClient.query(anyString())).thenReturn(future);
        
        // Set up for constructor
        when(mockBuilder.setUsername(anyString())).thenReturn(mockBuilder);
        when(mockBuilder.compressServerResponse(anyBoolean())).thenReturn(mockBuilder);
        when(mockBuilder.setDefaultDatabase(anyString())).thenReturn(mockBuilder);
        when(mockBuilder.setConnectTimeout(anyLong())).thenReturn(mockBuilder);
        when(mockBuilder.setSocketTimeout(anyLong())).thenReturn(mockBuilder);
        when(mockBuilder.addEndpoint(anyString())).thenReturn(mockBuilder);
        when(mockBuilder.setPassword(anyString())).thenReturn(mockBuilder);
        when(mockBuilder.build()).thenReturn(mockClient);
        when(mockClient.ping()).thenReturn(true);
        
        // Use reflection to mock the Client.Builder static method
        try (var mockStatic = Mockito.mockStatic(Client.Builder.class)) {
            mockStatic.when(Client.Builder::new).thenReturn(mockBuilder);

            // When
            ClickHouseService service = new ClickHouseService(validConfig);
            service.createTable(tableName, types);

            // Then
            verify(mockClient).query(contains("CREATE TABLE IF NOT EXISTS `new_table`"));
            verify(mockClient).query(contains("`id` UInt32"));
            verify(mockClient).query(contains("`name` String"));
            verify(mockClient).query(contains("`created_at` DateTime"));
            verify(mockClient).query(contains("ENGINE = MergeTree() ORDER BY tuple()"));
        }
    }

    /**
     * Test getClient method
     */
    @Test
    void testGetClient() throws Exception {
        // Set up for constructor
        when(mockBuilder.setUsername(anyString())).thenReturn(mockBuilder);
        when(mockBuilder.compressServerResponse(anyBoolean())).thenReturn(mockBuilder);
        when(mockBuilder.setDefaultDatabase(anyString())).thenReturn(mockBuilder);
        when(mockBuilder.setConnectTimeout(anyLong())).thenReturn(mockBuilder);
        when(mockBuilder.setSocketTimeout(anyLong())).thenReturn(mockBuilder);
        when(mockBuilder.addEndpoint(anyString())).thenReturn(mockBuilder);
        when(mockBuilder.setPassword(anyString())).thenReturn(mockBuilder);
        when(mockBuilder.build()).thenReturn(mockClient);
        when(mockClient.ping()).thenReturn(true);
        
        // Use reflection to mock the Client.Builder static method
        try (var mockStatic = Mockito.mockStatic(Client.Builder.class)) {
            mockStatic.when(Client.Builder::new).thenReturn(mockBuilder);

            // When
            ClickHouseService service = new ClickHouseService(validConfig);
            Client client = service.getClient();

            // Then
            assertSame(mockClient, client);
        }
    }
} 