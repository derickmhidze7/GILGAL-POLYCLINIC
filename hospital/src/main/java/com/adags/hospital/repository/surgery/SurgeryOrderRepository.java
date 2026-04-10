package com.adags.hospital.repository.surgery;

import com.adags.hospital.domain.surgery.SurgeryOrder;
import com.adags.hospital.domain.surgery.SurgeryStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SurgeryOrderRepository extends JpaRepository<SurgeryOrder, UUID> {

    List<SurgeryOrder> findByRequestingDoctorIdOrderByScheduledDateDesc(UUID doctorId);

    List<SurgeryOrder> findByPatientIdOrderByScheduledDateDesc(UUID patientId);

    List<SurgeryOrder> findByStatusOrderByScheduledDateAsc(SurgeryStatus status);

    @Query("SELECT s FROM SurgeryOrder s " +
           "LEFT JOIN FETCH s.patient " +
           "LEFT JOIN FETCH s.requestingDoctor " +
           "WHERE s.id = :id")
    Optional<SurgeryOrder> findByIdWithDetails(UUID id);

    @Query("SELECT s FROM SurgeryOrder s " +
           "LEFT JOIN FETCH s.patient " +
           "WHERE s.requestingDoctor.id = :doctorId " +
           "ORDER BY s.scheduledDate DESC")
    List<SurgeryOrder> findByDoctorWithPatient(UUID doctorId);

    /** All surgery orders visible on the nurse queue: active + completed (until discharged). */
    @Query("SELECT s FROM SurgeryOrder s " +
           "LEFT JOIN FETCH s.patient " +
           "LEFT JOIN FETCH s.requestingDoctor " +
           "WHERE s.status IN ('SCHEDULED','IN_PROGRESS','COMPLETED') " +
           "ORDER BY s.scheduledDate ASC")
    List<SurgeryOrder> findActiveForNurses();
}
