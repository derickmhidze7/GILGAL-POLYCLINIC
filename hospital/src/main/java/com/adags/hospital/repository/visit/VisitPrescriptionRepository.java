package com.adags.hospital.repository.visit;

import com.adags.hospital.domain.visit.VisitPrescription;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface VisitPrescriptionRepository extends JpaRepository<VisitPrescription, UUID> {

    @Query("SELECT vp FROM VisitPrescription vp " +
           "JOIN FETCH vp.priceItem " +
           "WHERE vp.medicalRecord.id = :recordId " +
           "ORDER BY vp.createdAt ASC")
    List<VisitPrescription> findByMedicalRecordIdOrderByCreatedAtAsc(
            @Param("recordId") UUID recordId);

    @Query("SELECT vp FROM VisitPrescription vp " +
           "JOIN FETCH vp.medicalRecord mr " +
           "JOIN FETCH mr.patient " +
           "JOIN FETCH vp.priceItem " +
           "WHERE mr.attendingDoctor.id = :doctorId " +
           "ORDER BY vp.createdAt DESC")
    List<VisitPrescription> findAllByDoctorId(@Param("doctorId") UUID doctorId);

    /**
     * Paid dispense queue: PENDING_DISPENSING prescriptions whose medical record
     * has at least one PAID or PARTIALLY_PAID invoice containing a PHARMACY line item.
     */
    @Query("SELECT vp FROM VisitPrescription vp " +
           "LEFT JOIN FETCH vp.medicalRecord mr " +
           "LEFT JOIN FETCH mr.patient p " +
           "LEFT JOIN FETCH vp.priceItem pi " +
           "WHERE vp.status = com.adags.hospital.domain.visit.VisitPrescriptionStatus.PENDING_DISPENSING " +
           "AND EXISTS (SELECT inv FROM com.adags.hospital.domain.billing.Invoice inv " +
                      "JOIN inv.lineItems li " +
                      "WHERE inv.medicalRecord = vp.medicalRecord " +
                      "AND li.category = com.adags.hospital.domain.billing.LineItemCategory.PHARMACY " +
                      "AND inv.status IN (com.adags.hospital.domain.billing.InvoiceStatus.PAID, " +
                                        "com.adags.hospital.domain.billing.InvoiceStatus.PARTIALLY_PAID)) " +
           "ORDER BY vp.createdAt ASC")
    List<VisitPrescription> findPaidPendingDispenseQueue();

    /**
     * Awaiting-payment queue: PENDING_DISPENSING prescriptions with no paid PHARMACY invoice yet.
     */
    @Query("SELECT vp FROM VisitPrescription vp " +
           "LEFT JOIN FETCH vp.medicalRecord mr " +
           "LEFT JOIN FETCH mr.patient p " +
           "LEFT JOIN FETCH vp.priceItem pi " +
           "WHERE vp.status = com.adags.hospital.domain.visit.VisitPrescriptionStatus.PENDING_DISPENSING " +
           "AND NOT EXISTS (SELECT inv FROM com.adags.hospital.domain.billing.Invoice inv " +
                          "JOIN inv.lineItems li " +
                          "WHERE inv.medicalRecord = vp.medicalRecord " +
                          "AND li.category = com.adags.hospital.domain.billing.LineItemCategory.PHARMACY " +
                          "AND inv.status IN (com.adags.hospital.domain.billing.InvoiceStatus.PAID, " +
                                            "com.adags.hospital.domain.billing.InvoiceStatus.PARTIALLY_PAID)) " +
           "ORDER BY vp.createdAt ASC")
    List<VisitPrescription> findAwaitingPaymentQueue();

    /**
     * Single prescription with all details needed for the dispense form.
     */
    @Query("SELECT vp FROM VisitPrescription vp " +
           "LEFT JOIN FETCH vp.medicalRecord mr " +
           "LEFT JOIN FETCH mr.patient " +
           "LEFT JOIN FETCH vp.priceItem " +
           "WHERE vp.id = :id")
    java.util.Optional<VisitPrescription> findByIdWithDetails(@Param("id") UUID id);

    /**
     * All prescriptions for a given patient (any status), ordered newest first.
     * Used by the patient journey page.
     */
    @Query("SELECT vp FROM VisitPrescription vp " +
           "LEFT JOIN FETCH vp.medicalRecord mr " +
           "WHERE mr.patient.id = :patientId " +
           "ORDER BY vp.createdAt DESC")
    List<VisitPrescription> findByPatientIdOrdered(@Param("patientId") UUID patientId);
}
