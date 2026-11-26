package com.rentals.rent;

/**
 * Rent charge - the amount a tenancy owes for a given month.
 * Status: PENDING -> PARTIAL -> PAID
 */
public class RentCharge {

    private Long chargeId;
    private Long tenancyId;
    private Long tenantId;          // joined from the tenancy
    private String tenantName;      // joined for display
    private String roomNumber;      // joined for display
    private Double amount;
    private Double paidAmount = 0.0;
    private Double lateFeeApplied = 0.0;
    private String dueDate;
    private String status = "PENDING";
    private String periodMonth;

    public Long getChargeId() { return chargeId; }
    public void setChargeId(Long chargeId) { this.chargeId = chargeId; }
    public Long getTenancyId() { return tenancyId; }
    public void setTenancyId(Long tenancyId) { this.tenancyId = tenancyId; }
    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }
    public String getTenantName() { return tenantName; }
    public void setTenantName(String tenantName) { this.tenantName = tenantName; }
    public String getRoomNumber() { return roomNumber; }
    public void setRoomNumber(String roomNumber) { this.roomNumber = roomNumber; }
    public Double getAmount() { return amount; }
    public void setAmount(Double amount) { this.amount = amount; }
    public Double getPaidAmount() { return paidAmount; }
    public void setPaidAmount(Double paidAmount) { this.paidAmount = paidAmount; }
    public Double getLateFeeApplied() { return lateFeeApplied; }
    public void setLateFeeApplied(Double lateFeeApplied) { this.lateFeeApplied = lateFeeApplied; }
    public Double getBalance() {
        double paid = paidAmount == null ? 0 : paidAmount;
        return (amount == null ? 0 : amount) - paid;
    }
    public String getDueDate() { return dueDate; }
    public void setDueDate(String dueDate) { this.dueDate = dueDate; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getPeriodMonth() { return periodMonth; }
    public void setPeriodMonth(String periodMonth) { this.periodMonth = periodMonth; }
}
