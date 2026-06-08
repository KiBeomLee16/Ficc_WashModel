package com.portfolio.ficc.io;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

@ConfigurationProperties(prefix = "ficc.database")
public record DatabaseConfig(String url, String user, String password) {

    public DatabaseConfig {
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("ficc.database.url is required");
        }
        if (user == null || user.isBlank()) {
            throw new IllegalArgumentException("ficc.database.user is required");
        }
        url = url.trim();
        user = user.trim();
        password = password == null ? "" : password;
    }

    public Connection getConnection() throws SQLException {
        return DriverManager.getConnection(url, user, password);
    }
}
