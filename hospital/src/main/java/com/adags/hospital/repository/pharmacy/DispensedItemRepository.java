package com.adags.hospital.repository.pharmacy;

import com.adags.hospital.domain.pharmacy.DispensedItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface DispensedItemRepository extends JpaRepository<DispensedItem, UUID> {

    List<DispensedItem> findByPrescriptionId(UUID prescriptionId);

    @Query("SELECT d FROM DispensedItem d " +
           "LEFT JOIN FETCH d.prescription p " +
           "LEFT JOIN FETCH p.medication " +
           "LEFT JOIN FETCH p.medicalRecord pmr " +
           "LEFT JOIN FETCH pmr.patient " +
           "LEFT JOIN FETCH d.visitPrescription vp " +
           "LEFT JOIN FETCH vp.medicalRecord vmr " +
           "LEFT JOIN FETCH vmr.patient " +
           "LEFT JOIN FETCH vp.priceItem " +
           "LEFT JOIN FETCH d.inventoryItem inv " +
           "LEFT JOIN FETCH inv.medication " +
           "LEFT JOIN FETCH d.stockBatch sb " +
           "LEFT JOIN FETCH sb.stockItem " +
           "WHERE d.dispensedBy.id = :staffId " +
           "ORDER BY d.dispensedAt DESC")
    List<DispensedItem> findByDispensedByIdWithDetails(@Param("staffId") UUID staffId);
}
