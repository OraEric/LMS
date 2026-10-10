import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

public class Reservation {
    public int reservationId;
    public int bookId;
    public int userId;
    public String userCode;
    public String borrowerName;
    public String bookTitle;
    public String reservationDate;
    public String expiryDate;
    public String status;

    public Reservation() {}

    public Reservation(int reservationId, int bookId, int userId, String userCode, String borrowerName, String bookTitle, String reservationDate, String expiryDate, String status) {
        this.reservationId = reservationId;
        this.bookId = bookId;
        this.userId = userId;
        this.userCode = userCode;
        this.borrowerName = borrowerName;
        this.bookTitle = bookTitle;
        this.reservationDate = reservationDate;
        this.expiryDate = expiryDate;
        this.status = status;
    }

    public static List<Reservation> getReservations(int userId, boolean isStaff) {
        List<Reservation> list = new ArrayList<>();

        String sql =
            "SELECT r.reservation_id, r.book_id, r.user_id, " +
            "u.user_code, u.first_name, u.last_name, " +
            "b.title AS book_title, r.reservation_date, " +
            "r.expiry_date, r.status " +
            "FROM reservations r " +
            "JOIN users u ON r.user_id = u.user_id " +
            "JOIN books b ON r.book_id = b.book_id ";

        if (!isStaff) {
            sql += "WHERE r.user_id = ? ";
        } else {
            sql += "WHERE r.status = 'Pending' ";
        }

        sql += "ORDER BY r.reservation_id DESC";

        try (Connection conn = Database.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            if (!isStaff) {
                stmt.setInt(1, userId);
            }

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    list.add(new Reservation(
                        rs.getInt("reservation_id"),
                        rs.getInt("book_id"),
                        rs.getInt("user_id"),
                        rs.getString("user_code"),
                        rs.getString("first_name") + " " + rs.getString("last_name"),
                        rs.getString("book_title"),
                        rs.getString("reservation_date"),
                        rs.getString("expiry_date"),
                        rs.getString("status")
                    ));
                }
            }
        } catch (Exception e) {
            System.err.println("Error reading reservations: " + e.getMessage());
        }

        return list;
    }

    public static boolean createReservation(int userId, int bookId) {
        if (userId <= 0 || bookId <= 0) {
            return false;
        }

        try (Connection conn = Database.getConnection()) {
            conn.setAutoCommit(false);

            String bookSql = "SELECT available_copies FROM books WHERE book_id = ?";

            try (PreparedStatement stmt = conn.prepareStatement(bookSql)) {
                stmt.setInt(1, bookId);

                try (ResultSet rs = stmt.executeQuery()) {
                    if (!rs.next()) {
                        conn.rollback();
                        return false;
                    }

                    if (rs.getInt("available_copies") > 0) {
                        conn.rollback();
                        return false;
                    }
                }
            }

            String existingSql =
                "SELECT reservation_id FROM reservations " +
                "WHERE book_id = ? AND user_id = ? " +
                "AND status = 'Pending'";

            try (PreparedStatement stmt = conn.prepareStatement(existingSql)) {
                stmt.setInt(1, bookId);
                stmt.setInt(2, userId);

                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        conn.rollback();
                        return false;
                    }
                }
            }

            Calendar cal = Calendar.getInstance();
            cal.add(Calendar.DAY_OF_MONTH, 7);

            SimpleDateFormat sdf =
                new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

            String expiryDate = sdf.format(cal.getTime());

            String insertSql =
                "INSERT INTO reservations " +
                "(book_id, user_id, reservation_date, expiry_date, status) " +
                "VALUES (?, ?, NOW(), ?, 'Pending')";

            try (PreparedStatement stmt = conn.prepareStatement(insertSql)) {
                stmt.setInt(1, bookId);
                stmt.setInt(2, userId);
                stmt.setString(3, expiryDate);
                stmt.executeUpdate();
            }

            conn.commit();
            return true;

        } catch (Exception e) {
            System.err.println("Error creating reservation: " + e.getMessage());
            return false;
        }
    }

    public static boolean cancelReservation(int reservationId, int userId, boolean isStaff) {
        if (reservationId <= 0) {
            return false;
        }

        String sql;

        if (isStaff) {
            sql =
                "UPDATE reservations SET status = 'Cancelled' " +
                "WHERE reservation_id = ? AND status = 'Pending'";
        } else {
            sql =
                "UPDATE reservations SET status = 'Cancelled' " +
                "WHERE reservation_id = ? AND user_id = ? AND status = 'Pending'";
        }

        try (Connection conn = Database.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, reservationId);

            if (!isStaff) {
                stmt.setInt(2, userId);
            }

            return stmt.executeUpdate() > 0;

        } catch (Exception e) {
            System.err.println("Error cancelling reservation: " + e.getMessage());
            return false;
        }
    }

    public static boolean fulfillReservation(int reservationId) {
        if (reservationId <= 0) {
            return false;
        }

        String sql =
            "UPDATE reservations SET status = 'Fulfilled' " +
            "WHERE reservation_id = ? AND status = 'Pending'";

        try (Connection conn = Database.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, reservationId);

            return stmt.executeUpdate() > 0;

        } catch (Exception e) {
            System.err.println("Error fulfilling reservation: " + e.getMessage());
            return false;
        }
    }
}