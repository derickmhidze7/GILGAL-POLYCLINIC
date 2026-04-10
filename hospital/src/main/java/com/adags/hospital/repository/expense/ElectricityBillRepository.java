package com.adags.hospital.repository.expense;

import com.adags.hospital.domain.expense.ElectricityBill;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ElectricityBillRepository extends JpaRepository<ElectricityBill, UUID> {

    Optional<ElectricityBill> findByBillingMonthAndBillingYear(int month, int year);

    List<ElectricityBill> findAllByBillingYearOrderByBillingMonthAsc(int year);
}
