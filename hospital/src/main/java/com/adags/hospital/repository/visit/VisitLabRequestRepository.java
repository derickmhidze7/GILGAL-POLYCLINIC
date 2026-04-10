package com.adags.hospital.repository.visit;

import com.adags.hospital.domain.visit.VisitLabRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface VisitLabRequestRepository extends JpaRepository<VisitLabRequest, UUID> {

    @Query("SELECT vl FROM VisitLabRequest vl " +
           "JOIN FETCH vl.priceItem " +
           "WHERE vl.medicalRecord.id = :recordId " +
           "ORDER BY vl.createdAt ASC")
    List<VisitLabRequest> findByMedicalRecordIdOrderByCreatedAtAsc(
            @Param("recordId") UUID recordId);

    @Query("SELECT vl FROM VisitLabRequest vl " +
           "JOIN FETCH vl.medicalRecord mr " +
           "JOIN FETCH mr.patient " +
           "JOIN FETCH vl.priceItem " +
           "WHERE mr.attendingDoctor.id = :doctorId " +
           "ORDER BY vl.createdAt DESC")
    List<VisitLabRequest> findAllByDoctorId(@Param("doctorId") UUID doctorId);

    /**
     * Returns V26 lab requests ready to be processed by the lab technician:
     *  - IN_PROGRESS requests (already accepted)
     *  - PENDING requests whose medical record has a PAID/PARTIALLY_PAID LAB invoice
     */
    @Query("SELECT vlr FROM VisitLabRequest vlr " +
           "LEFT JOIN FETCH vlr.medicalRecord mr " +
           "LEFT JOIN FETCH mr.patient p " +
           "WHERE (vlr.status = com.adags.hospital.domain.visit.VisitLabRequestStatus.IN_PROGRESS) " +
           "OR (vlr.status = com.adags.hospital.domain.visit.VisitLabRequestStatus.PENDING " +
               "AND EXISTS (SELECT inv FROM com.adags.hospital.domain.billing.Invoice inv " +
                           "JOIN inv.lineItems li " +
                           "WHERE inv.medicalRecord = vlr.medicalRecord " +
                           "AND li.category = com.adags.hospital.domain.billing.LineItemCategory.LAB " +
                           "AND inv.status IN (com.adags.hospital.domain.billing.InvoiceStatus.PAID, " +
                                             "com.adags.hospital.domain.billing.InvoiceStatus.PARTIALLY_PAID))) " +
           "ORDER BY vlr.urgency DESC, vlr.createdAt ASC")
    List<VisitLabRequest> findPaidQueueWithDetails();

    /**
     * Returns PENDING V26 lab requests still waiting for their lab invoice to be paid.
     */
    @Query("SELECT vlr FROM VisitLabRequest vlr " +
           "LEFT JOIN FETCH vlr.medicalRecord mr " +
           "LEFT JOIN FETCH mr.patient p " +
           "WHERE vlr.status = com.adags.hospital.domain.visit.VisitLabRequestStatus.PENDING " +
           "AND NOT EXISTS (SELECT inv FROM com.adags.hospital.domain.billing.Invoice inv " +
                          "JOIN inv.lineItems li " +
                          "WHERE inv.medicalRecord = vlr.medicalRecord " +
                          "AND li.category = com.adags.hospital.domain.billing.LineItemCategory.LAB " +
                          "AND inv.status IN (com.adags.hospital.domain.billing.InvoiceStatus.PAID, " +
                                            "com.adags.hospital.domain.billing.InvoiceStatus.PARTIALLY_PAID)) " +
           "ORDER BY vlr.createdAt ASC")
    List<VisitLabRequest> findAwaitingPaymentWithDetails();

    /**
     * Eagerly loads medicalRecord → patient, priceItem, and resultParameters for the result-entry form.
     * Required because spring.jpa.open-in-view=false: the session closes before Thymeleaf renders.
     */
    @Query("SELECT vlr FROM VisitLabRequest vlr " +
           "JOIN FETCH vlr.medicalRecord mr " +
           "JOIN FETCH mr.patient " +
           "JOIN FETCH vlr.priceItem " +
           "LEFT JOIN FETCH vlr.resultParameters " +
           "WHERE vlr.id = :id")
    java.util.Optional<VisitLabRequest> findByIdWithDetails(@Param("id") UUID id);

    /**
     * Returns all COMPLETED V26 lab requests requested by a specific doctor (via createdById).
     * Used by DoctorViewController to populate the lab-results page.
     */
    @Query("SELECT vlr FROM VisitLabRequest vlr " +
           "LEFT JOIN FETCH vlr.medicalRecord mr " +
           "LEFT JOIN FETCH mr.patient p " +
           "LEFT JOIN FETCH vlr.resultParameters rp " +
           "WHERE vlr.createdById = :doctorId " +
           "AND vlr.status = com.adags.hospital.domain.visit.VisitLabRequestStatus.COMPLETED " +
           "ORDER BY vlr.completedAt DESC")
    List<VisitLabRequest> findCompletedByDoctorIdWithDetails(@Param("doctorId") UUID doctorId);

    /**
     * Returns all COMPLETED V26 lab requests for the lab tech history page.
     */
    @Query("SELECT vlr FROM VisitLabRequest vlr " +
           "LEFT JOIN FETCH vlr.medicalRecord mr " +
           "LEFT JOIN FETCH mr.patient p " +
           "LEFT JOIN FETCH vlr.resultParameters rp " +
           "WHERE vlr.status = com.adags.hospital.domain.visit.VisitLabRequestStatus.COMPLETED " +
           "ORDER BY vlr.completedAt DESC")
    List<VisitLabRequest> findAllCompletedWithDetails();

    /**
     * All V26 lab requests for a given patient (any status), ordered newest first.
     * Used by the patient journey page.
     */
    @Query("SELECT vlr FROM VisitLabRequest vlr " +
           "LEFT JOIN FETCH vlr.medicalRecord mr " +
           "LEFT JOIN FETCH vlr.resultParameters rp " +
           "WHERE mr.patient.id = :patientId " +
           "ORDER BY vlr.createdAt DESC")
    List<VisitLabRequest> findByPatientIdWithDetails(@Param("patientId") UUID patientId);
}
