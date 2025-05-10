package org.example.bidirectional.config;

public class ConnectionConfig {
    private String protocol;
    private String host;
    private int port;
    private String database;
    private String username;
    private String authType;
    private String jwt;  // For JWT or token
    private String password;   // For password
    private long connectTimeout = 120000; // Default 120 seconds
    private long socketTimeout = 1200000; // Default 20 minutes

    public String getProtocol() {
        return protocol;
    }

    public void setProtocol(String protocol) {
        this.protocol = protocol;
    }

    // Getter and Setter for host
    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    // Getter and Setter for port
    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    // Getter and Setter for database
    public String getDatabase() {
        return database;
    }

    public void setDatabase(String database) {
        this.database = database;
    }

    // Getter and Setter for username
    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    // Getter and Setter for authType
    public String getAuthType() {
        return authType;
    }

    public void setAuthType(String authType) {
        this.authType = authType;
    }

    // Getter and Setter for jwtToken (used as a token or JWT)
    public String getJwt() {
        return jwt;
    }

    public void setJwt(String jwtToken) {
        this.jwt = jwtToken;
    }

    // Getter and Setter for password
    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
    
    // Getter and Setter for connectTimeout
    public long getConnectTimeout() {
        return connectTimeout;
    }

    public void setConnectTimeout(long connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    // Getter and Setter for socketTimeout
    public long getSocketTimeout() {
        return socketTimeout;
    }

    public void setSocketTimeout(long socketTimeout) {
        this.socketTimeout = socketTimeout;
    }
}
