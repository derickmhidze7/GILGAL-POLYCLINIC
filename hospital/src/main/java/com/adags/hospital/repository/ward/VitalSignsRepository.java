package com.adags.hospital.repository.ward;

import com.adags.hospital.domain.ward.VitalSigns;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface VitalSignsRepository extends JpaRepository<VitalSigns, UUID> {

    Page<VitalSigns> findByWardAssignmentIdOrderByRecordedAtDesc(UUID wardAssignmentId, Pageable pageable);

    List<VitalSigns> findTop5ByWardAssignmentIdOrderByRecordedAtDesc(UUID wardAssignmentId);

    List<VitalSigns> findByWardAssignmentIdOrderByRecordedAtDesc(UUID wardAssignmentId);

    List<VitalSigns> findByWardAssignmentIdAndHasAlertsTrue(UUID wardAssignmentId);
}
