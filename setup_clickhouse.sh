#!/bin/bash

# Create the database
clickhouse-client --user default --password clickhouse -q "CREATE DATABASE IF NOT EXISTS uk;"

# Create the table
clickhouse-client --user default --password clickhouse -q "
CREATE TABLE IF NOT EXISTS uk.uk_price_paid
(
    transaction_id String,
    price UInt32,
    date DateTime,
    postcode String,
    type Enum8('D' = 1, 'S' = 2, 'T' = 3, 'F' = 4, 'O' = 0),
    is_new Enum8('Y' = 1, 'N' = 0),
    duration Enum8('F' = 1, 'L' = 2, 'U' = 0),
    addr1 String,
    addr2 String,
    street String,
    locality String,
    town String,
    district String,
    county String,
    ppd_category_type String,
    record_status String
)
ENGINE = MergeTree
ORDER BY (postcode, addr1, addr2);"

# Verify the table was created
clickhouse-client --user default --password clickhouse -q "SHOW TABLES FROM uk;"

echo "ClickHouse database and table setup complete."
