package com.rentals.communications;

/**
 * One entry in a tenant's communication timeline.
 *
 * <p>Rows are written for outbound reminders, receipts, late-fee notices and
 * lease-expiry alerts. The provider message ID is stored so future delivery
 * callbacks (from a hosted relay) can be matched to the original message.</p>
 */
public class CommunicationLog {

    private Long communicationId;
    private Long tenantId;
    private Long tenancyId;
    private Long chargeId;
    private Long paymentId;
    private Long notificationId;
    private String tenantName;
    private String eventType = "REMINDER";
    private String channel;
    private String direction = "OUTBOUND";
    private String recipient;
    private String message;
    private String status;
    private String provider;
    private String providerMessageId;
    private String providerStatus;
    private String errorMessage;
    private String createdAt;
    private String updatedAt;

    public Long getCommunicationId() { return communicationId; }
    public void setCommunicationId(Long communicationId) { this.communicationId = communicationId; }
    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }
    public Long getTenancyId() { return tenancyId; }
    public void setTenancyId(Long tenancyId) { this.tenancyId = tenancyId; }
    public Long getChargeId() { return chargeId; }
    public void setChargeId(Long chargeId) { this.chargeId = chargeId; }
    public Long getPaymentId() { return paymentId; }
    public void setPaymentId(Long paymentId) { this.paymentId = paymentId; }
    public Long getNotificationId() { return notificationId; }
    public void setNotificationId(Long notificationId) { this.notificationId = notificationId; }
    public String getTenantName() { return tenantName; }
    public void setTenantName(String tenantName) { this.tenantName = tenantName; }
    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }
    public String getChannel() { return channel; }
    public void setChannel(String channel) { this.channel = channel; }
    public String getDirection() { return direction; }
    public void setDirection(String direction) { this.direction = direction; }
    public String getRecipient() { return recipient; }
    public void setRecipient(String recipient) { this.recipient = recipient; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }
    public String getProviderMessageId() { return providerMessageId; }
    public void setProviderMessageId(String providerMessageId) { this.providerMessageId = providerMessageId; }
    public String getProviderStatus() { return providerStatus; }
    public void setProviderStatus(String providerStatus) { this.providerStatus = providerStatus; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
    public String getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }
}
