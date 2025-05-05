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

# Pull the ClickHouse image if not already pulled
echo "Pulling ClickHouse Docker image..."
docker pull clickhouse/clickhouse-server:latest

# Create a temporary container to initialize database
echo "Setting up ClickHouse database..."
CONTAINER_ID=$(docker run -d --name clickhouse-temp \
    -e CLICKHOUSE_USER=default \
    -e CLICKHOUSE_PASSWORD=clickhouse \
    clickhouse/clickhouse-server:latest)

# Wait for ClickHouse to start
echo "Waiting for ClickHouse to initialize..."
sleep 10

# Create test database and table
echo "Creating test database and table..."
docker exec -it clickhouse-temp clickhouse-client --user default --password clickhouse -q "
CREATE DATABASE IF NOT EXISTS test;
CREATE TABLE IF NOT EXISTS test.sample (
    id UInt32,
    name String,
    value Float64
) ENGINE = MergeTree() ORDER BY id;

INSERT INTO test.sample VALUES (1, 'Test', 1.0), (2, 'Sample', 2.5), (3, 'Data', 3.14);
"

# Cleanup temporary container
echo "Cleaning up temporary container..."
docker stop clickhouse-temp
docker rm clickhouse-temp

echo ""
echo "ClickHouse setup completed successfully!"
echo "You can now start your application with: docker-compose up -d"
echo "Connect to ClickHouse at: http://localhost:8123 with default/clickhouse credentials"
echo ""

exit 0
