package com.rentals.dao;

import com.rentals.rent.RentCharge;
import com.rentals.settings.AppSettings;

import java.sql.*;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

/**
 * Data Access Object for rent charges and payment allocation.
 *
 * Business logic:
 *  - generateForCurrentMonth(): one charge per active tenancy, due day from settings
 *  - allocatePayment(): spreads a verified payment across the oldest unpaid charges
 *  - applyLateFees(): applies the configured fee once to overdue charges
 */
public class RentChargeDAO {

    /** Result of a late-fee run. */
    public record LateFeeResult(int charges, double totalFees) {}

    /**
     * Creates this month's rent charges for every active tenancy
     * that does not already have a charge for the current month.
     * @return number of charges created
     */
    public int generateForCurrentMonth() {
        YearMonth month = YearMonth.now();
        int dueDay = Math.min(AppSettings.dueDay(), month.lengthOfMonth());
        String dueDate = month.atDay(dueDay).toString();

        String sql = "INSERT INTO rent_charges (tenancy_id, amount, paid_amount, due_date, period_month, status) " +
                     "SELECT t.tenancy_id, t.monthly_rent, 0, ?, " +
                     "       strftime('%Y-%m', 'now'), 'PENDING' " +
                     "FROM tenancies t " +
                     "WHERE t.status = 'ACTIVE' " +
                     "AND NOT EXISTS (SELECT 1 FROM rent_charges c " +
                     "                WHERE c.tenancy_id = t.tenancy_id " +
                     "                AND c.period_month = strftime('%Y-%m', 'now'))";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, dueDate);
            return pstmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Generate rent charges error: " + e.getMessage());
            return 0;
        }
    }

    /** All charges with tenant and room details (newest period first). */
    public List<RentCharge> getAll() {
        List<RentCharge> charges = new ArrayList<>();
        String sql = "SELECT c.*, t.tenant_id AS tenant_id, tn.full_name AS tenant_name, r.room_number " +
                     "FROM rent_charges c " +
                     "JOIN tenancies t ON c.tenancy_id = t.tenancy_id " +
                     "JOIN tenants tn ON t.tenant_id = tn.tenant_id " +
                     "JOIN rooms r ON t.room_id = r.room_id " +
                     "ORDER BY c.period_month DESC, tn.full_name";
        try (Connection conn = DatabaseConnection.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) charges.add(map(rs));
        } catch (SQLException e) {
            System.err.println("Get rent charges error: " + e.getMessage());
        }
        return charges;
    }

    /** Only unpaid charges (shows up in reports and payment allocation). */
    public List<RentCharge> getOutstanding() {
        return queryCharges("WHERE c.status IN ('PENDING','PARTIAL') " +
                            "ORDER BY c.due_date");
    }

    /** Total outstanding balance (amount minus what was already paid). */
    public double totalOutstanding() {
        String sql = "SELECT COALESCE(SUM(amount - COALESCE(paid_amount, 0)), 0) " +
                     "FROM rent_charges WHERE status IN ('PENDING','PARTIAL')";
        try (Connection conn = DatabaseConnection.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            if (rs.next()) return rs.getDouble(1);
        } catch (SQLException e) {
            System.err.println("Outstanding total error: " + e.getMessage());
        }
        return 0;
    }

    /**
     * Allocates a payment to a tenant's oldest unpaid charges.
     * Creates payment_allocations rows and updates charge status.
     * @return the amount allocated
     */
    public double allocatePayment(Long paymentId, Long tenantId, double paymentAmount) {
        double remaining = paymentAmount;

        String selectSql = "SELECT c.charge_id, c.amount, COALESCE(c.paid_amount, 0) AS paid " +
                           "FROM rent_charges c " +
                           "JOIN tenancies t ON c.tenancy_id = t.tenancy_id " +
                           "WHERE t.tenant_id = ? AND c.status IN ('PENDING','PARTIAL') " +
                           "ORDER BY c.due_date, c.charge_id";

        try (Connection conn = DatabaseConnection.getConnection()) {
            conn.setAutoCommit(false);   // all-or-nothing allocation
            List<long[]> chargeIds = new ArrayList<>();
            List<double[]> balances = new ArrayList<>();

            try (PreparedStatement pstmt = conn.prepareStatement(selectSql)) {
                pstmt.setLong(1, tenantId);
                ResultSet rs = pstmt.executeQuery();
                while (rs.next()) {
                    double balance = rs.getDouble("amount") - rs.getDouble("paid");
                    if (balance > 0.001) {
                        chargeIds.add(new long[]{rs.getLong("charge_id")});
                        balances.add(new double[]{balance});
                    }
                }
            }

            for (int i = 0; i < chargeIds.size() && remaining > 0.001; i++) {
                long chargeId = chargeIds.get(i)[0];
                double balance = balances.get(i)[0];
                double alloc = Math.min(remaining, balance);

                // Record the allocation
                try (PreparedStatement pstmt = conn.prepareStatement(
                        "INSERT INTO payment_allocations (payment_id, charge_id, amount) VALUES (?, ?, ?)")) {
                    pstmt.setLong(1, paymentId);
                    pstmt.setLong(2, chargeId);
                    pstmt.setDouble(3, alloc);
                    pstmt.executeUpdate();
                }

                // Update the charge's paid amount and status
                try (PreparedStatement pstmt = conn.prepareStatement(
                        "UPDATE rent_charges SET paid_amount = COALESCE(paid_amount, 0) + ?, " +
                        "status = CASE WHEN COALESCE(paid_amount, 0) + ? >= amount THEN 'PAID' ELSE 'PARTIAL' END " +
                        "WHERE charge_id = ?")) {
                    pstmt.setDouble(1, alloc);
                    pstmt.setDouble(2, alloc);
                    pstmt.setLong(3, chargeId);
                    pstmt.executeUpdate();
                }

                remaining -= alloc;
            }

            conn.commit();
        } catch (SQLException e) {
            System.err.println("Allocate payment error: " + e.getMessage());
        }

        return paymentAmount - remaining;
    }

    /**
     * Reverses every allocation made for a payment (used when a verified
     * payment is deleted, so the tenant's arrears become correct again).
     */
    public boolean reverseAllocations(Long paymentId) {
        String selectSql = "SELECT charge_id, amount FROM payment_allocations WHERE payment_id = ?";
        try (Connection conn = DatabaseConnection.getConnection()) {
            conn.setAutoCommit(false);

            try (PreparedStatement select = conn.prepareStatement(selectSql)) {
                select.setLong(1, paymentId);
                ResultSet rs = select.executeQuery();
                while (rs.next()) {
                    long chargeId = rs.getLong("charge_id");
                    double amount = rs.getDouble("amount");

                    try (PreparedStatement update = conn.prepareStatement(
                            "UPDATE rent_charges SET paid_amount = MAX(COALESCE(paid_amount, 0) - ?, 0), " +
                            "status = CASE WHEN COALESCE(paid_amount, 0) - ? <= 0.001 THEN 'PENDING' " +
                            "              WHEN COALESCE(paid_amount, 0) - ? >= amount THEN 'PAID' ELSE 'PARTIAL' END " +
                            "WHERE charge_id = ?")) {
                        update.setDouble(1, amount);
                        update.setDouble(2, amount);
                        update.setDouble(3, amount);
                        update.setLong(4, chargeId);
                        update.executeUpdate();
                    }
                }
            }

            try (PreparedStatement delete = conn.prepareStatement(
                    "DELETE FROM payment_allocations WHERE payment_id = ?")) {
                delete.setLong(1, paymentId);
                delete.executeUpdate();
            }

            conn.commit();
            return true;
        } catch (SQLException e) {
            System.err.println("Reverse allocations error: " + e.getMessage());
            return false;
        }
    }

    /**
     * Counts charges that would receive a late fee right now.
     * Used by the confirmation dialog before applying fees.
     */
    public int countLateFeeCandidates() {
        if (AppSettings.lateFeePercent() <= 0) return 0;
        String sql = "SELECT COUNT(*) FROM rent_charges " +
                     "WHERE status IN ('PENDING','PARTIAL') " +
                     "AND COALESCE(late_fee_applied, 0) < 0.001 " +
                     "AND due_date < date('now', ?)";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, "-" + AppSettings.graceDays() + " days");
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) return rs.getInt(1);
        } catch (SQLException e) {
            System.err.println("Count late fees error: " + e.getMessage());
        }
        return 0;
    }

    /**
     * Applies the configured late-fee percentage once to every unpaid charge
     * that is past its grace period. The fee is added to the charge amount and
     * recorded in late_fee_applied so it cannot be applied twice.
     */
    public LateFeeResult applyLateFees() {
        double percent = AppSettings.lateFeePercent();
        int grace = AppSettings.graceDays();
        if (percent <= 0) return new LateFeeResult(0, 0);

        String selectSql = "SELECT charge_id, amount FROM rent_charges " +
                           "WHERE status IN ('PENDING','PARTIAL') " +
                           "AND COALESCE(late_fee_applied, 0) < 0.001 " +
                           "AND due_date < date('now', ?)";
        int count = 0;
        double total = 0;

        try (Connection conn = DatabaseConnection.getConnection()) {
            conn.setAutoCommit(false);

            List<long[]> ids = new ArrayList<>();
            List<Double> fees = new ArrayList<>();
            try (PreparedStatement select = conn.prepareStatement(selectSql)) {
                select.setString(1, "-" + grace + " days");
                ResultSet rs = select.executeQuery();
                while (rs.next()) {
                    double fee = Math.round(rs.getDouble("amount") * percent) / 100.0;
                    if (fee > 0.001) {
                        ids.add(new long[]{rs.getLong("charge_id")});
                        fees.add(fee);
                    }
                }
            }

            try (PreparedStatement update = conn.prepareStatement(
                    "UPDATE rent_charges SET amount = amount + ?, late_fee_applied = ?, " +
                    "status = CASE WHEN COALESCE(paid_amount, 0) >= amount + ? THEN 'PAID' ELSE status END " +
                    "WHERE charge_id = ?")) {
                for (int i = 0; i < ids.size(); i++) {
                    double fee = fees.get(i);
                    update.setDouble(1, fee);
                    update.setDouble(2, fee);
                    update.setDouble(3, fee);
                    update.setLong(4, ids.get(i)[0]);
                    update.addBatch();
                    total += fee;
                }
                update.executeBatch();
            }

            conn.commit();
            count = ids.size();
        } catch (SQLException e) {
            System.err.println("Apply late fees error: " + e.getMessage());
        }

        return new LateFeeResult(count, Math.round(total * 100.0) / 100.0);
    }

    /**
     * Charge allocations for one payment, used on PDF receipts.
     * Rows are [periodMonth, chargeAmount, allocatedAmount].
     */
    public List<Object[]> getAllocationsForPayment(Long paymentId) {
        List<Object[]> rows = new ArrayList<>();
        String sql = "SELECT c.period_month, c.amount, a.amount AS allocated " +
                     "FROM payment_allocations a " +
                     "JOIN rent_charges c ON a.charge_id = c.charge_id " +
                     "WHERE a.payment_id = ? ORDER BY c.due_date";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, paymentId);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                rows.add(new Object[]{
                    rs.getString("period_month"),
                    rs.getDouble("amount"),
                    rs.getDouble("allocated")
                });
            }
        } catch (SQLException e) {
            System.err.println("Get payment allocations error: " + e.getMessage());
        }
        return rows;
    }

    /* ---------- helpers ---------- */

    private List<RentCharge> queryCharges(String whereClause) {
        List<RentCharge> charges = new ArrayList<>();
        String sql = "SELECT c.*, t.tenant_id AS tenant_id, tn.full_name AS tenant_name, r.room_number " +
                     "FROM rent_charges c " +
                     "JOIN tenancies t ON c.tenancy_id = t.tenancy_id " +
                     "JOIN tenants tn ON t.tenant_id = tn.tenant_id " +
                     "JOIN rooms r ON t.room_id = r.room_id " + whereClause;
        try (Connection conn = DatabaseConnection.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) charges.add(map(rs));
        } catch (SQLException e) {
            System.err.println("Query rent charges error: " + e.getMessage());
        }
        return charges;
    }

    private RentCharge map(ResultSet rs) throws SQLException {
        RentCharge c = new RentCharge();
        c.setChargeId(rs.getLong("charge_id"));
        c.setTenancyId(rs.getLong("tenancy_id"));
        try { c.setTenantId(rs.getLong("tenant_id")); } catch (SQLException e) {}
        try { c.setTenantName(rs.getString("tenant_name")); } catch (SQLException e) {}
        try { c.setRoomNumber(rs.getString("room_number")); } catch (SQLException e) {}
        c.setAmount(rs.getDouble("amount"));
        try { c.setPaidAmount(rs.getDouble("paid_amount")); } catch (SQLException e) { c.setPaidAmount(0.0); }
        try { c.setLateFeeApplied(rs.getDouble("late_fee_applied")); } catch (SQLException e) { c.setLateFeeApplied(0.0); }
        c.setDueDate(rs.getString("due_date"));
        c.setStatus(rs.getString("status"));
        c.setPeriodMonth(rs.getString("period_month"));
        return c;
    }
}
