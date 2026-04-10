package com.adags.hospital.repository.expense;

import com.adags.hospital.domain.expense.WaterBill;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface WaterBillRepository extends JpaRepository<WaterBill, UUID> {

    Optional<WaterBill> findByBillingMonthAndBillingYear(int month, int year);

    List<WaterBill> findAllByBillingYearOrderByBillingMonthAsc(int year);
}
