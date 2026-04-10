package com.adags.hospital.repository.medicalrecord;

import com.adags.hospital.domain.medicalrecord.Prescription;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface PrescriptionRepository extends JpaRepository<Prescription, UUID> {

    List<Prescription> findByMedicalRecordId(UUID medicalRecordId);

    @Query("SELECT p FROM Prescription p WHERE p.medicalRecord.id = :medicalRecordId AND p.dispensed = false")
    List<Prescription> findUndispensedByMedicalRecordId(UUID medicalRecordId);

    @Query("SELECT p FROM Prescription p " +
           "JOIN FETCH p.medication " +
           "JOIN FETCH p.medicalRecord mr " +
           "JOIN FETCH mr.patient " +
           "LEFT JOIN FETCH mr.attendingDoctor " +
           "WHERE mr.attendingDoctor.id = :doctorId " +
           "AND p.dispensed = true " +
           "ORDER BY mr.visitDate DESC")
    List<Prescription> findDispensedByAttendingDoctorId(@Param("doctorId") UUID doctorId);

    @Query("SELECT p FROM Prescription p " +
           "LEFT JOIN FETCH p.medication " +
           "LEFT JOIN FETCH p.priceItem " +
           "JOIN FETCH p.medicalRecord mr " +
           "JOIN FETCH mr.patient " +
           "WHERE p.id = :id")
    java.util.Optional<Prescription> findByIdWithDetails(@Param("id") UUID id);

    @Query("SELECT p FROM Prescription p " +
           "LEFT JOIN FETCH p.medication " +
           "LEFT JOIN FETCH p.priceItem " +
           "JOIN FETCH p.medicalRecord mr " +
           "JOIN FETCH mr.patient " +
           "WHERE p.dispensed = false " +
           "AND p.pharmacyStatus IN (" +
           "    com.adags.hospital.domain.medicalrecord.PrescriptionPharmacyStatus.READY_TO_DISPENSE," +
           "    com.adags.hospital.domain.medicalrecord.PrescriptionPharmacyStatus.IN_PROGRESS," +
           "    com.adags.hospital.domain.medicalrecord.PrescriptionPharmacyStatus.PARTIALLY_DISPENSED," +
           "    com.adags.hospital.domain.medicalrecord.PrescriptionPharmacyStatus.ON_HOLD," +
           "    com.adags.hospital.domain.medicalrecord.PrescriptionPharmacyStatus.OUT_OF_STOCK" +
           ") " +
           "ORDER BY mr.visitDate ASC")
    List<Prescription> findPendingPharmacyQueue();
}
