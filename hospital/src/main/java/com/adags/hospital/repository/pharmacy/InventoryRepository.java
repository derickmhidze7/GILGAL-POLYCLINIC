package com.adags.hospital.repository.pharmacy;

import com.adags.hospital.domain.pharmacy.InventoryItem;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface InventoryRepository extends JpaRepository<InventoryItem, UUID> {

    Page<InventoryItem> findByMedicationId(UUID medicationId, Pageable pageable);

    @Query("SELECT i FROM InventoryItem i WHERE i.quantityInStock <= i.reorderLevel")
    List<InventoryItem> findLowStockItems();
}
