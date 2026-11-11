package com.rentals.service;

import com.rentals.settings.AppSettings;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Locale;

/**
 * Sends SMS and WhatsApp messages through the gateway configured by the admin.
 *
 * <p>SMS providers: Africa's Talking, Twilio and a generic HTTP gateway.
 * WhatsApp uses the Meta WhatsApp Cloud API. All calls require an internet
 * connection and valid provider credentials; nothing is sent when the channel
 * is disabled.</p>
 *
 * <p>This class only talks to the provider. Recipient lookup, logging and
 * status updates are handled by {@link NotificationService}.</p>
 */
public final class MessagingService {

    /** Supported SMS gateways. */
    public enum SmsProvider {
        AFRICAS_TALKING("Africa's Talking"),
        TWILIO("Twilio"),
        GENERIC("Generic HTTP gateway");

        private final String label;

        SmsProvider(String label) { this.label = label; }

        public String label() { return label; }

        public static SmsProvider from(String value) {
            if (value != null) {
                for (SmsProvider provider : values()) {
                    if (provider.name().equalsIgnoreCase(value.trim())) return provider;
                }
            }
            return AFRICAS_TALKING;
        }

        @Override
        public String toString() { return label; }
    }

    /** Values needed to send an SMS. */
    public record SmsConfig(
        boolean enabled,
        SmsProvider provider,
        String username,
        String apiKey,
        String senderId,
        String fromNumber,
        String genericUrl,
        String countryCode
    ) {
        public static SmsConfig fromSettings() {
            return new SmsConfig(
                AppSettings.smsEnabled(),
                SmsProvider.from(AppSettings.smsProvider()),
                AppSettings.smsUsername(),
                AppSettings.smsApiKey(),
                AppSettings.smsSenderId(),
                AppSettings.smsFromNumber(),
                AppSettings.smsGenericUrl(),
                AppSettings.smsCountryCode());
        }

        public boolean isConfigured() {
            return switch (provider) {
                case AFRICAS_TALKING -> !blank(username) && !blank(apiKey);
                case TWILIO -> !blank(username) && !blank(apiKey) && !blank(fromNumber);
                case GENERIC -> !blank(genericUrl);
            };
        }
    }

    /** Values needed to send a WhatsApp Cloud API message. */
    public record WhatsAppConfig(
        boolean enabled,
        String apiUrl,
        String phoneNumberId,
        String accessToken,
        String businessNumber,
        String templateName,
        String templateLanguage
    ) {
        public static WhatsAppConfig fromSettings() {
            return new WhatsAppConfig(
                AppSettings.whatsappEnabled(),
                AppSettings.whatsappApiUrl(),
                AppSettings.whatsappPhoneNumberId(),
                AppSettings.whatsappAccessToken(),
                AppSettings.whatsappBusinessNumber(),
                AppSettings.whatsappTemplateName(),
                AppSettings.whatsappTemplateLanguage());
        }

        public boolean isConfigured() {
            return !blank(phoneNumberId) && !blank(accessToken);
        }
    }

    /** Result of one delivery attempt. */
    public record Result(boolean success, String channel, String detail) {}

    private static final Duration TIMEOUT = Duration.ofSeconds(20);
    private static final HttpClient CLIENT = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(15))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build();

    private MessagingService() {}

    /* ==================== SMS ==================== */

    public static Result sendSms(SmsConfig config, String to, String message) {
        if (config == null) return fail("SMS", "SMS is not configured.");
        if (!config.enabled()) {
            return fail("SMS", "SMS is disabled. Enable it in Settings > Communication.");
        }
        if (!config.isConfigured()) {
            return fail("SMS", "SMS credentials are incomplete. Open Settings > Communication.");
        }

        String phone = normalizePhone(to, config.countryCode());
        if (phone.isBlank()) return fail("SMS", "The recipient has no phone number.");
        String text = message == null ? "" : message.trim();
        if (text.isBlank()) return fail("SMS", "The message is empty.");

        try {
            return switch (config.provider()) {
                case AFRICAS_TALKING -> sendAfricasTalking(config, phone, text);
                case TWILIO -> sendTwilio(config, phone, text);
                case GENERIC -> sendGeneric(config, phone, text);
            };
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return fail("SMS", "SMS request interrupted.");
        } catch (Exception e) {
            return fail("SMS", "SMS error: " + e.getMessage());
        }
    }

    private static Result sendAfricasTalking(SmsConfig config, String phone, String message)
            throws Exception {
        StringBuilder form = new StringBuilder()
            .append("username=").append(encode(config.username()))
            .append("&to=").append(encode(phone))
            .append("&message=").append(encode(message));
        if (!blank(config.senderId())) {
            form.append("&from=").append(encode(config.senderId()));
        }

        HttpRequest request = HttpRequest.newBuilder(
                URI.create("https://api.africastalking.com/version1/messaging"))
            .header("apiKey", config.apiKey())
            .header("Accept", "application/json")
            .header("Content-Type", "application/x-www-form-urlencoded")
            .timeout(TIMEOUT)
            .POST(HttpRequest.BodyPublishers.ofString(form.toString(), StandardCharsets.UTF_8))
            .build();

        return execute(request, "SMS (Africa's Talking)");
    }

    private static Result sendTwilio(SmsConfig config, String phone, String message)
            throws Exception {
        String accountSid = config.username().trim();
        String url = "https://api.twilio.com/2010-04-01/Accounts/"
            + encodePath(accountSid) + "/Messages.json";

        String form = "To=" + encode(phone)
            + "&From=" + encode(config.fromNumber())
            + "&Body=" + encode(message);

        String basic = Base64.getEncoder().encodeToString(
            (accountSid + ":" + config.apiKey()).getBytes(StandardCharsets.UTF_8));

        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
            .header("Authorization", "Basic " + basic)
            .header("Content-Type", "application/x-www-form-urlencoded")
            .timeout(TIMEOUT)
            .POST(HttpRequest.BodyPublishers.ofString(form, StandardCharsets.UTF_8))
            .build();

        return execute(request, "SMS (Twilio)");
    }

    private static Result sendGeneric(SmsConfig config, String phone, String message)
            throws Exception {
        String url = config.genericUrl().trim();
        HttpRequest.Builder builder;

        if (url.contains("{to}") || url.contains("{message}")) {
            String resolved = url
                .replace("{to}", encode(phone))
                .replace("{message}", encode(message));
            builder = HttpRequest.newBuilder(URI.create(resolved)).GET();
        } else {
            String json = "{\"to\":\"" + json(phone) + "\","
                + "\"message\":\"" + json(message) + "\","
                + "\"sender\":\"" + json(config.senderId()) + "\"}";
            builder = HttpRequest.newBuilder(URI.create(url))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8));
        }

        if (!blank(config.apiKey())) {
            builder.header("Authorization", "Bearer " + config.apiKey().trim());
        }
        builder.header("Accept", "application/json");
        builder.timeout(TIMEOUT);

        return execute(builder.build(), "SMS (generic gateway)");
    }

    /* ==================== WhatsApp ==================== */

    public static Result sendWhatsApp(WhatsAppConfig config, String to, String message) {
        if (config == null) return fail("WhatsApp", "WhatsApp is not configured.");
        if (!config.enabled()) {
            return fail("WhatsApp", "WhatsApp is disabled. Enable it in Settings > Communication.");
        }
        if (!config.isConfigured()) {
            return fail("WhatsApp", "WhatsApp credentials are incomplete. Open Settings > Communication.");
        }

        String phone = normalizePhone(to, AppSettings.smsCountryCode());
        if (phone.isBlank()) return fail("WhatsApp", "The recipient has no phone number.");
        String text = message == null ? "" : message.trim();
        if (text.isBlank()) return fail("WhatsApp", "The message is empty.");

        try {
            String base = config.apiUrl() == null || config.apiUrl().isBlank()
                ? "https://graph.facebook.com/v20.0"
                : config.apiUrl().trim();
            while (base.endsWith("/")) base = base.substring(0, base.length() - 1);

            String url = base + "/" + encodePath(config.phoneNumberId().trim()) + "/messages";
            String body;

            if (!blank(config.templateName())) {
                // A pre-approved template with one body variable ({{1}})
                body = "{\"messaging_product\":\"whatsapp\","
                    + "\"to\":\"" + json(phone) + "\","
                    + "\"type\":\"template\","
                    + "\"template\":{"
                    + "\"name\":\"" + json(config.templateName().trim()) + "\","
                    + "\"language\":{\"code\":\"" + json(config.templateLanguage().trim()) + "\"},"
                    + "\"components\":[{\"type\":\"body\",\"parameters\":["
                    + "{\"type\":\"text\",\"text\":\"" + json(text) + "\"}]}]}}";
            } else {
                // Plain text works only inside Meta's 24-hour customer service window.
                body = "{\"messaging_product\":\"whatsapp\","
                    + "\"recipient_type\":\"individual\","
                    + "\"to\":\"" + json(phone) + "\","
                    + "\"type\":\"text\","
                    + "\"text\":{\"preview_url\":false,\"body\":\"" + json(text) + "\"}}";
            }

            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .header("Authorization", "Bearer " + config.accessToken().trim())
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .timeout(TIMEOUT)
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();

            return execute(request, "WhatsApp");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return fail("WhatsApp", "WhatsApp request interrupted.");
        } catch (Exception e) {
            return fail("WhatsApp", "WhatsApp error: " + e.getMessage());
        }
    }

    /* ==================== HELPERS ==================== */

    /** Normalises a phone number to +countrycode form where possible. */
    public static String normalizePhone(String raw, String countryCode) {
        if (raw == null) return "";
        String cleaned = raw.trim().replaceAll("[^0-9+]", "");
        if (cleaned.isBlank()) return "";

        if (cleaned.startsWith("00")) {
            cleaned = "+" + cleaned.substring(2);
        }
        if (cleaned.startsWith("+")) {
            String digits = cleaned.substring(1).replaceAll("[^0-9]", "");
            return digits.isBlank() ? "" : "+" + digits;
        }

        String digits = cleaned.replaceAll("[^0-9]", "");
        if (digits.isBlank()) return "";
        String cc = countryCode == null ? "" : countryCode.replaceAll("[^0-9]", "");
        if (digits.startsWith("0") && !cc.isBlank()) {
            return "+" + cc + digits.substring(1);
        }
        if (digits.length() >= 10) return "+" + digits;
        return digits;
    }

    private static Result execute(HttpRequest request, String channel) throws Exception {
        HttpResponse<String> response = CLIENT.send(request,
            HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        int code = response.statusCode();
        if (code >= 200 && code < 300) {
            return new Result(true, channel, "HTTP " + code);
        }
        return new Result(false, channel,
            "HTTP " + code + ": " + truncate(response.body(), 300));
    }

    private static Result fail(String channel, String detail) {
        return new Result(false, channel, detail);
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    private static String encodePath(String value) {
        return encode(value).replace("+", "%20");
    }

    private static String truncate(String value, int max) {
        if (value == null) return "";
        String singleLine = value.replaceAll("\\s+", " ").trim();
        return singleLine.length() <= max ? singleLine : singleLine.substring(0, max) + "...";
    }

    private static String json(String value) {
        if (value == null) return "";
        StringBuilder sb = new StringBuilder(value.length() + 8);
        for (char c : value.toCharArray()) {
            switch (c) {
                case '\\' -> sb.append("\\\\");
                case '"' -> sb.append("\\\"");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) sb.append(String.format(Locale.ROOT, "\\u%04x", (int) c));
                    else sb.append(c);
                }
            }
        }
        return sb.toString();
    }
}
