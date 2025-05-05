# ClickHouse Connection Test Script

Write-Host "Testing ClickHouse Connection..." -ForegroundColor Green

# Connection parameters
$clickhouseHost = "localhost"
$port = 8123
$user = "default"
$password = "clickhouse"
$database = "uk"

# Define URL
$url = "http://${clickhouseHost}:${port}/?user=${user}&password=${password}&database=${database}"

Write-Host "Connection URL: $url" -ForegroundColor Cyan
Write-Host "Running simple query test..." -ForegroundColor Yellow

try {
    $response = Invoke-WebRequest -Uri $url -Method POST -Body "SELECT 1" -UseBasicParsing
    Write-Host "Basic connection test successful! Response: $($response.Content)" -ForegroundColor Green
    
    # Try querying the UK database
    Write-Host "Testing query on UK database..." -ForegroundColor Yellow
    $dbResponse = Invoke-WebRequest -Uri $url -Method POST -Body "SELECT COUNT(*) FROM uk_price_paid" -UseBasicParsing
    Write-Host "Database query successful! Record count: $($dbResponse.Content)" -ForegroundColor Green
    
    # Try querying with JSON output
    Write-Host "Testing JSON output format..." -ForegroundColor Yellow
    $jsonResponse = Invoke-WebRequest -Uri $url -Method POST -Body "SELECT * FROM uk_price_paid LIMIT 1 FORMAT JSON" -UseBasicParsing
    # Display a preview of the JSON response content
    $jsonPreview = $jsonResponse.Content.Substring(0, [Math]::Min(100, $jsonResponse.Content.Length)) + "..."
    Write-Host "JSON format query successful! Preview: $jsonPreview" -ForegroundColor Green
    
    Write-Host "All connection tests PASSED!" -ForegroundColor Green
}
catch {
    Write-Host "Connection failed with error:" -ForegroundColor Red
    Write-Host $_.Exception.Message -ForegroundColor Red
    Write-Host "Status code: $($_.Exception.Response.StatusCode)" -ForegroundColor Red
    
    # Provide troubleshooting suggestions
    Write-Host "`nTroubleshooting suggestions:" -ForegroundColor Yellow
    Write-Host "1. Verify Docker container is running: docker ps" -ForegroundColor Yellow
    Write-Host "2. Check container logs: docker logs clickhouse-flatfile-sync-clickhouse-1" -ForegroundColor Yellow
    Write-Host "3. Verify port mapping: docker port clickhouse-flatfile-sync-clickhouse-1" -ForegroundColor Yellow
    Write-Host "4. Try connecting from inside the container:" -ForegroundColor Yellow
    Write-Host "   docker exec clickhouse-flatfile-sync-clickhouse-1 clickhouse-client --user default --password clickhouse -q 'SELECT 1'" -ForegroundColor Yellow
}

Write-Host "`nTest completed." -ForegroundColor Cyan
