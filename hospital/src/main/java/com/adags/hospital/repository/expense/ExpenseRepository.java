package com.adags.hospital.repository.expense;

import com.adags.hospital.domain.expense.Expense;
import com.adags.hospital.domain.expense.ExpenseCategory;
import com.adags.hospital.domain.expense.ExpenseStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public interface ExpenseRepository extends JpaRepository<Expense, UUID> {

    // ── Filtered list ─────────────────────────────────────────────────────────
    @Query("""
            SELECT e FROM Expense e
            WHERE (:fromDate IS NULL OR e.expenseDate >= :fromDate)
              AND (:toDate   IS NULL OR e.expenseDate <= :toDate)
              AND (:category IS NULL OR e.category = :category)
              AND (:status   IS NULL OR e.status   = :status)
            ORDER BY e.expenseDate DESC, e.createdAt DESC
            """)
    Page<Expense> findFiltered(
            @Param("fromDate")   LocalDate fromDate,
            @Param("toDate")     LocalDate toDate,
            @Param("category")   ExpenseCategory category,
            @Param("status")     ExpenseStatus status,
            Pageable pageable);

    @Query("""
            SELECT e FROM Expense e
            WHERE (:fromDate IS NULL OR e.expenseDate >= :fromDate)
              AND (:toDate   IS NULL OR e.expenseDate <= :toDate)
              AND e.category IN :categories
              AND (:status   IS NULL OR e.status   = :status)
            ORDER BY e.expenseDate DESC, e.createdAt DESC
            """)
    Page<Expense> findFilteredByCategories(
            @Param("fromDate")   LocalDate fromDate,
            @Param("toDate")     LocalDate toDate,
            @Param("categories") List<ExpenseCategory> categories,
            @Param("status")     ExpenseStatus status,
            Pageable pageable);

    // ── Aggregates ────────────────────────────────────────────────────────────
    @Query("SELECT SUM(e.amount) FROM Expense e WHERE e.expenseDate = :date AND e.status <> 'REJECTED'")
    BigDecimal sumAmountByDate(@Param("date") LocalDate date);

    @Query("SELECT SUM(e.amount) FROM Expense e WHERE e.expenseDate BETWEEN :from AND :to AND e.status <> 'REJECTED'")
    BigDecimal sumAmountBetween(@Param("from") LocalDate from, @Param("to") LocalDate to);

    // ── Category breakdown ────────────────────────────────────────────────────
    @Query("""
            SELECT e.category, SUM(e.amount)
            FROM Expense e
            WHERE e.expenseDate BETWEEN :from AND :to
              AND e.status <> 'REJECTED'
            GROUP BY e.category
            ORDER BY SUM(e.amount) DESC
            """)
    List<Object[]> sumByCategoryBetween(@Param("from") LocalDate from, @Param("to") LocalDate to);

    // ── Monthly trend (last N months) ─────────────────────────────────────────
    @Query("""
            SELECT e.financialYear, e.billingMonth, SUM(e.amount)
            FROM Expense e
            WHERE e.expenseDate BETWEEN :from AND :to
              AND e.status <> 'REJECTED'
            GROUP BY e.financialYear, e.billingMonth
            ORDER BY e.financialYear, e.billingMonth
            """)
    List<Object[]> monthlyTrendBetween(@Param("from") LocalDate from, @Param("to") LocalDate to);

    // ── Status counts ─────────────────────────────────────────────────────────
    long countByStatus(ExpenseStatus status);

    // ── Period totals ─────────────────────────────────────────────────────────
    @Query("""
            SELECT COALESCE(SUM(e.amount), 0)
            FROM Expense e
            WHERE e.financialYear = :year AND e.billingMonth = :month
              AND e.status <> 'REJECTED'
            """)
    BigDecimal sumByYearAndMonth(@Param("year") int year, @Param("month") int month);
}
