package com.adags.hospital.repository.expense;

import com.adags.hospital.domain.expense.ExpenseBudget;
import com.adags.hospital.domain.expense.ExpenseCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ExpenseBudgetRepository extends JpaRepository<ExpenseBudget, UUID> {

    Optional<ExpenseBudget> findByCategoryAndBudgetPeriodAndPeriodYearAndPeriodMonth(
            ExpenseCategory category, String budgetPeriod, int periodYear, Integer periodMonth);

    List<ExpenseBudget> findAllByBudgetPeriodAndPeriodYear(String budgetPeriod, int year);

    List<ExpenseBudget> findAllByBudgetPeriodAndPeriodYearAndPeriodMonth(
            String budgetPeriod, int year, int month);
}
