package com.rentals.service;

import com.rentals.dao.ReminderDAO;
import com.rentals.dao.TenantDAO;
import com.rentals.notifications.NotificationLog;
import com.rentals.tenants.Tenant;

import java.util.Locale;

/**
 * Delivers a queued notification and records the outcome.
 *
 * <p>The tenant phone number is used for SMS. WhatsApp prefers the tenant's
 * WhatsApp number and falls back to the normal phone. Email is not delivered
 * because no mail server is configured; those entries can still be marked as
 * sent manually.</p>
 */
public final class NotificationService {

    /** Outcome of one delivery attempt. */
    public record SendResult(
        boolean success,
        String channel,
        String recipient,
        String detail
    ) {}

    private NotificationService() {}

    public static SendResult send(NotificationLog log) {
        if (log == null || log.getNotificationId() == null) {
            return new SendResult(false, "", "", "No notification selected.");
        }

        ReminderDAO reminderDAO = new ReminderDAO();
        String channel = log.getChannel() == null
            ? "" : log.getChannel().trim().toUpperCase(Locale.ROOT);
        String message = log.getMessage() == null ? "" : log.getMessage().trim();

        if (message.isBlank()) {
            reminderDAO.markFailed(log.getNotificationId(), null, "The message is empty.");
            return new SendResult(false, channel, "", "The message is empty.");
        }

        Tenant tenant = new TenantDAO().findById(log.getTenantId());
        if (tenant == null) {
            reminderDAO.markFailed(log.getNotificationId(), null, "Tenant not found.");
            return new SendResult(false, channel, "", "The tenant no longer exists.");
        }

        String recipient;
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
            reminderDAO.markSent(log.getNotificationId(), recipient);
        } else {
            reminderDAO.markFailed(log.getNotificationId(), recipient, result.detail());
        }

        return new SendResult(result.success(), result.channel(), recipient, result.detail());
    }
}
