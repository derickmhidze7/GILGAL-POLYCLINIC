package com.adags.hospital.repository.ward;

import com.adags.hospital.domain.ward.WoundCareNote;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface WoundCareNoteRepository extends JpaRepository<WoundCareNote, UUID> {

    Page<WoundCareNote> findByWardAssignmentIdOrderByRecordedAtDesc(UUID wardAssignmentId, Pageable pageable);

    List<WoundCareNote> findByWardAssignmentIdAndSignsOfInfectionTrue(UUID wardAssignmentId);

    List<WoundCareNote> findTop3ByWardAssignmentIdOrderByRecordedAtDesc(UUID wardAssignmentId);

    List<WoundCareNote> findByWardAssignmentIdOrderByRecordedAtDesc(UUID wardAssignmentId);
}
