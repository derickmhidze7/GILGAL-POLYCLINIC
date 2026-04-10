package com.adags.hospital.repository.lab;

import com.adags.hospital.domain.lab.LabStockRequest;
import com.adags.hospital.domain.lab.LabStockRequestStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface LabStockRequestRepository extends JpaRepository<LabStockRequest, UUID> {

    @Query("SELECT r FROM LabStockRequest r " +
           "JOIN FETCH r.stockItem si JOIN FETCH si.priceItem " +
           "LEFT JOIN FETCH r.requestedBy " +
           "WHERE r.status = :status " +
           "ORDER BY r.createdAt DESC")
    List<LabStockRequest> findByStatusWithDetails(LabStockRequestStatus status);

    @Query("SELECT r FROM LabStockRequest r " +
           "JOIN FETCH r.stockItem si JOIN FETCH si.priceItem " +
           "LEFT JOIN FETCH r.requestedBy " +
           "LEFT JOIN FETCH r.handledBy " +
           "WHERE r.requestedBy.id = :staffId " +
           "ORDER BY r.createdAt DESC")
    List<LabStockRequest> findByRequestedByIdWithDetails(UUID staffId);

    @Query("SELECT r FROM LabStockRequest r " +
           "JOIN FETCH r.stockItem si JOIN FETCH si.priceItem " +
           "LEFT JOIN FETCH r.requestedBy " +
           "LEFT JOIN FETCH r.handledBy " +
           "WHERE r.status <> 'PENDING' " +
           "ORDER BY r.createdAt DESC")
    List<LabStockRequest> findHandledWithDetails();
}
