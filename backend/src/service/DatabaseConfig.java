package service;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * Centralized Database Configuration Loader.
 * Reads settings from:
 * 1. Java System Properties (e.g. -Ddb.host=...)
 * 2. Environment Variables (e.g. DB_HOST, DB_NAME, DB_USERNAME, DB_PASSWORD)
 * 3. .env or application.properties files
 * 4. Safe production defaults
 */
public class DatabaseConfig {

    private static final String DEFAULT_HOST = "localhost";
    private static final String DEFAULT_PORT = "3306";
    private static final String DEFAULT_NAME = "makkal_nagar_grievance";
    private static final String DEFAULT_USER = "root";
    private static final String DEFAULT_PASS = "";

    private static Map<String, String> fileProperties = new HashMap<>();

    static {
        loadConfigFile();
    }

    private static void loadConfigFile() {
        // 1. Try application.properties
        File appProps = findFile("application.properties");
        if (appProps != null && appProps.exists()) {
            try (InputStream is = new FileInputStream(appProps)) {
                Properties props = new Properties();
                props.load(is);
                for (String key : props.stringPropertyNames()) {
                    fileProperties.put(key.trim().toLowerCase(), props.getProperty(key).trim());
                }
            } catch (Exception ignored) {}
        }

        // 2. Try .env
        File envFile = findFile(".env");
        if (envFile != null && envFile.exists()) {
            try {
                List<String> lines = Files.readAllLines(envFile.toPath());
                for (String line : lines) {
                    line = line.trim();
                    if (line.isEmpty() || line.startsWith("#")) continue;
                    int eq = line.indexOf('=');
                    if (eq != -1) {
                        String key = line.substring(0, eq).trim().toLowerCase();
                        String val = line.substring(eq + 1).trim();
                        if (val.startsWith("\"") && val.endsWith("\"") && val.length() >= 2) {
                            val = val.substring(1, val.length() - 1);
                        } else if (val.startsWith("'") && val.endsWith("'") && val.length() >= 2) {
                            val = val.substring(1, val.length() - 1);
                        }
                        fileProperties.put(key, val);
                    }
                }
            } catch (Exception ignored) {}
        }
    }

    private static File findFile(String name) {
        File f1 = new File(name);
        if (f1.exists()) return f1;
        File f2 = new File("backend/" + name);
        if (f2.exists()) return f2;
        File f3 = new File("../" + name);
        if (f3.exists()) return f3;
        return null;
    }

    private static String getVal(String sysProp, String envVar, String fileKey, String fallback) {
        String val = System.getProperty(sysProp);
        if (val != null && !val.trim().isEmpty()) return val.trim();

        val = System.getenv(envVar);
        if (val != null && !val.trim().isEmpty()) return val.trim();

        val = fileProperties.get(fileKey.toLowerCase());
        if (val != null && !val.trim().isEmpty()) return val.trim();

        return fallback;
    }

    public static String getHost() {
        return getVal("db.host", "DB_HOST", "db_host", DEFAULT_HOST);
    }

    public static int getPort() {
        String portStr = getVal("db.port", "DB_PORT", "db_port", DEFAULT_PORT);
        try {
            return Integer.parseInt(portStr);
        } catch (NumberFormatException e) {
            return 3306;
        }
    }

    public static String getDatabaseName() {
        return getVal("db.name", "DB_NAME", "db_name", DEFAULT_NAME);
    }

    public static String getUsername() {
        return getVal("db.username", "DB_USERNAME", "db_username", 
               getVal("db.user", "DB_USER", "db_user", DEFAULT_USER));
    }

    public static String getPassword() {
        return getVal("db.password", "DB_PASSWORD", "db_password",
               getVal("db.pass", "DB_PASS", "db_pass", DEFAULT_PASS));
    }

    public static String getJdbcUrl() {
        String explicitUrl = getVal("db.url", "DB_URL", "db_url", null);
        if (explicitUrl != null && !explicitUrl.trim().isEmpty()) {
            return explicitUrl.trim();
        }
        return String.format(
                "jdbc:mysql://%s:%d/%s?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC&characterEncoding=UTF-8",
                getHost(), getPort(), getDatabaseName()
        );
    }

    public static String getSafeConfigSummary() {
        return String.format("Host: %s | Port: %d | Database: %s | User: %s | Password: [PROTECTED]",
                getHost(), getPort(), getDatabaseName(), getUsername());
    }
}
