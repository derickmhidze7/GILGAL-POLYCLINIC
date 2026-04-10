package com.adags.hospital.repository.ward;

import com.adags.hospital.domain.ward.MedicationAdministrationRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface MedicationAdministrationRecordRepository extends JpaRepository<MedicationAdministrationRecord, UUID> {

    Page<MedicationAdministrationRecord> findByWardAssignmentIdOrderByScheduledTimeDesc(
            UUID wardAssignmentId, Pageable pageable);

    List<MedicationAdministrationRecord> findByWardAssignmentIdAndWasGivenFalse(UUID wardAssignmentId);

    List<MedicationAdministrationRecord> findByWardAssignmentId(UUID wardAssignmentId);
}
