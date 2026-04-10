package com.adags.hospital.repository.lab;

import com.adags.hospital.domain.lab.LabRequest;
import com.adags.hospital.domain.lab.LabRequestStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface LabRequestRepository extends JpaRepository<LabRequest, UUID> {

    Page<LabRequest> findByPatientId(UUID patientId, Pageable pageable);

    Page<LabRequest> findByStatus(LabRequestStatus status, Pageable pageable);

    Page<LabRequest> findByRequestingDoctorId(UUID doctorId, Pageable pageable);

    List<LabRequest> findByMedicalRecordId(UUID medicalRecordId);

    @Query("SELECT DISTINCT lr FROM LabRequest lr " +
           "LEFT JOIN FETCH lr.result res " +
           "LEFT JOIN FETCH res.performedBy " +
           "LEFT JOIN FETCH res.verifiedBy " +
           "LEFT JOIN FETCH res.parameters " +
           "LEFT JOIN FETCH lr.patient " +
           "WHERE lr.requestingDoctor.id = :doctorId " +
           "AND lr.status = com.adags.hospital.domain.lab.LabRequestStatus.COMPLETED")
    List<LabRequest> findCompletedByDoctorIdWithResults(@Param("doctorId") UUID doctorId);

    @Query("SELECT lr FROM LabRequest lr " +
           "LEFT JOIN FETCH lr.result res " +
           "LEFT JOIN FETCH res.parameters " +
           "WHERE lr.requestingDoctor.id = :doctorId " +
           "AND lr.status = com.adags.hospital.domain.lab.LabRequestStatus.COMPLETED")
    List<LabRequest> findCompletedByDoctorIdWithParameters(@Param("doctorId") UUID doctorId);

    @Query("SELECT lr FROM LabRequest lr " +
           "LEFT JOIN FETCH lr.patient " +
           "LEFT JOIN FETCH lr.requestingDoctor " +
           "LEFT JOIN FETCH lr.medicalRecord " +
           "LEFT JOIN FETCH lr.surgeryOrder " +
           "LEFT JOIN FETCH lr.result res " +
           "LEFT JOIN FETCH res.parameters " +
           "LEFT JOIN FETCH res.performedBy " +
           "LEFT JOIN FETCH res.verifiedBy " +
           "WHERE lr.id = :id")
    java.util.Optional<LabRequest> findByIdWithDetails(@Param("id") UUID id);

    @Query("SELECT lr FROM LabRequest lr " +
           "LEFT JOIN FETCH lr.patient " +
           "LEFT JOIN FETCH lr.requestingDoctor " +
           "LEFT JOIN FETCH lr.medicalRecord " +
           "WHERE lr.status IN (:statuses) " +
           "ORDER BY lr.urgency DESC, lr.requestedAt ASC")
    List<LabRequest> findByStatusInWithDetails(
            @Param("statuses") java.util.Collection<com.adags.hospital.domain.lab.LabRequestStatus> statuses);

    /**
     * Returns lab requests that are ready to be processed:
     *  - PENDING consultation requests whose medical record invoice (LAB category) is paid
     *  - PENDING surgery requests whose surgery invoice is paid
     *  - All IN_PROGRESS requests (already accepted by the lab tech)
     */
    @Query("SELECT lr FROM LabRequest lr " +
           "LEFT JOIN FETCH lr.patient " +
           "LEFT JOIN FETCH lr.requestingDoctor " +
           "LEFT JOIN FETCH lr.medicalRecord mr " +
           "LEFT JOIN FETCH lr.surgeryOrder so " +
           "WHERE (lr.status = com.adags.hospital.domain.lab.LabRequestStatus.IN_PROGRESS) " +
           "OR (lr.status = com.adags.hospital.domain.lab.LabRequestStatus.PENDING " +
               "AND mr IS NOT NULL " +
               "AND EXISTS (SELECT inv FROM com.adags.hospital.domain.billing.Invoice inv " +
                           "JOIN inv.lineItems li " +
                           "WHERE inv.medicalRecord = mr " +
                           "AND li.category = com.adags.hospital.domain.billing.LineItemCategory.LAB " +
                           "AND inv.status IN (com.adags.hospital.domain.billing.InvoiceStatus.PAID, " +
                                             "com.adags.hospital.domain.billing.InvoiceStatus.PARTIALLY_PAID))) " +
           "OR (lr.status = com.adags.hospital.domain.lab.LabRequestStatus.PENDING " +
               "AND so IS NOT NULL " +
               "AND EXISTS (SELECT inv FROM com.adags.hospital.domain.billing.Invoice inv " +
                           "WHERE inv.id = so.surgeryInvoiceId " +
                           "AND inv.status IN (com.adags.hospital.domain.billing.InvoiceStatus.PAID, " +
                                             "com.adags.hospital.domain.billing.InvoiceStatus.PARTIALLY_PAID))) " +
           "ORDER BY lr.urgency DESC, lr.requestedAt ASC")
    List<LabRequest> findPaidQueueWithDetails();

    /**
     * Returns PENDING lab requests still waiting for payment.
     * Includes both consultation-linked and surgery-linked unpaid requests.
     */
    @Query("SELECT lr FROM LabRequest lr " +
           "LEFT JOIN FETCH lr.patient " +
           "LEFT JOIN FETCH lr.requestingDoctor " +
           "LEFT JOIN FETCH lr.medicalRecord mr " +
           "LEFT JOIN FETCH lr.surgeryOrder so " +
           "WHERE lr.status = com.adags.hospital.domain.lab.LabRequestStatus.PENDING " +
           "AND (" +
               "(mr IS NULL AND so IS NULL) " +
               "OR (mr IS NOT NULL " +
                   "AND NOT EXISTS (SELECT inv FROM com.adags.hospital.domain.billing.Invoice inv " +
                                   "JOIN inv.lineItems li " +
                                   "WHERE inv.medicalRecord = mr " +
                                   "AND li.category = com.adags.hospital.domain.billing.LineItemCategory.LAB " +
                                   "AND inv.status IN (com.adags.hospital.domain.billing.InvoiceStatus.PAID, " +
                                                     "com.adags.hospital.domain.billing.InvoiceStatus.PARTIALLY_PAID))) " +
               "OR (so IS NOT NULL " +
                   "AND (so.surgeryInvoiceId IS NULL " +
                       "OR NOT EXISTS (SELECT inv FROM com.adags.hospital.domain.billing.Invoice inv " +
                                      "WHERE inv.id = so.surgeryInvoiceId " +
                                      "AND inv.status IN (com.adags.hospital.domain.billing.InvoiceStatus.PAID, " +
                                                        "com.adags.hospital.domain.billing.InvoiceStatus.PARTIALLY_PAID)))) " +
           ") " +
           "ORDER BY lr.requestedAt ASC")
    List<LabRequest> findAwaitingPaymentWithDetails();
}
