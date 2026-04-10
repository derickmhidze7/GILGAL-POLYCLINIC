package com.adags.hospital.repository.expense;

import com.adags.hospital.domain.expense.ExpenseSetting;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ExpenseSettingRepository extends JpaRepository<ExpenseSetting, UUID> {

    Optional<ExpenseSetting> findBySettingKey(String settingKey);
}
