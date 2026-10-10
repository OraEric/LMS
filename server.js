/**
 * NWU Library Management System - Frontend Controller
 * Connects FrontEnd UI to Java HttpServer REST API
 */

document.addEventListener("DOMContentLoaded", function () {
    // ----------------------------------------------------
    // SESSION HELPER FUNCTIONS
    // ----------------------------------------------------
    function getUserSession() {
        try {
            const data = localStorage.getItem("nwu_library_user");
            return data ? JSON.parse(data) : null;
        } catch (e) {
            return null;
        }
    }

    function setUserSession(user) {
        localStorage.setItem("nwu_library_user", JSON.stringify(user));
    }

    function clearUserSession() {
        localStorage.removeItem("nwu_library_user");
    }

    const currentUser = getUserSession();

    // ----------------------------------------------------
    // API CALL HELPER
    // ----------------------------------------------------
    async function apiCall(endpoint, method = "GET", data = null) {
        const options = {
            method: method,
            headers: {
                "Content-Type": "application/json"
            }
        };

        if (data && method !== "GET") {
            options.body = JSON.stringify(data);
        }

        try {
            const response = await fetch(endpoint, options);
            const resData = await response.json();
            return resData;
        } catch (err) {
            console.error("API error for " + endpoint + ":", err);
            return { success: false, message: "Network error or server unavailable: " + err.message };
        }
    }

    // ----------------------------------------------------
    // PAGE 1: LOGIN / REGISTER (index.html)
    // ----------------------------------------------------
    const logregBox = document.querySelector(".logreg-box");
    const registerLink = document.querySelector(".register-link");
    const loginLink = document.querySelector(".login-link");
    const roleDropdown = document.querySelector(".dropdown");
    const loginForm = document.querySelector(".form-box.login form");
    const registerForm = document.querySelector(".form-box.register form");

    if (logregBox) {
        if (registerLink) {
            registerLink.addEventListener("click", function (e) {
                e.preventDefault();
                logregBox.classList.add("register-active");
            });
        }

        if (loginLink) {
            loginLink.addEventListener("click", function (e) {
                e.preventDefault();
                logregBox.classList.remove("register-active");
            });
        }

        if (loginForm) {
            loginForm.addEventListener("submit", async function (e) {
                e.preventDefault();

                const inputs = loginForm.querySelectorAll("input");
                const usernameInput = inputs[0] ? inputs[0].value.trim() : "";
                const passwordInput = inputs[1] ? inputs[1].value.trim() : "";

                if (!usernameInput || !passwordInput) {
                    alert("Please enter both Username/User Code and Password.");
                    return;
                }

                const res = await apiCall("/api/login", "POST", {
                    userCode: usernameInput,
                    password: passwordInput
                });

                if (res && res.success && res.user) {
                    setUserSession(res.user);
                    const userRole = (res.user.role || "").toLowerCase();

                    if (userRole.includes("staff") || userRole.includes("librarian") || userRole.includes("admin")) {
                        window.location.href = "librarian-dashboard.html";
                    } else {
                        window.location.href = "student-dashboard.html";
                    }
                } else {
                    alert(res.message || "Invalid credentials. Please try again.");
                }
            });
        }

        if (registerForm) {
            registerForm.addEventListener("submit", async function (e) {
                e.preventDefault();

                const username = document.getElementById("username") ? document.getElementById("username").value.trim() : "";
                const email = document.getElementById("email") ? document.getElementById("email").value.trim() : "";
                const passInputs = registerForm.querySelectorAll("input[type='password']");
                const password = passInputs[0] ? passInputs[0].value.trim() : "";
                const confirmPassword = passInputs[1] ? passInputs[1].value.trim() : "";
                const selectedRole = roleDropdown && roleDropdown.value ? roleDropdown.value : "student";

                if (!username || !email || !password) {
                    alert("Please fill in all required registration fields.");
                    return;
                }

                if (password !== confirmPassword) {
                    alert("Passwords do not match!");
                    return;
                }

                const roleName = (selectedRole === "librarian" || selectedRole === "staff") ? "Staff" : "Student";

                const res = await apiCall("/api/register", "POST", {
                    userCode: username,
                    firstName: username,
                    lastName: "User",
                    email: email,
                    password: password,
                    role: roleName
                });

                if (res && res.success) {
                    alert(res.message || "Registration successful! You can now sign in.");
                    logregBox.classList.remove("register-active");
                } else {
                    alert(res.message || "Registration failed. User ID or Email might already exist.");
                }
            });
        }
    }

    const logoutBtns = document.querySelectorAll(".logout, a[href='index.html'].logout");
    logoutBtns.forEach(btn => {
        btn.addEventListener("click", function (e) {
            clearUserSession();
        });
    });

    // ----------------------------------------------------
    // PAGE NAVIGATION / TAB SWITCHING
    // ----------------------------------------------------
    const navLinks = document.querySelectorAll(".sidebar-nav .nav-link");
    const pageSections = document.querySelectorAll(".page-section");

    if (navLinks.length > 0 && pageSections.length > 0) {
        function showSection(targetId) {
            pageSections.forEach(sec => {
                if ("#" + sec.id === targetId || sec.id === targetId) {
                    sec.style.display = "block";
                    sec.classList.add("active");
                } else {
                    sec.style.display = "none";
                    sec.classList.remove("active");
                }
            });

            navLinks.forEach(link => {
                if (link.getAttribute("href") === targetId) {
                    link.classList.add("active");
                } else {
                    link.classList.remove("active");
                }
            });
        }

        navLinks.forEach(link => {
            link.addEventListener("click", function (e) {
                const target = this.getAttribute("href");
                if (target && target.startsWith("#")) {
                    e.preventDefault();
                    showSection(target);
                }
            });
        });

        const initialHash = window.location.hash || "#dashboard";
        showSection(initialHash);
    }

    // ----------------------------------------------------
    // PAGE 2: STUDENT DASHBOARD (student-dashboard.html)
    // ----------------------------------------------------
    const isStudentPage = document.title.toLowerCase().includes("student dashboard");
    if (isStudentPage) {
        if (!currentUser) {
            alert("Please log in to access the Student Dashboard.");
            window.location.href = "index.html";
            return;
        }

        const profileElements = document.querySelectorAll(".profile-small h4, .student-profile strong, .topbar p");
        profileElements.forEach(el => {
            if (el.tagName === "H4" || el.tagName === "STRONG") el.textContent = currentUser.name || currentUser.userCode;
        });

        async function loadStudentBooks() {
            const books = await apiCall("/api/books");
            const activeLoans = await apiCall("/api/loans", "POST", { userRole: currentUser.role, userId: currentUser.userId, userCode: currentUser.userCode });

            const activeLoansCountEl = document.querySelector(".stat-card.blue h2");
            if (activeLoansCountEl && Array.isArray(activeLoans)) {
                activeLoansCountEl.textContent = activeLoans.length;
            }

            const booksBorrowedCountEl = document.querySelector(".stat-card.green h2");
            if (booksBorrowedCountEl && Array.isArray(activeLoans)) {
                booksBorrowedCountEl.textContent = activeLoans.length;
            }

            renderStudentBooksTable(Array.isArray(books) ? books : []);
            renderStudentLoansTable(Array.isArray(activeLoans) ? activeLoans : []);
        }

        function renderStudentBooksTable(books) {
            const booksSection = document.getElementById("books");
            if (!booksSection) return;

            // FILTER: Only show books that have > 0 available copies (hide 0/4 completely)
            const availableBooks = books.filter(b => b.availableCopies > 0);

            let tableBody = booksSection.querySelector("tbody");
            if (!tableBody) {
                let container = booksSection.querySelector(".section-content") || booksSection;
                container.innerHTML = `
                    <div class="table-container" style="background:#fff; padding:20px; border-radius:10px; margin-top:20px; box-shadow:0 4px 6px rgba(0,0,0,0.05);">
                        <div style="display:flex; justify-content:space-between; align-items:center; margin-bottom:15px;">
                            <h3>Available Library Books</h3>
                            <input type="text" id="studentBookSearch" placeholder="Search title or ISBN..." style="padding:8px 12px; border:1px solid #ccc; border-radius:6px; width:250px;">
                        </div>
                        <table style="width:100%; border-collapse:collapse; text-align:left;">
                            <thead>
                                <tr style="border-bottom:2px solid #e2e8f0; padding:10px;">
                                    <th style="padding:12px;">ID</th>
                                    <th style="padding:12px;">Title</th>
                                    <th style="padding:12px;">ISBN</th>
                                    <th style="padding:12px;">Category</th>
                                    <th style="padding:12px;">Publisher</th>
                                    <th style="padding:12px;">Available</th>
                                    <th style="padding:12px;">Action</th>
                                </tr>
                            </thead>
                            <tbody id="studentBookTableBody"></tbody>
                        </table>
                    </div>
                `;
                tableBody = document.getElementById("studentBookTableBody");

                const searchInput = document.getElementById("studentBookSearch");
                if (searchInput) {
                    searchInput.addEventListener("input", function() {
                        const term = this.value.toLowerCase();
                        const filtered = availableBooks.filter(b => 
                            (b.title && b.title.toLowerCase().includes(term)) ||
                            (b.isbn && b.isbn.toLowerCase().includes(term)) ||
                            (b.category && b.category.toLowerCase().includes(term))
                        );
                        populateRows(filtered);
                    });
                }
            }

            function populateRows(items) {
                if (!tableBody) return;
                tableBody.innerHTML = "";
                if (items.length === 0) {
                    tableBody.innerHTML = `<tr><td colspan="7" style="padding:15px; text-align:center; color:#64748b;">No available books found in catalog.</td></tr>`;
                    return;
                }

                items.forEach(b => {
                    const tr = document.createElement("tr");
                    tr.style.borderBottom = "1px solid #f1f5f9";
                    tr.innerHTML = `
                        <td style="padding:12px;">${b.bookId}</td>
                        <td style="padding:12px;"><strong>${escapeHtml(b.title)}</strong></td>
                        <td style="padding:12px;">${escapeHtml(b.isbn)}</td>
                        <td style="padding:12px;">${escapeHtml(b.category || "General")}</td>
                        <td style="padding:12px;">${escapeHtml(b.publisher || "NWU Press")}</td>
                        <td style="padding:12px;"><span style="padding:4px 8px; border-radius:4px; font-weight:600; font-size:12px; background:#dcfce7; color:#15803d;">${b.availableCopies} / ${b.totalCopies}</span></td>
                        <td style="padding:12px;">
                            <button class="borrow-btn" data-id="${b.bookId}" style="background:#2563eb; color:#fff; border:none; padding:6px 12px; border-radius:4px; cursor:pointer;">
                                Borrow
                            </button>
                        </td>
                    `;
                    tableBody.appendChild(tr);
                });

                tableBody.querySelectorAll(".borrow-btn").forEach(btn => {
                    btn.addEventListener("click", async function () {
                        const bookId = parseInt(this.getAttribute("data-id"));
                        if (!bookId) return;

                        if (confirm("Would you like to borrow this book?")) {
                            const res = await apiCall("/api/loans/issue", "POST", {
                                userId: currentUser.userId,
                                userCode: currentUser.userCode,
                                bookId: bookId
                            });

                            if (res && res.success) {
                                alert("Book borrowed successfully!");
                                loadStudentBooks();
                            } else {
                                alert(res.message || "Could not borrow book.");
                            }
                        }
                    });
                });
            }

            populateRows(availableBooks);
        }

        function renderStudentLoansTable(loans) {
            const loansSection = document.getElementById("loans");
            if (!loansSection) return;

            let tableBody = loansSection.querySelector("tbody");
            if (!tableBody) {
                let container = loansSection.querySelector(".section-content") || loansSection;
                container.innerHTML = `
                    <div class="table-container" style="background:#fff; padding:20px; border-radius:10px; margin-top:20px; box-shadow:0 4px 6px rgba(0,0,0,0.05);">
                        <h3>My Active Loans</h3>
                        <table style="width:100%; border-collapse:collapse; text-align:left; margin-top:15px;">
                            <thead>
                                <tr style="border-bottom:2px solid #e2e8f0; padding:10px;">
                                    <th style="padding:12px;">Loan ID</th>
                                    <th style="padding:12px;">Book Title</th>
                                    <th style="padding:12px;">Issue Date</th>
                                    <th style="padding:12px;">Due Date</th>
                                    <th style="padding:12px;">Status</th>
                                </tr>
                            </thead>
                            <tbody id="studentLoansTableBody"></tbody>
                        </table>
                    </div>
                `;
                tableBody = document.getElementById("studentLoansTableBody");
            }

            if (tableBody) {
                tableBody.innerHTML = "";
                if (loans.length === 0) {
                    tableBody.innerHTML = `<tr><td colspan="5" style="padding:15px; text-align:center; color:#64748b;">You have no active loans.</td></tr>`;
                    return;
                }

                loans.forEach(l => {
                    const tr = document.createElement("tr");
                    tr.style.borderBottom = "1px solid #f1f5f9";
                    tr.innerHTML = `
                        <td style="padding:12px;">#${l.loanId}</td>
                        <td style="padding:12px;"><strong>${escapeHtml(l.bookTitle)}</strong></td>
                        <td style="padding:12px;">${escapeHtml(l.issueDate)}</td>
                        <td style="padding:12px;">${escapeHtml(l.dueDate)}</td>
                        <td style="padding:12px;"><span style="padding:4px 8px; border-radius:4px; font-weight:600; font-size:12px; background:#e0f2fe; color:#0369a1;">${escapeHtml(l.status)}</span></td>
                    `;
                    tableBody.appendChild(tr);
                });
            }
        }

        loadStudentBooks();
    }

    // ----------------------------------------------------
    // PAGE 3: LIBRARIAN DASHBOARD (librarian-dashboard.html)
    // ----------------------------------------------------
    const isLibrarianPage = document.title.toLowerCase().includes("librarian dashboard");
    if (isLibrarianPage) {
        if (!currentUser || !(currentUser.role || "").toLowerCase().match(/(staff|librarian|admin)/)) {
            alert("Access Denied: Please log in with Librarian/Staff credentials.");
            window.location.href = "index.html";
            return;
        }

        const staffProfileElements = document.querySelectorAll(".profile-small h4, .student-profile strong");
        staffProfileElements.forEach(el => {
            el.textContent = currentUser.name || currentUser.userCode;
        });

        async function loadLibrarianData() {
            const books = await apiCall("/api/books");
            const loans = await apiCall("/api/loans", "POST", { userRole: currentUser.role });

            const booksList = Array.isArray(books) ? books : [];
            const loansList = Array.isArray(loans) ? loans : [];

            const totalBooksEl = document.querySelector(".stat-card.blue h2");
            if (totalBooksEl) totalBooksEl.textContent = booksList.reduce((acc, b) => acc + (b.totalCopies || 1), 0);

            const activeLoansEl = document.querySelector(".stat-card.green h2, .stat-card.orange h2");
            if (activeLoansEl) activeLoansEl.textContent = loansList.length;

            renderLibrarianBooksTable(booksList);
            renderLibrarianLoansTable(loansList);
        }

        function renderLibrarianBooksTable(books) {
            const booksSection = document.getElementById("books");
            if (!booksSection) return;

            let tableBody = booksSection.querySelector("tbody");
            if (!tableBody) {
                let container = booksSection.querySelector(".section-content") || booksSection;
                container.innerHTML = `
                    <div class="table-container" style="background:#fff; padding:20px; border-radius:10px; margin-top:20px; box-shadow:0 4px 6px rgba(0,0,0,0.05);">
                        <div style="display:flex; justify-content:space-between; align-items:center; margin-bottom:15px; flex-wrap:wrap; gap:10px;">
                            <h3>Book Catalog Management</h3>
                            <div>
                                <input type="text" id="libBookSearch" placeholder="Search book title..." style="padding:8px 12px; border:1px solid #ccc; border-radius:6px; margin-right:10px;">
                                <button id="addBookModalBtn" style="background:#2563eb; color:#fff; border:none; padding:8px 16px; border-radius:6px; cursor:pointer; font-weight:600;"><i class='bx bx-plus'></i> Add Book</button>
                            </div>
                        </div>
                        <table style="width:100%; border-collapse:collapse; text-align:left;">
                            <thead>
                                <tr style="border-bottom:2px solid #e2e8f0; padding:10px;">
                                    <th style="padding:12px;">ID</th>
                                    <th style="padding:12px;">Title</th>
                                    <th style="padding:12px;">ISBN</th>
                                    <th style="padding:12px;">Publisher</th>
                                    <th style="padding:12px;">Year</th>
                                    <th style="padding:12px;">Available / Total</th>
                                    <th style="padding:12px;">Action</th>
                                </tr>
                            </thead>
                            <tbody id="libBookTableBody"></tbody>
                        </table>
                    </div>
                `;
                tableBody = document.getElementById("libBookTableBody");

                const addBtn = document.getElementById("addBookModalBtn");
                if (addBtn) {
                    addBtn.addEventListener("click", showAddBookPrompt);
                }

                const searchInput = document.getElementById("libBookSearch");
                if (searchInput) {
                    searchInput.addEventListener("input", function() {
                        const term = this.value.toLowerCase();
                        const filtered = books.filter(b => (b.title && b.title.toLowerCase().includes(term)) || (b.isbn && b.isbn.toLowerCase().includes(term)));
                        populateRows(filtered);
                    });
                }
            }

            function populateRows(items) {
                if (!tableBody) return;
                tableBody.innerHTML = "";
                if (items.length === 0) {
                    tableBody.innerHTML = `<tr><td colspan="7" style="padding:15px; text-align:center; color:#64748b;">No books found in catalog.</td></tr>`;
                    return;
                }

                items.forEach(b => {
                    const tr = document.createElement("tr");
                    tr.style.borderBottom = "1px solid #f1f5f9";
                    tr.innerHTML = `
                        <td style="padding:12px;">${b.bookId}</td>
                        <td style="padding:12px;"><strong>${escapeHtml(b.title)}</strong></td>
                        <td style="padding:12px;">${escapeHtml(b.isbn)}</td>
                        <td style="padding:12px;">${escapeHtml(b.publisher || "-")}</td>
                        <td style="padding:12px;">${b.year || 2024}</td>
                        <td style="padding:12px;"><span style="font-weight:600; color:${b.availableCopies > 0 ? '#2563eb' : '#ef4444'};">${b.availableCopies}</span> / ${b.totalCopies}</td>
                        <td style="padding:12px;">
                            <button class="delete-book-btn" data-id="${b.bookId}" style="background:#ef4444; color:#fff; border:none; padding:6px 12px; border-radius:4px; cursor:pointer;">Delete</button>
                        </td>
                    `;
                    tableBody.appendChild(tr);
                });

                tableBody.querySelectorAll(".delete-book-btn").forEach(btn => {
                    btn.addEventListener("click", async function() {
                        const bookId = parseInt(this.getAttribute("data-id"));
                        if (confirm(`Are you sure you want to delete Book ID #${bookId}?`)) {
                            const res = await apiCall("/api/books/delete", "POST", {
                                userRole: currentUser.role,
                                bookId: bookId
                            });

                            if (res && res.success) {
                                alert("Book deleted successfully.");
                                loadLibrarianData();
                            } else {
                                alert(res.message || "Failed to delete book.");
                            }
                        }
                    });
                });
            }

            populateRows(books);
        }

        async function showAddBookPrompt() {
            const title = prompt("Enter Book Title:");
            if (!title) return;
            const isbn = prompt("Enter ISBN:");
            if (!isbn) return;
            const publisher = prompt("Enter Publisher Name:", "Pearson");
            if (!publisher) return;
            const yearStr = prompt("Enter Publication Year:", "2026");
            const copiesStr = prompt("Enter Number of Copies:", "3");

            const res = await apiCall("/api/books/add", "POST", {
                userRole: currentUser.role,
                title: title.trim(),
                isbn: isbn.trim(),
                publisher: publisher.trim(),
                year: parseInt(yearStr) || 2026,
                copies: parseInt(copiesStr) || 1
            });

            if (res && res.success) {
                alert(res.message || "Book added successfully!");
                loadLibrarianData();
            } else {
                alert(res.message || "Failed to add book.");
            }
        }

        function renderLibrarianLoansTable(loans) {
            const loansSection = document.getElementById("loans");
            if (!loansSection) return;

            let tableBody = loansSection.querySelector("tbody");
            if (!tableBody) {
                let container = loansSection.querySelector(".section-content") || loansSection;
                container.innerHTML = `
                    <div class="table-container" style="background:#fff; padding:20px; border-radius:10px; margin-top:20px; box-shadow:0 4px 6px rgba(0,0,0,0.05);">
                        <div style="display:flex; justify-content:space-between; align-items:center; margin-bottom:15px;">
                            <h3>Active Loans & Borrowings</h3>
                            <button id="issueLoanModalBtn" style="background:#16a34a; color:#fff; border:none; padding:8px 16px; border-radius:6px; cursor:pointer; font-weight:600;"><i class='bx bx-plus'></i> Issue New Loan</button>
                        </div>
                        <table style="width:100%; border-collapse:collapse; text-align:left;">
                            <thead>
                                <tr style="border-bottom:2px solid #e2e8f0; padding:10px;">
                                    <th style="padding:12px;">Loan ID</th>
                                    <th style="padding:12px;">User Code</th>
                                    <th style="padding:12px;">Borrower</th>
                                    <th style="padding:12px;">Book Title</th>
                                    <th style="padding:12px;">Issue Date</th>
                                    <th style="padding:12px;">Due Date</th>
                                    <th style="padding:12px;">Status</th>
                                    <th style="padding:12px;">Action</th>
                                </tr>
                            </thead>
                            <tbody id="libLoansTableBody"></tbody>
                        </table>
                    </div>
                `;
                tableBody = document.getElementById("libLoansTableBody");

                const issueBtn = document.getElementById("issueLoanModalBtn");
                if (issueBtn) {
                    issueBtn.addEventListener("click", showIssueLoanPrompt);
                }
            }

            if (tableBody) {
                tableBody.innerHTML = "";
                if (loans.length === 0) {
                    tableBody.innerHTML = `<tr><td colspan="8" style="padding:15px; text-align:center; color:#64748b;">No active loans found.</td></tr>`;
                    return;
                }

                loans.forEach(l => {
                    const tr = document.createElement("tr");
                    tr.style.borderBottom = "1px solid #f1f5f9";
                    tr.innerHTML = `
                        <td style="padding:12px;">#${l.loanId}</td>
                        <td style="padding:12px;">${escapeHtml(l.userCode)}</td>
                        <td style="padding:12px;"><strong>${escapeHtml(l.borrower)}</strong></td>
                        <td style="padding:12px;">${escapeHtml(l.bookTitle)}</td>
                        <td style="padding:12px;">${escapeHtml(l.issueDate)}</td>
                        <td style="padding:12px;">${escapeHtml(l.dueDate)}</td>
                        <td style="padding:12px;"><span style="padding:4px 8px; border-radius:4px; font-weight:600; font-size:12px; background:#e0f2fe; color:#0369a1;">${escapeHtml(l.status)}</span></td>
                        <td style="padding:12px;">
                            <button class="return-loan-btn" data-id="${l.loanId}" style="background:#16a34a; color:#fff; border:none; padding:6px 12px; border-radius:4px; cursor:pointer;">Return Book</button>
                        </td>
                    `;
                    tableBody.appendChild(tr);
                });

                tableBody.querySelectorAll(".return-loan-btn").forEach(btn => {
                    btn.addEventListener("click", async function() {
                        const loanId = parseInt(this.getAttribute("data-id"));
                        if (confirm(`Process return for Loan ID #${loanId}?`)) {
                            const res = await apiCall("/api/loans/return", "POST", {
                                userRole: currentUser.role,
                                loanId: loanId
                            });

                            if (res && res.success) {
                                alert("Book returned successfully!");
                                loadLibrarianData();
                            } else {
                                alert(res.message || "Failed to process return.");
                            }
                        }
                    });
                });
            }
        }

        async function showIssueLoanPrompt() {
            const userIdStr = prompt("Enter User ID or Student Code:", "1");
            if (!userIdStr) return;
            const bookIdStr = prompt("Enter Book ID to issue:", "1");
            if (!bookIdStr) return;

            const res = await apiCall("/api/loans/issue", "POST", {
                userId: parseInt(userIdStr) || 0,
                userCode: userIdStr.trim(),
                bookId: parseInt(bookIdStr)
            });

            if (res && res.success) {
                alert(res.message || "Loan issued successfully!");
                loadLibrarianData();
            } else {
                alert(res.message || "Failed to issue loan.");
            }
        }

        loadLibrarianData();
    }

    function escapeHtml(str) {
        if (!str) return "";
        return String(str)
            .replace(/&/g, "&amp;")
            .replace(/</g, "&lt;")
            .replace(/>/g, "&gt;")
            .replace(/"/g, "&quot;")
            .replace(/'/g, "&#039;");
    }
});
