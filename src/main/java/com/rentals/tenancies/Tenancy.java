package com.rentals.tenancies;

public class Tenancy {
    private Long tenancyId;
    private Long tenantId;
    private Long roomId;
    private String tenantName;
    private String roomNumber;
    private String startDate;
    private String endDate;
    private double monthlyRent;
    private double deposit;
    private String status = "ACTIVE";

    public Tenancy() {}
    public Tenancy(Long tenantId, Long roomId, String startDate, double monthlyRent) {
        this.tenantId = tenantId; this.roomId = roomId; this.startDate = startDate; this.monthlyRent = monthlyRent;
    }

    public Long getTenancyId() { return tenancyId; }
    public void setTenancyId(Long tenancyId) { this.tenancyId = tenancyId; }
    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }
    public Long getRoomId() { return roomId; }
    public void setRoomId(Long roomId) { this.roomId = roomId; }
    public String getTenantName() { return tenantName; }
    public void setTenantName(String tenantName) { this.tenantName = tenantName; }
    public String getRoomNumber() { return roomNumber; }
    public void setRoomNumber(String roomNumber) { this.roomNumber = roomNumber; }
    public String getStartDate() { return startDate; }
    public void setStartDate(String startDate) { this.startDate = startDate; }
    public String getEndDate() { return endDate; }
    public void setEndDate(String endDate) { this.endDate = endDate; }
    public double getMonthlyRent() { return monthlyRent; }
    public void setMonthlyRent(double monthlyRent) { this.monthlyRent = monthlyRent; }
    public double getDeposit() { return deposit; }
    public void setDeposit(double deposit) { this.deposit = deposit; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
