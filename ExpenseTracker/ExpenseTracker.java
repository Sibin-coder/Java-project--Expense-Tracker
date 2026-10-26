import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.*;
import java.math.BigDecimal;
import java.sql.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;

import database.DatabaseManager;
import model.Expense;
import repository.ExpenseRepository;
import service.ExpenseService;
import ui.LoginDialog;

/*
 * ExpenseTracker.java
 *
 * Requirements:
 * 1. Java SE (Swing + JDBC)
 * 2. MySQL Server running locally
 * 3. MySQL Connector/J JAR added to the IDE project classpath
 *
 * The application creates the users and expenses tables automatically:
 *
 * CREATE DATABASE expense_tracker;
 * USE expense_tracker;
 * CREATE TABLE users (
 *   id INT PRIMARY KEY AUTO_INCREMENT,
 *   username VARCHAR(50) NOT NULL UNIQUE,
 *   password_hash CHAR(64) NOT NULL,
 *   created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
 * );
 * CREATE TABLE expenses (
 *   id INT PRIMARY KEY AUTO_INCREMENT,
 *   user_id INT NOT NULL,
 *   expense_date DATE NOT NULL,
 *   category VARCHAR(50) NOT NULL,
 *   payment_method VARCHAR(40) NOT NULL,
 *   description VARCHAR(255),
 *   amount DECIMAL(12,2) NOT NULL,
 *   created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
 * );
 *
 * Change DB_USER and DB_PASSWORD below to match your MySQL installation.
 */
public class ExpenseTracker extends JFrame {
    private static final String DB_URL =
            "jdbc:mysql://localhost:3306/expense_tracker"
                    + "?useSSL=false&allowPublicKeyRetrieval=true"
                    + "&serverTimezone=Asia/Kolkata";
    private static final String DB_USER = "root";
    private static final String DB_PASSWORD = "1234";

    private static final String[] CATEGORIES = {
            "Food", "Transport", "Bills", "Entertainment",
            "Health", "Shopping", "Education", "Other"
    };
    private static final String[] PAYMENT_METHODS = {
            "UPI", "Cash", "Card", "Bank Transfer"
    };

    private Connection connection;
    private DatabaseManager databaseManager;
    private ExpenseRepository expenseRepository;
    private ExpenseService expenseService;
    private int currentUserId;
    private String currentUsername;
    private final CardLayout cardLayout = new CardLayout();
    private final JPanel contentPanel = new JPanel(cardLayout);

    private final JLabel totalMonthLabel = new JLabel("₹0.00");
    private final JLabel countMonthLabel = new JLabel("0");
    private final JLabel topCategoryLabel = new JLabel("-");
    private final DefaultTableModel tableModel = new DefaultTableModel(
            new Object[]{"ID", "Date", "Category", "Description", "Payment", "Amount (₹)"}, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };

    private final JTable expenseTable = new JTable(tableModel);

    private final JTextField amountField = new JTextField();
    private final JTextField dateField = new JTextField(LocalDate.now().toString());
    private final JComboBox<String> categoryBox = new JComboBox<>(CATEGORIES);
    private final JComboBox<String> paymentBox = new JComboBox<>(PAYMENT_METHODS);
    private final JTextField descriptionField = new JTextField();

    private final JTextField searchField = new JTextField(14);
    private final JComboBox<String> filterCategoryBox =
            new JComboBox<>(new String[]{"All", "Food", "Transport", "Bills",
                    "Entertainment", "Health", "Shopping", "Education", "Other"});

    public ExpenseTracker() {
        super("Expense Tracker | INR");
        connectDatabase();
        if (!loginUser()) {
            dispose();
            return;
        }
        createInterface();
        loadExpenses("");
        refreshDashboard();
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1050, 680);
        setMinimumSize(new Dimension(850, 560));
        setLocationRelativeTo(null);
    }

    private void connectDatabase() {
        try {
            databaseManager = new DatabaseManager(DB_URL, DB_USER, DB_PASSWORD);
            databaseManager.connect();
            connection = databaseManager.getConnection();
            initializeSchema();
            expenseRepository = new ExpenseRepository(connection, () -> currentUserId);
            expenseService = new ExpenseService(expenseRepository);
        } catch (SQLException e) {
            JOptionPane.showMessageDialog(null,
                    "Could not connect to MySQL.\n\nCheck that MySQL is running, "
                            + "the database 'expense_tracker' exists, and DB_USER/DB_PASSWORD "
                            + "are correct.\n\nDetails: " + e.getMessage(),
                    "Database Connection Error", JOptionPane.ERROR_MESSAGE);
            System.exit(1);
        }
    }

    private void initializeSchema() throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS users ("
                            + "id INT PRIMARY KEY AUTO_INCREMENT,"
                            + "username VARCHAR(50) NOT NULL UNIQUE,"
                            + "password_hash CHAR(64) NOT NULL,"
                            + "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
            statement.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS expenses ("
                            + "id INT PRIMARY KEY AUTO_INCREMENT,"
                            + "user_id INT NOT NULL,"
                            + "expense_date DATE NOT NULL,"
                            + "category VARCHAR(50) NOT NULL,"
                            + "payment_method VARCHAR(40) NOT NULL,"
                            + "description VARCHAR(255),"
                            + "amount DECIMAL(12,2) NOT NULL,"
                            + "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,"
                            + "CONSTRAINT fk_expenses_user FOREIGN KEY (user_id) REFERENCES users(id))");
        }
        try (Statement statement = connection.createStatement()) {
            try {
                statement.executeUpdate("ALTER TABLE expenses ADD COLUMN user_id INT NULL");
            } catch (SQLException ignored) {
                // The column already exists in an existing installation.
            }
        }
    }

    private boolean loginUser() {
        while (true) {
            LoginDialog dialog = new LoginDialog(this::showLoginError);
            dialog.setVisible(true);
            if (dialog.isCancelled()) return false;
            if (dialog.isCreateAccount()) {
                createAccount();
                continue;
            }
            try {
                String passwordHash = hashPassword(dialog.getPassword());
                try (PreparedStatement statement = connection.prepareStatement(
                        "SELECT id, username FROM users WHERE username=? AND password_hash=?")) {
                    statement.setString(1, dialog.getUsername());
                    statement.setString(2, passwordHash);
                    try (ResultSet results = statement.executeQuery()) {
                        if (results.next()) {
                            currentUserId = results.getInt("id");
                            currentUsername = results.getString("username");
                            return true;
                        }
                    }
                }
                showLoginError("Invalid username or password.");
            } catch (SQLIntegrityConstraintViolationException ex) {
                showLoginError("That username already exists.");
            } catch (SQLException ex) {
                showLoginError("Could not access user accounts: " + ex.getMessage());
            }
        }
    }

    private void createAccount() {
        JTextField username = new JTextField(20);
        JPasswordField password = new JPasswordField(20);
        JPasswordField confirmPassword = new JPasswordField(20);
        JPanel fields = new JPanel(new GridLayout(0, 2, 8, 8));
        fields.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        fields.add(new JLabel("Username:"));
        fields.add(username);
        fields.add(new JLabel("Password:"));
        fields.add(password);
        fields.add(new JLabel("Confirm password:"));
        fields.add(confirmPassword);

        while (true) {
            int result = JOptionPane.showConfirmDialog(null, fields,
                    "Create Expense Tracker Account", JOptionPane.OK_CANCEL_OPTION,
                    JOptionPane.PLAIN_MESSAGE);
            if (result != JOptionPane.OK_OPTION) return;
            String usernameValue = username.getText().trim();
            char[] passwordValue = password.getPassword();
            char[] confirmationValue = confirmPassword.getPassword();
            if (usernameValue.isEmpty() || passwordValue.length == 0) {
                showLoginError("Username and password are required.");
                continue;
            }
            if (!java.util.Arrays.equals(passwordValue, confirmationValue)) {
                showLoginError("The passwords do not match.");
                continue;
            }
            if (usernameValue.length() < 3) {
                showLoginError("Username must contain at least 3 characters.");
                continue;
            }
            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO users (username, password_hash) VALUES (?, ?)")) {
                statement.setString(1, usernameValue);
                statement.setString(2, hashPassword(passwordValue));
                statement.executeUpdate();
                JOptionPane.showMessageDialog(null,
                        "Account created successfully.\nYou can now log in as " + usernameValue + ".",
                        "Account Created", JOptionPane.INFORMATION_MESSAGE);
                return;
            } catch (SQLIntegrityConstraintViolationException ex) {
                showLoginError("That username already exists. Choose another username.");
            } catch (SQLException ex) {
                showLoginError("Could not create account:\n" + ex.getMessage());
                return;
            }
        }
    }

    private void showLoginError(String message) {
        JOptionPane.showMessageDialog(null, message, "Login", JOptionPane.ERROR_MESSAGE);
    }

    private String hashPassword(char[] password) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(new String(password).getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder();
            for (byte value : hash) result.append(String.format("%02x", value));
            return result.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable.", ex);
        }
    }

    private void createInterface() {
        setLayout(new BorderLayout());

        JPanel sidebar = new JPanel();
        sidebar.setBackground(new Color(24, 34, 58));
        sidebar.setPreferredSize(new Dimension(225, 0));
        sidebar.setLayout(new BoxLayout(sidebar, BoxLayout.Y_AXIS));

        JLabel brand = new JLabel("  EXPENSE TRACKER");
        brand.setForeground(new Color(125, 211, 252));
        brand.setFont(new Font("SansSerif", Font.BOLD, 17));
        brand.setBorder(BorderFactory.createEmptyBorder(24, 10, 24, 5));
        sidebar.add(brand);

        addNavButton(sidebar, "Dashboard", "dashboard");
        addNavButton(sidebar, "Add Expense", "add");
        addNavButton(sidebar, "Expense History", "history");
        addNavButton(sidebar, "Reports", "reports");
        sidebar.add(Box.createVerticalGlue());

        JLabel currency = new JLabel("  Currency: INR (₹)");
        currency.setForeground(new Color(200, 210, 225));
        currency.setBorder(BorderFactory.createEmptyBorder(15, 8, 20, 8));
        sidebar.add(currency);
        JLabel user = new JLabel("  User: " + currentUsername);
        user.setForeground(new Color(186, 230, 253));
        user.setBorder(BorderFactory.createEmptyBorder(8, 8, 15, 8));
        sidebar.add(user);

        contentPanel.add(buildDashboard(), "dashboard");
        contentPanel.add(buildAddExpense(), "add");
        contentPanel.add(buildHistory(), "history");
        contentPanel.add(buildReports(), "reports");

        add(sidebar, BorderLayout.WEST);
        add(contentPanel, BorderLayout.CENTER);
        getContentPane().setBackground(new Color(244, 247, 251));
        cardLayout.show(contentPanel, "dashboard");
    }

    private void addNavButton(JPanel sidebar, String title, String screen) {
        JButton button = new JButton(title);
        button.setMaximumSize(new Dimension(Integer.MAX_VALUE, 45));
        button.setHorizontalAlignment(SwingConstants.LEFT);
        button.setForeground(Color.WHITE);
        button.setBackground(new Color(24, 34, 58));
        button.setOpaque(true);
        button.setBorderPainted(false);
        button.setFocusPainted(false);
        button.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createEmptyBorder(3, 10, 3, 10),
                BorderFactory.createEmptyBorder(10, 14, 10, 10)));
        button.setFont(new Font("SansSerif", Font.BOLD, 14));
        button.setCursor(new Cursor(Cursor.HAND_CURSOR));
        button.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                button.setBackground(new Color(37, 99, 235));
            }

            @Override
            public void mouseExited(MouseEvent e) {
                button.setBackground(new Color(24, 34, 58));
            }
        });
        button.addActionListener(e -> {
            if ("history".equals(screen)) loadExpenses("");
            if ("dashboard".equals(screen)) refreshDashboard();
            if ("reports".equals(screen)) refreshReports();
            cardLayout.show(contentPanel, screen);
        });
        sidebar.add(button);
    }

    private JPanel basePanel(String title) {
        JPanel panel = new JPanel(new BorderLayout(12, 12));
        panel.setBackground(new Color(244, 247, 251));
        panel.setBorder(BorderFactory.createEmptyBorder(22, 22, 22, 22));
        JLabel heading = new JLabel(title);
        heading.setFont(new Font("SansSerif", Font.BOLD, 25));
        heading.setForeground(new Color(31, 41, 55));
        panel.add(heading, BorderLayout.NORTH);
        return panel;
    }

    private JPanel buildDashboard() {
        JPanel panel = basePanel("Dashboard");
        JPanel body = new JPanel(new GridLayout(2, 2, 14, 14));
        body.setOpaque(false);

        body.add(statCard("Spent this month", totalMonthLabel));
        body.add(statCard("Transactions this month", countMonthLabel));
        body.add(statCard("Top spending category", topCategoryLabel));

        JPanel actions = new JPanel();
        actions.setBackground(Color.WHITE);
        actions.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(225, 230, 238)),
                BorderFactory.createEmptyBorder(20, 20, 20, 20)));
        actions.setLayout(new BoxLayout(actions, BoxLayout.Y_AXIS));
        JLabel actionTitle = new JLabel("Quick actions");
        actionTitle.setFont(new Font("SansSerif", Font.BOLD, 17));
        JButton add = new JButton(" + Add an expense");
        add.addActionListener(e -> cardLayout.show(contentPanel, "add"));
        JButton history = new JButton("View expense history");
        history.addActionListener(e -> {
            loadExpenses("");
            cardLayout.show(contentPanel, "history");
        });
        actions.add(actionTitle);
        actions.add(Box.createVerticalStrut(18));
        actions.add(add);
        actions.add(Box.createVerticalStrut(10));
        actions.add(history);
        body.add(actions);

        panel.add(body, BorderLayout.CENTER);
        return panel;
    }

    private JPanel statCard(String title, JLabel value) {
        JPanel card = new JPanel();
        card.setBackground(Color.WHITE);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(225, 230, 238)),
                BorderFactory.createEmptyBorder(22, 22, 22, 22)));
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        JLabel label = new JLabel(title);
        label.setForeground(new Color(100, 110, 125));
        value.setFont(new Font("SansSerif", Font.BOLD, 27));
        value.setForeground(new Color(37, 99, 235));
        card.add(label);
        card.add(Box.createVerticalStrut(16));
        card.add(value);
        return card;
    }

    private JPanel buildAddExpense() {
        JPanel panel = basePanel("Add Expense");
        JPanel form = new JPanel(new GridBagLayout());
        form.setBackground(Color.WHITE);
        form.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(225, 230, 238)),
                BorderFactory.createEmptyBorder(22, 22, 22, 22)));
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(9, 8, 9, 8);
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1;

        addFormRow(form, c, 0, "Amount (₹)", amountField);
        addFormRow(form, c, 1, "Date (YYYY-MM-DD)", dateField);
        addFormRow(form, c, 2, "Category", categoryBox);
        addFormRow(form, c, 3, "Payment method", paymentBox);
        addFormRow(form, c, 4, "Description", descriptionField);

        JButton save = new JButton("Save Expense");
        save.addActionListener(e -> saveExpense());
        JButton clear = new JButton("Clear");
        clear.addActionListener(e -> clearForm());

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT));
        buttons.setOpaque(false);
        buttons.add(save);
        buttons.add(clear);
        c.gridx = 1;
        c.gridy = 5;
        form.add(buttons, c);

        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setOpaque(false);
        wrapper.add(form, BorderLayout.NORTH);
        panel.add(wrapper, BorderLayout.CENTER);
        return panel;
    }

    private void addFormRow(JPanel form, GridBagConstraints c, int row,
                            String label, Component input) {
        c.gridx = 0;
        c.gridy = row;
        c.weightx = 0.35;
        form.add(new JLabel(label), c);
        c.gridx = 1;
        c.weightx = 0.65;
        if (input instanceof JTextField) ((JTextField) input).setColumns(22);
        form.add(input, c);
    }

    private JPanel buildHistory() {
        JPanel panel = basePanel("Expense History");
        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
        top.setOpaque(false);
        top.add(new JLabel("Search description:"));
        top.add(searchField);
        top.add(new JLabel("Category:"));
        top.add(filterCategoryBox);
        JButton search = new JButton("Search");
        search.addActionListener(e -> loadExpenses(searchField.getText().trim()));
        top.add(search);
        JButton reset = new JButton("Reset");
        reset.addActionListener(e -> {
            searchField.setText("");
            filterCategoryBox.setSelectedItem("All");
            loadExpenses("");
        });
        top.add(reset);

        expenseTable.setRowHeight(27);
        expenseTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        JScrollPane scroll = new JScrollPane(expenseTable);

        JButton edit = new JButton("Edit selected");
        edit.addActionListener(e -> editSelectedExpense());
        JButton delete = new JButton("Delete selected");
        delete.addActionListener(e -> deleteSelectedExpense());
        JButton refresh = new JButton("Refresh");
        refresh.addActionListener(e -> loadExpenses(searchField.getText().trim()));

        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.LEFT));
        bottom.setOpaque(false);
        bottom.add(edit);
        bottom.add(delete);
        bottom.add(refresh);

        JPanel center = new JPanel(new BorderLayout(8, 8));
        center.setOpaque(false);
        center.add(top, BorderLayout.NORTH);
        center.add(scroll, BorderLayout.CENTER);
        center.add(bottom, BorderLayout.SOUTH);
        panel.add(center, BorderLayout.CENTER);
        return panel;
    }

    private JPanel buildReports() {
        JPanel panel = basePanel("Reports");
        JTextArea report = new JTextArea();
        report.setEditable(false);
        report.setFont(new Font("Monospaced", Font.PLAIN, 14));
        report.setMargin(new Insets(18, 18, 18, 18));
        report.setBackground(Color.WHITE);
        JButton refresh = new JButton("Refresh report");
        refresh.addActionListener(e -> refreshReports());
        JPanel center = new JPanel(new BorderLayout(8, 8));
        center.setOpaque(false);
        center.add(refresh, BorderLayout.NORTH);
        center.add(new JScrollPane(report), BorderLayout.CENTER);
        panel.add(center, BorderLayout.CENTER);
        panel.putClientProperty("reportArea", report);
        return panel;
    }

    private void saveExpense() {
        BigDecimal amount;
        LocalDate date;
        try {
            amount = new BigDecimal(amountField.getText().trim());
            if (amount.compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("Amount must be greater than zero.");
            }
            date = LocalDate.parse(dateField.getText().trim());
            if (date.isAfter(LocalDate.now())) {
                throw new IllegalArgumentException("Expense date cannot be in the future.");
            }
        } catch (NumberFormatException ex) {
            showError("Enter a valid amount, such as 125.50.");
            return;
        } catch (DateTimeParseException ex) {
            showError("Enter the date in YYYY-MM-DD format.");
            return;
        } catch (IllegalArgumentException ex) {
            showError(ex.getMessage());
            return;
        }

        try {
            expenseService.addExpense(amount, date,
                    (String) categoryBox.getSelectedItem(),
                    (String) paymentBox.getSelectedItem(),
                    descriptionField.getText().trim());
            JOptionPane.showMessageDialog(this, "Expense saved successfully.");
            clearForm();
            loadExpenses("");
            refreshDashboard();
            refreshReports();
            cardLayout.show(contentPanel, "dashboard");
        } catch (SQLException ex) {
            showError("Could not save expense: " + ex.getMessage());
        }
    }

    private void loadExpenses(String search) {
        if (connection == null) return;
        tableModel.setRowCount(0);
        String category = (String) filterCategoryBox.getSelectedItem();
        StringBuilder sql = new StringBuilder(
                "SELECT id, expense_date, category, description, payment_method, amount "
                        + "FROM expenses WHERE user_id=? AND description LIKE ?");
        if (category != null && !"All".equals(category)) {
            sql.append(" AND category = ?");
        }
        sql.append(" ORDER BY expense_date DESC, id DESC");

        try (PreparedStatement ps = connection.prepareStatement(sql.toString())) {
            ps.setInt(1, currentUserId);
            ps.setString(2, "%" + search + "%");
            if (category != null && !"All".equals(category)) ps.setString(3, category);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    tableModel.addRow(new Object[]{
                            rs.getInt("id"),
                            rs.getDate("expense_date"),
                            rs.getString("category"),
                            rs.getString("description"),
                            rs.getString("payment_method"),
                            "₹" + rs.getBigDecimal("amount").toPlainString()
                    });
                }
            }
        } catch (SQLException ex) {
            showError("Could not load expenses: " + ex.getMessage());
        }
    }

    private void editSelectedExpense() {
        int row = expenseTable.getSelectedRow();
        if (row < 0) {
            showError("Select an expense first.");
            return;
        }

        int id = (Integer) tableModel.getValueAt(row, 0);
        JTextField amount = new JTextField(tableModel.getValueAt(row, 5).toString().replace("₹", ""));
        JTextField date = new JTextField(tableModel.getValueAt(row, 1).toString());
        JComboBox<String> category = new JComboBox<>(CATEGORIES);
        category.setSelectedItem(tableModel.getValueAt(row, 2).toString());
        JComboBox<String> payment = new JComboBox<>(PAYMENT_METHODS);
        payment.setSelectedItem(tableModel.getValueAt(row, 4).toString());
        JTextField description = new JTextField(String.valueOf(tableModel.getValueAt(row, 3)));

        JPanel form = new JPanel(new GridLayout(0, 2, 8, 8));
        form.add(new JLabel("Amount (₹)")); form.add(amount);
        form.add(new JLabel("Date (YYYY-MM-DD)")); form.add(date);
        form.add(new JLabel("Category")); form.add(category);
        form.add(new JLabel("Payment")); form.add(payment);
        form.add(new JLabel("Description")); form.add(description);

        if (JOptionPane.showConfirmDialog(this, form, "Edit Expense",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) return;

        try {
            BigDecimal value = new BigDecimal(amount.getText().trim());
            LocalDate dateValue = LocalDate.parse(date.getText().trim());
            if (value.compareTo(BigDecimal.ZERO) <= 0) throw new IllegalArgumentException("Amount must be greater than zero.");
            if (dateValue.isAfter(LocalDate.now())) throw new IllegalArgumentException("Date cannot be in the future.");

            String sql = "UPDATE expenses SET expense_date=?, category=?, payment_method=?, "
                    + "description=?, amount=? WHERE id=? AND user_id=?";
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setDate(1, Date.valueOf(dateValue));
                ps.setString(2, (String) category.getSelectedItem());
                ps.setString(3, (String) payment.getSelectedItem());
                ps.setString(4, description.getText().trim());
                ps.setBigDecimal(5, value);
                ps.setInt(6, id);
                ps.setInt(7, currentUserId);
                ps.executeUpdate();
            }
            loadExpenses(searchField.getText().trim());
            refreshDashboard();
            refreshReports();
        } catch (Exception ex) {
            showError("Could not update expense: " + ex.getMessage());
        }
    }

    private void deleteSelectedExpense() {
        int row = expenseTable.getSelectedRow();
        if (row < 0) {
            showError("Select an expense first.");
            return;
        }
        int id = (Integer) tableModel.getValueAt(row, 0);
        int answer = JOptionPane.showConfirmDialog(this,
                "Delete the selected expense?", "Confirm deletion", JOptionPane.YES_NO_OPTION);
        if (answer != JOptionPane.YES_OPTION) return;

        try (PreparedStatement ps = connection.prepareStatement(
                "DELETE FROM expenses WHERE id=? AND user_id=?")) {
            ps.setInt(1, id);
            ps.setInt(2, currentUserId);
            ps.executeUpdate();
            loadExpenses(searchField.getText().trim());
            refreshDashboard();
            refreshReports();
        } catch (SQLException ex) {
            showError("Could not delete expense: " + ex.getMessage());
        }
    }

    private void refreshDashboard() {
        if (connection == null) return;
        YearMonth month = YearMonth.now();
        String monthStart = month.atDay(1).toString();
        String nextMonth = month.plusMonths(1).atDay(1).toString();

        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT COALESCE(SUM(amount),0), COUNT(*) FROM expenses "
                        + "WHERE user_id=? AND expense_date >= ? AND expense_date < ?")) {
            ps.setInt(1, currentUserId);
            ps.setDate(2, Date.valueOf(monthStart));
            ps.setDate(3, Date.valueOf(nextMonth));
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    totalMonthLabel.setText("₹" + rs.getBigDecimal(1).toPlainString());
                    countMonthLabel.setText(String.valueOf(rs.getInt(2)));
                }
            }
            try (PreparedStatement top = connection.prepareStatement(
                    "SELECT category FROM expenses WHERE user_id=? AND expense_date >= ? AND expense_date < ? "
                            + "GROUP BY category ORDER BY SUM(amount) DESC LIMIT 1")) {
                top.setInt(1, currentUserId);
                top.setDate(2, Date.valueOf(monthStart));
                top.setDate(3, Date.valueOf(nextMonth));
                try (ResultSet rs = top.executeQuery()) {
                    topCategoryLabel.setText(rs.next() ? rs.getString(1) : "-");
                }
            }
        } catch (SQLException ex) {
            showError("Could not refresh dashboard: " + ex.getMessage());
        }
    }

    private void refreshReports() {
        if (connection == null) return;
        JPanel reportsPanel = null;
        for (Component component : contentPanel.getComponents()) {
            if (component instanceof JPanel) {
                JPanel p = (JPanel) component;
                Object property = p.getClientProperty("reportArea");
                if (property instanceof JTextArea) {
                    reportsPanel = p;
                    break;
                }
            }
        }
        if (reportsPanel == null) return;
        JTextArea area = (JTextArea) reportsPanel.getClientProperty("reportArea");

        StringBuilder text = new StringBuilder();
        text.append("EXPENSE REPORTS (Indian Rupees)\n");
        text.append("Generated: ").append(LocalDate.now()).append("\n\n");
        text.append("MONTHLY TOTALS\n");
        String sql = "SELECT YEAR(expense_date) AS yr, MONTH(expense_date) AS mn, "
                + "SUM(amount) AS total, COUNT(*) AS transactions "
                + "FROM expenses WHERE user_id=? GROUP BY YEAR(expense_date), MONTH(expense_date) "
                + "ORDER BY yr DESC, mn DESC";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, currentUserId);
            try (ResultSet rs = ps.executeQuery()) {
            boolean found = false;
            while (rs.next()) {
                found = true;
                text.append(String.format("%04d-%02d  ₹%-14s  Transactions: %d%n",
                        rs.getInt("yr"), rs.getInt("mn"),
                        rs.getBigDecimal("total").toPlainString(), rs.getInt("transactions")));
            }
            if (!found) text.append("No expenses recorded yet.\n");
            }
        } catch (SQLException ex) {
            text.append("Could not load monthly totals: ").append(ex.getMessage()).append('\n');
        }

        text.append("\nSPENDING BY CATEGORY (all time)\n");
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT category, SUM(amount) AS total FROM expenses WHERE user_id=? "
                        + "GROUP BY category ORDER BY SUM(amount) DESC");
             ) {
            ps.setInt(1, currentUserId);
            try (ResultSet rs = ps.executeQuery()) {
            boolean found = false;
            while (rs.next()) {
                found = true;
                text.append(String.format("%-18s ₹%s%n",
                        rs.getString("category"), rs.getBigDecimal("total").toPlainString()));
            }
            if (!found) text.append("No category data yet.\n");
            }
        } catch (SQLException ex) {
            text.append("Could not load category report: ").append(ex.getMessage()).append('\n');
        }
        area.setText(text.toString());
        area.setCaretPosition(0);
    }

    private void clearForm() {
        amountField.setText("");
        dateField.setText(LocalDate.now().toString());
        categoryBox.setSelectedIndex(0);
        paymentBox.setSelectedIndex(0);
        descriptionField.setText("");
    }

    private void showError(String message) {
        JOptionPane.showMessageDialog(this, message, "Expense Tracker",
                JOptionPane.ERROR_MESSAGE);
    }

    @Override
    public void dispose() {
        try {
            if (databaseManager != null) databaseManager.close();
        } catch (SQLException ignored) {
            // Window is closing; no further action is required.
        }
        super.dispose();
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {
                // Keep the default Swing look and feel.
            }
            ExpenseTracker tracker = new ExpenseTracker();
            if (tracker.currentUserId > 0) {
                tracker.setVisible(true);
            }
        });
    }
}
