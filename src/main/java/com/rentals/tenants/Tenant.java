package com.rentals.tenants;

/**
 * Tenant entity with document and credential fields.
 * Supports National ID, School ID, Refugee ID and other documents.
 */
public class Tenant {

    private Long tenantId;
    private String fullName;
    private String phone;
    private String whatsappNumber;
    private String email;
    private String idNumber;           // National ID
    private String schoolId;           // School ID (for students)
    private String refugeeId;          // Refugee ID
    private String emergencyContact;
    private String emergencyPhone;
    private String roomNumber;         // Assigned room number
    private Double rentAmount;         // Monthly rent/pricing
    private String notes;
    private String status = "ACTIVE";

    public Tenant() {}
    public Tenant(String fullName, String phone) {
        this.fullName = fullName;
        this.phone = phone;
    }

    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }
    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getWhatsappNumber() { return whatsappNumber; }
    public void setWhatsappNumber(String whatsappNumber) { this.whatsappNumber = whatsappNumber; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getIdNumber() { return idNumber; }
    public void setIdNumber(String idNumber) { this.idNumber = idNumber; }
    public String getSchoolId() { return schoolId; }
    public void setSchoolId(String schoolId) { this.schoolId = schoolId; }
    public String getRefugeeId() { return refugeeId; }
    public void setRefugeeId(String refugeeId) { this.refugeeId = refugeeId; }
    public String getEmergencyContact() { return emergencyContact; }
    public void setEmergencyContact(String emergencyContact) { this.emergencyContact = emergencyContact; }
    public String getEmergencyPhone() { return emergencyPhone; }
    public void setEmergencyPhone(String emergencyPhone) { this.emergencyPhone = emergencyPhone; }
    public String getRoomNumber() { return roomNumber; }
    public void setRoomNumber(String roomNumber) { this.roomNumber = roomNumber; }
    public Double getRentAmount() { return rentAmount; }
    public void setRentAmount(Double rentAmount) { this.rentAmount = rentAmount; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
