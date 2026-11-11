package com.rentals.payments;

/**
 * Payment method - e.g. MTN Mobile Money, Cash, Bank Transfer.
 */
public class PaymentMethod {

    private Long methodId;
    private String name;
    private String type;
    private String accountNumber;
    private String instructions;
    private boolean enabled;

    public Long getMethodId() { return methodId; }
    public void setMethodId(Long methodId) { this.methodId = methodId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getAccountNumber() { return accountNumber; }
    public void setAccountNumber(String accountNumber) { this.accountNumber = accountNumber; }
    public String getInstructions() { return instructions; }
    public void setInstructions(String instructions) { this.instructions = instructions; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
}
