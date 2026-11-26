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
import com.rentals.dao.RentChargeDAO;
import com.rentals.payments.Payment;
import com.rentals.settings.AppSettings;
import com.rentals.util.FormatUtils;

import java.awt.Color;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Generates a printable PDF receipt for a verified payment.
 */
public class ReceiptService {

    private static final Color PRIMARY = new Color(25, 118, 210);
    private static final Color HEADER_BG = new Color(227, 242, 253);
    private static final Color BORDER = new Color(200, 210, 225);

    private static final Font TITLE = new Font(Font.HELVETICA, 18, Font.BOLD, PRIMARY);
    private static final Font SUBTITLE = new Font(Font.HELVETICA, 11, Font.NORMAL, Color.DARK_GRAY);
    private static final Font BODY = new Font(Font.HELVETICA, 10, Font.NORMAL, Color.BLACK);
    private static final Font BODY_BOLD = new Font(Font.HELVETICA, 10, Font.BOLD, Color.BLACK);
    private static final Font SMALL = new Font(Font.HELVETICA, 8, Font.NORMAL, Color.GRAY);

    private final RentChargeDAO rentChargeDAO = new RentChargeDAO();

    /** Stable receipt number used on the PDF and in receipt SMS messages. */
    public static String receiptNumber(Payment payment) {
        String datePart = (payment.getPaymentDate() == null ? LocalDate.now().toString()
            : payment.getPaymentDate()).replace("-", "");
        return AppSettings.receiptPrefix() + "-" + datePart + "-"
            + String.format("%05d", payment.getPaymentId());
    }

    /** Creates the receipt PDF and returns its path. */
    public Path generate(Payment payment) throws Exception {
        Path dir = AppPaths.receiptsDir();
        String receiptNo = receiptNumber(payment);
        Path file = dir.resolve("receipt-" + receiptNo + ".pdf");

        Document document = new Document(PageSize.A4, 50, 50, 45, 45);
        try (OutputStream out = Files.newOutputStream(file)) {
            PdfWriter.getInstance(document, out);
            document.open();

            // ---------- Company header ----------
            Paragraph company = new Paragraph(AppSettings.companyName(), TITLE);
            company.setAlignment(Element.ALIGN_CENTER);
            document.add(company);

            String contact = joinContact();
            if (!contact.isBlank()) {
                Paragraph contactLine = new Paragraph(contact, SUBTITLE);
                contactLine.setAlignment(Element.ALIGN_CENTER);
                document.add(contactLine);
            }

            Paragraph receiptTitle = new Paragraph("PAYMENT RECEIPT", new Font(Font.HELVETICA, 14, Font.BOLD));
            receiptTitle.setAlignment(Element.ALIGN_CENTER);
            receiptTitle.setSpacingBefore(18);
            receiptTitle.setSpacingAfter(18);
            document.add(receiptTitle);

            // ---------- Receipt details ----------
            PdfPTable meta = new PdfPTable(new float[]{1, 2, 1, 2});
            meta.setWidthPercentage(100);
            addMeta(meta, "Receipt No.", receiptNo);
            addMeta(meta, "Date", payment.getPaymentDate() == null ? LocalDate.now().toString()
                : payment.getPaymentDate());
            addMeta(meta, "Tenant", safe(payment.getTenantName(), "Tenant #" + payment.getTenantId()));
            addMeta(meta, "Status", safe(payment.getStatus(), "PENDING"));
            addMeta(meta, "Method", safe(payment.getPaymentMethod(), "-"));
            addMeta(meta, "Reference", safe(payment.getReferenceNumber(), "-"));
            document.add(meta);

            // ---------- Amount box ----------
            PdfPTable amountTable = new PdfPTable(1);
            amountTable.setWidthPercentage(100);
            amountTable.setSpacingBefore(18);
            PdfPCell amountCell = new PdfPCell(new Phrase(
                "Amount received:  " + FormatUtils.money(payment.getAmount() == null ? 0 : payment.getAmount()),
                new Font(Font.HELVETICA, 14, Font.BOLD, PRIMARY)));
            amountCell.setBackgroundColor(HEADER_BG);
            amountCell.setPadding(14);
            amountCell.setBorderColor(BORDER);
            amountTable.addCell(amountCell);
            document.add(amountTable);

            // ---------- Allocation table ----------
            List<Object[]> allocations = rentChargeDAO.getAllocationsForPayment(payment.getPaymentId());
            Paragraph allocationTitle = new Paragraph("Applied to rent charges", BODY_BOLD);
            allocationTitle.setSpacingBefore(22);
            allocationTitle.setSpacingAfter(8);
            document.add(allocationTitle);

            if (allocations.isEmpty()) {
                document.add(new Paragraph(
                    "No rent charges were allocated to this payment (it may have been recorded before charges existed).",
                    BODY));
            } else {
                PdfPTable table = new PdfPTable(new float[]{2, 2, 2});
                table.setWidthPercentage(100);
                addHead(table, "Period");
                addHead(table, "Charge amount");
                addHead(table, "Allocated");
                for (Object[] row : allocations) {
                    table.addCell(cell(String.valueOf(row[0])));
                    table.addCell(cell(FormatUtils.money((Double) row[1])));
                    table.addCell(cell(FormatUtils.money((Double) row[2])));
                }
                document.add(table);
            }

            if (payment.getNotes() != null && !payment.getNotes().isBlank()) {
                Paragraph notes = new Paragraph("Notes: " + payment.getNotes(), BODY);
                notes.setSpacingBefore(16);
                document.add(notes);
            }

            Paragraph footer = new Paragraph(
                "Thank you for your payment. This receipt was generated by " + AppSettings.companyName() + ".", SMALL);
            footer.setSpacingBefore(30);
            document.add(footer);

            Paragraph generated = new Paragraph(
                "Generated " + java.time.LocalDateTime.now().format(
                    DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm")), SMALL);
            generated.setSpacingBefore(4);
            document.add(generated);

            document.close();
        }
        return file;
    }

    private void addMeta(PdfPTable table, String label, String value) {
        PdfPCell labelCell = new PdfPCell(new Phrase(label, BODY_BOLD));
        labelCell.setBorderColor(BORDER);
        labelCell.setBackgroundColor(HEADER_BG);
        labelCell.setPadding(7);
        table.addCell(labelCell);

        PdfPCell valueCell = new PdfPCell(new Phrase(value, BODY));
        valueCell.setBorderColor(BORDER);
        valueCell.setPadding(7);
        valueCell.setColspan(1);
        table.addCell(valueCell);
    }

    private void addHead(PdfPTable table, String text) {
        PdfPCell head = new PdfPCell(new Phrase(text, BODY_BOLD));
        head.setBackgroundColor(HEADER_BG);
        head.setBorderColor(BORDER);
        head.setPadding(7);
        table.addCell(head);
    }

    private PdfPCell cell(String text) {
        PdfPCell cell = new PdfPCell(new Phrase(text, BODY));
        cell.setBorderColor(BORDER);
        cell.setPadding(7);
        return cell;
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

    private String safe(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
