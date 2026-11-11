package com.rentals.service;

import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.rentals.config.AppPaths;
import com.rentals.dao.ExpenseDAO;
import com.rentals.dao.RentChargeDAO;
import com.rentals.dao.ReportDAO;
import com.rentals.expenses.Expense;
import com.rentals.rent.RentCharge;
import com.rentals.settings.AppSettings;
import com.rentals.util.FormatUtils;

import java.awt.Color;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Exports the report data as CSV (for Excel) or as a printable PDF.
 *
 * CSV files are UTF-8 with a BOM so Excel opens them correctly. PDFs use the
 * same company header and colours as the payment receipts.
 */
public class ReportExportService {

    /** The reports available from the Reports screen. */
    public enum ReportType {
        SUMMARY("Financial Summary", "summary"),
        OUTSTANDING("Outstanding Rent Charges", "outstanding-rent"),
        PROPERTIES("Property Occupancy", "property-occupancy"),
        MONTHLY_REVENUE("Monthly Revenue", "monthly-revenue"),
        EXPENSES("Expenses", "expenses");

        private final String title;
        private final String fileSlug;

        ReportType(String title, String fileSlug) {
            this.title = title;
            this.fileSlug = fileSlug;
        }

        public String title() { return title; }
        public String fileSlug() { return fileSlug; }

        @Override
        public String toString() { return title; }
    }

    /** A simple table of text values used by both writers. */
    private record TableData(List<String> headers, List<List<String>> rows, boolean moneyColumns) {}

    private static final Color PRIMARY = new Color(25, 118, 210);
    private static final Color HEADER_BG = new Color(227, 242, 253);
    private static final Color BORDER = new Color(200, 210, 225);
    private static final Color ZEBRA = new Color(248, 250, 253);

    private static final Font TITLE = new Font(Font.HELVETICA, 17, Font.BOLD, PRIMARY);
    private static final Font SUBTITLE = new Font(Font.HELVETICA, 10, Font.NORMAL, Color.DARK_GRAY);
    private static final Font BODY = new Font(Font.HELVETICA, 9, Font.NORMAL, Color.BLACK);
    private static final Font BODY_BOLD = new Font(Font.HELVETICA, 9, Font.BOLD, Color.BLACK);
    private static final Font HEAD = new Font(Font.HELVETICA, 9, Font.BOLD, Color.BLACK);
    private static final Font SMALL = new Font(Font.HELVETICA, 8, Font.NORMAL, Color.GRAY);

    private final ReportDAO reportDAO = new ReportDAO();
    private final RentChargeDAO rentChargeDAO = new RentChargeDAO();
    private final ExpenseDAO expenseDAO = new ExpenseDAO();

    /* ==================== CSV ==================== */

    public Path exportCsv(ReportType type) throws IOException {
        TableData data = collect(type);
        Path file = outputFile(type, "csv");
        StringBuilder sb = new StringBuilder();

        // UTF-8 BOM so Excel recognises the encoding
        sb.append('\uFEFF');
        sb.append(row(data.headers()));
        for (List<String> row : data.rows()) {
            sb.append(row(row));
        }

        Files.writeString(file, sb.toString(), StandardCharsets.UTF_8);
        return file;
    }

    /* ==================== PDF ==================== */

    public Path exportPdf(ReportType type) throws Exception {
        TableData data = collect(type);
        Path file = outputFile(type, "pdf");

        Document document = new Document(PageSize.A4, 42, 42, 40, 40);
        try (OutputStream out = Files.newOutputStream(file)) {
            PdfWriter.getInstance(document, out);
            document.open();

            Paragraph company = new Paragraph(AppSettings.companyName(), TITLE);
            company.setAlignment(Element.ALIGN_CENTER);
            document.add(company);

            String contact = joinContact();
            if (!contact.isBlank()) {
                Paragraph contactLine = new Paragraph(contact, SUBTITLE);
                contactLine.setAlignment(Element.ALIGN_CENTER);
                document.add(contactLine);
            }

            Paragraph title = new Paragraph(type.title(), new Font(Font.HELVETICA, 14, Font.BOLD));
            title.setAlignment(Element.ALIGN_CENTER);
            title.setSpacingBefore(18);
            document.add(title);

            Paragraph generated = new Paragraph(
                "Generated " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm")),
                SMALL);
            generated.setAlignment(Element.ALIGN_CENTER);
            generated.setSpacingAfter(14);
            document.add(generated);

            PdfPTable table = new PdfPTable(data.headers().size());
            table.setWidthPercentage(100);
            table.setHeaderRows(1);

            for (String header : data.headers()) {
                PdfPCell head = new PdfPCell(new Phrase(header, HEAD));
                head.setBackgroundColor(HEADER_BG);
                head.setBorderColor(BORDER);
                head.setPadding(6);
                table.addCell(head);
            }

            int rowIndex = 0;
            for (List<String> row : data.rows()) {
                for (int c = 0; c < row.size(); c++) {
                    PdfPCell cell = new PdfPCell(new Phrase(row.get(c), BODY));
                    cell.setBorderColor(BORDER);
                    cell.setPadding(6);
                    if (rowIndex % 2 == 1) cell.setBackgroundColor(ZEBRA);
                    if (data.moneyColumns() && c > 0) cell.setHorizontalAlignment(Element.ALIGN_RIGHT);
                    table.addCell(cell);
                }
                rowIndex++;
            }

            document.add(table);

            Paragraph footer = new Paragraph(
                data.rows().size() + " row(s)  |  Currency: " + AppSettings.currency() +
                "  |  " + AppSettings.companyName(), SMALL);
            footer.setSpacingBefore(12);
            document.add(footer);

            document.close();
        }
        return file;
    }

    /* ==================== DATA ==================== */

    private TableData collect(ReportType type) {
        return switch (type) {
            case SUMMARY -> summaryData();
            case OUTSTANDING -> outstandingData();
            case PROPERTIES -> propertyData();
            case MONTHLY_REVENUE -> monthlyRevenueData();
            case EXPENSES -> expenseData();
        };
    }

    private TableData summaryData() {
        double revenue = reportDAO.totalRevenue();
        double expenses = reportDAO.totalExpenses();

        List<List<String>> rows = new ArrayList<>();
        addSummary(rows, "Total Revenue", FormatUtils.money(revenue));
        addSummary(rows, "Revenue This Month", FormatUtils.money(reportDAO.revenueThisMonth()));
        addSummary(rows, "Expected Monthly Rent", FormatUtils.money(reportDAO.expectedMonthlyRent()));
        addSummary(rows, "Outstanding Arrears", FormatUtils.money(reportDAO.arrears()));
        addSummary(rows, "Total Expenses", FormatUtils.money(expenses));
        addSummary(rows, "Net Income", FormatUtils.money(revenue - expenses));
        addSummary(rows, "Occupancy Rate", String.format(Locale.US, "%.1f%%", reportDAO.occupancyRate()));
        addSummary(rows, "Properties", String.valueOf(reportDAO.totalProperties()));
        addSummary(rows, "Rooms (Occupied / Total)",
            reportDAO.occupiedRooms() + " / " + reportDAO.totalRooms());
        addSummary(rows, "Vacant Rooms", String.valueOf(reportDAO.vacantRooms()));
        addSummary(rows, "Active Tenancies", String.valueOf(reportDAO.activeTenancies()));
        addSummary(rows, "Tenants", String.valueOf(reportDAO.totalTenants()));

        return new TableData(List.of("Metric", "Value"), rows, false);
    }

    private void addSummary(List<List<String>> rows, String label, String value) {
        rows.add(List.of(label, value));
    }

    private TableData outstandingData() {
        List<List<String>> rows = new ArrayList<>();
        for (RentCharge charge : rentChargeDAO.getOutstanding()) {
            rows.add(List.of(
                safe(charge.getTenantName()),
                safe(charge.getRoomNumber()),
                safe(charge.getPeriodMonth()),
                FormatUtils.number(charge.getAmount() == null ? 0 : charge.getAmount()),
                FormatUtils.number(charge.getPaidAmount() == null ? 0 : charge.getPaidAmount()),
                FormatUtils.number(charge.getBalance() == null ? 0 : charge.getBalance()),
                safe(charge.getDueDate()),
                safe(charge.getStatus())
            ));
        }
        return new TableData(
            List.of("Tenant", "Room", "Period", "Amount", "Paid", "Balance", "Due Date", "Status"),
            rows, true);
    }

    private TableData propertyData() {
        List<List<String>> rows = new ArrayList<>();
        int totalRooms = 0;
        int occupied = 0;
        int vacant = 0;
        for (Object[] row : reportDAO.propertySummary()) {
            int total = (Integer) row[1];
            int occ = (Integer) row[2];
            int vac = (Integer) row[3];
            totalRooms += total;
            occupied += occ;
            vacant += vac;
            rows.add(List.of(
                String.valueOf(row[0]),
                String.valueOf(total),
                String.valueOf(occ),
                String.valueOf(vac)
            ));
        }
        rows.add(List.of("TOTAL", String.valueOf(totalRooms), String.valueOf(occupied), String.valueOf(vacant)));
        return new TableData(List.of("Property", "Total Rooms", "Occupied", "Vacant"), rows, false);
    }

    private TableData monthlyRevenueData() {
        List<List<String>> rows = new ArrayList<>();
        double total = 0;
        for (Object[] row : reportDAO.monthlyRevenue()) {
            double amount = (Double) row[1];
            total += amount;
            rows.add(List.of(String.valueOf(row[0]), FormatUtils.number(amount)));
        }
        rows.add(List.of("TOTAL", FormatUtils.number(total)));
        return new TableData(List.of("Month", "Collected"), rows, true);
    }

    private TableData expenseData() {
        List<List<String>> rows = new ArrayList<>();
        double total = 0;
        for (Expense expense : expenseDAO.getAll()) {
            double amount = expense.getAmount() == null ? 0 : expense.getAmount();
            total += amount;
            rows.add(List.of(
                safe(expense.getExpenseDate()),
                safe(expense.getPropertyName()),
                safe(expense.getDescription()),
                safe(expense.getCategory()),
                FormatUtils.number(amount)
            ));
        }
        rows.add(List.of("", "", "", "TOTAL", FormatUtils.number(total)));
        return new TableData(List.of("Date", "Property", "Description", "Category", "Amount"), rows, true);
    }

    /* ==================== HELPERS ==================== */

    private Path outputFile(ReportType type, String extension) throws IOException {
        Path dir = AppPaths.reportsDir();
        String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        return dir.resolve(type.fileSlug() + "-" + stamp + "." + extension);
    }

    /** Escapes one CSV row (RFC 4180 style). */
    private String row(List<String> values) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append(csvValue(values.get(i)));
        }
        sb.append("\r\n");
        return sb.toString();
    }

    private String csvValue(String value) {
        String v = value == null ? "" : value;
        if (v.contains(",") || v.contains("\"") || v.contains("\n") || v.contains("\r")) {
            return '"' + v.replace("\"", "\"\"") + '"';
        }
        return v;
    }

    private String joinContact() {
        StringBuilder sb = new StringBuilder();
        for (String part : new String[]{AppSettings.companyAddress(), AppSettings.companyPhone(), AppSettings.companyEmail()}) {
            if (part != null && !part.isBlank()) {
                if (!sb.isEmpty()) sb.append("  |  ");
                sb.append(part);
            }
        }
        return sb.toString();
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
