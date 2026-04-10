package com.adags.hospital.repository.lab;

import com.adags.hospital.domain.lab.LabResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface LabResultRepository extends JpaRepository<LabResult, UUID> {

    Optional<LabResult> findByLabRequestId(UUID labRequestId);

    @Query("SELECT r FROM LabResult r " +
           "JOIN FETCH r.labRequest req " +
           "JOIN FETCH req.patient " +
           "LEFT JOIN FETCH r.performedBy " +
           "LEFT JOIN FETCH r.verifiedBy " +
           "LEFT JOIN FETCH r.parameters " +
           "WHERE r.submitted = true " +
           "ORDER BY r.resultDateTime DESC")
    List<LabResult> findAllSubmittedWithDetails();

    @Query("SELECT r FROM LabResult r " +
           "JOIN FETCH r.labRequest req " +
           "JOIN FETCH req.patient " +
           "LEFT JOIN FETCH r.performedBy " +
           "LEFT JOIN FETCH r.parameters " +
           "WHERE r.performedBy.id = :staffId " +
           "ORDER BY r.resultDateTime DESC")
    List<LabResult> findByPerformedByIdWithDetails(@Param("staffId") UUID staffId);

    @Query("SELECT r FROM LabResult r " +
           "JOIN FETCH r.labRequest req " +
           "JOIN FETCH req.patient " +
           "LEFT JOIN FETCH req.requestingDoctor " +
           "LEFT JOIN FETCH r.performedBy " +
           "LEFT JOIN FETCH r.verifiedBy " +
           "LEFT JOIN FETCH r.parameters " +
           "WHERE r.id = :resultId")
    Optional<LabResult> findByIdWithAllDetails(@Param("resultId") UUID resultId);
}
