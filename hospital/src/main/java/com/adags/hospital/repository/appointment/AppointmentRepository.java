package com.adags.hospital.repository.appointment;

import com.adags.hospital.domain.appointment.Appointment;
import com.adags.hospital.domain.appointment.AppointmentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AppointmentRepository extends JpaRepository<Appointment, UUID> {

    Page<Appointment> findByPatientId(UUID patientId, Pageable pageable);

    Page<Appointment> findByDoctorId(UUID doctorId, Pageable pageable);

    Page<Appointment> findByStatus(AppointmentStatus status, Pageable pageable);

    @Query("SELECT a FROM Appointment a WHERE a.scheduledDateTime BETWEEN :from AND :to")
    List<Appointment> findByScheduledDateTimeBetween(LocalDateTime from, LocalDateTime to);

    @Query("SELECT a FROM Appointment a WHERE a.doctor.id = :doctorId AND a.scheduledDateTime BETWEEN :from AND :to")
    List<Appointment> findByDoctorIdAndScheduledDateTimeBetween(UUID doctorId, LocalDateTime from, LocalDateTime to);

    @Query("SELECT a FROM Appointment a JOIN FETCH a.patient WHERE a.id = :id")
    Optional<Appointment> findByIdWithPatient(UUID id);

    /** Patients awaiting triage assessment (not yet assessed). */
    @Query("SELECT a FROM Appointment a JOIN FETCH a.patient " +
           "WHERE a.appointmentType = com.adags.hospital.domain.appointment.AppointmentType.TRIAGE " +
           "AND a.status IN (com.adags.hospital.domain.appointment.AppointmentStatus.SCHEDULED, " +
           "com.adags.hospital.domain.appointment.AppointmentStatus.CONFIRMED) " +
           "ORDER BY a.scheduledDateTime ASC")
    List<Appointment> findActiveTriageQueue();

    /**
     * Patients already assessed by triage (IN_PROGRESS) — awaiting payment / doctor.
     * Excludes patients whose consultation invoice has already been paid
     * (those patients have moved to the doctor and appear in the "With Doctor" section).
     */
    @Query("SELECT a FROM Appointment a JOIN FETCH a.patient " +
           "WHERE a.appointmentType = com.adags.hospital.domain.appointment.AppointmentType.TRIAGE " +
           "AND a.status = com.adags.hospital.domain.appointment.AppointmentStatus.IN_PROGRESS " +
           "AND NOT EXISTS (" +
           "  SELECT t FROM com.adags.hospital.domain.triage.TriageAssessment t " +
           "  WHERE t.appointment = a " +
           "  AND t.consultationInvoice IS NOT NULL " +
           "  AND t.consultationInvoice.status = com.adags.hospital.domain.billing.InvoiceStatus.PAID" +
           ") " +
           "ORDER BY a.scheduledDateTime ASC")
    List<Appointment> findAssessedAwaitingDoctorQueue();

    /**
     * Doctor's consultation queue: appointments whose triage assessment was
     * referred to this doctor and whose medical record (if any) is not yet finalized.
     */
    @Query("SELECT DISTINCT a FROM Appointment a " +
           "JOIN FETCH a.patient " +
           "JOIN com.adags.hospital.domain.triage.TriageAssessment t ON t.appointment = a " +
           "WHERE t.referredDoctor.id = :doctorId " +
           "AND a.patient.active = true " +
           "AND (NOT EXISTS (" +
           "    SELECT mr FROM com.adags.hospital.domain.medicalrecord.MedicalRecord mr " +
           "    WHERE mr.appointment = a " +
           "    AND mr.consultationStatus IN (" +
           "        com.adags.hospital.domain.medicalrecord.ConsultationStatus.FINALIZED, " +
           "        com.adags.hospital.domain.medicalrecord.ConsultationStatus.LOCKED" +
           "    )" +
           ")) " +
           "ORDER BY a.scheduledDateTime ASC")
    List<Appointment> findDoctorConsultationQueue(UUID doctorId);

    /** Returns IDs of patients who currently have an open triage appointment. */
    @Query("SELECT a.patient.id FROM Appointment a " +
           "WHERE a.appointmentType = com.adags.hospital.domain.appointment.AppointmentType.TRIAGE " +
           "AND a.status IN (" +
           "  com.adags.hospital.domain.appointment.AppointmentStatus.SCHEDULED, " +
           "  com.adags.hospital.domain.appointment.AppointmentStatus.CONFIRMED, " +
           "  com.adags.hospital.domain.appointment.AppointmentStatus.IN_PROGRESS)")
    List<UUID> findPatientIdsWithActiveTriageAppointment();

    /**
     * Patients currently with a doctor — two paths:
     * 1. Triage-routed: TRIAGE appointment IN_PROGRESS where the consultation
     *    invoice is PAID and the medical record is not yet finalized/locked.
     * 2. Directly-booked: non-TRIAGE appointments still in SCHEDULED/CONFIRMED/IN_PROGRESS.
     */
    @Query("SELECT a FROM Appointment a JOIN FETCH a.patient " +
           "WHERE (" +
           "  (a.appointmentType = com.adags.hospital.domain.appointment.AppointmentType.TRIAGE " +
           "   AND a.status = com.adags.hospital.domain.appointment.AppointmentStatus.IN_PROGRESS " +
           "   AND EXISTS (" +
           "     SELECT t FROM com.adags.hospital.domain.triage.TriageAssessment t " +
           "     WHERE t.appointment = a " +
           "     AND t.consultationInvoice IS NOT NULL " +
           "     AND t.consultationInvoice.status = com.adags.hospital.domain.billing.InvoiceStatus.PAID" +
           "   ) " +
           "   AND NOT EXISTS (" +
           "     SELECT mr FROM com.adags.hospital.domain.medicalrecord.MedicalRecord mr " +
           "     WHERE mr.appointment = a " +
           "     AND mr.consultationStatus IN (" +
           "       com.adags.hospital.domain.medicalrecord.ConsultationStatus.FINALIZED, " +
           "       com.adags.hospital.domain.medicalrecord.ConsultationStatus.LOCKED" +
           "     )" +
           "   )" +
           "  ) OR (" +
           "  a.appointmentType != com.adags.hospital.domain.appointment.AppointmentType.TRIAGE " +
           "  AND a.status IN (" +
           "    com.adags.hospital.domain.appointment.AppointmentStatus.SCHEDULED, " +
           "    com.adags.hospital.domain.appointment.AppointmentStatus.CONFIRMED, " +
           "    com.adags.hospital.domain.appointment.AppointmentStatus.IN_PROGRESS)" +
           "  )" +
           ") " +
           "ORDER BY a.scheduledDateTime ASC")
    List<Appointment> findAllActiveConsultationAppointments();
}
