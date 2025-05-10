# ClickHouse-FlatFile-Sync

<div align="center">


**Bidirectional data transfer between ClickHouse databases and flat files with a modern UI**

[![Java Version](https://img.shields.io/badge/Java-24-orange.svg)](https://adoptium.net/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.4-green.svg)](https://spring.io/projects/spring-boot)
[![React](https://img.shields.io/badge/React-18-blue.svg)](https://react.dev/)
[![ClickHouse](https://img.shields.io/badge/ClickHouse-Compatible-yellow.svg)](https://clickhouse.com/)

</div>

## 📋 Overview

ClickHouse-FlatFile-Sync is a data integration tool designed to allow bidirectional data transfer between ClickHouse databases and flat files (primarily CSV). Built with a modern tech stack and optimized for performance, this application provides an intuitive interface for database administrators, data engineers, and analysts to move data efficiently between different storage formats.

Whether you need to export query results with JOINs across multiple tables to CSV files or ingest data from CSV files into ClickHouse with proper column mapping and type detection, ClickHouse-FlatFile-Sync streamlines the entire process through an easy-to-use web interface.

## 🌟 Key Features

### Bidirectional Data Transfer
- **ClickHouse → Flat File**: Export data from ClickHouse tables to CSV files
  - Support for complex queries with JOINs across multiple tables
  - Column selection with custom ordering
  - Custom delimiter configuration
  - Optimized streaming for large datasets
  - Real-time progress tracking

- **Flat File → ClickHouse**: Import CSV data into new or existing ClickHouse tables
  - Automatic schema detection and data type inference
  - Column mapping with validation
  - Batch processing with progress monitoring
  - Support for various delimiters including tabs
  - Handling of quoted fields in complex CSV formats

### Advanced Connection Management
- **Multiple Authentication Methods**:
  - Password-based authentication
  - JWT token-based authentication
  - Connection pooling for optimal performance

- **Connection Testing and Validation**:
  - Ping functionality to verify connectivity
  - Database permissions validation
  - Connection health monitoring
  - Detailed error reporting

### Intelligent Schema Handling
- **Automatic Schema Discovery**:
  - Table listing and metadata exploration
  - Column information with data types
  - Primary key detection
  - Index recommendations

- **Smart Data Type Mapping**:
  - Automatic CSV data type inference
  - ClickHouse-specific type conversion
  - Custom type mapping overrides
  - Support for complex nested types

### User Experience
- **Modern, Responsive UI**:
  - Mobile and desktop friendly design
  - Dark mode support
  - Intuitive workflow with clear visual feedback
  - Drag-and-drop file uploads

- **Progress Monitoring**:
  - Real-time progress indicators during data transfer
  - Transfer speed metrics
  - Time remaining estimation for large datasets
  - Detailed operation logs

- **Advanced Error Handling**:
  - Detailed error messages with suggestions
  - Retry mechanisms for transient errors
  - Comprehensive logging and diagnostics
  - Validation before operations start

### CSV Handling Features
- **Advanced CSV Preview**:
  - Support for headerless files with auto-generated column names
  - Proper handling of tab-delimited files with quoted fields
  - Custom delimiter detection
  - Preview of large files with efficient streaming

- **Format Flexibility**:
  - Support for various CSV dialects
  - Handling of quoted fields and escape characters
  - Support for multi-line text within fields
  - Custom date/time format parsing

## 💻 Installation

### Prerequisites

- **Java 24+** (OpenJDK or Oracle JDK)
- **Node.js 18+** and NPM 9+
- **ClickHouse Server** (local or remote)
- **Maven 3.8+** (for building from source)
- **Git** (for cloning the repository)

### Option 1: Docker Deployment (Recommended)

The easiest way to get started is using our Docker Compose setup:

```bash
# Clone the repository
git clone https://github.com/imshrishk/ClickHouse-FlatFile-Sync.git
cd ClickHouse-FlatFile-Sync

# Start the application (backend + frontend + clickhouse)
docker-compose up -d
```

This will start the application with the following services:
- Frontend: http://localhost:5173
- Backend API: http://localhost:8080/api
- ClickHouse: http://localhost:8123

### Option 2: Manual Installation

#### Backend Setup

```bash
# Clone the repository
git clone https://github.com/imshrishk/ClickHouse-FlatFile-Sync.git
cd ClickHouse-FlatFile-Sync/backend

# Build the application
./mvnw clean package -DskipTests

# Run the application
java -jar target/bidirectional-0.0.1-SNAPSHOT.jar
```

Backend will be available at http://localhost:8080/api.

#### Frontend Setup

```bash
# Navigate to the frontend directory
cd ../frontend

# Install dependencies
npm install

# Start the development server
npm run dev
```

Frontend will be available at http://localhost:5173.

### Configuration Options

Create an `application.yml` file in the `backend/src/main/resources` directory or use environment variables to customize the configuration:

```yaml
# Server configuration
server:
  port: 8080
  servlet:
    context-path: /api
  compression:
    enabled: true
    mime-types: text/html,text/css,application/javascript,application/json
  tomcat:
    max-http-form-post-size: -1
    max-swallow-size: -1
    connection-timeout: 300000

# ClickHouse default connection settings (optional)
clickhouse:
  default:
    host: localhost
    port: 8123
    protocol: http
    database: default
    username: default
    password: ""
    auth-type: basic
    connect-timeout: 60000
    socket-timeout: 300000

# File storage configuration
file:
  upload:
    temp-dir: ./temp
    max-size: 10GB
    chunk-size: 10MB
    streaming-enabled: true
```

Or via environment variables:

```bash
export SERVER_PORT=8080
export CLICKHOUSE_DEFAULT_HOST=localhost
export CLICKHOUSE_DEFAULT_PORT=8123
export SPRING_PROFILES_ACTIVE=docker
```

#### Docker Environment Variables

When using Docker, you can configure the application via environment variables in the docker-compose.yml file:

```yaml
backend:
  environment:
    - SPRING_PROFILES_ACTIVE=docker
    - CLICKHOUSE_DEFAULT_HOST=clickhouse
    - CLICKHOUSE_DEFAULT_PORT=8123
    - CLICKHOUSE_DEFAULT_USERNAME=default
    - CLICKHOUSE_DEFAULT_PASSWORD=clickhouse
    - CONFIG_FRONTEND=http://frontend:80, http://localhost:5173
    - JAVA_OPTS=-Dserver.port=8080 -Dserver.servlet.context-path=/api

frontend:
  environment:
    - VITE_SPRING_BOOT_URL=http://backend:8080/api
```

## 📝 Usage

### Basic Workflow

1. **Launch the Application**:
   - Open your browser and navigate to the application URL
   - For local setup: http://localhost:5173

2. **Choose Operation Type**:
   - Select "Upload to ClickHouse" (CSV → Database) or
   - Select "Download from ClickHouse" (Database → CSV)

3. **Database Connection**:
   - Configure ClickHouse connection parameters
   - Test the connection to ensure connectivity
   - Save connection details for future use

4. **Data Selection and Mapping**:
   - For uploads: Select CSV file, configure delimiter, map columns
   - For downloads: Select table(s), configure JOIN operations, select columns

5. **Execute Transfer**:
   - Review configuration
   - Click "Start Transfer" to begin the operation
   - Monitor progress in real-time

6. **View Results**:
   - Download generated files or
   - View import summary with row counts

### Advanced Usage Examples

#### Exporting Joined Data

To export data from multiple joined tables:

1. Select "Download from ClickHouse" from the main menu
2. Configure your ClickHouse connection
3. Select the main table from the tables list
4. Click "Add Join" to add additional tables
5. Configure join type (LEFT, INNER, etc.) and join conditions
6. Select columns from all tables to include in the export
7. Preview the data to verify query correctness
8. Execute the export to download the CSV file

#### Handling Large Files

For large file uploads (>1GB):

1. Increase JVM heap size: `java -Xmx4g -jar bidirectional-0.0.1-SNAPSHOT.jar`
2. Enable streaming mode in the UI by checking "Use streaming mode"
3. Consider using tab-delimited files for better performance
4. For very large files, use the batch import feature to process chunks

#### Working with Headerless CSV Files

For CSV files without headers:

1. Upload your file and select the appropriate delimiter
2. Uncheck the "Has Header" option in the CSV preview screen
3. The application will generate column names automatically (Column1, Column2, etc.)
4. You can rename these columns before proceeding with the import
5. Map the generated columns to appropriate ClickHouse data types

## 📚 Documentation

### API Reference

The backend provides a RESTful API that can be consumed programmatically:

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/api/clickhouse/test-connection` | POST | Test connection to ClickHouse |
| `/api/clickhouse/tables` | POST | List available tables |
| `/api/clickhouse/columns` | POST | Get columns for a table |
| `/api/clickhouse/query-selected-columns` | POST | Query selected columns with optional JOINs |
| `/api/clickhouse/preview-csv` | POST | Preview CSV file contents |
| `/api/clickhouse/upload` | POST | Upload CSV to ClickHouse |
| `/api/clickhouse/download` | POST | Export ClickHouse data to CSV |
| `/api/clickhouse/types` | POST | Get available data types |
| `/api/health-test` | GET | Check API health status |

All API endpoints are accessible via the `/api` context path (e.g., `http://localhost:8080/api/clickhouse/test-connection`).

### Architecture

The application follows a modern microservices architecture:

```
+-------------------+         +-------------------+
|                   |         |                   |
|  React Frontend   |<------->|  Spring Backend   |
|  (TypeScript)     |   API   |  (Java)           |
|                   |         |                   |
+-------------------+         +-------------------+
                                       |
                                       v
                              +-------------------+
                              |                   |
                              |    ClickHouse     |
                              |    Database       |
                              |                   |
                              +-------------------+
```

#### Key Components

- **Frontend**: React 18 with TypeScript, Vite, and modern UI components
- **Backend**: Spring Boot 3.4 with Java 24
- **Data Processing**: Custom CSV parsing and ClickHouse integration
- **API Layer**: RESTful endpoints with streaming support for large files
- **Security**: CORS configuration and authentication options

### Security Considerations

- All REST endpoints are protected against CSRF attacks
- JWT tokens are securely stored and transmitted
- Password authentication uses bcrypt hashing
- File uploads are validated for content type and size
- CORS is properly configured for secure cross-origin requests

## 🔧 Troubleshooting

### Common Issues

#### Connection Errors

**Problem**: Unable to connect to ClickHouse server.

**Solution**:
- Verify server URL, port, and credentials
- Check firewall settings
- Ensure ClickHouse server is running
- Check network connectivity with `ping` or `telnet`
- Verify Docker network settings if using containerized setup
- Check the logs for detailed error messages: `docker-compose logs backend`

#### API Connectivity Issues

**Problem**: Frontend not connecting to backend API, or 405 Method Not Allowed errors.

**Solution**:
- Ensure backend is running on the expected port (8080)
- Verify that the server context path is set to `/api` in application properties
- Check that controller mappings don't duplicate the `/api` prefix
- Confirm proper CORS configuration in both frontend and backend
- Ensure the Nginx configuration correctly proxies requests to the backend
- Verify environment variables for frontend API URL: `VITE_SPRING_BOOT_URL=http://backend:8080/api`
- Use the built-in server health check: `http://localhost:8080/api/health-test`

#### Memory Errors

**Problem**: `OutOfMemoryError` during large file processing.

**Solution**:
- Increase JVM heap size: `java -Xmx4g -jar bidirectional-0.0.1-SNAPSHOT.jar`
- Enable streaming mode in the UI
- Process files in smaller batches
- Adjust the `file.upload.chunk-size` setting in application.yml
- Use tab-delimited format for better performance with large files
- For Docker, increase container memory limits in docker-compose.yml

#### Type Conversion Errors

**Problem**: Data type conversion errors during import.

**Solution**:
- Check source data for invalid values
- Manually specify column types instead of auto-detection
- Adjust type inference settings in the UI
- Pre-process problematic columns in your CSV files
- Check for date format inconsistencies
- Review logs for specific conversion errors

#### Download Failed: Network Error

**Problem**: Getting "Download Failed: Network Error" when trying to download data from ClickHouse.

**Solution**:
- This issue is typically caused by improper handling of large file downloads or CORS configuration issues
- Ensure Nginx is configured with proper timeout settings and disabled buffering for large downloads:
  ```nginx
  # Disable buffering for large responses
  proxy_buffering off;
  proxy_request_buffering off;
  
  # Increase timeouts for large operations
  proxy_connect_timeout 600s;
  proxy_send_timeout 600s;
  proxy_read_timeout 600s;
  ```
- Add the Access-Control-Expose-Headers to include 'Content-Disposition' and 'Content-Length':
  ```nginx
  add_header 'Access-Control-Expose-Headers' 'Content-Disposition,Content-Length,X-Line-Count' always;
  ```
- For more reliable downloads, prefer using the Fetch API over Axios in frontend code
- Use direct streaming from the backend to the client rather than creating temporary files
- If still facing issues, check network monitor in browser developer tools for specific error codes
- In Docker environments, ensure sufficient memory is allocated to containers
- Increase timeout settings in application.yml:
  ```yaml
  server:
    tomcat:
      connection-timeout: 300000
  clickhouse:
    default:
      socket-timeout: 300000
  ```

### Logging

To enable debug logging, add the following to your `application.yml`:

```yaml
logging:
  level:
    root: INFO
    org.example.bidirectional: DEBUG
    org.example.bidirectional.service: TRACE
    com.clickhouse.client: DEBUG
```

Log files are located in:
- Linux/macOS: `./logs/application.log`
- Windows: `.\logs\application.log`
- Docker: Access logs via `docker-compose logs backend`

## 🚀 Advanced Features

### CSV Preview for Complex Formats

The application includes specialized handling for complex CSV formats:

- **Headerless Files**: Automatically generates column names for files without headers
- **Quoted Fields**: Properly handles quoted fields in tab-delimited files
- **Custom Delimiters**: Supports comma, tab, semicolon, and custom delimiters
- **Large File Preview**: Efficiently previews large files by processing only the first portion

### Streaming Data Transfer

For large datasets, the application uses streaming to efficiently transfer data:

- **Chunked Processing**: Processes large files in manageable chunks
- **Memory Optimization**: Minimizes memory usage during large transfers
- **Progress Tracking**: Provides real-time progress updates during transfers
- **Resumable Transfers**: Supports resuming interrupted transfers

### Data Type Inference

The application includes smart data type inference for CSV imports:

- **Automatic Detection**: Analyzes sample data to suggest appropriate types
- **ClickHouse Optimization**: Recommends optimal ClickHouse-specific types
- **Manual Override**: Allows manual type selection when needed
- **Validation**: Validates data against selected types before import

## 📈 Performance Tips

- Use tab-delimited format for large files (faster parsing than CSV)
- Enable streaming mode for files larger than 1GB
- Use appropriate ClickHouse data types for better query performance
- For very large datasets, consider using ClickHouse's native format instead of CSV
- Adjust batch size settings for optimal performance on your hardware
- Use SSD storage for temporary files during processing
- Monitor memory usage and adjust JVM settings accordingly
