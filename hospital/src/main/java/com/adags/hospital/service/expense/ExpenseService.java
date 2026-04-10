package com.adags.hospital.service.expense;

import com.adags.hospital.domain.expense.*;
import com.adags.hospital.dto.expense.ExpenseAnalyticsDto;
import com.adags.hospital.dto.expense.ExpenseFormDto;
import com.adags.hospital.dto.expense.ExpenseRowDto;
import com.adags.hospital.repository.expense.*;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Month;
import java.time.format.TextStyle;
import java.util.*;

@Service
public class ExpenseService {

    private static final Logger log = LoggerFactory.getLogger(ExpenseService.class);

    // Category → Bootstrap badge colour (bg-*)
    private static final Map<ExpenseCategory, String> CATEGORY_COLOURS = Map.of(
            ExpenseCategory.SALARIES,          "bg-primary",
            ExpenseCategory.UTILITIES,         "bg-info",
            ExpenseCategory.MEDICAL_SUPPLIES,  "bg-success",
            ExpenseCategory.EQUIPMENT,         "bg-warning text-dark",
            ExpenseCategory.MAINTENANCE,       "bg-secondary",
            ExpenseCategory.LAB_SUPPLIES,      "bg-dark",
            ExpenseCategory.PHARMACY_RESTOCK,  "bg-danger",
            ExpenseCategory.TRANSPORT,         "bg-light text-dark",
            ExpenseCategory.CLEANING,          "bg-info text-dark",
            ExpenseCategory.MISCELLANEOUS,     "bg-secondary"
    );

    // Chart.js colours for category breakdown
    private static final List<String> CHART_COLOURS = List.of(
            "#4e73df","#1cc88a","#36b9cc","#f6c23e","#e74a3b",
            "#858796","#5a5c69","#2e59d9","#17a673","#2c9faf"
    );

    @Value("${app.upload.path:uploads}")
    private String uploadBasePath;

    private final ExpenseRepository          expenseRepo;
    private final ExpenseSettingRepository   settingRepo;
    private final ExpenseBudgetRepository    budgetRepo;

    public ExpenseService(ExpenseRepository expenseRepo,
                          ExpenseSettingRepository settingRepo,
                          ExpenseBudgetRepository budgetRepo) {
        this.expenseRepo = expenseRepo;
        this.settingRepo = settingRepo;
        this.budgetRepo  = budgetRepo;
    }

    // ── Create ────────────────────────────────────────────────────────────────

    @Transactional
    public Expense createExpense(ExpenseFormDto dto, String recordedBy, MultipartFile file) {
        Expense expense = new Expense();
        mapDtoToEntity(dto, expense);
        expense.setRecordedBy(recordedBy);
        expense.setSourceType(ExpenseSourceType.MANUAL);

        BigDecimal threshold = getApprovalThreshold();
        if (expense.getAmount().compareTo(threshold) > 0) {
            expense.setStatus(ExpenseStatus.PENDING_APPROVAL);
        } else {
            expense.setStatus(ExpenseStatus.APPROVED);
            expense.setApprovedBy("AUTO");
            expense.setApprovedAt(LocalDateTime.now());
        }

        if (file != null && !file.isEmpty()) {
            expense.setReceiptFilePath(saveFile(file, expense.getFinancialYear(), expense.getBillingMonth()));
        }
        return expenseRepo.save(expense);
    }

    // ── Update ────────────────────────────────────────────────────────────────

    @Transactional
    public Expense updateExpense(UUID id, ExpenseFormDto dto, String updatedBy, MultipartFile file) {
        Expense expense = expenseRepo.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Expense not found: " + id));
        if (expense.isLocked()) {
            throw new IllegalStateException("Approved expense cannot be edited.");
        }
        mapDtoToEntity(dto, expense);

        if (file != null && !file.isEmpty()) {
            expense.setReceiptFilePath(saveFile(file, expense.getFinancialYear(), expense.getBillingMonth()));
        }
        return expenseRepo.save(expense);
    }

    // ── Approve ───────────────────────────────────────────────────────────────

    @Transactional
    public void approveExpense(UUID id, String approvedBy) {
        Expense expense = expenseRepo.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Expense not found: " + id));
        expense.setStatus(ExpenseStatus.APPROVED);
        expense.setApprovedBy(approvedBy);
        expense.setApprovedAt(LocalDateTime.now());
        expense.setLocked(true);
        expenseRepo.save(expense);
    }

    // ── Reject ────────────────────────────────────────────────────────────────

    @Transactional
    public void rejectExpense(UUID id, String reason, String rejectedBy) {
        Expense expense = expenseRepo.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Expense not found: " + id));
        expense.setStatus(ExpenseStatus.REJECTED);
        expense.setApprovalNotes(reason);
        expense.setApprovedBy(rejectedBy);
        expense.setApprovedAt(LocalDateTime.now());
        expenseRepo.save(expense);
    }

    // ── Delete ────────────────────────────────────────────────────────────────

    @Transactional
    public void deleteExpense(UUID id) {
        Expense expense = expenseRepo.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Expense not found: " + id));
        if (expense.isLocked()) {
            throw new IllegalStateException("Locked expense cannot be deleted.");
        }
        expenseRepo.delete(expense);
    }

    // ── Find for edit form ────────────────────────────────────────────────────

    public Expense findById(UUID id) {
        return expenseRepo.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Expense not found: " + id));
    }

    // ── Filtered page (list view) ─────────────────────────────────────────────

    public Page<ExpenseRowDto> getFilteredPage(LocalDate fromDate, LocalDate toDate,
                                               ExpenseCategory category, ExpenseStatus status,
                                               int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return expenseRepo.findFiltered(fromDate, toDate, category, status, pageable)
                .map(this::toRowDto);
    }

    public Page<ExpenseRowDto> getFilteredPageByCategories(LocalDate fromDate, LocalDate toDate,
                                                           List<ExpenseCategory> categories,
                                                           ExpenseStatus status,
                                                           int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        if (categories == null || categories.isEmpty()) return Page.empty(pageable);
        return expenseRepo.findFilteredByCategories(fromDate, toDate, categories, status, pageable)
                .map(this::toRowDto);
    }

    // ── Analytics ─────────────────────────────────────────────────────────────

    public ExpenseAnalyticsDto getAnalytics() {
        LocalDate now         = LocalDate.now();
        LocalDate monthStart  = now.withDayOfMonth(1);
        LocalDate yearStart   = now.withDayOfYear(1);

        ExpenseAnalyticsDto dto = new ExpenseAnalyticsDto();

        BigDecimal monthTotal = expenseRepo.sumAmountBetween(monthStart, now);
        BigDecimal yearTotal  = expenseRepo.sumAmountBetween(yearStart, now);
        dto.setTotalThisMonth(monthTotal != null ? monthTotal : BigDecimal.ZERO);
        dto.setTotalThisYear(yearTotal   != null ? yearTotal  : BigDecimal.ZERO);
        dto.setPendingApprovals(expenseRepo.countByStatus(ExpenseStatus.PENDING_APPROVAL));
        dto.setRejectedCount(expenseRepo.countByStatus(ExpenseStatus.REJECTED));

        // Category breakdown for the current year
        List<Object[]> catRows = expenseRepo.sumByCategoryBetween(yearStart, now);
        List<ExpenseAnalyticsDto.CategoryTotal> catTotals = new ArrayList<>();
        int colIdx = 0;
        for (Object[] row : catRows) {
            ExpenseCategory cat = (ExpenseCategory) row[0];
            BigDecimal total    = (BigDecimal) row[1];
            String colour = CHART_COLOURS.get(colIdx % CHART_COLOURS.size());
            catTotals.add(new ExpenseAnalyticsDto.CategoryTotal(cat.getLabel(), total, colour));
            colIdx++;
        }
        dto.setCategoryBreakdown(catTotals);

        // Monthly trend — last 12 months
        LocalDate trendFrom = now.minusMonths(11).withDayOfMonth(1);
        List<Object[]> trendRows = expenseRepo.monthlyTrendBetween(trendFrom, now);
        Map<String, BigDecimal> trendMap = new LinkedHashMap<>();
        for (int i = 11; i >= 0; i--) {
            LocalDate m = now.minusMonths(i);
            String key = m.getYear() + "-" + String.format("%02d", m.getMonthValue());
            trendMap.put(key, BigDecimal.ZERO);
        }
        for (Object[] row : trendRows) {
            int yr  = (int) row[0];
            int mo  = (int) row[1];
            BigDecimal total = (BigDecimal) row[2];
            String key = yr + "-" + String.format("%02d", mo);
            trendMap.put(key, total);
        }
        List<ExpenseAnalyticsDto.MonthlyTrend> trend = new ArrayList<>();
        for (Map.Entry<String, BigDecimal> e : trendMap.entrySet()) {
            String[] parts = e.getKey().split("-");
            int yr = Integer.parseInt(parts[0]);
            int mo = Integer.parseInt(parts[1]);
            String label = Month.of(mo).getDisplayName(TextStyle.SHORT, Locale.ENGLISH) + " " + yr;
            trend.add(new ExpenseAnalyticsDto.MonthlyTrend(label, e.getValue()));
        }
        dto.setMonthlyTrend(trend);

        return dto;
    }

    // ── Summary stats used by list header cards ───────────────────────────────

    public Map<String, Object> getSummaryStats() {
        LocalDate today = LocalDate.now();
        Map<String, Object> stats = new LinkedHashMap<>();
        BigDecimal todayTotal = expenseRepo.sumAmountByDate(today);
        BigDecimal weekTotal  = expenseRepo.sumAmountBetween(today.minusDays(6), today);
        BigDecimal monthTotal = expenseRepo.sumAmountBetween(today.withDayOfMonth(1), today);
        stats.put("todayTotal",        todayTotal  != null ? todayTotal  : BigDecimal.ZERO);
        stats.put("weekTotal",         weekTotal   != null ? weekTotal   : BigDecimal.ZERO);
        stats.put("monthTotal",        monthTotal  != null ? monthTotal  : BigDecimal.ZERO);
        stats.put("pendingApprovals",  expenseRepo.countByStatus(ExpenseStatus.PENDING_APPROVAL));
        return stats;
    }

    // ── Approval threshold ────────────────────────────────────────────────────

    public BigDecimal getApprovalThreshold() {
        return settingRepo.findBySettingKey("APPROVAL_THRESHOLD")
                .map(s -> new BigDecimal(s.getSettingValue()))
                .orElse(new BigDecimal("500000"));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void mapDtoToEntity(ExpenseFormDto dto, Expense expense) {
        expense.setTitle(dto.getTitle());
        expense.setCategory(dto.getCategory());
        expense.setAmount(dto.getAmount());
        expense.setExpenseDate(dto.getExpenseDate());
        expense.setPaymentMethod(dto.getPaymentMethod());
        expense.setPaidTo(dto.getPaidTo());
        expense.setReceiptNumber(dto.getReceiptNumber());
        expense.setNotes(dto.getNotes());
        expense.setRecurring(dto.isRecurring());
        if (dto.isRecurring() && StringUtils.hasText(dto.getRecurringFrequency())) {
            expense.setRecurringFrequency(
                    RecurringFrequency.valueOf(dto.getRecurringFrequency()));
        } else {
            expense.setRecurringFrequency(null);
        }
        LocalDate d = dto.getExpenseDate();
        expense.setFinancialYear(d.getYear());
        expense.setBillingMonth(d.getMonthValue());
    }

    private String saveFile(MultipartFile file, int year, int month) {
        try {
            Path dir = Paths.get(uploadBasePath, "expenses", String.valueOf(year),
                    String.format("%02d", month));
            Files.createDirectories(dir);
            String ext      = getExtension(Objects.requireNonNull(file.getOriginalFilename()));
            String fileName = UUID.randomUUID() + ext;
            Files.copy(file.getInputStream(), dir.resolve(fileName));
            return Paths.get("expenses", String.valueOf(year),
                    String.format("%02d", month), fileName).toString().replace("\\", "/");
        } catch (IOException e) {
            log.error("Failed to save receipt file", e);
            return null;
        }
    }

    private String getExtension(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot >= 0 ? filename.substring(dot) : "";
    }

    private ExpenseRowDto toRowDto(Expense e) {
        String catCss    = CATEGORY_COLOURS.getOrDefault(e.getCategory(), "bg-secondary");
        String statusCss = switch (e.getStatus()) {
            case APPROVED         -> "bg-success";
            case PENDING_APPROVAL -> "bg-warning text-dark";
            case REJECTED         -> "bg-danger";
            case DRAFT            -> "bg-light text-dark";
        };
        return new ExpenseRowDto(
                e.getId().toString(),
                e.getExpenseDate(),
                e.getTitle(),
                e.getCategory().getLabel(),
                catCss,
                e.getAmount(),
                e.getPaidTo(),
                e.getStatus().getLabel(),
                statusCss,
                !e.isLocked(),
                e.getStatus() == ExpenseStatus.PENDING_APPROVAL,
                e.getReceiptFilePath() != null
        );
    }
}
