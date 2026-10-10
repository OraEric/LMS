import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

public class Book {
    public int bookId;
    public String title;
    public String isbn;
    public int categoryId;
    public String categoryName;
    public int publisherId;
    public String publisherName;
    public int publicationYear;
    public int totalCopies;
    public int availableCopies;

    public Book() {}

    public Book(int bookId, String title, String isbn,
                int categoryId, String categoryName,
                int publisherId, String publisherName,
                int publicationYear, int totalCopies,
                int availableCopies) {

        this.bookId = bookId;
        this.title = title;
        this.isbn = isbn;
        this.categoryId = categoryId;
        this.categoryName = categoryName;
        this.publisherId = publisherId;
        this.publisherName = publisherName;
        this.publicationYear = publicationYear;
        this.totalCopies = totalCopies;
        this.availableCopies = availableCopies;
    }

    public static List<Book> getAllBooks(String search) {
        List<Book> list = new ArrayList<>();

        String sqlSqlite =
            "SELECT b.book_id, b.title, b.isbn, " +
            "b.category_id, c.category_name, b.publisher_name, " +
            "b.publication_year, b.total_copies, b.available_copies " +
            "FROM books b " +
            "LEFT JOIN categories c ON b.category_id = c.category_id ";

        if (search != null && !search.trim().isEmpty()) {
            sqlSqlite +=
                "WHERE LOWER(b.title) LIKE ? " +
                "OR LOWER(b.isbn) LIKE ? " +
                "OR LOWER(b.publisher_name) LIKE ? ";
        }
        sqlSqlite += "ORDER BY b.book_id ASC";

        try (Connection conn = Database.getConnection()) {
            try {
                PreparedStatement stmt = conn.prepareStatement(sqlSqlite);
                if (search != null && !search.trim().isEmpty()) {
                    String q = "%" + search.trim().toLowerCase() + "%";
                    stmt.setString(1, q);
                    stmt.setString(2, q);
                    stmt.setString(3, q);
                }
                ResultSet rs = stmt.executeQuery();
                while (rs.next()) {
                    list.add(new Book(
                        rs.getInt("book_id"),
                        rs.getString("title"),
                        rs.getString("isbn"),
                        rs.getInt("category_id"),
                        rs.getString("category_name") != null ? rs.getString("category_name") : "General",
                        0,
                        rs.getString("publisher_name") != null ? rs.getString("publisher_name") : "NWU Press",
                        rs.getInt("publication_year"),
                        rs.getInt("total_copies"),
                        rs.getInt("available_copies")
                    ));
                }
                return list;
            } catch (Exception ex1) {
                String sqlMysql =
                    "SELECT b.book_id, b.title, b.isbn, " +
                    "b.category_id, c.category_name, " +
                    "b.publisher_id, p.name AS publisher_name, " +
                    "b.publication_year, b.total_copies, b.available_copies " +
                    "FROM books b " +
                    "LEFT JOIN categories c ON b.category_id = c.category_id " +
                    "LEFT JOIN publishers p ON b.publisher_id = p.publisher_id ";

                if (search != null && !search.trim().isEmpty()) {
                    sqlMysql +=
                        "WHERE LOWER(b.title) LIKE ? " +
                        "OR LOWER(b.isbn) LIKE ? " +
                        "OR LOWER(p.name) LIKE ? ";
                }
                sqlMysql += "ORDER BY b.book_id ASC";

                PreparedStatement stmt = conn.prepareStatement(sqlMysql);
                if (search != null && !search.trim().isEmpty()) {
                    String q = "%" + search.trim().toLowerCase() + "%";
                    stmt.setString(1, q);
                    stmt.setString(2, q);
                    stmt.setString(3, q);
                }
                ResultSet rs = stmt.executeQuery();
                while (rs.next()) {
                    list.add(new Book(
                        rs.getInt("book_id"),
                        rs.getString("title"),
                        rs.getString("isbn"),
                        rs.getInt("category_id"),
                        rs.getString("category_name") != null ? rs.getString("category_name") : "General",
                        rs.getInt("publisher_id"),
                        rs.getString("publisher_name") != null ? rs.getString("publisher_name") : "NWU Press",
                        rs.getInt("publication_year"),
                        rs.getInt("total_copies"),
                        rs.getInt("available_copies")
                    ));
                }
                return list;
            }
        } catch (Exception e) {
            System.err.println("Error reading books: " + e.getMessage());
        }

        return list;
    }

    public static boolean addBook(
            String title,
            String isbn,
            int categoryId,
            String publisherName,
            int year,
            int copies) {

        if (title == null || title.trim().isEmpty() || isbn == null || isbn.trim().isEmpty()) {
            return false;
        }

        String pubName = (publisherName != null && !publisherName.trim().isEmpty()) ? publisherName.trim() : "NWU Press";

        try (Connection conn = Database.getConnection()) {
            String sql1 = "INSERT INTO books (title, isbn, category_id, publisher_name, publication_year, total_copies, available_copies) VALUES (?, ?, ?, ?, ?, ?, ?)";
            try (PreparedStatement stmt = conn.prepareStatement(sql1)) {
                stmt.setString(1, title);
                stmt.setString(2, isbn);
                stmt.setInt(3, categoryId > 0 ? categoryId : 1);
                stmt.setString(4, pubName);
                stmt.setInt(5, year);
                stmt.setInt(6, copies);
                stmt.setInt(7, copies);
                return stmt.executeUpdate() > 0;
            } catch (Exception ex1) {
                int pubId = 1;
                try {
                    String checkPub = "SELECT publisher_id FROM publishers WHERE LOWER(name) = ? LIMIT 1";
                    try (PreparedStatement ps = conn.prepareStatement(checkPub)) {
                        ps.setString(1, pubName.toLowerCase());
                        ResultSet prs = ps.executeQuery();
                        if (prs.next()) {
                            pubId = prs.getInt("publisher_id");
                        } else {
                            String insPub = "INSERT INTO publishers (name) VALUES (?)";
                            try (PreparedStatement ips = conn.prepareStatement(insPub, java.sql.Statement.RETURN_GENERATED_KEYS)) {
                                ips.setString(1, pubName);
                                ips.executeUpdate();
                                ResultSet keys = ips.getGeneratedKeys();
                                if (keys.next()) pubId = keys.getInt(1);
                            }
                        }
                    }
                } catch (Exception ePub) {}

                String sql2 = "INSERT INTO books (title, isbn, category_id, publisher_id, publication_year, total_copies, available_copies) VALUES (?, ?, ?, ?, ?, ?, ?)";
                try (PreparedStatement stmt2 = conn.prepareStatement(sql2)) {
                    stmt2.setString(1, title);
                    stmt2.setString(2, isbn);
                    stmt2.setInt(3, categoryId > 0 ? categoryId : 1);
                    stmt2.setInt(4, pubId);
                    stmt2.setInt(5, year);
                    stmt2.setInt(6, copies);
                    stmt2.setInt(7, copies);
                    return stmt2.executeUpdate() > 0;
                }
            }
        } catch (Exception e) {
            System.err.println("Error adding book: " + e.getMessage());
            return false;
        }
    }

    public static boolean deleteBook(int bookId) {

        String sql = "DELETE FROM books WHERE book_id = ?";

        try (Connection conn = Database.getConnection();
            PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, bookId);

            return stmt.executeUpdate() > 0;

        } catch (Exception e) {
            System.err.println("Error deleting book: " + e.getMessage());
            return false;
        }
    }
}