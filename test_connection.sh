#!/bin/bash

echo "Testing ClickHouse HTTP connection..."
# Basic connection test
curl -s "http://localhost:8123/?user=default&password=clickhouse" -d "SELECT 1 AS test"
echo

# Test with specific database
echo "Testing connection to the UK database..."
curl -s "http://localhost:8123/?user=default&password=clickhouse&database=uk" -d "SHOW TABLES"
echo

# Test with format specification
echo "Testing with JSON format..."
curl -s "http://localhost:8123/?user=default&password=clickhouse&database=uk&output_format_json_quote_64bit_integers=0&enable_http_compression=1&log_queries=1" -d "SELECT * FROM uk_price_paid LIMIT 1 FORMAT JSON"
echo

# Test connection with lower timeouts
echo "Testing with reduced timeouts..."
curl -s --max-time 10 "http://localhost:8123/?user=default&password=clickhouse&database=uk&connect_timeout=5&receive_timeout=5&send_timeout=5" -d "SELECT 1"
echo

echo "All tests completed!"
