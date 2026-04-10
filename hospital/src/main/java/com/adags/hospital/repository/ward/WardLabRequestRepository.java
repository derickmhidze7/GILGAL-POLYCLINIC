package com.adags.hospital.repository.ward;

import com.adags.hospital.domain.ward.WardLabRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface WardLabRequestRepository extends JpaRepository<WardLabRequest, UUID> {

    @Query("SELECT r FROM WardLabRequest r LEFT JOIN FETCH r.priceItem LEFT JOIN FETCH r.requestedBy " +
           "WHERE r.wardAssignment.id = :assignmentId ORDER BY r.requestedAt DESC")
    List<WardLabRequest> findByWardAssignmentIdOrderByRequestedAtDesc(@Param("assignmentId") UUID assignmentId);
}
