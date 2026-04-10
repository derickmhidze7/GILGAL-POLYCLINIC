package com.adags.hospital.domain.expense;

import com.adags.hospital.domain.common.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "expense_budgets")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExpenseBudget extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 50)
    private ExpenseCategory category;

    @Column(name = "budget_period", nullable = false, length = 10)
    private String budgetPeriod;  // "MONTHLY" or "ANNUAL"

    @Column(name = "period_month")
    private Integer periodMonth;  // null for ANNUAL

    @Column(name = "period_year", nullable = false)
    private int periodYear;

    @Column(name = "budget_amount", nullable = false, precision = 14, scale = 2)
    @Builder.Default
    private BigDecimal budgetAmount = BigDecimal.ZERO;
}
