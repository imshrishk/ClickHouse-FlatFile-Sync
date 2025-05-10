package org.example.bidirectional.config;

import java.util.List;
import org.example.bidirectional.model.JoinTable;

/**
 * Configuration for querying data with selected columns from a ClickHouse table
 */
public class SelectedColumnsQueryConfig {
    private ConnectionConfig connection;
    private String tableName;
    private List<String> columns;
    private String delimiter;
    private Integer limit;
    private List<JoinTable> joinTables;

    // Getters and Setters
    public ConnectionConfig getConnection() {
        return connection;
    }

    public void setConnection(ConnectionConfig connection) {
        this.connection = connection;
    }

    public String getTableName() {
        return tableName;
    }

    public void setTableName(String tableName) {
        this.tableName = tableName;
    }

    public List<String> getColumns() {
        return columns;
    }

    public void setColumns(List<String> columns) {
        this.columns = columns;
    }

    public String getDelimiter() {
        return delimiter;
    }

    public void setDelimiter(String delimiter) {
        this.delimiter = delimiter;
    }

    public Integer getLimit() {
        return limit;
    }

    public void setLimit(Integer limit) {
        this.limit = limit;
    }

    public List<JoinTable> getJoinTables() {
        return joinTables;
    }

    public void setJoinTables(List<JoinTable> joinTables) {
        this.joinTables = joinTables;
    }
}
