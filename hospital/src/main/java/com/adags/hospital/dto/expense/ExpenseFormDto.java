package com.adags.hospital.dto.expense;

import com.adags.hospital.domain.billing.PaymentMethod;
import com.adags.hospital.domain.expense.ExpenseCategory;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDate;

public class ExpenseFormDto {

    private String id; // null for new, UUID string for edit

    @NotBlank(message = "Title is required")
    @Size(max = 255)
    private String title;

    @NotNull(message = "Category is required")
    private ExpenseCategory category;

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be greater than zero")
    private BigDecimal amount;

    @NotNull(message = "Expense date is required")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate expenseDate;

    @NotNull(message = "Payment method is required")
    private PaymentMethod paymentMethod;

    @Size(max = 255)
    private String paidTo;

    @Size(max = 100)
    private String receiptNumber;

    private String notes;

    private boolean recurring;

    private String recurringFrequency;

    // File upload (optional)
    private MultipartFile receiptFile;

    // Derived (set by controller from date)
    private int financialYear;
    private int billingMonth;

    // ── Getters & Setters ──────────────────────────────────────────────────────

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public ExpenseCategory getCategory() { return category; }
    public void setCategory(ExpenseCategory category) { this.category = category; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public LocalDate getExpenseDate() { return expenseDate; }
    public void setExpenseDate(LocalDate expenseDate) { this.expenseDate = expenseDate; }

    public PaymentMethod getPaymentMethod() { return paymentMethod; }
    public void setPaymentMethod(PaymentMethod paymentMethod) { this.paymentMethod = paymentMethod; }

    public String getPaidTo() { return paidTo; }
    public void setPaidTo(String paidTo) { this.paidTo = paidTo; }

    public String getReceiptNumber() { return receiptNumber; }
    public void setReceiptNumber(String receiptNumber) { this.receiptNumber = receiptNumber; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public boolean isRecurring() { return recurring; }
    public void setRecurring(boolean recurring) { this.recurring = recurring; }

    public String getRecurringFrequency() { return recurringFrequency; }
    public void setRecurringFrequency(String recurringFrequency) { this.recurringFrequency = recurringFrequency; }

    public MultipartFile getReceiptFile() { return receiptFile; }
    public void setReceiptFile(MultipartFile receiptFile) { this.receiptFile = receiptFile; }

    public int getFinancialYear() { return financialYear; }
    public void setFinancialYear(int financialYear) { this.financialYear = financialYear; }

    public int getBillingMonth() { return billingMonth; }
    public void setBillingMonth(int billingMonth) { this.billingMonth = billingMonth; }
}
