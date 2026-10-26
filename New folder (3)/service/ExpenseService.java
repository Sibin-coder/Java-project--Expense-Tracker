package service;

import model.Expense;
import repository.ExpenseRepository;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;

public class ExpenseService {
    private final ExpenseRepository repository;

    public ExpenseService(ExpenseRepository repository) {
        this.repository = repository;
    }

    public void addExpense(BigDecimal amount, LocalDate date, String category,
                           String paymentMethod, String description) throws SQLException {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Amount must be greater than zero.");
        }
        if (date.isAfter(LocalDate.now())) {
            throw new IllegalArgumentException("Expense date cannot be in the future.");
        }
        repository.add(new Expense(0, date, category, paymentMethod, description, amount));
    }
}
