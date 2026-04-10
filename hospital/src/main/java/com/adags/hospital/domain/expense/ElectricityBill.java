package com.adags.hospital.domain.expense;

import com.adags.hospital.domain.common.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "electricity_bills")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ElectricityBill extends BaseEntity {

    @Column(name = "billing_month", nullable = false)
    private int billingMonth;

    @Column(name = "billing_year", nullable = false)
    private int billingYear;

    @Column(name = "units_bought", nullable = false, precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal unitsBought = BigDecimal.ZERO;

    @Column(name = "amount", nullable = false, precision = 14, scale = 2)
    @Builder.Default
    private BigDecimal amount = BigDecimal.ZERO;

    @Column(name = "date_bought", nullable = false)
    private LocalDate dateBought;

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
