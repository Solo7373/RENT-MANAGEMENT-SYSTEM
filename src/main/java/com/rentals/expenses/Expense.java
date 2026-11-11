package com.rentals.expenses;

/**
 * Expense - money spent on a property (repairs, utilities, etc.).
 */
public class Expense {

    private Long expenseId;
    private Long propertyId;
    private String propertyName;   // joined for display
    private String description;
    private Double amount;
    private String expenseDate;
    private String category;

    public Expense() {}

    public Expense(Long propertyId, String description, Double amount, String category) {
        this.propertyId = propertyId;
        this.description = description;
        this.amount = amount;
        this.category = category;
    }

    public Long getExpenseId() { return expenseId; }
    public void setExpenseId(Long expenseId) { this.expenseId = expenseId; }
    public Long getPropertyId() { return propertyId; }
    public void setPropertyId(Long propertyId) { this.propertyId = propertyId; }
    public String getPropertyName() { return propertyName; }
    public void setPropertyName(String propertyName) { this.propertyName = propertyName; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Double getAmount() { return amount; }
    public void setAmount(Double amount) { this.amount = amount; }
    public String getExpenseDate() { return expenseDate; }
    public void setExpenseDate(String expenseDate) { this.expenseDate = expenseDate; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
}
