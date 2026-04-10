package com.adags.hospital.domain.expense;

import com.adags.hospital.domain.billing.PaymentMethod;
import com.adags.hospital.domain.common.BaseEntity;
import com.adags.hospital.domain.staff.Staff;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "salary_records")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SalaryRecord extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "staff_id", nullable = false)
    private Staff staff;

    @Column(name = "basic_salary", nullable = false, precision = 14, scale = 2)
    @Builder.Default
    private BigDecimal basicSalary = BigDecimal.ZERO;

    @Column(name = "overtime_hours", nullable = false, precision = 6, scale = 2)
    @Builder.Default
    private BigDecimal overtimeHours = BigDecimal.ZERO;

    @Column(name = "overtime_rate", nullable = false, precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal overtimeRate = BigDecimal.ZERO;

    @Column(name = "total_overtime", nullable = false, precision = 14, scale = 2)
    @Builder.Default
    private BigDecimal totalOvertime = BigDecimal.ZERO;

    @Column(name = "total_payable", nullable = false, precision = 14, scale = 2)
    @Builder.Default
    private BigDecimal totalPayable = BigDecimal.ZERO;

    @Column(name = "pay_period_month", nullable = false)
    private int payPeriodMonth;

    @Column(name = "pay_period_year", nullable = false)
    private int payPeriodYear;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", length = 30)
    private PaymentMethod paymentMethod;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status", nullable = false, length = 20)
    @Builder.Default
    private ExpensePaymentStatus paymentStatus = ExpensePaymentStatus.PENDING;

    @Column(name = "payment_date")
    private LocalDate paymentDate;

    @Column(name = "proof_file_path", length = 500)
    private String proofFilePath;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "expense_id")
    private Expense expense;
}
