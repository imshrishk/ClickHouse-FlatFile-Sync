#!/bin/bash

# This script sets up ClickHouse using Docker instead of a direct binary download
# This avoids network errors when trying to download large binaries

set -e

echo "Setting up ClickHouse environment..."

# Make sure Docker is running
if ! docker info > /dev/null 2>&1; then
    echo "Error: Docker is not running. Please start Docker and try again."
    exit 1
fi

# Wait for ClickHouse to be ready
echo "Waiting for ClickHouse to be ready..."
max_attempts=30
attempt=1
while ! curl -s http://clickhouse:8123/ping > /dev/null; do
    if [ $attempt -gt $max_attempts ]; then
        echo "Error: ClickHouse did not become ready in time"
        exit 1
    fi
    echo "Attempt $attempt of $max_attempts: Waiting for ClickHouse..."
    sleep 2
    attempt=$((attempt + 1))
done
echo "ClickHouse is ready!"

# Create database and tables
echo "Creating database and tables..."
if ! curl -s http://clickhouse:8123/ --data-binary @create_schema.sql; then
    echo "Error: Failed to create database and tables"
    exit 1
fi

# Verify database creation
echo "Verifying database creation..."
if ! curl -s "http://clickhouse:8123/?query=SHOW+DATABASES" | grep -q "uk"; then
    echo "Error: Database 'uk' was not created"
    exit 1
fi

# Verify table creation
echo "Verifying table creation..."
if ! curl -s "http://clickhouse:8123/?query=SHOW+TABLES+FROM+uk" | grep -q "uk_price_paid"; then
    echo "Error: Table 'uk_price_paid' was not created"
    exit 1
fi

# Import sample data if available
if [ -f "sample_data.csv" ]; then
    echo "Importing sample data..."
    if ! curl -s http://clickhouse:8123/ --data-binary "INSERT INTO uk.uk_price_paid FORMAT CSVWithNames" < sample_data.csv; then
        echo "Error: Failed to import sample data"
        exit 1
    fi
    echo "Sample data imported successfully"
fi

echo ""
echo "ClickHouse setup completed successfully!"
echo "You can now start your application with: docker-compose up -d"
echo "Connect to ClickHouse at: http://localhost:8123 with default/clickhouse credentials"
echo ""

exit 0
