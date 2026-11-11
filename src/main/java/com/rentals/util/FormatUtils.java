package com.rentals.util;

import com.rentals.settings.AppSettings;

import java.awt.Desktop;
import java.nio.file.Path;
import java.text.NumberFormat;
import java.util.Locale;

/**
 * Small formatting and desktop helpers shared by views, PDFs and CSV exports.
 */
public final class FormatUtils {

    private FormatUtils() {}

    /** Formats money using the configured currency, e.g. "UGX 1,250,000". */
    public static String money(double amount) {
        NumberFormat nf = NumberFormat.getNumberInstance(Locale.US);
        nf.setMinimumFractionDigits(0);
        nf.setMaximumFractionDigits(2);
        return AppSettings.currency() + " " + nf.format(amount);
    }

    /** Number only, without the currency prefix. */
    public static String number(double amount) {
        NumberFormat nf = NumberFormat.getNumberInstance(Locale.US);
        nf.setMinimumFractionDigits(0);
        nf.setMaximumFractionDigits(2);
        return nf.format(amount);
    }

    /** Opens a file or folder with the operating system's default application. */
    public static void open(Path path) {
        try {
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().open(path.toFile());
            }
        } catch (Exception e) {
            System.err.println("Could not open " + path + ": " + e.getMessage());
        }
    }
}
