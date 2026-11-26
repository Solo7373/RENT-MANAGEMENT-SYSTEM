package com.rentals.service;

import com.rentals.communications.CommunicationLog;
import com.rentals.dao.CommunicationLogDAO;
import com.rentals.dao.PaymentDAO;
import com.rentals.dao.ReminderDAO;
import com.rentals.dao.TenantDAO;
import com.rentals.notifications.NotificationLog;
import com.rentals.payments.Payment;
import com.rentals.settings.AppSettings;
import com.rentals.tenants.Tenant;
import com.rentals.util.FormatUtils;

import java.time.LocalDate;
import java.util.Locale;

/**
 * Delivers a queued notification and records the outcome.
 *
 * <p>The tenant phone number is used for SMS. WhatsApp prefers the tenant's
 * WhatsApp number and falls back to the normal phone. Email is not delivered
 * because no mail server is configured; those entries can still be marked as
 * sent manually.</p>
 *
 * <p>When auto-tracking is enabled, every attempt is also written to
 * {@code communication_logs}, which is the per-tenant communication timeline.
 * Provider message IDs and statuses are stored on both records. Failures clear
 * the dedupe key on the reminder so a later scheduler run can retry it.</p>
 */
public final class NotificationService {

    /** Outcome of one delivery attempt. */
    public record SendResult(
        boolean success,
        String channel,
        String recipient,
        String detail
    ) {}

    private static final ReminderDAO reminderDAO = new ReminderDAO();
    private static final CommunicationLogDAO communicationDAO = new CommunicationLogDAO();

    private NotificationService() {}

    public static SendResult send(NotificationLog log) {
        if (log == null || log.getNotificationId() == null) {
            return new SendResult(false, "", "", "No notification selected.");
        }

        String channel = log.getChannel() == null
            ? "" : log.getChannel().trim().toUpperCase(Locale.ROOT);
        String message = log.getMessage() == null ? "" : log.getMessage().trim();
        String eventType = log.getEventType() == null || log.getEventType().isBlank()
            ? "REMINDER" : log.getEventType();

        Tenant tenant = new TenantDAO().findById(log.getTenantId());
        if (tenant == null) {
            reminderDAO.markFailed(log.getNotificationId(), null, "Tenant not found.");
            return new SendResult(false, channel, "", "The tenant no longer exists.");
        }

        if (message.isBlank()) {
            String detail = "The message is empty.";
            reminderDAO.markFailed(log.getNotificationId(), null, detail);
            recordOutcome(log, eventType, channel, "", message, null, detail, true);
            return new SendResult(false, channel, "", detail);
        }

        String recipient;
        String provider = providerLabel(channel);
        MessagingService.Result result;

        switch (channel) {
            case "SMS" -> {
                recipient = tenant.getPhone();
                if (recipient == null || recipient.isBlank()) {
                    result = new MessagingService.Result(false, "SMS",
                        "The tenant has no phone number.");
                } else {
                    result = MessagingService.sendSms(
                        MessagingService.SmsConfig.fromSettings(), recipient, message);
                    provider = smsProviderLabel();
                }
            }
            case "WHATSAPP" -> {
                recipient = tenant.getWhatsappNumber();
                if (recipient == null || recipient.isBlank()) recipient = tenant.getPhone();
                if (recipient == null || recipient.isBlank()) {
                    result = new MessagingService.Result(false, "WhatsApp",
                        "The tenant has no WhatsApp or phone number.");
                } else {
                    result = MessagingService.sendWhatsApp(
                        MessagingService.WhatsAppConfig.fromSettings(), recipient, message);
                    provider = "Meta WhatsApp Cloud API";
                }
            }
            case "EMAIL" -> {
                recipient = tenant.getEmail();
                result = new MessagingService.Result(false, "Email",
                    "Email delivery is not configured. Use SMS/WhatsApp or mark it sent manually.");
            }
            default -> {
                recipient = "";
                result = new MessagingService.Result(false, channel,
                    "Unknown channel '" + channel + "'.");
            }
        }

        if (result.success()) {
            reminderDAO.markSent(log.getNotificationId(), recipient, provider,
                result.providerMessageId(), result.providerStatus());
        } else {
            reminderDAO.markFailed(log.getNotificationId(), recipient, result.detail(),
                provider, result.providerMessageId(), result.providerStatus());
        }

        recordOutcome(log, eventType, channel, recipient, message, result, null, false);

        return new SendResult(result.success(), result.channel(), recipient, result.detail());
    }

    /**
     * Sends a payment receipt SMS to the tenant and records it in the tenant's
     * communication history. Used after a payment has been verified.
     */
    public static SendResult sendReceipt(Payment payment) {
        if (payment == null || payment.getPaymentId() == null) {
            return new SendResult(false, "SMS", "",
                "Select a saved payment first.");
        }

        // Re-read the payment so a stale table row cannot send a receipt for
        // a payment that is still pending or was deleted.
        Payment saved = new PaymentDAO().findById(payment.getPaymentId());
        if (saved == null) {
            return new SendResult(false, "SMS", "", "The payment no longer exists.");
        }
        payment = saved;
        if (!"VERIFIED".equalsIgnoreCase(payment.getStatus())) {
            return new SendResult(false, "SMS", "",
                "Verify the payment before sending a receipt SMS.");
        }

        Tenant tenant = new TenantDAO().findById(payment.getTenantId());
        if (tenant == null) {
            return new SendResult(false, "SMS", "", "The tenant no longer exists.");
        }

        String recipient = tenant.getPhone();
        String receiptNo = ReceiptService.receiptNumber(payment);
        String date = payment.getPaymentDate() == null
            ? LocalDate.now().toString() : payment.getPaymentDate();
        String message = AppSettings.companyName() + ": payment of "
            + FormatUtils.money(payment.getAmount()) + " received from "
            + tenant.getFullName() + " on " + date
            + ". Receipt " + receiptNo + ". Thank you.";
        String provider = smsProviderLabel();

        MessagingService.Result result;
        if (recipient == null || recipient.isBlank()) {
            result = new MessagingService.Result(false, "SMS",
                "The tenant has no phone number.");
        } else {
            result = MessagingService.sendSms(
                MessagingService.SmsConfig.fromSettings(), recipient, message);
        }

        // A manual receipt is always recorded, even when automatic reminder
        // tracking is switched off, so failed attempts remain visible.
        CommunicationLog entry = new CommunicationLog();
        entry.setTenantId(tenant.getTenantId());
        entry.setPaymentId(payment.getPaymentId());
        entry.setEventType("RECEIPT");
        entry.setChannel("SMS");
        entry.setRecipient(recipient);
        entry.setMessage(message);
        entry.setStatus(result.success() ? "SENT" : "FAILED");
        entry.setProvider(provider);
        entry.setProviderMessageId(result.providerMessageId());
        entry.setProviderStatus(result.providerStatus());
        entry.setErrorMessage(result.success() ? null : result.detail());
        communicationDAO.create(entry);

        if (!result.success()) {
            System.err.println("Receipt SMS for payment #" + payment.getPaymentId()
                + " failed: " + result.detail());
        }

        return new SendResult(result.success(), result.channel(), recipient, result.detail());
    }

    /**
     * Records an explicit test message (for example from the setup wizard).
     * Test messages are not linked to a tenant but still appear in the recent
     * communication log for troubleshooting.
     */
    public static void recordTestMessage(String channel, String recipient, String message,
                                         MessagingService.Result result) {
        if (result == null) return;
        CommunicationLog entry = new CommunicationLog();
        entry.setEventType("TEST");
        entry.setChannel(channel);
        entry.setRecipient(recipient);
        entry.setMessage(message);
        entry.setStatus(result.success() ? "SENT" : "FAILED");
        entry.setProvider(providerLabel(channel));
        entry.setProviderMessageId(result.providerMessageId());
        entry.setProviderStatus(result.providerStatus());
        entry.setErrorMessage(result.success() ? null : result.detail());
        communicationDAO.create(entry);
    }

    /* ---------- helpers ---------- */

    private static void recordOutcome(NotificationLog log, String eventType, String channel,
                                      String recipient, String message,
                                      MessagingService.Result result, String error,
                                      boolean force) {
        if (!force && !AppSettings.autoTrackEnabled()) return;

        CommunicationLog entry = new CommunicationLog();
        entry.setTenantId(log.getTenantId());
        entry.setTenancyId(log.getTenancyId());
        entry.setChargeId(log.getChargeId());
        entry.setPaymentId(log.getPaymentId());
        entry.setNotificationId(log.getNotificationId());
        entry.setEventType(eventType);
        entry.setChannel(channel);
        entry.setRecipient(recipient);
        entry.setMessage(message);
        entry.setStatus(result != null && result.success() ? "SENT" : "FAILED");
        entry.setProvider(providerLabel(channel));
        if (result != null) {
            entry.setProviderMessageId(result.providerMessageId());
            entry.setProviderStatus(result.providerStatus());
            entry.setErrorMessage(result.success() ? null : result.detail());
        } else {
            entry.setErrorMessage(error);
        }
        communicationDAO.create(entry);
    }

    private static String providerLabel(String channel) {
        String normalised = channel == null ? "" : channel.toUpperCase(Locale.ROOT);
        return switch (normalised) {
            case "SMS" -> smsProviderLabel();
            case "WHATSAPP" -> "Meta WhatsApp Cloud API";
            case "EMAIL" -> "Email (not configured)";
            default -> channel == null ? "" : channel;
        };
    }

    private static String smsProviderLabel() {
        return switch (MessagingService.SmsProvider.from(AppSettings.smsProvider())) {
            case AFRICAS_TALKING -> "Africa's Talking "
                + MessagingService.Environment.from(AppSettings.smsEnvironment()).label();
            case TWILIO -> "Twilio";
            case GENERIC -> "Generic HTTP gateway";
        };
    }
}
