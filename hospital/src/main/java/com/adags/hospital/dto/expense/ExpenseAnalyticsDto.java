package com.adags.hospital.dto.expense;

import java.math.BigDecimal;
import java.util.List;

public class ExpenseAnalyticsDto {

    // ── Summary stats ─────────────────────────────────────────────────────────
    private BigDecimal totalThisMonth;
    private BigDecimal totalThisYear;
    private long pendingApprovals;
    private long rejectedCount;

    // ── Charts ───────────────────────────────────────────────────────────────
    private List<CategoryTotal> categoryBreakdown;
    private List<MonthlyTrend>  monthlyTrend;

    public ExpenseAnalyticsDto() {}

    // ── Nested records ────────────────────────────────────────────────────────

    public record CategoryTotal(String label, BigDecimal total, String colour) {}

    public record MonthlyTrend(String monthLabel, BigDecimal total) {}

    // ── Getters & Setters ─────────────────────────────────────────────────────

    public BigDecimal getTotalThisMonth() { return totalThisMonth; }
    public void setTotalThisMonth(BigDecimal v) { this.totalThisMonth = v; }

    public BigDecimal getTotalThisYear() { return totalThisYear; }
    public void setTotalThisYear(BigDecimal v) { this.totalThisYear = v; }

    public long getPendingApprovals() { return pendingApprovals; }
    public void setPendingApprovals(long v) { this.pendingApprovals = v; }

    public long getRejectedCount() { return rejectedCount; }
    public void setRejectedCount(long v) { this.rejectedCount = v; }

    public List<CategoryTotal> getCategoryBreakdown() { return categoryBreakdown; }
    public void setCategoryBreakdown(List<CategoryTotal> v) { this.categoryBreakdown = v; }

    public List<MonthlyTrend> getMonthlyTrend() { return monthlyTrend; }
    public void setMonthlyTrend(List<MonthlyTrend> v) { this.monthlyTrend = v; }
}
