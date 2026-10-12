package service;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * Production MySQL Database Connection Manager.
 * Provides JDBC connection lifecycle, status verification, and transactional execution.
 */
public class DatabaseConnection {

    private static boolean driverAvailable = false;

    static {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            driverAvailable = true;
        } catch (ClassNotFoundException e) {
            driverAvailable = false;
        }
    }

    public static boolean isDriverAvailable() {
        return driverAvailable;
    }

    public static Connection getConnection() throws SQLException {
        if (!driverAvailable) {
            throw new SQLException("MySQL JDBC Driver (com.mysql.cj.jdbc.Driver) is not present on the Java classpath.");
        }
        String url = DatabaseConfig.getJdbcUrl();
        String user = DatabaseConfig.getUsername();
        String pass = DatabaseConfig.getPassword();
        return DriverManager.getConnection(url, user, pass);
    }

    public static boolean testConnection() {
        if (!driverAvailable) return false;
        try (Connection conn = getConnection()) {
            return conn != null && !conn.isClosed();
        } catch (Exception e) {
            return false;
        }
    }

    public static String getStatusMessage() {
        if (!driverAvailable) {
            return "[MYSQL STATUS] Driver not loaded. Ensure mysql-connector-j JAR is on classpath (backend/lib/).";
        }
        if (testConnection()) {
            return "[MYSQL STATUS] Connected successfully to " + DatabaseConfig.getDatabaseName() + " via " + DatabaseConfig.getSafeConfigSummary();
        }
        return "[MYSQL STATUS] Driver loaded, but database server is not reachable at " + DatabaseConfig.getHost() + ":" + DatabaseConfig.getPort() +
               ". Check MySQL service status and credentials in .env/application.properties.";
    }

    public static void printStartupDiagnostics() {
        System.out.println("-------------------------------------------------------------------------------");
        System.out.println("                 MYSQL DATABASE CONFIGURATION & CONNECTIVITY");
        System.out.println("-------------------------------------------------------------------------------");
        System.out.println("JDBC Driver Available : " + (driverAvailable ? "YES (com.mysql.cj.jdbc.Driver)" : "NO (Missing JAR)"));
        System.out.println("Target Configuration   : " + DatabaseConfig.getSafeConfigSummary());
        System.out.println("JDBC URL               : " + DatabaseConfig.getJdbcUrl());
        boolean connected = testConnection();
        System.out.println("Connection Test        : " + (connected ? "CONNECTED (200 OK)" : "UNREACHABLE"));
        if (!connected) {
            System.out.println("-------------------------------------------------------------------------------");
            System.out.println("[HOW TO START MYSQL]");
            System.out.println("  1. Ensure MySQL 8.0 service is running: net start mysql (Windows) or systemctl start mysql");
            System.out.println("  2. Create database: mysql -u root -p -e 'CREATE DATABASE makkal_nagar_grievance;'");
            System.out.println("  3. Initialize tables: mysql -u root -p makkal_nagar_grievance < database/schema.sql");
            System.out.println("  4. Set credentials in .env or application.properties if non-default");
        }
        System.out.println("-------------------------------------------------------------------------------\n");
    }

    @FunctionalInterface
    public interface TransactionCallback<T> {
        T execute(Connection conn) throws Exception;
    }

    public static <T> T executeTransaction(TransactionCallback<T> callback) throws Exception {
        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);
            try {
                T result = callback.execute(conn);
                conn.commit();
                return result;
            } catch (Exception ex) {
                conn.rollback();
                throw ex;
            }
        }
    }
}
