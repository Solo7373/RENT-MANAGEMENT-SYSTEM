package com.rentals.properties;

public class Property {
    private Long propertyId;
    private String name;
    private String address;
    private String description;
    private String status = "ACTIVE";

    public Property() {}
    public Property(String name, String address, String description) {
        this.name = name; this.address = address; this.description = description;
    }

    public Long getPropertyId() { return propertyId; }
    public void setPropertyId(Long propertyId) { this.propertyId = propertyId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
