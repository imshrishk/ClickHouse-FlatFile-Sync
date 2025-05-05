package org.example.bidirectional.service;

import com.clickhouse.client.api.Client;
import com.clickhouse.client.api.query.QueryResponse;
import com.clickhouse.client.api.query.QuerySettings;
import com.clickhouse.data.ClickHouseFormat;
import com.opencsv.CSVReader;
import org.example.bidirectional.config.ConnectionConfig;
import org.example.bidirectional.exception.AuthenticationException;
import org.example.bidirectional.model.ColumnInfo;
import org.example.bidirectional.model.JoinTable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.*;
import java.util.concurrent.Future;

/**
 * Service class for managing ClickHouse database operations.
 * Handles connection, queries, schema exploration, and data manipulation.
 * 
 * <p>This service is the primary interface between the application and ClickHouse database.
 * It establishes and maintains connections using the official ClickHouse Java client,
 * and provides methods for executing queries, fetching metadata, and managing data transfer.</p>
 * 
 * <p>Key responsibilities:</p>
 * <ul>
 *   <li>Connection management with authentication support (JWT/password)</li>
 *   <li>Schema discovery (tables, columns, types)</li>
 *   <li>Data querying with support for JOIN operations</li>
 *   <li>Table creation and structure definition</li>
 *   <li>CSV data format handling for import/export operations</li>
 * </ul>
 * 
 * <p>The service uses the ClickHouse HTTP interface and supports both
 * synchronous and asynchronous query execution through the client API.</p>
 */
public class ClickHouseService {
    private static final Logger logger = LoggerFactory.getLogger(ClickHouseService.class);
    
    private final Client client;
    private final String database;
    private static ArrayList<String> types = null;

    /**
     * Converts a string representation of special characters to actual characters.
     * Used primarily for handling delimiter strings in CSV processing.
     * 
     * <p>Supports common escape sequences like:</p>
     * <ul>
     *   <li>\n - newline</li>
     *   <li>\t - tab</li>
     *   <li>\r - carriage return</li>
     *   <li>\" - double quote</li>
     *   <li>\\ - backslash</li>
     * </ul>
     * 
     * <p>For single characters, returns the first character of the input string.</p>
     * 
     * @param input The string representation of the character
     * @return The converted character
     * @throws IllegalArgumentException if input is null or empty
     */
    public static char convertStringToChar(String input) {
        if (input == null || input.isEmpty()) {
            logger.error("Input for character conversion is null or empty");
            throw new IllegalArgumentException("Input must be a non-empty string");
        }

        return switch (input) {
            case "\\n" -> '\n';
            case "\\t" -> '\t';
            case "\\r" -> '\r';
            case "\\b" -> '\b';
            case "\\f" -> '\f';
            case "\\'" -> '\'';
            case "\\\"" -> '\"';
            case "\\\\" -> '\\';
            default -> input.charAt(0);
        };
    }

    /**
     * Constructs a ClickHouseService with the provided connection configuration.
     * Establishes a connection to the ClickHouse database with appropriate authentication.
     * 
     * <p>The connection process:</p>
     * <ol>
     *   <li>Configures the client builder with connection parameters (timeout, compression, etc.)</li>
     *   <li>Sets up the endpoint based on protocol (HTTP/HTTPS)</li>
     *   <li>Configures authentication using either JWT token or password</li>
     *   <li>Tests the connection with a ping request</li>
     * </ol>
     * 
     * <p>Connection failures are converted to specific exceptions based on the error type.
     * Authentication failures throw AuthenticationException, while other errors
     * throw RuntimeException with appropriate messages.</p>
     * 
     * @param props The connection configuration including host, port, credentials, etc.
     * @throws AuthenticationException if authentication fails
     * @throws RuntimeException if connection fails for other reasons
     */
    public ClickHouseService(ConnectionConfig props) {
        logger.info("Initializing ClickHouseService with connection to {}:{} (database: {})", 
            props.getHost(), props.getPort(), props.getDatabase());
        
        this.database = props.getDatabase().trim();

        try {
            // Configure client builder with common settings
            var cb = new Client.Builder()
                    .setUsername(props.getUsername())
                    .compressServerResponse(true)
                    .setDefaultDatabase(props.getDatabase())
                    .setConnectTimeout(60_000)     // 60 seconds
                    .setSocketTimeout(60_000);     // 60 seconds

            String host = props.getHost();

            // Configure endpoint based on protocol (HTTP/HTTPS)
            if (props.getProtocol().equalsIgnoreCase("http")) {
                if (!host.startsWith("http://"))
                    host = "http://" + host;

                cb.addEndpoint(host + ":" + props.getPort());
                logger.debug("Using HTTP endpoint: {}", host + ":" + props.getPort());
            } else if (props.getProtocol().equalsIgnoreCase("https")) {
                if (!host.startsWith("https://"))
                    host = "https://" + host;

                cb.addEndpoint(host + ":" + props.getPort());
                logger.debug("Using HTTPS endpoint: {}", host + ":" + props.getPort());
            } else {
                logger.error("Unsupported protocol: {}", props.getProtocol());
                throw new IllegalArgumentException("Unsupported protocol: " + props.getProtocol());
            }

            // Configure authentication (JWT or password)
            if (props.getAuthType().equalsIgnoreCase("jwt")) {
                logger.debug("Using JWT authentication");
                if (props.getJwt() == null || props.getJwt().isEmpty()) {
                    logger.error("JWT token is missing");
                    throw new AuthenticationException("JWT token is required but not provided");
                }
                this.client = cb.setAccessToken(props.getJwt()).build();
            } else if (props.getAuthType().equalsIgnoreCase("password")) {
                logger.debug("Using password authentication");
                if (props.getPassword() == null || props.getPassword().isEmpty()) {
                    logger.error("Password is missing");
                    throw new AuthenticationException("Password is required but not provided");
                }
                this.client = cb.setPassword(props.getPassword()).build();
            } else {
                logger.error("Invalid authentication type: {}", props.getAuthType());
                throw new AuthenticationException("Invalid authentication type: " + props.getAuthType());
            }

            // Test connection with ping
            if (!client.ping()) {
                logger.error("Connection test failed");
                throw new AuthenticationException("Connection test failed - invalid credentials or token");
            }
            
            logger.info("Successfully connected to ClickHouse database: {}", props.getDatabase());
        } catch (Exception e) {
            if (e instanceof AuthenticationException) {
                logger.error("Authentication error: {}", e.getMessage());
                throw (AuthenticationException) e;
            } else if (e.getMessage().toLowerCase().contains("authentication")) {
                logger.error("Authentication failed: {}", e.getMessage());
                throw new AuthenticationException("Invalid credentials or token: " + e.getMessage());
            }
            logger.error("Failed to connect to ClickHouse: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to connect to ClickHouse: " + e.getMessage(), e);
        }
    }

    /**
     * Tests if the connection to ClickHouse is active.
     * Uses the client ping operation to verify connectivity without executing a full query.
     * 
     * <p>This method is useful for:</p>
     * <ul>
     *   <li>Checking connection status before executing queries</li>
     *   <li>Health checks in monitoring systems</li>
     *   <li>Verifying reconnection attempts after connection failures</li>
     * </ul>
     * 
     * @return true if connection is active, false otherwise
     */
    public boolean testConnection() {
        boolean result = client.ping();
        logger.debug("Connection test result: {}", result);
        return result;
    }

    /**
     * Fetches a list of strings from ClickHouse based on the provided SQL query.
     * The first column of each row is returned as a list of strings.
     * 
     * <p>This is a helper method used internally by other service methods to execute
     * queries that return a single column of results, such as:</p>
     * <ul>
     *   <li>Table listings</li>
     *   <li>Column names</li>
     *   <li>Data type names</li>
     *   <li>Single-column aggregate results</li>
     * </ul>
     *
     * <p>The method uses CSV format for transferring data from ClickHouse
     * and parses the results using CSVReader for reliability.</p>
     *
     * @param sqlQuery the SQL query to execute
     * @return a list of strings from the first column of the result set
     * @throws RuntimeException if query execution fails
     */
    private List<String> getListFromResponse(String sqlQuery) {
        logger.debug("Executing query to get list: {}", sqlQuery);
        QuerySettings settings = new QuerySettings().setFormat(ClickHouseFormat.CSV);
        Future<QueryResponse> response = client.query(sqlQuery, settings);

        try (QueryResponse qr = response.get();
             BufferedReader reader = new BufferedReader(new InputStreamReader(qr.getInputStream()));
             CSVReader csvReader = new CSVReader(reader)) {
            List<String> names = new ArrayList<>();

            String[] row;
            while ((row = csvReader.readNext()) != null) {
                if (row.length > 0) {
                    names.add(row[0]);
                } else {
                    logger.warn("Encountered empty row in query result");
                }
            }

            logger.debug("Query returned {} results", names.size());
            return names;
        } catch (Exception e) {
            logger.error("Failed to fetch data from ClickHouse: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to fetch data from ClickHouse: " + e.getMessage(), e);
        }
    }

    /**
     * Gets the total number of rows in a table.
     * Executes a COUNT(*) query on the specified table.
     * 
     * <p>This method is useful for:</p>
     * <ul>
     *   <li>Monitoring table size</li>
     *   <li>Calculating ingestion progress</li>
     *   <li>Planning query execution for large tables</li>
     *   <li>Verifying data operations (insert, delete)</li>
     * </ul>
     * 
     * @param tableName The name of the table
     * @return The total row count
     * @throws IllegalArgumentException if tableName is null or empty
     * @throws RuntimeException if the query fails
     */
    public long getTotalRows(String tableName) {
        if (tableName == null || tableName.trim().isEmpty()) {
            logger.error("Cannot get row count for null or empty table name");
            throw new IllegalArgumentException("Table name cannot be null or empty");
        }
        
        logger.debug("Getting total rows for table: {}", tableName);
        String query = "SELECT COUNT(*) FROM " + quote(tableName);
        try {
            List<String> result = getListFromResponse(query);
            if (result.isEmpty()) {
                logger.warn("Count query returned no results for table: {}", tableName);
                return 0;
            }
            long count = Long.parseLong(result.getFirst());
            logger.debug("Table {} has {} rows", tableName, count);
            return count;
        } catch (NumberFormatException e) {
            logger.error("Failed to parse row count for table {}: {}", tableName, e.getMessage());
            throw new RuntimeException("Failed to parse row count: " + e.getMessage(), e);
        }
    }

    /**
     * Lists all tables in the current database.
     * Executes a SHOW TABLES query and returns table names.
     * 
     * <p>This method provides database schema discovery functionality and is 
     * used in the application to:</p>
     * <ul>
     *   <li>Populate table selection dropdowns</li>
     *   <li>Verify table existence before operations</li>
     *   <li>Support database exploration features</li>
     * </ul>
     * 
     * @return List of table names
     */
    public List<String> listTables() {
        logger.debug("Listing tables in database: {}", database);
        List<String> tables = getListFromResponse("SHOW TABLES FROM " + database);
        logger.info("Found {} tables in database {}", tables.size(), database);
        return tables;
    }

    /**
     * Gets column information (name and type) for a specific table.
     * Queries the system.columns table to retrieve metadata about the columns.
     * 
     * <p>This method is essential for:</p>
     * <ul>
     *   <li>Schema discovery and exploration</li>
     *   <li>Dynamic query building</li>
     *   <li>Type-aware data processing</li>
     *   <li>Column selection interfaces</li>
     * </ul>
     * 
     * <p>The returned list contains ColumnInfo objects with name and type information,
     * which can be used for building type-aware queries and validating data.</p>
     * 
     * @param tableName The name of the table
     * @return List of ColumnInfo objects containing column name and type
     * @throws IllegalArgumentException if tableName is null or empty
     * @throws RuntimeException if the query fails
     */
    public List<ColumnInfo> getColumns(String tableName) {
        if (tableName == null || tableName.trim().isEmpty()) {
            logger.error("Cannot get columns for null or empty table name");
            throw new IllegalArgumentException("Table name cannot be null or empty");
        }
        
        logger.debug("Getting columns for table: {}", tableName);
        String sql = String.format("SELECT name, type FROM system.columns WHERE database = '%s' AND table = '%s';",
                database, tableName);

        QuerySettings settings = new QuerySettings().setFormat(ClickHouseFormat.CSV);
        Future<QueryResponse> response = client.query(sql, settings);

        try (QueryResponse qr = response.get();
             BufferedReader reader = new BufferedReader(new InputStreamReader(qr.getInputStream()));
             CSVReader csvReader = new CSVReader(reader)) {
            List<ColumnInfo> columns = new ArrayList<>();

            String[] row;
            while ((row = csvReader.readNext()) != null) {
                if (row.length >= 2) {
                    columns.add(new ColumnInfo(row[0], row[1]));
                } else {
                    logger.warn("Unexpected column data format: expected 2 fields, got {}", row.length);
                }
            }

            logger.info("Found {} columns in table {}", columns.size(), tableName);
            return columns;
        } catch (Exception e) {
            logger.error("Failed to fetch columns for table {}: {}", tableName, e.getMessage(), e);
            throw new RuntimeException("Failed to fetch columns: " + e.getMessage(), e);
        }
    }

    /**
     * Gets all supported data types in ClickHouse.
     * Caches the results after the first call for better performance.
     * 
     * <p>This method provides information about available data types by:
     * <ul>
     *   <li>Querying the system.data_type_families table</li>
     *   <li>Including both base types and aliases</li>
     *   <li>Ordering types with String first for user convenience</li>
     *   <li>Caching results to avoid repeated queries</li>
     * </ul>
     * 
     * <p>This information is used in the UI for column type selection
     * and for validating types during table creation.</p>
     * 
     * @return List of data type names
     */
    public ArrayList<String> getTypes() {
        logger.debug("Getting available data types");
        
        if (types != null) {
            logger.debug("Returning cached types list with {} entries", types.size());
            return types;
        }

        String sql = "SELECT name FROM system.data_type_families " +
                     "UNION DISTINCT " +
                     "SELECT alias_to AS name FROM system.data_type_families WHERE name != '';";

        types = (ArrayList<String>) getListFromResponse(sql);

        // Ensure String type is at the beginning of the list
        types.remove("String");
        Collections.sort(types);
        types.addFirst("String");

        logger.info("Retrieved {} data types from database", types.size());
        return types;
    }

    /**
     * Creates a new table with the specified column types.
     * Generates a CREATE TABLE statement based on the provided column definitions.
     * 
     * <p>Table creation process:</p>
     * <ol>
     *   <li>Validates input parameters (table name, column types)</li>
     *   <li>Builds a CREATE TABLE query with proper column definitions</li>
     *   <li>Uses MergeTree engine with default sorting by tuple()</li>
     *   <li>Executes the query and handles any errors</li>
     * </ol>
     * 
     * <p>The method uses IF NOT EXISTS to avoid errors if the table already exists,
     * allowing it to be used safely for ensuring table presence.</p>
     * 
     * @param tableName The name of the table to create
     * @param types Map of column names to data types
     * @throws IllegalArgumentException if table name or column types are invalid
     * @throws Exception if table creation fails
     */
    public void createTable(String tableName, Map<String, String> types) throws Exception {
        if (tableName == null || tableName.trim().isEmpty()) {
            logger.error("Cannot create table with null or empty name");
            throw new IllegalArgumentException("Table name cannot be null or empty");
        }
        
        if (types == null || types.isEmpty()) {
            logger.error("Cannot create table {} with no columns", tableName);
            throw new IllegalArgumentException("Column types map cannot be null or empty");
        }
        
        logger.info("Creating table {} with {} columns", tableName, types.size());
        
        // Construct a CREATE TABLE query based on the headers
        int i = types.size() - 1;

        StringBuilder createTableQuery = new StringBuilder("CREATE TABLE IF NOT EXISTS " + quote(tableName) + " (");
        for (Map.Entry<String, String> e : types.entrySet()) {
            if (e.getKey() == null || e.getKey().trim().isEmpty()) {
                logger.warn("Skipping column with empty name");
                continue;
            }
            if (e.getValue() == null || e.getValue().trim().isEmpty()) {
                logger.warn("Column {} has empty type, defaulting to String", e.getKey());
                createTableQuery.append(quote(e.getKey())).append(" String");
            } else {
                createTableQuery.append(quote(e.getKey())).append(' ').append(e.getValue());
            }

            if (i > 0)
                createTableQuery.append(", ");

            --i;
        }
        createTableQuery.append(") ENGINE = MergeTree() ORDER BY tuple();");

        String query = createTableQuery.toString();
        logger.debug("Executing table creation query: {}", query);
        
        try {
            // Execute the CREATE TABLE query
            client.query(query).get();
            logger.info("Successfully created table: {}", tableName);
        } catch (Exception e) {
            logger.error("Failed to create table {}: {}", tableName, e.getMessage(), e);
            throw new Exception("Failed to create table: " + e.getMessage(), e);
        }
    }

    /**
     * Fetches data from ClickHouse using the provided SQL query and
     * returns the result as a list of String arrays.
     * 
     * <p>This internal helper method:</p>
     * <ul>
     *   <li>Executes a query with CSV format output</li>
     *   <li>Parses the CSV data into String arrays</li>
     *   <li>Preserves column names and data types</li>
     *   <li>Handles stream processing for large result sets</li>
     * </ul>
     * 
     * <p>Used by other service methods that need to fetch and process
     * tabular data from ClickHouse.</p>
     * 
     * @param sql The SQL query to execute
     * @return List of String arrays representing rows (including header row)
     * @throws Exception if query execution fails
     */
    private List<String[]> fetchDataHelper(String sql) throws Exception {
        logger.debug("Fetching data with query: {}", sql);
        QuerySettings settings = new QuerySettings().setFormat(ClickHouseFormat.CSVWithNames);

        Future<QueryResponse> response = client.query(sql, settings);

        try (QueryResponse qr = response.get();
             BufferedReader reader = new BufferedReader(new InputStreamReader(qr.getInputStream()));
             CSVReader csvReader = new CSVReader(reader)) {
            List<String[]> result = csvReader.readAll();
            logger.debug("Query returned {} rows (including header)", result.size());
            return result;
        } catch (Exception e) {
            logger.error("Failed to fetch data: {}", e.getMessage(), e);
            throw new Exception("Failed to fetch data: " + e.getMessage(), e);
        }
    }

    /**
     * Quotes an identifier for safe use in SQL queries.
     * Prevents SQL injection and handles special characters in identifiers.
     * 
     * <p>This method implements ClickHouse's backtick quoting syntax:</p>
     * <ul>
     *   <li>Surrounds the identifier with backticks (`)</li>
     *   <li>Escapes backticks within the identifier by doubling them</li>
     *   <li>Handles null inputs by returning empty quoted string</li>
     * </ul>
     * 
     * <p>Used throughout the service to safely include table and column names in SQL queries.</p>
     * 
     * @param name The identifier to quote
     * @return The quoted identifier
     */
    protected static String quote(String name) {
        if (name == null) {
            logger.warn("Attempt to quote null name, returning empty quoted string");
            return "``";
        }
        return "`" + name.replace("`", "``") + "`";
    }

    /**
     * Builds a SQL query with proper joins based on the provided parameters.
     * Generates a SELECT statement with support for table joins and column selection.
     * 
     * <p>The method constructs a query that:</p>
     * <ol>
     *   <li>Selects specified columns from the main table</li>
     *   <li>Handles qualified column names (table.column)</li>
     *   <li>Adds JOIN clauses with proper conditions</li>
     *   <li>Ensures all identifiers are properly quoted</li>
     * </ol>
     * 
     * <p>This method is central to the application's data retrieval functionality,
     * enabling complex data relationships to be queried efficiently.</p>
     * 
     * @param tableName The main table name
     * @param columns List of column names to select
     * @param joins List of JoinTable objects defining the joins
     * @return The constructed SQL query string
     * @throws IllegalArgumentException if table name or columns are invalid
     */
    public String getJoinedQuery(String tableName, List<String> columns, List<JoinTable> joins) {
        if (tableName == null || tableName.trim().isEmpty()) {
            logger.error("Cannot create joined query with null or empty table name");
            throw new IllegalArgumentException("Table name cannot be null or empty");
        }
        
        if (columns == null || columns.isEmpty()) {
            logger.error("Cannot create joined query for table {} with no columns", tableName);
            throw new IllegalArgumentException("Columns list cannot be null or empty");
        }
        
        logger.debug("Building joined query for table {} with {} columns and {} joins", 
            tableName, columns.size(), (joins != null ? joins.size() : 0));
        
        // Build the SQL query string
        StringBuilder queryBuilder = new StringBuilder();
        queryBuilder.append("SELECT ");
        
        // Add columns to the SELECT clause
        for (int i = 0; i < columns.size(); i++) {
            if (columns.get(i) == null || columns.get(i).trim().isEmpty()) {
                logger.warn("Skipping null or empty column at index {}", i);
                continue;
            }
            
            if (columns.get(i).contains(".")) {
                // Handle qualified column names (table.column)
                String[] column = columns.get(i).split("\\.");
                if (column.length == 2) {
                    queryBuilder.append(quote(column[0]));
                    queryBuilder.append(".");
                    queryBuilder.append(quote(column[1]));
                } else {
                    logger.warn("Invalid qualified column name format: {}", columns.get(i));
                    queryBuilder.append(quote(columns.get(i)));
                }
            } else {
                // Handle unqualified column names
                queryBuilder.append(quote(tableName)).append('.');
                queryBuilder.append(quote(columns.get(i)));
            }

            if (i < columns.size() - 1) queryBuilder.append(',');
        }
        
        // Add FROM clause with main table
        queryBuilder.append(" FROM `").append(tableName).append('`');

        // Add JOIN clauses if provided
        if (joins != null && !joins.isEmpty()) {
            for (JoinTable jt : joins) {
                if (jt.getTableName() == null || jt.getTableName().trim().isEmpty()) {
                    logger.warn("Skipping join with null or empty table name");
                    continue;
                }
                
                if (jt.getJoinType() == null || jt.getJoinType().trim().isEmpty()) {
                    logger.warn("Using default LEFT JOIN for join with table {}", jt.getTableName());
                    queryBuilder.append(" LEFT JOIN ");
                } else {
                    queryBuilder.append(" ").append(jt.getJoinType()).append(" ");
                }
                
                queryBuilder.append(quote(jt.getTableName())).append(" ON ");
                
                if (jt.getJoinCondition() == null || jt.getJoinCondition().trim().isEmpty()) {
                    logger.warn("Join condition is empty for table {}, using 1=1", jt.getTableName());
                    queryBuilder.append("1=1");
                } else {
                    queryBuilder.append(jt.getJoinCondition());
                }
            }
        }

        String query = queryBuilder.toString();
        logger.debug("Generated joined query: {}", query);
        return query;
    }

    /**
     * Queries selected columns from a table, optionally with joins.
     * Returns a preview of up to 100 rows for UI display.
     * 
     * <p>This method is designed for generating data previews and supports:</p>
     * <ul>
     *   <li>Column selection for focused data retrieval</li>
     *   <li>JOIN operations for related data</li>
     *   <li>Custom delimiters for CSV formatting</li>
     *   <li>Preview limits to ensure fast response for large tables</li>
     * </ul>
     * 
     * <p>The results include a header row with column names, followed by data rows.</p>
     * 
     * @param tableName The main table name
     * @param columns List of column names to select
     * @param joins List of JoinTable objects defining the joins
     * @param delimiter The delimiter to use in the CSV output
     * @return List of String arrays representing rows (including header row)
     * @throws Exception if query execution fails
     */
    public List<String[]> querySelectedColumns(
            String tableName,
            List<String> columns,
            List<JoinTable> joins,
            String delimiter
    ) throws Exception {
        try {
            logger.info("Querying selected columns from table {} (preview)", tableName);
            String sql = getJoinedQuery(tableName, columns, joins);

            return fetchDataHelper(sql + " LIMIT 100");
        } catch (Exception e) {
            logger.error("Failed to query selected columns from table {}: {}", tableName, e.getMessage(), e);
            throw new Exception("Failed to query selected columns: " + e.getMessage(), e);
        }
    }

    /**
     * Gets the ClickHouse client instance.
     * Provides access to the underlying client for advanced operations.
     * 
     * <p>This method exposes the ClickHouse client instance for cases where
     * direct client access is needed, such as:</p>
     * <ul>
     *   <li>Custom query execution</li>
     *   <li>Batch operations</li>
     *   <li>Advanced settings configuration</li>
     *   <li>Direct data streaming</li>
     * </ul>
     * 
     * <p>Use with caution as direct client usage bypasses the service's
     * error handling and logging.</p>
     * 
     * @return The ClickHouse client
     */
    public Client getClient() {
        return client;
    }

    /**
     * Executes a SQL query and writes the result directly to an output stream.
     * This is useful for streaming large result sets without loading them into memory.
     * 
     * @param sql The SQL query to execute
     * @param outputStream The output stream to write to
     * @param format The format to use (e.g., CSV, CSVWithNames)
     * @throws Exception If query execution fails
     */
    public void executeQueryToOutputStream(String sql, java.io.OutputStream outputStream, String format) throws Exception {
        logger.debug("Executing query with {} format: {}", format, sql);
        
        // Use CSVWithNames by default if not specified
        ClickHouseFormat clickHouseFormat;
        if (format == null || format.trim().isEmpty()) {
            clickHouseFormat = ClickHouseFormat.CSVWithNames;
        } else {
            try {
                clickHouseFormat = ClickHouseFormat.valueOf(format);
            } catch (IllegalArgumentException e) {
                logger.warn("Invalid format specified '{}', falling back to CSVWithNames", format);
                clickHouseFormat = ClickHouseFormat.CSVWithNames;
            }
        }
        
        QuerySettings settings = new QuerySettings().setFormat(clickHouseFormat);
        
        try {
            Future<QueryResponse> response = client.query(sql, settings);
            
            try (QueryResponse qr = response.get();
                 java.io.InputStream inputStream = qr.getInputStream()) {
                
                byte[] buffer = new byte[8192];
                int bytesRead;
                long totalBytes = 0;
                
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                    totalBytes += bytesRead;
                    
                    // Log progress for large result sets
                    if (totalBytes % (50 * 8192) == 0) {
                        logger.debug("Transferred approximately {} MB of data", totalBytes / (1024 * 1024));
                    }
                }
                
                outputStream.flush();
                logger.info("Successfully transferred {} MB of data", totalBytes / (1024 * 1024));
            }
        } catch (Exception e) {
            logger.error("Failed to execute query: {}", e.getMessage(), e);
            throw new Exception("Failed to execute query: " + e.getMessage(), e);
        }
    }
}
