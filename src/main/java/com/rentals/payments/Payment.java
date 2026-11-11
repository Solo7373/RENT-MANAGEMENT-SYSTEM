package com.rentals.payments;

/**
 * Payment entity - money paid by a tenant.
 */
public class Payment {

    private Long paymentId;
    private Long tenantId;
    private String tenantName;      // joined from tenants table for display
    private Double amount;
    private String paymentMethod;
    private String referenceNumber;
    private String status = "PENDING";
    private String notes;
    private String paymentDate;
    private String createdAt;

    public Payment() {}

    public Payment(Long tenantId, Double amount, String paymentMethod) {
        this.tenantId = tenantId;
        this.amount = amount;
        this.paymentMethod = paymentMethod;
    }

    public Long getPaymentId() { return paymentId; }
    public void setPaymentId(Long paymentId) { this.paymentId = paymentId; }
    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }
    public String getTenantName() { return tenantName; }
    public void setTenantName(String tenantName) { this.tenantName = tenantName; }
    public Double getAmount() { return amount; }
    public void setAmount(Double amount) { this.amount = amount; }
    public String getPaymentMethod() { return paymentMethod; }
    public void setPaymentMethod(String paymentMethod) { this.paymentMethod = paymentMethod; }
    public String getReferenceNumber() { return referenceNumber; }
    public void setReferenceNumber(String referenceNumber) { this.referenceNumber = referenceNumber; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public String getPaymentDate() { return paymentDate; }
    public void setPaymentDate(String paymentDate) { this.paymentDate = paymentDate; }
    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
}
