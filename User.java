import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

public class User {
    public int userId;
    public String userCode;
    public String firstName;
    public String lastName;
    public String email;
    public int roleId;
    public String roleName;
    public String status;

    public User() {}

    public User(int userId, String userCode, String firstName, String lastName,
                String email, int roleId, String roleName, String status) {
        this.userId = userId;
        this.userCode = userCode;
        this.firstName = firstName;
        this.lastName = lastName;
        this.email = email;
        this.roleId = roleId;
        this.roleName = roleName;
        this.status = status;
    }

    public static User login(String userCode, String password) {
        if (userCode == null || userCode.trim().isEmpty() ||
            password == null || password.trim().isEmpty()) {
            return null;
        }

        String code = userCode.trim();

        String sql = "SELECT u.user_id, u.user_code, u.first_name, u.last_name, " +
                     "u.email, u.role_id, r.role_name, u.status " +
                     "FROM users u " +
                     "LEFT JOIN roles r ON u.role_id = r.role_id " +
                     "WHERE (LOWER(u.user_code) = ? OR LOWER(u.email) = ?)";

        try (Connection conn = Database.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, code.toLowerCase());
            stmt.setString(2, code.toLowerCase());

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    String rName = rs.getString("role_name");

                    if (rName == null) {
                        int rId = rs.getInt("role_id");
                        rName = (rId == 1 || rId == 2) ? "Staff" : "Student";
                    }

                    return new User(
                        rs.getInt("user_id"),
                        rs.getString("user_code"),
                        rs.getString("first_name"),
                        rs.getString("last_name"),
                        rs.getString("email"),
                        rs.getInt("role_id"),
                        rName,
                        rs.getString("status") != null
                            ? rs.getString("status")
                            : "Active"
                    );
                }
            }

        } catch (Exception e) {
            System.err.println("Login DB error: " + e.getMessage());
        }

        return null;
    }

    public static boolean registerUser(String userCode, String firstName,
                                       String lastName, String email,
                                       String roleName, String password) {

        if (userCode == null || userCode.trim().isEmpty() ||
            password == null || password.trim().isEmpty()) {
            return false;
        }

        String checkSql =
            "SELECT user_id FROM users " +
            "WHERE LOWER(user_code) = ? OR LOWER(email) = ?";

        try (Connection conn = Database.getConnection();
             PreparedStatement checkStmt = conn.prepareStatement(checkSql)) {

            String code = userCode.trim();
            String mail = email != null ? email.trim() : "";

            checkStmt.setString(1, code.toLowerCase());
            checkStmt.setString(2, mail.toLowerCase());

            try (ResultSet rs = checkStmt.executeQuery()) {
                if (rs.next()) {
                    System.err.println("Registration failed: User ID or email already exists.");
                    return false;
                }
            }

            int roleId =
                "Staff".equalsIgnoreCase(roleName) ||
                "Librarian".equalsIgnoreCase(roleName)
                    ? 2
                    : 3;

            String sql =
                "INSERT INTO users " +
                "(user_code, first_name, last_name, email, password, role_id, status) " +
                "VALUES (?, ?, ?, ?, ?, ?, 'Active')";

            try (PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setString(1, code);
                stmt.setString(2,
                    firstName != null && !firstName.trim().isEmpty()
                        ? firstName.trim()
                        : "Student"
                );
                stmt.setString(3,
                    lastName != null && !lastName.trim().isEmpty()
                        ? lastName.trim()
                        : "User"
                );
                stmt.setString(4,
                    !mail.isEmpty()
                        ? mail
                        : code + "@student.nwu.ac.za"
                );
                stmt.setString(5, password);
                stmt.setInt(6, roleId);

                return stmt.executeUpdate() > 0;
            }

        } catch (Exception e) {
            System.err.println("Registration DB error: " + e.getMessage());
            return false;
        }
    }

    public static int getUserIdByCode(String userCode) {
        if (userCode == null || userCode.trim().isEmpty()) return 0;
        String sql = "SELECT user_id FROM users WHERE LOWER(user_code) = ? OR LOWER(email) = ? LIMIT 1";
        try (Connection conn = Database.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, userCode.trim().toLowerCase());
            stmt.setString(2, userCode.trim().toLowerCase());
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) return rs.getInt("user_id");
            }
        } catch (Exception e) {}
        return 0;
    }
}