import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

public class Loan {
    public int loanId;
    public int userId;
    public String userCode;
    public String borrowerName;
    public String bookTitle;
    public String issueDate;
    public String dueDate;
    public String returnDate;
    public String status;

    public Loan() {}

    public Loan(int loanId, int userId, String userCode, String borrowerName, String bookTitle, String issueDate, String dueDate, String returnDate, String status) {
        this.loanId = loanId;
        this.userId = userId;
        this.userCode = userCode;
        this.borrowerName = borrowerName;
        this.bookTitle = bookTitle;
        this.issueDate = issueDate;
        this.dueDate = dueDate;
        this.returnDate = returnDate;
        this.status = status;
    }

    public static List<Loan> getLoans(int filterUserId, boolean isStaff) {
        List<Loan> list = new ArrayList<>();
        String sql = "SELECT l.loan_id, l.user_id, u.user_code, " +
                     "u.first_name, u.last_name, b.title AS book_title, " +
                     "l.issue_date, l.due_date, l.return_date, l.status " +
                     "FROM loans l " +
                     "JOIN users u ON l.user_id = u.user_id " +
                     "JOIN book_copies bc ON l.copy_id = bc.copy_id " +
                     "JOIN books b ON bc.book_id = b.book_id ";
        
        if (!isStaff && filterUserId > 0) {
            sql += "WHERE l.user_id = " + filterUserId + " ";
        }
        sql += "ORDER BY l.loan_id DESC";

        try (Connection conn = Database.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                list.add(new Loan(
                    rs.getInt("loan_id"),
                    rs.getInt("user_id"),
                    rs.getString("user_code"),
                    rs.getString("first_name") + " " + rs.getString("last_name"),
                    rs.getString("book_title"),
                    rs.getString("issue_date"),
                    rs.getString("due_date"),
                    rs.getString("return_date"),
                    rs.getString("status")
                ));
            }
        } catch (Exception e) {
            System.err.println("Error reading loans: " + e.getMessage());
        }
        return list;
    }

    // BORROW BOOK: Check status = 'Available', update book_copies and decrement available_copies
    public static boolean issueBook(int userId, int bookId) {
        if (userId <= 0 || bookId <= 0) return false;
        Connection conn = null;
        try {
            conn = Database.getConnection();
            conn.setAutoCommit(false);

            int avail = 0;
            String checkStockSql = "SELECT available_copies FROM books WHERE book_id = ?";
            try (PreparedStatement ss = conn.prepareStatement(checkStockSql)) {
                ss.setInt(1, bookId);
                ResultSet srs = ss.executeQuery();
                if (srs.next()) {
                    avail = srs.getInt("available_copies");
                } else {
                    conn.rollback();
                    return false;
                }
            }

            if (avail <= 0) {
                conn.rollback();
                return false;
            }

            int copyId = -1;
            try {
                String findCopySql = "SELECT copy_id FROM book_copies WHERE book_id = ? AND status = 'Available' LIMIT 1";
                try (PreparedStatement cs = conn.prepareStatement(findCopySql)) {
                    cs.setInt(1, bookId);
                    ResultSet crs = cs.executeQuery();
                    if (crs.next()) {
                        copyId = crs.getInt("copy_id");
                    }
                }
            } catch (Exception eCopy) {}

            if (copyId != -1) {
                try {
                    String updateCopySql = "UPDATE book_copies SET status = 'Borrowed' WHERE copy_id = ?";
                    try (PreparedStatement ucs = conn.prepareStatement(updateCopySql)) {
                        ucs.setInt(1, copyId);
                        ucs.executeUpdate();
                    }
                } catch (Exception eUp) {}
            } else {
                try {
                    String newCopySql = "INSERT INTO book_copies (book_id, barcode, status) VALUES (?, ?, 'Borrowed')";
                    try (PreparedStatement ncs = conn.prepareStatement(newCopySql, Statement.RETURN_GENERATED_KEYS)) {
                        ncs.setInt(1, bookId);
                        ncs.setString(2, "BC-BOOK-" + bookId + "-" + (System.currentTimeMillis() % 10000));
                        ncs.executeUpdate();
                        ResultSet keys = ncs.getGeneratedKeys();
                        if (keys.next()) copyId = keys.getInt(1);
                    }
                } catch (Exception eIns) {
                    copyId = 1;
                }
            }

            if (copyId <= 0) copyId = 1;

            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            Calendar cal = Calendar.getInstance();
            String issue = sdf.format(cal.getTime());
            cal.add(Calendar.DAY_OF_MONTH, 14);
            String due = sdf.format(cal.getTime());

            String sql1 = "INSERT INTO loans (copy_id, user_id, issue_date, due_date, status, issued_by) VALUES (?, ?, ?, ?, 'Borrowed', ?)";
            try (PreparedStatement stmt1 = conn.prepareStatement(sql1)) {
                stmt1.setInt(1, copyId);
                stmt1.setInt(2, userId);
                stmt1.setString(3, issue);
                stmt1.setString(4, due);
                stmt1.setInt(5, userId);
                stmt1.executeUpdate();
            } catch (Exception exInsLoan) {
                String sql2 = "INSERT INTO loans (copy_id, user_id, issue_date, due_date, status) VALUES (?, ?, ?, ?, 'Borrowed')";
                try (PreparedStatement stmt2 = conn.prepareStatement(sql2)) {
                    stmt2.setInt(1, copyId);
                    stmt2.setInt(2, userId);
                    stmt2.setString(3, issue);
                    stmt2.setString(4, due);
                    stmt2.executeUpdate();
                }
            }

            

            conn.commit();
            return true;
        } catch (Exception e) {
            System.err.println("Error issuing loan: " + e.getMessage());
            if (conn != null) {
                try { conn.rollback(); } catch (Exception ex) {}
            }
            return false;
        } finally {
            if (conn != null) {
                try { conn.setAutoCommit(true); conn.close(); } catch (Exception ex) {}
            }
        }
    }

    // RETURN BOOK: Update loans status, mark copy_id as Available, and increment available_copies
    public static boolean returnBook(int loanId) {
        Connection conn = null;
        try {
            conn = Database.getConnection();
            conn.setAutoCommit(false);

            int copyId = -1;
            String fetchLoanSql = "SELECT copy_id FROM loans WHERE loan_id = ? AND status IN ('Borrowed','Overdue')";
            try (PreparedStatement fls = conn.prepareStatement(fetchLoanSql)) {
                fls.setInt(1, loanId);
                ResultSet rs = fls.executeQuery();
                if (rs.next()) {
                    copyId = rs.getInt("copy_id");
                } else {
                    conn.rollback();
                    return false;
                }
            }

            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            String retDate = sdf.format(new Date());

            String sql = "UPDATE loans SET return_date = ?, status = 'Returned' WHERE loan_id = ? AND status IN ('Borrowed','Overdue')";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, retDate);
                stmt.setInt(2, loanId);
                int updated = stmt.executeUpdate();

                if (updated == 0) {
                    conn.rollback();
                    return false;
                }
            }

            String updateCopySql = "UPDATE book_copies SET status = 'Available' WHERE copy_id = ?";
            try (PreparedStatement ucs = conn.prepareStatement(updateCopySql)) {
                ucs.setInt(1, copyId);
                ucs.executeUpdate();
            }

            String incSql = "UPDATE books SET available_copies = available_copies + 1 WHERE book_id = (SELECT book_id FROM book_copies WHERE copy_id = ?)";
            try (PreparedStatement is = conn.prepareStatement(incSql)) {
                is.setInt(1, copyId);
                is.executeUpdate();
            }

            conn.commit();
            return true;
        } catch (Exception e) {
            System.err.println("Error returning loan: " + e.getMessage());
            if (conn != null) {
                try {
                    conn.rollback();
                } catch (Exception ex) {
                }
            }
            return false;
        } finally {
            if (conn != null) {
                try {
                    conn.setAutoCommit(true);
                    conn.close();
                } catch (Exception ex) {
                }
            }
        }
    }
}
