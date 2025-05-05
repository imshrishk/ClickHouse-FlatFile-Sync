# Connecting to the ClickHouse Database

## Connection Settings

Use the following settings to connect to your ClickHouse database:

- **Protocol**: HTTP
- **Host**: localhost
- **Port**: 8123
- **Username**: default
- **Password**: clickhouse
- **Database**: uk

## Testing the Connection

You can test the connection with the following SQL query:

```sql
SELECT * FROM uk.uk_price_paid LIMIT 10;
```

## Table Structure

The `uk_price_paid` table structure is:

```sql
CREATE TABLE uk.uk_price_paid
(
    transaction_id String,
    price UInt32,
    date String,
    postcode String,
    type String,
    is_new String,
    duration String,
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
ORDER BY (postcode, addr1, addr2);
```

## Common Queries

### Count the number of records

```sql
SELECT COUNT(*) FROM uk.uk_price_paid;
```

### Get average house price by town

```sql
SELECT 
    town, 
    AVG(price) AS avg_price 
FROM uk.uk_price_paid 
GROUP BY town 
ORDER BY avg_price DESC 
LIMIT 10;
```

### Properties sold by year

```sql
SELECT 
    toYear(parseDateTimeBestEffort(date)) AS year, 
    COUNT(*) AS property_count 
FROM uk.uk_price_paid 
GROUP BY year 
ORDER BY year;
```
