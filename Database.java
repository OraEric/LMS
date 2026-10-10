import java.io.File;
import java.io.FileInputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;

public class Database {
    private static String host = "localhost";
    private static String port = "3306";
    private static String dbName = "library_db";
    private static String user = "root";
    private static String password = "";
    private static boolean isMySql = false;

    static {
        try {
            File f = new File("config.properties");
            if (f.exists()) {
                Properties p = new Properties();
                p.load(new FileInputStream(f));
                host = p.getProperty("db.host", host);
                port = p.getProperty("db.port", port);
                dbName = p.getProperty("db.name", dbName);
                user = p.getProperty("db.user", user);
                password = p.getProperty("db.password", password);
            }
        } catch (Exception e) {
            // We ignore config read error
        }
    }

    public static Connection getConnection() throws SQLException {
        String mysqlUrl = "jdbc:mysql://" + host + ":" + port + "/" + dbName + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";
        String[] passwords = { password, "", "root", "admin", "1234", "123456", "password" };

        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            for (String pass : passwords) {
                if (pass == null) continue;
                try {
                    Connection conn = DriverManager.getConnection(mysqlUrl, user, pass);
                    if (!isMySql) {
                        System.out.println("[Database] Connected to MySQL database: " + dbName);
                        isMySql = true;
                    }
                    return conn;
                } catch (SQLException ex) {
                System.err.println("[MySQL] Failed with password attempt: " + ex.getMessage());
                }
            }
        } catch (Exception e) {
            
        }

        // Fallback to SQLite if MySQL is offline
        isMySql = false;
        return getSqliteConnection();
    }

    private static Connection getSqliteConnection() throws SQLException {
        try {
            Class.forName("org.sqlite.JDBC");
            boolean exists = new File("library_db.db").exists();
            Connection conn = DriverManager.getConnection("jdbc:sqlite:library_db.db");
            if (!exists) {
                initSqlite(conn);
            }
            return conn;
        } catch (Exception e) {
            throw new SQLException("Database connection error: " + e.getMessage());
        }
    }

    private static void initSqlite(Connection conn) {
        try (Statement s = conn.createStatement()) {
            s.executeUpdate("CREATE TABLE IF NOT EXISTS roles (role_id INTEGER PRIMARY KEY, role_name TEXT);");
            s.executeUpdate("INSERT OR IGNORE INTO roles VALUES (1, 'Administrator'), (2, 'Staff'), (3, 'Student');");

            s.executeUpdate("CREATE TABLE IF NOT EXISTS users (user_id INTEGER PRIMARY KEY AUTOINCREMENT, user_code TEXT UNIQUE, first_name TEXT, last_name TEXT, email TEXT, role_id INTEGER, status TEXT DEFAULT 'Active');");
            s.executeUpdate("INSERT OR IGNORE INTO users VALUES (1, 'STU1001', 'Amahle', 'Nxumalo', 'amahle.Nxu@nwu.ac.za', 3, 'Active');");
            s.executeUpdate("INSERT OR IGNORE INTO users VALUES (2, 'EMP1001', 'Nomsa', 'Khumalo', 'nomsa.khumalo@nwu.ac.za', 2, 'Active');");

            s.executeUpdate("CREATE TABLE IF NOT EXISTS categories (category_id INTEGER PRIMARY KEY, category_name TEXT);");
            s.executeUpdate("INSERT OR IGNORE INTO categories VALUES (1, 'Computer Science'), (2, 'Software Engineering'), (3, 'Database Systems'), (4, 'Mathematics');");

            s.executeUpdate("CREATE TABLE IF NOT EXISTS books (book_id INTEGER PRIMARY KEY AUTOINCREMENT, title TEXT, isbn TEXT UNIQUE, category_id INTEGER, publisher_name TEXT, publication_year INTEGER, total_copies INTEGER, available_copies INTEGER);");
            s.executeUpdate("INSERT OR IGNORE INTO books VALUES (1, 'Software Engineering', '978-0133943030', 2, 'Pearson', 2017, 5, 4);");
            s.executeUpdate("INSERT OR IGNORE INTO books VALUES (2, 'Starting Out with Python', '978-0134444321', 1, 'Pearson', 2018, 4, 3);");
            s.executeUpdate("INSERT OR IGNORE INTO books VALUES (3, 'Database System Concepts', '978-0073523323', 3, 'McGraw-Hill', 1997, 4, 4);");

            s.executeUpdate("CREATE TABLE IF NOT EXISTS book_copies (copy_id INTEGER PRIMARY KEY AUTOINCREMENT, book_id INTEGER, barcode TEXT, status TEXT DEFAULT 'Available');");
            s.executeUpdate("INSERT OR IGNORE INTO book_copies VALUES (1, 1, 'BC-SE-001', 'Available'), (2, 2, 'BC-PY-001', 'Available');");

            s.executeUpdate("CREATE TABLE IF NOT EXISTS loans (loan_id INTEGER PRIMARY KEY AUTOINCREMENT, copy_id INTEGER, user_id INTEGER, issue_date TEXT, due_date TEXT, return_date TEXT, status TEXT DEFAULT 'Borrowed');");
            s.executeUpdate("CREATE TABLE IF NOT EXISTS fines (fine_id INTEGER PRIMARY KEY AUTOINCREMENT, loan_id INTEGER, user_id INTEGER, fine_amount REAL, status TEXT DEFAULT 'Unpaid');");
            System.out.println("[Database] Initialized SQLite database.");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static boolean isMySqlActive() {
        return isMySql;
    }
}
