import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

public class Fine {

    public int fineId;
    public int loanId;
    public int userId;
    public String userCode;
    public String borrowerName;
    public String bookTitle;
    public String dueDate;
    public int daysOverdue;
    public double fineAmount;
    public String status;
    public String notes;

    public Fine(
            int fineId,
            int loanId,
            int userId,
            String userCode,
            String borrowerName,
            String bookTitle,
            String dueDate,
            int daysOverdue,
            double fineAmount,
            String status,
            String notes) {

        this.fineId = fineId;
        this.loanId = loanId;
        this.userId = userId;
        this.userCode = userCode;
        this.borrowerName = borrowerName;
        this.bookTitle = bookTitle;
        this.dueDate = dueDate;
        this.daysOverdue = daysOverdue;
        this.fineAmount = fineAmount;
        this.status = status;
        this.notes = notes;
    }

    public static List<Fine> getAllFines() {

        List<Fine> list = new ArrayList<>();

        String sql =
            "SELECT f.fine_id, f.loan_id, f.user_id, " +
            "u.user_code, " +
            "CONCAT(u.first_name, ' ', u.last_name) AS borrower_name, " +
            "b.title AS book_title, " +
            "l.due_date, " +
            "DATEDIFF(CURRENT_DATE, l.due_date) AS days_overdue, " +
            "f.fine_amount, f.status, f.notes " +
            "FROM fines f " +
            "JOIN users u ON f.user_id = u.user_id " +
            "JOIN loans l ON f.loan_id = l.loan_id " +
            "JOIN book_copies bc ON l.copy_id = bc.copy_id " +
            "JOIN books b ON bc.book_id = b.book_id " +
            "ORDER BY f.fine_id DESC";

        try (
            Connection conn = Database.getConnection();
            PreparedStatement stmt = conn.prepareStatement(sql);
            ResultSet rs = stmt.executeQuery()
        ) {

            while (rs.next()) {

                int days = rs.getInt("days_overdue");

                if (days < 0) {
                    days = 0;
                }

                list.add(
                    new Fine(
                        rs.getInt("fine_id"),
                        rs.getInt("loan_id"),
                        rs.getInt("user_id"),
                        rs.getString("user_code"),
                        rs.getString("borrower_name"),
                        rs.getString("book_title"),
                        rs.getString("due_date"),
                        days,
                        rs.getDouble("fine_amount"),
                        rs.getString("status"),
                        rs.getString("notes")
                    )
                );
            }

        } catch (Exception e) {

            System.err.println(
                "Error reading fines: " + e.getMessage()
            );
        }

        return list;
    }
}