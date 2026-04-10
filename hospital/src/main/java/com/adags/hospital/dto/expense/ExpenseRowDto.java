package com.adags.hospital.dto.expense;

import java.math.BigDecimal;
import java.time.LocalDate;

public class ExpenseRowDto {

    private String id;
    private LocalDate expenseDate;
    private String title;
    private String categoryLabel;
    private String categoryBadgeCss;
    private BigDecimal amount;
    private String paidTo;
    private String statusLabel;
    private String statusBadgeCss;
    private boolean canEdit;
    private boolean canApprove;
    private boolean hasReceipt;

    public ExpenseRowDto() {}

    public ExpenseRowDto(String id, LocalDate expenseDate, String title, String categoryLabel,
                         String categoryBadgeCss, BigDecimal amount, String paidTo,
                         String statusLabel, String statusBadgeCss,
                         boolean canEdit, boolean canApprove, boolean hasReceipt) {
        this.id = id;
        this.expenseDate = expenseDate;
        this.title = title;
        this.categoryLabel = categoryLabel;
        this.categoryBadgeCss = categoryBadgeCss;
        this.amount = amount;
        this.paidTo = paidTo;
        this.statusLabel = statusLabel;
        this.statusBadgeCss = statusBadgeCss;
        this.canEdit = canEdit;
        this.canApprove = canApprove;
        this.hasReceipt = hasReceipt;
    }

    // ── Getters & Setters ──────────────────────────────────────────────────────

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public LocalDate getExpenseDate() { return expenseDate; }
    public void setExpenseDate(LocalDate expenseDate) { this.expenseDate = expenseDate; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getCategoryLabel() { return categoryLabel; }
    public void setCategoryLabel(String categoryLabel) { this.categoryLabel = categoryLabel; }

    public String getCategoryBadgeCss() { return categoryBadgeCss; }
    public void setCategoryBadgeCss(String categoryBadgeCss) { this.categoryBadgeCss = categoryBadgeCss; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public String getPaidTo() { return paidTo; }
    public void setPaidTo(String paidTo) { this.paidTo = paidTo; }

    public String getStatusLabel() { return statusLabel; }
    public void setStatusLabel(String statusLabel) { this.statusLabel = statusLabel; }

    public String getStatusBadgeCss() { return statusBadgeCss; }
    public void setStatusBadgeCss(String statusBadgeCss) { this.statusBadgeCss = statusBadgeCss; }

    public boolean isCanEdit() { return canEdit; }
    public void setCanEdit(boolean canEdit) { this.canEdit = canEdit; }

    public boolean isCanApprove() { return canApprove; }
    public void setCanApprove(boolean canApprove) { this.canApprove = canApprove; }

    public boolean isHasReceipt() { return hasReceipt; }
    public void setHasReceipt(boolean hasReceipt) { this.hasReceipt = hasReceipt; }
}
