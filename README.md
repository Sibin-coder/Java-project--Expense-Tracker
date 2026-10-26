# Expense Tracker

A Java desktop application for recording and reviewing personal expenses using Java Swing, JDBC and MySQL.

## Features
- Create an account and log in
- Add expense records with amount, date, category, payment method and description
- View and search/filter expense history
- Edit or delete selected records
- Dashboard with monthly spending, transaction count and top category
- Monthly and category-wise reports

## Technologies
- Java SE
- Java Swing and AWT
- JDBC
- MySQL Server
- MySQL Connector/J
- Java Time API and BigDecimal

## Requirements
- JDK compatible with the source
- MySQL Server
- MySQL Connector/J JAR

## Setup
1. Start MySQL Server.
2. Create the database `expense_tracker` if it does not already exist.
3. Update `DB_USER` and `DB_PASSWORD` in `ExpenseTracker.java` for your local MySQL account.
4. Add MySQL Connector/J to the project classpath.
5. Compile and run the application from your IDE, or use a command similar to:
   `javac -cp ".;mysql-connector-j.jar" ExpenseTracker.java`
   `java -cp ".;mysql-connector-j.jar" ExpenseTracker`
   On Linux/macOS, replace `;` in the classpath with `:`.

The application creates the `users` and `expenses` tables when it connects to the database.

## Database
- `users`: account id, unique username, password hash, creation timestamp.
- `expenses`: expense id, user id, date, category, payment method, description, amount, creation timestamp.
- Relationship: one user can own many expenses.

## Notes
This is a desktop application, not a web application. Do not commit real database passwords or other secrets to GitHub.
