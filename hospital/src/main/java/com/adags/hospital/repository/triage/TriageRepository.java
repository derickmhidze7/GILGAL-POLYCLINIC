package com.adags.hospital.repository.triage;

import com.adags.hospital.domain.triage.TriageAssessment;
import com.adags.hospital.domain.triage.TriagePriority;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TriageRepository extends JpaRepository<TriageAssessment, UUID> {

    Page<TriageAssessment> findByPatientId(UUID patientId, Pageable pageable);

    Optional<TriageAssessment> findByAppointmentId(UUID appointmentId);

    List<TriageAssessment> findByTriagePriority(TriagePriority priority);

    @Query("SELECT t FROM TriageAssessment t " +
           "LEFT JOIN FETCH t.patient " +
           "LEFT JOIN FETCH t.nurse " +
           "WHERE t.nurse.id = :nurseId " +
           "ORDER BY t.assessmentDateTime DESC")
    List<TriageAssessment> findByNurseIdWithDetails(@Param("nurseId") UUID nurseId);

    /** Batch-load triage assessments that have a referral doctor set, for the given appointment IDs.
     *  Used by the receptionist view to detect specialist-forwarded appointments. */
    @Query("SELECT t FROM TriageAssessment t " +
           "LEFT JOIN FETCH t.referredDoctor " +
           "LEFT JOIN FETCH t.consultationInvoice " +
           "WHERE t.appointment.id IN :appointmentIds " +
           "AND t.referredDoctor IS NOT NULL")
    List<TriageAssessment> findByAppointmentIdInWithReferral(
            @Param("appointmentIds") Collection<UUID> appointmentIds);
}
