package com.adags.hospital.repository.ward;

import com.adags.hospital.domain.ward.WardPrescription;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface WardPrescriptionRepository extends JpaRepository<WardPrescription, UUID> {
    List<WardPrescription> findByWardAssignmentIdAndActiveTrueOrderByPrescribedAtDesc(UUID assignmentId);
}
