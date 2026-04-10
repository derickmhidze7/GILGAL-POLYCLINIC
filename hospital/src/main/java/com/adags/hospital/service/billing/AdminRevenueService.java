package com.adags.hospital.service.billing;

import com.adags.hospital.domain.billing.InvoiceLineItem;
import com.adags.hospital.domain.billing.InvoiceStatus;
import com.adags.hospital.domain.billing.LineItemCategory;
import com.adags.hospital.dto.billing.MonthlyRevenueDto;
import com.adags.hospital.dto.billing.RevenueBreakdownDto;
import com.adags.hospital.dto.billing.RevenueLineItemDetailDto;
import com.adags.hospital.repository.billing.InvoiceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Aggregates invoice revenue data for the Admin Revenue Analytics page.
 * Only counts fully PAID invoices as collected revenue.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminRevenueService {

    private static final List<InvoiceStatus> REVENUE_STATUSES =
            List.of(InvoiceStatus.PAID);

    /** Display order for LineItemCategory rows. */
    private static final LineItemCategory[] DISPLAY_ORDER = {
            LineItemCategory.CONSULTATION,
            LineItemCategory.LAB,
            LineItemCategory.PHARMACY,
            LineItemCategory.PROCEDURE,
            LineItemCategory.BED,
            LineItemCategory.RADIOLOGY,
            LineItemCategory.OTHER
    };

    private static final String[] MONTH_NAMES = {
            "January", "February", "March", "April", "May", "June",
            "July", "August", "September", "October", "November", "December"
    };

    private final InvoiceRepository invoiceRepository;

    // ── Result record ────────────────────────────────────────────────────────

    public record RevenueReport(
            String  periodLabel,
            LocalDate fromDate,
            LocalDate toDate,
            List<RevenueBreakdownDto> categories,
            BigDecimal grandTotal,
            long   totalInvoices,
            List<MonthlyRevenueDto> monthlyRows,
            BigDecimal yearTotal,
            int    selectedYear,
            int    selectedMonth) {
    }

    /** Result of the item-level drill-down page. */
    public record ItemsReport(
            String   periodLabel,
            String   categoryLabel,
            String   categoryCode,
            LocalDate fromDate,
            LocalDate toDate,
            int      selectedYear,
            int      selectedMonth,
            List<RevenueLineItemDetailDto> items,
            BigDecimal grandTotal,
            long     itemCount) {
    }

    // ── Main entry point ─────────────────────────────────────────────────────

    /**
     * Builds a {@link RevenueReport} from the given filter parameters.
     * {@code filterType} must be one of: {@code "month"}, {@code "day"},
     * {@code "week"}, {@code "range"}, {@code "year"}.  Defaults to
     * {@code "month"} (current month) when null.
     */
    public RevenueReport buildReport(String filterType,
                                     Integer year,
                                     Integer month,
                                     LocalDate day,
                                     LocalDate weekOf,
                                     LocalDate rangeFrom,
                                     LocalDate rangeTo) {

        int currentYear  = LocalDate.now().getYear();
        int currentMonth = LocalDate.now().getMonthValue();
        int resolvedYear  = (year  != null) ? year  : currentYear;
        int resolvedMonth = (month != null) ? month : currentMonth;
        String ft = (filterType == null || filterType.isBlank()) ? "month" : filterType;

        // ── Resolve date range ───────────────────────────────────────────────
        LocalDate fromDate;
        LocalDate toDate;
        String    periodLabel;

        switch (ft) {
            case "day" -> {
                LocalDate d  = (day != null) ? day : LocalDate.now();
                fromDate     = d;
                toDate       = d;
                periodLabel  = "Revenue: " + d.format(DateTimeFormatter.ofPattern("dd MMMM yyyy"));
            }
            case "week" -> {
                LocalDate ref = (weekOf != null) ? weekOf : LocalDate.now();
                fromDate      = ref.with(DayOfWeek.MONDAY);
                toDate        = ref.with(DayOfWeek.SUNDAY);
                periodLabel   = "Revenue: Week of "
                        + fromDate.format(DateTimeFormatter.ofPattern("dd MMM"))
                        + " – "
                        + toDate.format(DateTimeFormatter.ofPattern("dd MMM yyyy"));
            }
            case "range" -> {
                fromDate    = (rangeFrom != null) ? rangeFrom : LocalDate.now().withDayOfMonth(1);
                toDate      = (rangeTo   != null) ? rangeTo   : LocalDate.now();
                periodLabel = fromDate.format(DateTimeFormatter.ofPattern("dd MMM yyyy"))
                        + " – "
                        + toDate.format(DateTimeFormatter.ofPattern("dd MMM yyyy"));
            }
            case "year" -> {
                fromDate    = LocalDate.of(resolvedYear, 1, 1);
                toDate      = LocalDate.of(resolvedYear, 12, 31);
                periodLabel = "Revenue: Year " + resolvedYear;
            }
            default -> { // "month"
                fromDate    = LocalDate.of(resolvedYear, resolvedMonth, 1);
                toDate      = fromDate.withDayOfMonth(fromDate.lengthOfMonth());
                periodLabel = "Revenue: "
                        + fromDate.format(DateTimeFormatter.ofPattern("MMMM yyyy"));
            }
        }

        LocalDateTime fromDt = fromDate.atStartOfDay();
        LocalDateTime toDt   = toDate.atTime(23, 59, 59);

        // ── Category breakdown ───────────────────────────────────────────────
        List<Object[]> rawCategories =
                invoiceRepository.revenueByCategoryBetween(REVENUE_STATUSES, fromDt, toDt);

        Map<LineItemCategory, BigDecimal> catTotals = new EnumMap<>(LineItemCategory.class);
        Map<LineItemCategory, Long>       catCounts = new EnumMap<>(LineItemCategory.class);

        for (Object[] row : rawCategories) {
            LineItemCategory cat   = (LineItemCategory) row[0];
            BigDecimal       total = (BigDecimal) row[1];
            long             count = ((Number) row[2]).longValue();
            catTotals.put(cat, total);
            catCounts.put(cat, count);
        }

        List<RevenueBreakdownDto> categories = new ArrayList<>();
        BigDecimal grandTotal   = BigDecimal.ZERO;
        long       totalInvoices = 0;

        for (LineItemCategory cat : DISPLAY_ORDER) {
            BigDecimal catTotal = catTotals.getOrDefault(cat, BigDecimal.ZERO);
            long       catCount = catCounts.getOrDefault(cat, 0L);
            categories.add(new RevenueBreakdownDto(cat.name(), labelFor(cat), catTotal, catCount));
            grandTotal    = grandTotal.add(catTotal);
            totalInvoices += catCount;
        }

        // ── Monthly rows (for "year" and "month" filter types) ───────────────
        List<MonthlyRevenueDto> monthlyRows = new ArrayList<>();
        if ("year".equals(ft) || "month".equals(ft)) {
            // Always show all 12 months for the selected year
            LocalDateTime yearFrom = LocalDate.of(resolvedYear, 1, 1).atStartOfDay();
            LocalDateTime yearTo   = LocalDate.of(resolvedYear, 12, 31).atTime(23, 59, 59);

            List<Object[]> rawMonthly =
                    invoiceRepository.monthlyRevenueBetween(REVENUE_STATUSES, yearFrom, yearTo);

            Map<Integer, BigDecimal> monthMap = new HashMap<>();
            for (Object[] row : rawMonthly) {
                int        m = ((Number) row[0]).intValue();
                BigDecimal t = (BigDecimal) row[1];
                monthMap.put(m, t);
            }
            for (int m = 1; m <= 12; m++) {
                monthlyRows.add(new MonthlyRevenueDto(
                        m, MONTH_NAMES[m - 1], monthMap.getOrDefault(m, BigDecimal.ZERO)));
            }
        }

        BigDecimal yearTotal = monthlyRows.stream()
                .map(MonthlyRevenueDto::total)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new RevenueReport(
                periodLabel, fromDate, toDate,
                categories, grandTotal, totalInvoices,
                monthlyRows, yearTotal, resolvedYear, resolvedMonth);
    }

    // ── Item-level drill-down ─────────────────────────────────────────────────

    /**
     * Returns all individual invoice line-items for the given period + category.
     * Used by the Revenue Items detail page.
     */
    public ItemsReport buildItemsReport(String filterType,
                                        Integer year,
                                        Integer month,
                                        LocalDate day,
                                        LocalDate weekOf,
                                        LocalDate rangeFrom,
                                        LocalDate rangeTo,
                                        LineItemCategory category) {

        int currentYear   = LocalDate.now().getYear();
        int currentMonth  = LocalDate.now().getMonthValue();
        int resolvedYear  = (year  != null) ? year  : currentYear;
        int resolvedMonth = (month != null) ? month : currentMonth;
        String ft = (filterType == null || filterType.isBlank()) ? "month" : filterType;

        LocalDate fromDate;
        LocalDate toDate;
        String    periodLabel;

        switch (ft) {
            case "day" -> {
                LocalDate d = (day != null) ? day : LocalDate.now();
                fromDate    = d;
                toDate      = d;
                periodLabel = d.format(DateTimeFormatter.ofPattern("dd MMMM yyyy"));
            }
            case "week" -> {
                LocalDate ref = (weekOf != null) ? weekOf : LocalDate.now();
                fromDate      = ref.with(DayOfWeek.MONDAY);
                toDate        = ref.with(DayOfWeek.SUNDAY);
                periodLabel   = "Week of "
                        + fromDate.format(DateTimeFormatter.ofPattern("dd MMM"))
                        + " – "
                        + toDate.format(DateTimeFormatter.ofPattern("dd MMM yyyy"));
            }
            case "range" -> {
                fromDate    = (rangeFrom != null) ? rangeFrom : LocalDate.now().withDayOfMonth(1);
                toDate      = (rangeTo   != null) ? rangeTo   : LocalDate.now();
                periodLabel = fromDate.format(DateTimeFormatter.ofPattern("dd MMM yyyy"))
                        + " – "
                        + toDate.format(DateTimeFormatter.ofPattern("dd MMM yyyy"));
            }
            case "year" -> {
                fromDate    = LocalDate.of(resolvedYear, 1, 1);
                toDate      = LocalDate.of(resolvedYear, 12, 31);
                periodLabel = "Year " + resolvedYear;
            }
            default -> { // "month"
                fromDate    = LocalDate.of(resolvedYear, resolvedMonth, 1);
                toDate      = fromDate.withDayOfMonth(fromDate.lengthOfMonth());
                periodLabel = fromDate.format(DateTimeFormatter.ofPattern("MMMM yyyy"));
            }
        }

        LocalDateTime fromDt = fromDate.atStartOfDay();
        LocalDateTime toDt   = toDate.atTime(23, 59, 59);

        List<InvoiceLineItem> rows = invoiceRepository.findLineItemsWithDetails(
                REVENUE_STATUSES, fromDt, toDt, category);

        List<RevenueLineItemDetailDto> items = new ArrayList<>(rows.size());
        BigDecimal grandTotal = BigDecimal.ZERO;

        for (InvoiceLineItem li : rows) {
            var invoice  = li.getInvoice();
            var patient  = invoice.getPatient();
            var user     = invoice.getCreatedByUser(); // may be null

            String patientName = patient.getFirstName() + " " + patient.getLastName();

            String staffName = "—";
            String staffRole = "—";
            if (user != null) {
                var staff = user.getStaff(); // lazy load — fine inside @Transactional
                if (staff != null) {
                    staffName = staff.getFirstName() + " " + staff.getLastName();
                    staffRole = staff.getStaffRole() != null
                            ? staff.getStaffRole().name().replace("_", " ") : "—";
                } else {
                    staffName = user.getUsername();
                    staffRole = user.getRole() != null
                            ? user.getRole().name().replace("_", " ") : "—";
                }
            }

            items.add(new RevenueLineItemDetailDto(
                    invoice.getInvoiceNumber(),
                    invoice.getInvoiceDate(),
                    invoice.getStatus().name(),
                    li.getDescription(),
                    li.getCategory(),
                    li.getQuantity(),
                    li.getUnitPrice(),
                    li.getLineTotal(),
                    patientName,
                    staffName,
                    staffRole));
            grandTotal = grandTotal.add(li.getLineTotal());
        }

        return new ItemsReport(
                periodLabel, labelFor(category), category.name(),
                fromDate, toDate, resolvedYear, resolvedMonth,
                items, grandTotal, items.size());
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private String labelFor(LineItemCategory cat) {
        return switch (cat) {
            case CONSULTATION -> "Consultations & Appointments";
            case LAB          -> "Lab Tests";
            case PHARMACY     -> "Pharmacy";
            case PROCEDURE    -> "Surgery / Procedures";
            case BED          -> "Bed / Ward";
            case RADIOLOGY    -> "Radiology";
            case OTHER        -> "Other";
        };
    }
}
