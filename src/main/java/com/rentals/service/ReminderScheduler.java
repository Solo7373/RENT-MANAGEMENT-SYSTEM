package com.rentals.service;

import com.rentals.dao.ReminderDAO;
import com.rentals.dao.RentChargeDAO;
import com.rentals.notifications.NotificationLog;
import com.rentals.reminders.ReminderRule;
import com.rentals.rent.RentCharge;
import com.rentals.settings.AppSettings;
import com.rentals.util.FormatUtils;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Sends due rent reminders while the desktop application is open.
 *
 * <p>This is not a 24/7 service. The scheduler starts after an admin logs in
 * and stops on logout. If the app is closed, nothing is sent until it is opened
 * again. "Run Due Reminders Now" in the Reminders screen can trigger the same
 * scan manually.</p>
 *
 * <p>For each outstanding rent charge the scheduler chooses the most recent
 * enabled rule whose trigger day has been reached, but only when its channel is
 * configured (SMS or WhatsApp). One message is sent per rule + charge pair; the
 * unique dedupe key prevents repeats. A failed attempt clears its key so the
 * next scan can retry.</p>
 */
public final class ReminderScheduler {

    /** Summary of one scan. */
    public record RunResult(
        boolean started,
        int chargesScanned,
        int sent,
        int failed,
        int skipped,
        int notDue,
        String detail
    ) {}

    private static final ScheduledExecutorService EXECUTOR =
        Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "reminder-scheduler");
            thread.setDaemon(true);
            return thread;
        });

    private static final AtomicBoolean STARTED = new AtomicBoolean(false);
    private static final AtomicBoolean SCANNING = new AtomicBoolean(false);

    private static final ReminderDAO reminderDAO = new ReminderDAO();
    private static final RentChargeDAO rentChargeDAO = new RentChargeDAO();

    private static volatile ScheduledFuture<?> future;
    private static volatile String lastRunSummary = "Not run yet.";

    private ReminderScheduler() {}

    /** Starts the periodic in-app scan. Safe to call more than once. */
    public static synchronized void start() {
        if (!STARTED.compareAndSet(false, true)) return;

        int minutes = AppSettings.reminderSchedulerMinutes();
        long periodSeconds = Math.max(1, minutes) * 60L;
        future = EXECUTOR.scheduleWithFixedDelay(() -> {
            try {
                runDueRemindersNow(true);
            } catch (Throwable error) {
                System.err.println("Reminder scheduler error: " + error.getMessage());
            }
        }, 20, periodSeconds, TimeUnit.SECONDS);

        System.out.println("Reminder scheduler started (every " + minutes
            + " minute(s); automatic sending is "
            + (AppSettings.reminderAutoSendEnabled() ? "on" : "off") + ").");
    }

    /** Stops scheduling new scans; an in-flight scan is allowed to finish. */
    public static synchronized void stop() {
        STARTED.set(false);
        if (future != null) {
            future.cancel(false);
            future = null;
        }
    }

    public static boolean isRunning() {
        return STARTED.get();
    }

    public static String lastRunSummary() {
        return lastRunSummary;
    }

    /**
     * Runs a scan immediately.
     *
     * @param requireAutoSend when true (periodic scans) the automatic-send
     *                        setting is honoured; manual runs always proceed
     */
    public static RunResult runDueRemindersNow(boolean requireAutoSend) {
        if (requireAutoSend && !AppSettings.reminderAutoSendEnabled()) {
            String detail = "Automatic sending is off. Enable it in Communication Setup.";
            lastRunSummary = detail;
            return new RunResult(false, 0, 0, 0, 0, 0, detail);
        }

        if (!SCANNING.compareAndSet(false, true)) {
            String detail = "A reminder scan is already running.";
            return new RunResult(false, 0, 0, 0, 0, 0, detail);
        }

        try {
            RunResult result = scan();
            lastRunSummary = result.detail();
            return result;
        } finally {
            SCANNING.set(false);
        }
    }

    /** Manual run used by the Reminders screen. */
    public static RunResult runDueRemindersNow() {
        return runDueRemindersNow(false);
    }

    private static RunResult scan() {
        // Make sure the current month has charges, then look at everything unpaid.
        rentChargeDAO.generateForCurrentMonth();
        List<RentCharge> charges = rentChargeDAO.getOutstanding();
        List<ReminderRule> rules = reminderDAO.getAllRules().stream()
            .filter(ReminderRule::isEnabled)
            .filter(rule -> channelReady(rule.getChannel()))
            .toList();

        if (rules.isEmpty()) {
            String detail = "No enabled reminder rule has a configured channel. "
                + "Enable SMS or WhatsApp in Communication Setup, or enable an SMS rule.";
            return new RunResult(true, charges.size(), 0, 0, 0, 0, detail);
        }

        LocalDate today = LocalDate.now();
        int sent = 0;
        int failed = 0;
        int skipped = 0;
        int notDue = 0;

        for (RentCharge charge : charges) {
            if (charge.getTenantId() == null) continue;
            LocalDate dueDate = parseDate(charge.getDueDate());
            if (dueDate == null) continue;

            long daysFromDue = ChronoUnit.DAYS.between(dueDate, today);
            ReminderRule rule = rules.stream()
                .filter(candidate -> candidate.getDaysAfterDue() != null
                    && daysFromDue >= candidate.getDaysAfterDue())
                .max(Comparator
                    .comparingInt((ReminderRule candidate) -> candidate.getDaysAfterDue())
                    .thenComparingInt(candidate -> candidate.getStage() == null
                        ? 0 : candidate.getStage()))
                .orElse(null);

            if (rule == null) {
                notDue++;
                continue;
            }

            String dedupeKey = "charge:" + charge.getChargeId()
                + ":rule:" + rule.getRuleId();
            if (reminderDAO.dedupeExists(dedupeKey)) {
                skipped++;
                continue;
            }

            String message = render(rule.getMessageTemplate(), charge);
            Long notificationId = reminderDAO.queueTrackedNotification(
                charge.getTenantId(), charge.getTenancyId(), charge.getChargeId(),
                rule.getRuleId(), "REMINDER", rule.getChannel(), message, dedupeKey);

            if (notificationId == null) {
                skipped++;
                continue;
            }

            NotificationLog log = new NotificationLog();
            log.setNotificationId(notificationId);
            log.setTenantId(charge.getTenantId());
            log.setTenancyId(charge.getTenancyId());
            log.setChargeId(charge.getChargeId());
            log.setRuleId(rule.getRuleId());
            log.setEventType("REMINDER");
            log.setChannel(rule.getChannel());
            log.setMessage(message);

            NotificationService.SendResult result = NotificationService.send(log);
            if (result.success()) sent++;
            else failed++;
        }

        String detail = "Scanned " + charges.size() + " outstanding charge(s): "
            + sent + " sent, " + failed + " failed, "
            + skipped + " already sent, " + notDue + " not due yet.";
        return new RunResult(true, charges.size(), sent, failed, skipped, notDue, detail);
    }

    private static boolean channelReady(String channel) {
        String normalised = channel == null ? "" : channel.trim().toUpperCase(Locale.ROOT);
        return switch (normalised) {
            case "SMS" -> AppSettings.smsConfigured();
            case "WHATSAPP" -> AppSettings.whatsappConfigured();
            default -> false;   // Email has no delivery mechanism configured.
        };
    }

    private static String render(String template, RentCharge charge) {
        String balance = FormatUtils.money(charge.getBalance());
        String text = template == null || template.isBlank()
            ? "Hello {name}, your rent balance is {amount}. Please pay soon."
            : template;
        return text
            .replace("{name}", charge.getTenantName() == null ? "tenant" : charge.getTenantName())
            .replace("{amount}", balance)
            .replace("{balance}", balance)
            .replace("{due_date}", charge.getDueDate() == null ? "" : charge.getDueDate())
            .replace("{period}", charge.getPeriodMonth() == null ? "" : charge.getPeriodMonth())
            .replace("{room}", charge.getRoomNumber() == null ? "" : charge.getRoomNumber())
            .replace("{company}", AppSettings.companyName());
    }

    private static LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return LocalDate.parse(value.trim());
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
