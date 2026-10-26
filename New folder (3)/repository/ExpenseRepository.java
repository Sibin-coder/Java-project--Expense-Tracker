package repository;

import model.Expense;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.function.IntSupplier;

public class ExpenseRepository {
    private final Connection connection;
    private final IntSupplier currentUserId;

    public ExpenseRepository(Connection connection, IntSupplier currentUserId) {
        this.connection = connection;
        this.currentUserId = currentUserId;
    }

    public void add(Expense expense) throws SQLException {
        String sql = "INSERT INTO expenses "
                + "(user_id, expense_date, category, payment_method, description, amount) "
                + "VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, currentUserId.getAsInt());
            statement.setDate(2, Date.valueOf(expense.getDate()));
            statement.setString(3, expense.getCategory());
            statement.setString(4, expense.getPaymentMethod());
            statement.setString(5, expense.getDescription());
            statement.setBigDecimal(6, expense.getAmount());
            statement.executeUpdate();
        }
    }
}
