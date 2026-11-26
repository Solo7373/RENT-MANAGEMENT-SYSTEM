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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Sends SMS and WhatsApp messages through the gateway configured by the admin.
 *
 * <p>SMS providers: Africa's Talking, Twilio and a generic HTTP gateway.
 * WhatsApp uses the Meta WhatsApp Cloud API. All calls require an internet
 * connection and valid provider credentials; nothing is sent when the channel
 * is disabled.</p>
 *
 * <p>Africa's Talking has a sandbox and a production environment. The sandbox
 * uses a different host and only delivers to numbers that were added to the
 * sandbox app in the Africa's Talking dashboard.</p>
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

    /** Africa's Talking environment. */
    public enum Environment {
        SANDBOX("Sandbox", "https://api.sandbox.africastalking.com/version1"),
        PRODUCTION("Production", "https://api.africastalking.com/version1");

        private final String label;
        private final String apiBase;

        Environment(String label, String apiBase) {
            this.label = label;
            this.apiBase = apiBase;
        }

        public String label() { return label; }
        public String apiBase() { return apiBase; }

        public static Environment from(String value) {
            if (value != null && "SANDBOX".equalsIgnoreCase(value.trim())) return SANDBOX;
            return PRODUCTION;
        }

        @Override
        public String toString() { return label; }
    }

    /** Values needed to send an SMS. */
    public record SmsConfig(
        boolean enabled,
        SmsProvider provider,
        Environment environment,
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
                Environment.from(AppSettings.smsEnvironment()),
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

    /**
     * Result of one delivery attempt.
     *
     * <p>{@code providerMessageId} is the Africa's Talking message ID, Twilio
     * message SID or generic provider ID when the gateway returns one. It is
     * stored so future delivery callbacks can be matched to the message.</p>
     */
    public record Result(
        boolean success,
        String channel,
        String detail,
        String providerMessageId,
        String providerStatus
    ) {
        public Result(boolean success, String channel, String detail) {
            this(success, channel, detail, "", "");
        }

        public Result {
            providerMessageId = providerMessageId == null ? "" : providerMessageId;
            providerStatus = providerStatus == null ? "" : providerStatus;
        }
    }

    /** Result of a credential/connectivity check (does not send a message). */
    public record TestResult(boolean success, String detail) {}

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
                URI.create(config.environment().apiBase() + "/messaging"))
            .header("apiKey", config.apiKey())
            .header("Accept", "application/json")
            .header("Content-Type", "application/x-www-form-urlencoded")
            .timeout(TIMEOUT)
            .POST(HttpRequest.BodyPublishers.ofString(form.toString(), StandardCharsets.UTF_8))
            .build();

        HttpResponse<String> response = send(request);
        int code = response.statusCode();
        String body = response.body();

        if (code < 200 || code >= 300) {
            return new Result(false, "SMS (Africa's Talking)",
                "HTTP " + code + ": " + truncate(body, 300), "", "");
        }

        String messageId = jsonField(body, "messageId");
        String status = jsonField(body, "status");
        Integer statusCode = jsonInt(body, "statusCode");
        String summary = jsonField(body, "Message");

        boolean accepted = statusCode == null || (statusCode >= 100 && statusCode < 200);
        if (!blank(status) && !"Success".equalsIgnoreCase(status)) accepted = false;
        // A 2xx body without a recipient status or message ID did not accept a message.
        if (messageId.isBlank() && status.isBlank()) accepted = false;

        if (!accepted) {
            return new Result(false, "SMS (Africa's Talking)",
                "Africa's Talking rejected the message"
                    + (status.isBlank() ? "" : ": " + status)
                    + (summary.isBlank() ? "" : " (" + summary + ")"),
                messageId, status);
        }

        return new Result(true, "SMS (Africa's Talking)",
            "HTTP " + code + (status.isBlank() ? "" : " \u2022 " + status)
                + (messageId.isBlank() ? "" : " \u2022 ID " + messageId),
            messageId, status);
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
            .header("Accept", "application/json")
            .timeout(TIMEOUT)
            .POST(HttpRequest.BodyPublishers.ofString(form, StandardCharsets.UTF_8))
            .build();

        HttpResponse<String> response = send(request);
        int code = response.statusCode();
        String body = response.body();

        if (code < 200 || code >= 300) {
            return new Result(false, "SMS (Twilio)",
                "HTTP " + code + ": " + truncate(body, 300), "", "");
        }

        String messageId = jsonField(body, "sid");
        String status = jsonField(body, "status");
        return new Result(true, "SMS (Twilio)",
            "HTTP " + code + (status.isBlank() ? "" : " \u2022 " + status),
            messageId, status);
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

        HttpResponse<String> response = send(builder.build());
        int code = response.statusCode();
        String body = response.body();

        if (code < 200 || code >= 300) {
            return new Result(false, "SMS (generic gateway)",
                "HTTP " + code + ": " + truncate(body, 300), "", "");
        }

        String messageId = firstNonBlank(jsonField(body, "messageId"), jsonField(body, "id"));
        String status = firstNonBlank(jsonField(body, "status"), "accepted");
        return new Result(true, "SMS (generic gateway)",
            "HTTP " + code + (messageId.isBlank() ? "" : " \u2022 ID " + messageId),
            messageId, status);
    }

    /* ==================== TEST CONNECTION ==================== */

    /**
     * Checks the saved credentials without sending an SMS. Africa's Talking
     * returns the account balance, Twilio returns the account status and a
     * generic gateway is checked for reachability only.
     */
    public static TestResult testConnection() {
        return testConnection(SmsConfig.fromSettings());
    }

    public static TestResult testConnection(SmsConfig config) {
        if (config == null) return new TestResult(false, "SMS is not configured.");
        if (!config.isConfigured()) {
            return new TestResult(false,
                "SMS credentials are incomplete. Fill in the provider details first.");
        }

        try {
            return switch (config.provider()) {
                case AFRICAS_TALKING -> testAfricasTalking(config);
                case TWILIO -> testTwilio(config);
                case GENERIC -> testGeneric(config);
            };
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new TestResult(false, "The connection test was interrupted.");
        } catch (Exception e) {
            return new TestResult(false, "Connection error: " + e.getMessage());
        }
    }

    private static TestResult testAfricasTalking(SmsConfig config) throws Exception {
        String url = config.environment().apiBase()
            + "/user?username=" + encode(config.username());
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
            .header("apiKey", config.apiKey())
            .header("Accept", "application/json")
            .timeout(TIMEOUT)
            .GET()
            .build();

        HttpResponse<String> response = send(request);
        int code = response.statusCode();
        String body = response.body();
        if (code < 200 || code >= 300) {
            return new TestResult(false,
                "HTTP " + code + ": " + truncate(body, 300));
        }

        String balance = jsonField(body, "balance");
        return new TestResult(true, "Connected to Africa's Talking ("
            + config.environment().label() + ")"
            + (balance.isBlank() ? "." : " \u2022 balance " + balance));
    }

    private static TestResult testTwilio(SmsConfig config) throws Exception {
        String accountSid = config.username().trim();
        String url = "https://api.twilio.com/2010-04-01/Accounts/"
            + encodePath(accountSid) + ".json";
        String basic = Base64.getEncoder().encodeToString(
            (accountSid + ":" + config.apiKey()).getBytes(StandardCharsets.UTF_8));

        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
            .header("Authorization", "Basic " + basic)
            .header("Accept", "application/json")
            .timeout(TIMEOUT)
            .GET()
            .build();

        HttpResponse<String> response = send(request);
        int code = response.statusCode();
        String body = response.body();
        if (code < 200 || code >= 300) {
            return new TestResult(false,
                "HTTP " + code + ": " + truncate(body, 300));
        }

        String name = jsonField(body, "friendly_name");
        String status = jsonField(body, "status");
        return new TestResult(true, "Connected to Twilio"
            + (name.isBlank() ? "" : " \u2022 " + name)
            + (status.isBlank() ? "." : " \u2022 account " + status));
    }

    private static TestResult testGeneric(SmsConfig config) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(config.genericUrl().trim()))
            .method("HEAD", HttpRequest.BodyPublishers.noBody())
            .timeout(TIMEOUT)
            .build();

        HttpResponse<Void> response = CLIENT.send(request, HttpResponse.BodyHandlers.discarding());
        return new TestResult(true, "Gateway host reached (HTTP " + response.statusCode()
            + "). Generic gateways do not expose a standard balance check; use Send Test SMS "
            + "to confirm delivery.");
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

            HttpResponse<String> response = send(request);
            int code = response.statusCode();
            String responseBody = response.body();
            if (code < 200 || code >= 300) {
                return new Result(false, "WhatsApp",
                    "HTTP " + code + ": " + truncate(responseBody, 300), "", "");
            }

            String messageId = jsonField(responseBody, "id");
            return new Result(true, "WhatsApp",
                "HTTP " + code + (messageId.isBlank() ? "" : " \u2022 ID " + messageId),
                messageId, "accepted");
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

    private static HttpResponse<String> send(HttpRequest request) throws Exception {
        return CLIENT.send(request,
            HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private static Result fail(String channel, String detail) {
        return new Result(false, channel, detail);
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static String firstNonBlank(String first, String second) {
        return blank(first) ? (second == null ? "" : second) : first;
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

    /** Reads a JSON string field without adding a full JSON dependency. */
    static String jsonField(String json, String field) {
        if (json == null || field == null) return "";
        Pattern pattern = Pattern.compile(
            "\"" + Pattern.quote(field) + "\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"");
        Matcher matcher = pattern.matcher(json);
        if (!matcher.find()) return "";
        return unescapeJson(matcher.group(1));
    }

    /** Reads a JSON integer field without adding a full JSON dependency. */
    static Integer jsonInt(String json, String field) {
        if (json == null || field == null) return null;
        Pattern pattern = Pattern.compile(
            "\"" + Pattern.quote(field) + "\"\\s*:\\s*(-?\\d+)");
        Matcher matcher = pattern.matcher(json);
        if (!matcher.find()) return null;
        try {
            return Integer.parseInt(matcher.group(1));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String unescapeJson(String value) {
        StringBuilder sb = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c != '\\' || i + 1 >= value.length()) {
                sb.append(c);
                continue;
            }
            char next = value.charAt(++i);
            switch (next) {
                case 'n' -> sb.append('\n');
                case 'r' -> sb.append('\r');
                case 't' -> sb.append('\t');
                case 'b' -> sb.append('\b');
                case 'f' -> sb.append('\f');
                case '"' -> sb.append('"');
                case '\\' -> sb.append('\\');
                case '/' -> sb.append('/');
                case 'u' -> {
                    if (i + 4 < value.length()) {
                        try {
                            sb.append((char) Integer.parseInt(value.substring(i + 1, i + 5), 16));
                            i += 4;
                        } catch (NumberFormatException e) {
                            sb.append("\\u");
                        }
                    } else {
                        sb.append("\\u");
                    }
                }
                default -> sb.append(next);
            }
        }
        return sb.toString();
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
