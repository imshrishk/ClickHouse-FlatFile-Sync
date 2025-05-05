# ClickHouse-FlatFile-Sync

<div align="center">


**Bidirectional data transfer between ClickHouse databases and flat files with a modern UI**

[![Java Version](https://img.shields.io/badge/Java-24-orange.svg)](https://adoptium.net/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.4-green.svg)](https://spring.io/projects/spring-boot)
[![React](https://img.shields.io/badge/React-18-blue.svg)](https://react.dev/)
[![ClickHouse](https://img.shields.io/badge/ClickHouse-Compatible-yellow.svg)](https://clickhouse.com/)

[Features](#key-features) •
[Installation](#installation) •
[Usage](#usage) •
[Documentation](#documentation) •
[Troubleshooting](#troubleshooting)

</div>

## 📋 Overview

ClickHouse-FlatFile-Sync is a data integration tool designed to allows data transfer between ClickHouse databases and flat files (primarily CSV). Built with a modern tech stack and optimized for performance, this application provides an intuitive interface for database administrators, data engineers, and analysts to move data efficiently between different storage formats.

Whether you need to export query results with JOINs across multiple tables to CSV files or ingest data from CSV files into ClickHouse with proper column mapping and type detection, ClickHouse-FlatFile-Sync streamlines the entire process through an easy-to-use web interface.

## 🌟 Key Features

### Bidirectional Data Transfer
- **ClickHouse → Flat File**: Export data from ClickHouse tables to CSV files
  - Support for complex queries with JOINs across multiple tables
  - Column selection with custom ordering
  - Custom delimiter configuration
  - Optimized streaming for large datasets

- **Flat File → ClickHouse**: Import CSV data into new or existing ClickHouse tables
  - Automatic schema detection and data type inference
  - Column mapping with validation
  - Batch processing with progress monitoring
  - Support for various delimiters

### Advanced Connection Management
- **Multiple Authentication Methods**:
  - Password-based authentication
  - JWT token-based authentication
  - Connection pooling for optimal performance

- **Connection Testing and Validation**:
  - Ping functionality to verify connectivity
  - Database permissions validation
  - Connection health monitoring

### Intelligent Schema Handling
- **Automatic Schema Discovery**:
  - Table listing and metadata exploration
  - Column information with data types
  - Primary key detection

- **Smart Data Type Mapping**:
  - Automatic CSV data type inference
  - ClickHouse-specific type conversion
  - Custom type mapping overrides

### User Experience
- **Modern, Responsive UI**:
  - Mobile and desktop friendly design
  - Dark mode support
  - Intuitive workflow with clear visual feedback

- **Progress Monitoring**:
  - Real-time progress indicators during data transfer
  - Transfer speed metrics
  - Time remaining estimation for large datasets

- **Advanced Error Handling**:
  - Detailed error messages with suggestions
  - Retry mechanisms for transient errors
  - Comprehensive logging and diagnostics

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

Backend will be available at http://localhost:8080.

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

# ClickHouse default connection settings (optional)
clickhouse:
  default:
    host: localhost
    port: 8123
    protocol: http
    database: default
    username: default
    password: ""

# File storage configuration
file:
  upload:
    temp-dir: ./temp
    max-size: 2GB
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
   - For local setup: http://localhost:8080 (or http://localhost:5173 in development mode)

2. **Choose Operation Type**:
   - Select "Upload to ClickHouse" (CSV → Database) or
   - Select "Download from ClickHouse" (Database → CSV)

3. **Database Connection**:
   - Configure ClickHouse connection parameters
   - Test the connection to ensure connectivity

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

### Security Considerations

- All REST endpoints are protected against CSRF attacks
- JWT tokens are securely stored and transmitted
- Password authentication uses bcrypt hashing
- File uploads are validated for content type and size

## 🔧 Troubleshooting

### Common Issues

#### Connection Errors

**Problem**: Unable to connect to ClickHouse server.

**Solution**:
- Verify server URL, port, and credentials
- Check firewall settings
- Ensure ClickHouse server is running
- Check network connectivity with `ping` or `telnet`

#### API Connectivity Issues

**Problem**: Frontend not connecting to backend API, or 405 Method Not Allowed errors.

**Solution**:
- Ensure backend is running on the expected port (8080)
- Verify that the server context path is set to `/api` in application properties
- Check that controller mappings don't duplicate the `/api` prefix
- Confirm proper CORS configuration in both frontend and backend
- Ensure the Nginx configuration correctly proxies requests to the backend
- Verify environment variables for frontend API URL: `VITE_SPRING_BOOT_URL=http://backend:8080/api`

#### Memory Errors

**Problem**: `OutOfMemoryError` during large file processing.

**Solution**:
- Increase JVM heap size: `java -Xmx4g -jar bidirectional-0.0.1-SNAPSHOT.jar`
- Enable streaming mode in the UI
- Process files in smaller batches

#### Type Conversion Errors

**Problem**: Data type conversion errors during import.

**Solution**:
- Check source data for invalid values
- Manually specify column types instead of auto-detection
- Adjust type inference settings in the UI

### Logging

To enable debug logging, add the following to your `application.yml`:

```yaml
logging:
  level:
    root: INFO
    org.example.bidirectional: DEBUG
```

Log files are located in:
- Linux/macOS: `./logs/application.log`
- Windows: `.\logs\application.log`
