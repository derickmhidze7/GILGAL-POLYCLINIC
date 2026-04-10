package com.adags.hospital.repository.expense;

import com.adags.hospital.domain.expense.SalaryRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Repository
public interface SalaryRecordRepository extends JpaRepository<SalaryRecord, UUID> {

    List<SalaryRecord> findAllByPayPeriodYearAndPayPeriodMonthOrderByStaff_LastNameAsc(int year, int month);

    @Query("SELECT COALESCE(SUM(sr.totalPayable), 0) FROM SalaryRecord sr WHERE sr.payPeriodYear = :year AND sr.payPeriodMonth = :month")
    BigDecimal sumTotalPayableByPeriod(@Param("year") int year, @Param("month") int month);

    @Query("SELECT COALESCE(SUM(sr.totalOvertime), 0) FROM SalaryRecord sr WHERE sr.payPeriodYear = :year AND sr.payPeriodMonth = :month")
    BigDecimal sumOvertimeByPeriod(@Param("year") int year, @Param("month") int month);
}
