import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Main {

    private static final int PORT = 8080;

    public static void main(String[] args) throws Exception {

        System.out.println("");
        System.out.println("   LIBRARY MANAGEMENT SYSTEM ");
        System.out.println("");

        // Test database connection
        try {
            Database.getConnection();

            if (Database.isMySqlActive()) {
                System.out.println("[Database Status] Connected to MySQL Server!");
            } else {
                System.out.println("[Database Status] Using SQLite Fallback Mode.");
            }

        } catch (Exception e) {
            System.out.println("[Database Status] Connection Notice: " + e.getMessage());
        }

        HttpServer server = HttpServer.create(
            new InetSocketAddress(PORT),
            0
        );

        server.createContext("/api", new ApiHandler());
        server.createContext("/", new FileHandler());
        
        server.start();

        System.out.println(" Library Server Started Successfully!");
        System.out.println(
            "    Open Web Page in Browser: http://localhost:"
            + PORT
            + "/login.html"
        );
        System.out.println(
            "----------------------------------------------------------------------------\n"
        );
    }
    // STATIC WEB FILE HANDLER

    static class FileHandler implements HttpHandler {

        @Override
        public void handle(HttpExchange exchange) throws IOException {

            String path = exchange.getRequestURI().getPath();

            if (path.equals("/") || path.isEmpty()) {
                path = "/login.html";
            }

            File file = new File("." + path);

            if (!file.exists() || file.isDirectory()) {
                file = new File("./login.html");
            }

            String mime = "text/html";

            if (path.endsWith(".css")) {
                mime = "text/css";
            }

            if (path.endsWith(".js")) {
                mime = "application/javascript";
            }

            byte[] bytes;

            if (file.exists()) {
                bytes = readAllBytes(file);
            } else {
                bytes = "<h1>404 Not Found</h1>"
                    .getBytes(StandardCharsets.UTF_8);
            }

            exchange.getResponseHeaders().set(
                "Content-Type",
                mime + "; charset=UTF-8"
            );

            exchange.sendResponseHeaders(
                file.exists() ? 200 : 404,
                bytes.length
            );

            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        }


        private byte[] readAllBytes(File file) throws IOException {

            try (FileInputStream fis = new FileInputStream(file)) {

                byte[] data = new byte[(int) file.length()];

                fis.read(data);

                return data;
            }
        }
    }


    // REST API REQUEST HANDLER

    static class ApiHandler implements HttpHandler {

        @Override
        public void handle(HttpExchange exchange) throws IOException {

            exchange.getResponseHeaders().set(
                "Access-Control-Allow-Origin",
                "*"
            );

            exchange.getResponseHeaders().set(
                "Access-Control-Allow-Methods",
                "GET, POST, OPTIONS"
            );

            exchange.getResponseHeaders().set(
                "Access-Control-Allow-Headers",
                "Content-Type"
            );


            // Handle browser pre-flight request
            if ("OPTIONS".equalsIgnoreCase(
                exchange.getRequestMethod()
            )) {

                exchange.sendResponseHeaders(204, -1);

                return;
            }

            String path =
                exchange.getRequestURI().getPath();

            String method =
                exchange.getRequestMethod();

            try {

                // LOGIN
                if (
                    "/api/login".equals(path)
                    && "POST".equalsIgnoreCase(method)
                ) {

                    handleLogin(exchange);
                }

                // REGISTER
                else if (
                    "/api/register".equals(path)
                    && "POST".equalsIgnoreCase(method)
                ) {

                    handleRegister(exchange);
                }

                // GET BOOKS
                else if (
                    "/api/books".equals(path)
                    && "GET".equalsIgnoreCase(method)
                ) {

                    handleGetBooks(exchange);
                }

                // ADD BOOK
                else if (
                    "/api/books/add".equals(path)
                    && "POST".equalsIgnoreCase(method)
                ) {

                    handleAddBook(exchange);
                }


                // DELETE BOOK
                else if (
                    "/api/books/delete".equals(path)
                    && "POST".equalsIgnoreCase(method)
                ) {

                    handleDeleteBook(exchange);
                }


                // GET ACTIVE LOANS
                // POST is used so the frontend can send the user's role
                else if (
                    "/api/loans".equals(path)
                    && "POST".equalsIgnoreCase(method)
                ) {

                    handleGetLoans(exchange);
                }


                // ISSUE LOAN
                else if (
                    "/api/loans/issue".equals(path)
                    && "POST".equalsIgnoreCase(method)
                ) {

                    handleIssueLoan(exchange);
                }

                // GET FINES - STAFF ONLY
                else if (
                    "/api/fines".equals(path)
                    && "GET".equalsIgnoreCase(method)
                ) {

                    handleGetFines(exchange);
                }
                // RETURN LOAN
                else if (
                    "/api/loans/return".equals(path)
                    && "POST".equalsIgnoreCase(method)
                ) {

                    handleReturnLoan(exchange);
                }
                else if (
                    "/api/reservations".equals(path)
                    && "POST".equalsIgnoreCase(method)
                ) {
                    handleGetReservations(exchange);
                }
                else if (
                    "/api/reservations/create".equals(path)
                    && "POST".equalsIgnoreCase(method)
                ) {
                    handleCreateReservation(exchange);
                }
                else if (
                    "/api/reservations/cancel".equals(path)
                    && "POST".equalsIgnoreCase(method)
                ) {
                    handleCancelReservation(exchange);
                }
                else if (
                    "/api/reservations/fulfill".equals(path)
                    && "POST".equalsIgnoreCase(method)
                ) {
                    handleFulfillReservation(exchange);
                }

                // UNKNOWN REQUEST
                else {

                    sendJson(
                        exchange,
                        404,
                        "{\"error\":\"Not found\"}"
                    );
                }

            } catch (Exception e) {

                sendJson(
                    exchange,
                    500,
                    "{\"error\":\""
                    + escape(e.getMessage())
                    + "\"}"
                );
            }
        }

        // LOGIN

        private void handleLogin(HttpExchange ex)
            throws IOException {

            Map<String, String> body =
                parseJson(readBody(ex));

            String userCode =
                body.get("userCode");
            String password =
                body.get("password");

            if (
                userCode == null
                || password == null
            ) {

                sendJson(
                    ex,
                    400,
                    "{\"success\":false,\"message\":\"Username/User Code and Password are required.\"}"
                );

                return;
            }


            User u =
                User.login(
                    userCode,
                    password
                );


            if (u != null) {

                String json =
                    String.format(
                        "{\"success\":true,\"user\":{\"userId\":%d,\"userCode\":\"%s\",\"name\":\"%s %s\",\"email\":\"%s\",\"role\":\"%s\"}}",
                        u.userId,
                        escape(u.userCode),
                        escape(u.firstName),
                        escape(u.lastName),
                        escape(u.email),
                        escape(u.roleName)
                    );

                sendJson(
                    ex,
                    200,
                    json
                );

            } else {

                sendJson(
                    ex,
                    401,
                    "{\"success\":false,\"message\":\"Invalid credentials. Wrong username or password.\"}"
                );
            }
        }

        // REGISTER

        private void handleRegister(HttpExchange ex)
            throws IOException {

            Map<String, String> body =
                parseJson(readBody(ex));


            String userCode =
                body.get("userCode");

            String firstName =
                body.get("firstName");

            String lastName =
                body.get("lastName");

            String email =
                body.get("email");

            String password =
                body.get("password");

            String roleName =
                body.getOrDefault(
                    "role",
                    "Student"
                );


            if (
                userCode == null
                || userCode.trim().isEmpty()
                || password == null
                || password.trim().isEmpty()
            ) {

                sendJson(
                    ex,
                    400,
                    "{\"success\":false,\"message\":\"User ID and Password are required for registration.\"}"
                );

                return;
            }


            boolean ok =
                User.registerUser(
                    userCode,
                    firstName,
                    lastName,
                    email,
                    roleName,
                    password
                );


            if (ok) {

                sendJson(
                    ex,
                    200,
                    "{\"success\":true,\"message\":\"Account registered successfully in database! You can now log in.\"}"
                );

            } else {

                sendJson(
                    ex,
                    400,
                    "{\"success\":false,\"message\":\"Registration failed. User ID or Student Number may already exist in database.\"}"
                );
            }
        }


        // GET BOOKS
        private void handleGetBooks(HttpExchange ex)
            throws IOException {

            List<Book> books =
                Book.getAllBooks(null);

            StringBuilder sb =
                new StringBuilder("[");

            for (
                int i = 0;
                i < books.size();
                i++
            ) {

                Book b =
                    books.get(i);

                sb.append(
                    String.format(
                        "{\"bookId\":%d,\"title\":\"%s\",\"isbn\":\"%s\",\"category\":\"%s\",\"publisher\":\"%s\",\"year\":%d,\"totalCopies\":%d,\"availableCopies\":%d}",
                        b.bookId,
                        escape(b.title),
                        escape(b.isbn),
                        escape(b.categoryName),
                        escape(b.publisherName),
                        b.publicationYear,
                        b.totalCopies,
                        b.availableCopies
                    )
                );

                if (
                    i < books.size() - 1
                ) {

                    sb.append(",");
                }
            }

            sb.append("]");

            sendJson(
                ex,
                200,
                sb.toString()
            );
        }

        // ADD BOOK - STAFF ONLY

        private void handleAddBook(HttpExchange ex) throws IOException {

            Map<String, String> body = parseJson(readBody(ex));

            String role = body.getOrDefault("userRole", "Student");

            if (!("Staff".equalsIgnoreCase(role)
                    || "Librarian".equalsIgnoreCase(role)
                    || "Administrator".equalsIgnoreCase(role))) {

                sendJson(ex, 403,
                    "{\"success\":false,\"message\":\"Access Denied: Only Staff members can add books.\"}");
                return;
            }

            String title = body.getOrDefault("title", "").trim();
            String isbn = body.getOrDefault("isbn", "").trim();
            String publisherName = body.getOrDefault("publisher", "").trim();

            int year = parseInt(body.get("year"), 2026);
            int copies = parseInt(body.get("copies"), 1);

            if (title.isEmpty() || isbn.isEmpty() || publisherName.isEmpty()) {
                sendJson(ex, 400,
                    "{\"success\":false,\"message\":\"Title, ISBN and Publisher are required.\"}");
                return;
            }

            try (java.sql.Connection conn = Database.getConnection()) {

                String publisherSql =
                    "SELECT publisher_id FROM publishers WHERE name = ?";

                int publisherId = -1;

                try (java.sql.PreparedStatement stmt =
                        conn.prepareStatement(publisherSql)) {

                    stmt.setString(1, publisherName);

                    try (java.sql.ResultSet rs = stmt.executeQuery()) {

                        if (rs.next()) {
                            publisherId = rs.getInt("publisher_id");
                        }
                    }
                }

                if (publisherId == -1) {

                    sendJson(ex, 400,
                        "{\"success\":false,\"message\":\"Publisher not found. Please enter an existing publisher name.\"}");

                    return;
                }

                boolean ok = Book.addBook(
                    title,
                    isbn,
                    1,
                    publisherName,
                    year,
                    copies
                );

                if (ok) {

                    sendJson(ex, 200,
                        "{\"success\":true,\"message\":\"Book added successfully.\"}");

                } else {

                    sendJson(ex, 400,
                        "{\"success\":false,\"message\":\"Could not add book to database.\"}");
                }

            } catch (Exception e) {

                sendJson(ex, 500,
                    "{\"success\":false,\"message\":\"Database error: "
                    + escape(e.getMessage()) + "\"}");
            }
        }

        // DELETE BOOK - STAFF ONLY

        private void handleDeleteBook(HttpExchange ex)
            throws IOException {

            Map<String, String> body =
                parseJson(readBody(ex));


            String role =
                body.getOrDefault(
                    "userRole",
                    "Student"
                );


            boolean isStaff =
                isStaffRole(role);


            if (!isStaff) {

                sendJson(
                    ex,
                    403,
                    "{\"success\":false,\"message\":\"Access Denied: Students are not permitted to remove books from the library database.\"}"
                );

                return;
            }


            int bookId =
                parseInt(
                    body.get("bookId"),
                    0
                );


            boolean ok =
                Book.deleteBook(bookId);


            sendJson(
                ex,
                ok ? 200 : 400,
                "{\"success\":" + ok + "}"
            );
        }
        // GET ACTIVE LOANS - STAFF ONLY

        private void handleGetLoans(HttpExchange ex) throws IOException {
            Map<String, String> body = parseJson(readBody(ex));
            String role = body.getOrDefault("userRole", "Student");
            boolean isStaff = isStaffRole(role);
            int userId = parseInt(body.get("userId"), 0);

            if (isStaff) {
                List<Loan> loans = Loan.getLoans(0, true);
                sendLoansJson(ex, loans);
                return;
            }

            if (userId <= 0) {
                sendJson(
                    ex,
                    400,
                    "{\"success\":false,\"message\":\"Valid user ID is required.\"}"
                );
                return;
            }

            List<Loan> loans = Loan.getLoans(userId, false);
            sendLoansJson(ex, loans);
        }

        private void sendLoansJson(HttpExchange ex, List<Loan> loans) throws IOException {
            StringBuilder sb = new StringBuilder("[");

            for (int i = 0; i < loans.size(); i++) {
                Loan l = loans.get(i);

                sb.append(
                    String.format(
                        "{\"loanId\":%d,\"userId\":%d,\"userCode\":\"%s\",\"borrower\":\"%s\",\"bookTitle\":\"%s\",\"issueDate\":\"%s\",\"dueDate\":\"%s\",\"returnDate\":\"%s\",\"status\":\"%s\"}",
                        l.loanId,
                        l.userId,
                        escape(l.userCode),
                        escape(l.borrowerName),
                        escape(l.bookTitle),
                        escape(l.issueDate),
                        escape(l.dueDate),
                        escape(l.returnDate),
                        escape(l.status)
                    )
                );

                if (i < loans.size() - 1) {
                    sb.append(",");
                }
            }

            sb.append("]");

            sendJson(ex, 200, sb.toString());
        }
        // ISSUE BOOK
        private void handleGetFines(HttpExchange ex)
            throws IOException {

            try {

                // First update overdue loans and create fines
                generateOverdueFines();

                List<Fine> fines =
                    Fine.getAllFines();

                StringBuilder sb =
                    new StringBuilder("[");

                for (int i = 0; i < fines.size(); i++) {

                    Fine f = fines.get(i);

                    sb.append(
                        String.format(
                            "{\"fineId\":%d," +
                            "\"loanId\":%d," +
                            "\"userId\":%d," +
                            "\"userCode\":\"%s\"," +
                            "\"borrower\":\"%s\"," +
                            "\"bookTitle\":\"%s\"," +
                            "\"dueDate\":\"%s\"," +
                            "\"daysOverdue\":%d," +
                            "\"fineAmount\":%.2f," +
                            "\"status\":\"%s\"," +
                            "\"notes\":\"%s\"}",
                            f.fineId,
                            f.loanId,
                            f.userId,
                            escape(f.userCode),
                            escape(f.borrowerName),
                            escape(f.bookTitle),
                            escape(f.dueDate),
                            f.daysOverdue,
                            f.fineAmount,
                            escape(f.status),
                            escape(f.notes)
                        )
                    );

                    if (i < fines.size() - 1) {
                        sb.append(",");
                    }
                }

                sb.append("]");

                sendJson(
                    ex,
                    200,
                    sb.toString()
                );

            } catch (Exception e) {

                sendJson(
                    ex,
                    500,
                    "{\"success\":false,\"message\":\"Error loading fines: "
                    + escape(e.getMessage())
                    + "\"}"
                );
            }
        }

        private void handleGetReservations(HttpExchange ex) throws IOException {
            Map<String, String> body = parseJson(readBody(ex));

            String role = body.getOrDefault("userRole", "Student");
            boolean isStaff = isStaffRole(role);

            int userId = parseInt(body.get("userId"), 0);

            if (!isStaff && userId <= 0) {
                sendJson(
                    ex,
                    400,
                    "{\"success\":false,\"message\":\"Valid user ID is required.\"}"
                );
                return;
            }

            List<Reservation> reservations =
                Reservation.getReservations(userId, isStaff);

            StringBuilder sb = new StringBuilder("[");

            for (int i = 0; i < reservations.size(); i++) {
                Reservation r = reservations.get(i);

                sb.append(
                    String.format(
                        "{\"reservationId\":%d,\"bookId\":%d,\"userId\":%d,\"userCode\":\"%s\",\"borrower\":\"%s\",\"bookTitle\":\"%s\",\"reservationDate\":\"%s\",\"expiryDate\":\"%s\",\"status\":\"%s\"}",
                        r.reservationId,
                        r.bookId,
                        r.userId,
                        escape(r.userCode),
                        escape(r.borrowerName),
                        escape(r.bookTitle),
                        escape(r.reservationDate),
                        escape(r.expiryDate),
                        escape(r.status)
                    )
                );

                if (i < reservations.size() - 1) {
                    sb.append(",");
                }
            }

            sb.append("]");

            sendJson(ex, 200, sb.toString());
        }

        private void handleCreateReservation(HttpExchange ex) throws IOException {
            Map<String, String> body = parseJson(readBody(ex));

            int userId = parseInt(body.get("userId"), 0);
            int bookId = parseInt(body.get("bookId"), 0);

            if (userId <= 0 || bookId <= 0) {
                sendJson(
                    ex,
                    400,
                    "{\"success\":false,\"message\":\"Valid user ID and book ID are required.\"}"
                );
                return;
            }

            boolean ok = Reservation.createReservation(userId, bookId);

            if (ok) {
                sendJson(
                    ex,
                    200,
                    "{\"success\":true,\"message\":\"Book reserved successfully. Your reservation is valid for 7 days.\"}"
                );
            } else {
                sendJson(
                    ex,
                    400,
                    "{\"success\":false,\"message\":\"Reservation could not be created. The book may be available or you may already have a reservation for it.\"}"
                );
            }
        }

        private void handleCancelReservation(HttpExchange ex) throws IOException {
            Map<String, String> body = parseJson(readBody(ex));

            String role = body.getOrDefault("userRole", "Student");
            boolean isStaff = isStaffRole(role);

            int reservationId =
                parseInt(body.get("reservationId"), 0);

            int userId =
                parseInt(body.get("userId"), 0);

            boolean ok =
                Reservation.cancelReservation(
                    reservationId,
                    userId,
                    isStaff
                );

            sendJson(
                ex,
                ok ? 200 : 400,
                "{\"success\":" + ok + "}"
            );
        }

        private void handleFulfillReservation(HttpExchange ex) throws IOException {
            Map<String, String> body = parseJson(readBody(ex));

            String role = body.getOrDefault("userRole", "Student");

            if (!isStaffRole(role)) {
                sendJson(
                    ex,
                    403,
                    "{\"success\":false,\"message\":\"Access Denied: Only staff members can fulfill reservations.\"}"
                );
                return;
            }

            int reservationId =
                parseInt(body.get("reservationId"), 0);

            boolean ok =
                Reservation.fulfillReservation(reservationId);

            sendJson(
                ex,
                ok ? 200 : 400,
                "{\"success\":" + ok + "}"
            );
        }

        private void handleIssueLoan(HttpExchange ex)
            throws IOException {

            Map<String, String> body =
                parseJson(readBody(ex));


            int userId =
                parseInt(
                    body.get("userId"),
                    1
                );


            int bookId =
                parseInt(
                    body.get("bookId"),
                    1
                );


            boolean ok =
                Loan.issueBook(
                    userId,
                    bookId
                );


            sendJson(
                ex,
                ok ? 200 : 400,
                "{\"success\":" + ok + "}"
            );
        }
        // RETURN BOOK - STAFF ONLY

        private void handleReturnLoan(HttpExchange ex)
            throws IOException {

            Map<String, String> body =
                parseJson(readBody(ex));


            String role =
                body.getOrDefault(
                    "userRole",
                    "Student"
                );


            boolean isStaff =
                isStaffRole(role);


            if (!isStaff) {

                sendJson(
                    ex,
                    403,
                    "{\"success\":false,\"message\":\"Access Denied: Only Staff members can process book returns.\"}"
                );

                return;
            }


            int loanId =
                parseInt(
                    body.get("loanId"),
                    0
                );


            boolean ok =
                Loan.returnBook(
                    loanId
                );


            sendJson(
                ex,
                ok ? 200 : 400,
                "{\"success\":" + ok + "}"
            );
        }
        // CHECK STAFF ROLE

        private boolean isStaffRole(String role) {

            if (role == null) {
                return false;
            }


            return
                "Staff".equalsIgnoreCase(role)
                || "Librarian".equalsIgnoreCase(role)
                || "Administrator".equalsIgnoreCase(role);
        }


        // SEND JSON RESPONSE

        private void sendJson(
            HttpExchange ex,
            int code,
            String json
        ) throws IOException {

            byte[] bytes =
                json.getBytes(
                    StandardCharsets.UTF_8
                );


            ex.getResponseHeaders().set(
                "Content-Type",
                "application/json; charset=UTF-8"
            );


            ex.sendResponseHeaders(
                code,
                bytes.length
            );


            try (
                OutputStream os =
                    ex.getResponseBody()
            ) {

                os.write(bytes);
            }
        }


        // READ REQUEST BODY

        private String readBody(
            HttpExchange ex
        ) throws IOException {

            try (
                BufferedReader br =
                    new BufferedReader(
                        new InputStreamReader(
                            ex.getRequestBody(),
                            StandardCharsets.UTF_8
                        )
                    )
            ) {

                StringBuilder sb =
                    new StringBuilder();


                String line;


                while (
                    (line = br.readLine()) != null
                ) {

                    sb.append(line);
                }


                return sb.toString();
            }
        }

        void generateOverdueFines() throws Exception {

            try (java.sql.Connection conn = Database.getConnection()) {

                // Find all borrowed books that are past their due date
                String overdueSql =
                    "SELECT loan_id, user_id, due_date " +
                    "FROM loans " +
                    "WHERE status = 'Borrowed' " +
                    "AND due_date < NOW()";

                try (
                    java.sql.PreparedStatement stmt =
                        conn.prepareStatement(overdueSql);
                    java.sql.ResultSet rs =
                        stmt.executeQuery()
                ) {

                    while (rs.next()) {

                        int loanId =
                            rs.getInt("loan_id");

                        int userId =
                            rs.getInt("user_id");

                        // Change loan status to Overdue
                        String updateLoan =
                            "UPDATE loans " +
                            "SET status = 'Overdue' " +
                            "WHERE loan_id = ? " +
                            "AND status = 'Borrowed'";

                        try (
                            java.sql.PreparedStatement updateStmt =
                                conn.prepareStatement(updateLoan)
                        ) {

                            updateStmt.setInt(1, loanId);
                            updateStmt.executeUpdate();
                        }


                        // Check whether a fine already exists
                        String checkFine =
                            "SELECT fine_id " +
                            "FROM fines " +
                            "WHERE loan_id = ?";

                        boolean fineExists = false;

                        try (
                            java.sql.PreparedStatement checkStmt =
                                conn.prepareStatement(checkFine)
                        ) {

                            checkStmt.setInt(1, loanId);

                            try (
                                java.sql.ResultSet fineRs =
                                    checkStmt.executeQuery()
                            ) {

                                if (fineRs.next()) {
                                    fineExists = true;
                                }
                            }
                        }


                        // Create fine only once
                        if (!fineExists) {

                            String insertFine =
                                "INSERT INTO fines " +
                                "(loan_id, user_id, fine_amount, status, notes) " +
                                "VALUES (?, ?, " +
                                "(DATEDIFF(CURRENT_DATE, ?) * 2.00), " +
                                "'Unpaid', " +
                                "'Automatic overdue fine - R2.00 per day')";

                            try (
                                java.sql.PreparedStatement fineStmt =
                                    conn.prepareStatement(insertFine)
                            ) {

                                fineStmt.setInt(1, loanId);
                                fineStmt.setInt(2, userId);
                                fineStmt.setString(
                                    3,
                                    rs.getString("due_date")
                                );

                                fineStmt.executeUpdate();
                            }
                        }
                    }
                }
            }
        }
        // SIMPLE JSON PARSER
        
        private Map<String, String> parseJson(String json) {

            Map<String, String> m = new HashMap<>();

            if (json == null || json.trim().isEmpty()) {
                return m;
            }

            String clean = json.trim();

            if (clean.startsWith("{")) {
                clean = clean.substring(1);
            }

            if (clean.endsWith("}")) {
                clean = clean.substring(0, clean.length() - 1);
            }

            for (String pair : clean.split(",")) {

                String[] kv = pair.split(":", 2);

                if (kv.length == 2) {

                    String key = kv[0].trim();

                    String val = kv[1].trim();

                    if (key.startsWith("\"") && key.endsWith("\"")) {
                        key = key.substring(1, key.length() - 1);
                    }

                    if (val.startsWith("\"") && val.endsWith("\"")) {
                        val = val.substring(1, val.length() - 1);
                    }

                    m.put(key, val);
                }
            }

            return m;
        }

        // PARSE INTEGER

        private int parseInt(
            String val,
            int def
        ) {

            try {

                return Integer.parseInt(
                    val.trim()
                );

            } catch (Exception e) {

                return def;
            }
        }
        // ESCAPE JSON VALUES

        private String escape(
            String str
        ) {

            if (str == null) {
                return "";
            }


            return str
                .replace(
                    "\"",
                    "\\\""
                )
                .replace(
                    "\n",
                    " "
                );
        }
    }
}