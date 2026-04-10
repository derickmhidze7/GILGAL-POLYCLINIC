package com.adags.hospital.domain.expense;

import com.adags.hospital.domain.common.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "water_bills")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WaterBill extends BaseEntity {

    @Column(name = "billing_month", nullable = false)
    private int billingMonth;

    @Column(name = "billing_year", nullable = false)
    private int billingYear;

    @Column(name = "previous_reading", nullable = false, precision = 10, scale = 3)
    @Builder.Default
    private BigDecimal previousReading = BigDecimal.ZERO;

    @Column(name = "current_reading", nullable = false, precision = 10, scale = 3)
    @Builder.Default
    private BigDecimal currentReading = BigDecimal.ZERO;

    @Column(name = "units_consumed", nullable = false, precision = 10, scale = 3)
    @Builder.Default
    private BigDecimal unitsConsumed = BigDecimal.ZERO;

    @Column(name = "rate_per_unit", nullable = false, precision = 10, scale = 4)
    @Builder.Default
    private BigDecimal ratePerUnit = new BigDecimal("500.0000");

    @Column(name = "total_amount", nullable = false, precision = 14, scale = 2)
    @Builder.Default
    private BigDecimal totalAmount = BigDecimal.ZERO;

    @Column(name = "bill_reference", length = 100)
    private String billReference;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status", nullable = false, length = 20)
    @Builder.Default
    private ExpensePaymentStatus paymentStatus = ExpensePaymentStatus.UNPAID;

    @Column(name = "bill_file_path", length = 500)
    private String billFilePath;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "expense_id")
    private Expense expense;
}
