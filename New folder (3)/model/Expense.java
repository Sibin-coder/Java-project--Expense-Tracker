package model;

import java.math.BigDecimal;
import java.time.LocalDate;

public class Expense {
    private final int id;
    private final LocalDate date;
    private final String category;
    private final String paymentMethod;
    private final String description;
    private final BigDecimal amount;

    public Expense(int id, LocalDate date, String category, String paymentMethod,
                   String description, BigDecimal amount) {
        this.id = id;
        this.date = date;
        this.category = category;
        this.paymentMethod = paymentMethod;
        this.description = description;
        this.amount = amount;
    }

    public int getId() {
        return id;
    }

    public LocalDate getDate() {
        return date;
    }

    public String getCategory() {
        return category;
    }

    public String getPaymentMethod() {
        return paymentMethod;
    }

    public String getDescription() {
        return description;
    }

    public BigDecimal getAmount() {
        return amount;
    }
}
