package com.adags.hospital.repository.medicalrecord;

import com.adags.hospital.domain.medicalrecord.MedicalRecord;
import com.adags.hospital.domain.medicalrecord.ConsultationStatus;
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
public interface MedicalRecordRepository extends JpaRepository<MedicalRecord, UUID> {

    Page<MedicalRecord> findByPatientId(UUID patientId, Pageable pageable);

    Page<MedicalRecord> findByAttendingDoctorId(UUID doctorId, Pageable pageable);

    @Query("SELECT r FROM MedicalRecord r " +
           "JOIN FETCH r.patient p " +
           "JOIN FETCH r.attendingDoctor d " +
           "WHERE p.id = :patientId AND d.id = :doctorId " +
           "ORDER BY r.visitDate DESC")
    List<MedicalRecord> findByPatientIdAndDoctorId(
            @Param("patientId") UUID patientId,
            @Param("doctorId") UUID doctorId);

    @Query("SELECT r FROM MedicalRecord r " +
           "JOIN FETCH r.patient p " +
           "JOIN FETCH r.attendingDoctor d " +
           "WHERE p.id = :patientId " +
           "ORDER BY r.visitDate DESC")
    List<MedicalRecord> findAllByPatientIdOrdered(@Param("patientId") UUID patientId);

    @Query("SELECT r FROM MedicalRecord r " +
           "JOIN FETCH r.patient p " +
           "JOIN FETCH r.attendingDoctor d " +
           "WHERE d.id = :doctorId AND r.consultationStatus = :status " +
           "ORDER BY r.visitDate DESC")
    List<MedicalRecord> findByDoctorIdAndStatus(
            @Param("doctorId") UUID doctorId,
            @Param("status") ConsultationStatus status);

    Optional<MedicalRecord> findByAppointmentId(UUID appointmentId);

    @Query("SELECT mr FROM MedicalRecord mr " +
           "JOIN FETCH mr.appointment a " +
           "JOIN FETCH a.patient p " +
           "WHERE mr.attendingDoctor.id = :doctorId " +
           "AND mr.consultationStatus = com.adags.hospital.domain.medicalrecord.ConsultationStatus.FINALIZED " +
           "AND (mr.nextStep IS NULL OR mr.nextStep != com.adags.hospital.domain.medicalrecord.DispositionType.DISCHARGED) " +
           "AND p.active = true " +
           "ORDER BY a.scheduledDateTime DESC")
    List<MedicalRecord> findActivePatientsByDoctor(@Param("doctorId") UUID doctorId);

    @Query("SELECT r FROM MedicalRecord r " +
           "JOIN FETCH r.patient p " +
           "JOIN FETCH r.attendingDoctor d " +
           "WHERE d.id = :doctorId AND r.consultationStatus = :status " +
           "ORDER BY r.visitDate ASC")
    List<MedicalRecord> findByDoctorIdAndStatusAsc(
            @Param("doctorId") UUID doctorId,
            @Param("status") ConsultationStatus status);

    /**
     * All OPEN or FINALIZED records for a doctor where the patient is still active.
     * Used by the Prescriptions / Lab Requests pages.
     * Does NOT require an appointment link — covers directly-booked patients too.
     */
    /**
     * All OPEN or FINALIZED records for a doctor where the patient is still active.
     * Used by the Prescriptions / Lab Requests pages.
     * No collection joins — avoids Hibernate MultipleBagFetchException.
     */
    @Query("SELECT r FROM MedicalRecord r " +
           "JOIN FETCH r.patient p " +
           "LEFT JOIN FETCH r.appointment " +
           "WHERE r.attendingDoctor.id = :doctorId " +
           "AND r.consultationStatus IN :statuses " +
           "AND p.active = true " +
           "ORDER BY r.visitDate ASC")
    List<MedicalRecord> findActiveByDoctorAndStatuses(
            @Param("doctorId") UUID doctorId,
            @Param("statuses") Collection<ConsultationStatus> statuses);

    /** Batch-load medical records that have a forwarded-to doctor set, for the given appointment IDs.
     *  Used by the receptionist view to detect specialist-forwarded appointments. */
    @Query("SELECT m FROM MedicalRecord m " +
           "LEFT JOIN FETCH m.forwardedToDoctor " +
           "WHERE m.appointment.id IN :appointmentIds " +
           "AND m.forwardedToDoctor IS NOT NULL")
    List<MedicalRecord> findByAppointmentIdInWithForwardedDoctor(
            @Param("appointmentIds") Collection<UUID> appointmentIds);
}
