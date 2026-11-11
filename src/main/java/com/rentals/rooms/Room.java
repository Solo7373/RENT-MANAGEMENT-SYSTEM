package com.rentals.rooms;

public class Room {
    private Long roomId;
    private Long propertyId;
    private String roomNumber;
    private String roomType;
    private double rentAmount;
    private String status = "VACANT";
    private String conditionNotes;

    public Room() {}
    public Room(Long propertyId, String roomNumber, String roomType, double rentAmount) {
        this.propertyId = propertyId;
        this.roomNumber = roomNumber;
        this.roomType = roomType;
        this.rentAmount = rentAmount;
    }

    public Long getRoomId() { return roomId; }
    public void setRoomId(Long roomId) { this.roomId = roomId; }
    public Long getPropertyId() { return propertyId; }
    public void setPropertyId(Long propertyId) { this.propertyId = propertyId; }
    public String getRoomNumber() { return roomNumber; }
    public void setRoomNumber(String roomNumber) { this.roomNumber = roomNumber; }
    public String getRoomType() { return roomType; }
    public void setRoomType(String roomType) { this.roomType = roomType; }
    public double getRentAmount() { return rentAmount; }
    public void setRentAmount(double rentAmount) { this.rentAmount = rentAmount; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getConditionNotes() { return conditionNotes; }
    public void setConditionNotes(String conditionNotes) { this.conditionNotes = conditionNotes; }
}
