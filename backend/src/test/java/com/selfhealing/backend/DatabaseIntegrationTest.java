package com.selfhealing.backend;

import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DatabaseIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private DataSource dataSource;

    @Test
    void testDataSourceIsHikariAndConnected() throws Exception {
        assertTrue(dataSource instanceof HikariDataSource, "DataSource should be HikariCP");
        
        try (Connection connection = dataSource.getConnection()) {
            assertTrue(connection.isValid(2), "Connection should be valid");
        }
    }

    @Test
    void testFlywayMigrationsApplied() throws Exception {
        // If Flyway applied successfully, the 'orders' table should exist
        try (Connection connection = dataSource.getConnection()) {
            ResultSet rs = connection.getMetaData().getTables(null, null, "orders", null);
            assertTrue(rs.next(), "Table 'orders' should exist after Flyway migrations");
        }
    }

    @Test
    void testFlywayAdminUserSeeded() throws Exception {
        // Check if DataSeeder (or Flyway) seeded the admin user
        try (Connection connection = dataSource.getConnection()) {
            ResultSet rs = connection.createStatement().executeQuery("SELECT COUNT(*) FROM users WHERE username = 'admin'");
            assertTrue(rs.next());
            assertEquals(1, rs.getInt(1), "Admin user should exist in the database");
        }
    }
}
