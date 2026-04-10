package com.adags.hospital.domain.expense;

import com.adags.hospital.domain.billing.PaymentMethod;
import com.adags.hospital.domain.common.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "expenses")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Expense extends BaseEntity {

    @Column(name = "title", nullable = false, length = 255)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 50)
    private ExpenseCategory category;

    @Column(name = "amount", nullable = false, precision = 14, scale = 2)
    @Builder.Default
    private BigDecimal amount = BigDecimal.ZERO;

    @Column(name = "expense_date", nullable = false)
    private LocalDate expenseDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", length = 30)
    private PaymentMethod paymentMethod;

    @Column(name = "paid_to", length = 200)
    private String paidTo;

    @Column(name = "receipt_number", length = 100)
    private String receiptNumber;

    @Column(name = "receipt_file_path", length = 500)
    private String receiptFilePath;

    @Column(name = "recorded_by", nullable = false, length = 100)
    private String recordedBy;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    @Builder.Default
    private ExpenseStatus status = ExpenseStatus.PENDING_APPROVAL;

    @Column(name = "approved_by", length = 100)
    private String approvedBy;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @Column(name = "approval_notes", columnDefinition = "TEXT")
    private String approvalNotes;

    @Column(name = "is_recurring", nullable = false)
    @Builder.Default
    private boolean recurring = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "recurring_frequency", length = 20)
    private RecurringFrequency recurringFrequency;

    @Column(name = "financial_year", nullable = false)
    private int financialYear;

    @Column(name = "billing_month", nullable = false)
    private int billingMonth;

    @Column(name = "is_locked", nullable = false)
    @Builder.Default
    private boolean locked = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 30)
    @Builder.Default
    private ExpenseSourceType sourceType = ExpenseSourceType.MANUAL;
}
