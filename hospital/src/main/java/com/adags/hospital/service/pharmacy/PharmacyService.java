package com.adags.hospital.service.pharmacy;

import com.adags.hospital.domain.pharmacy.InventoryItem;
import com.adags.hospital.domain.pharmacy.Medication;
import com.adags.hospital.repository.pharmacy.InventoryRepository;
import com.adags.hospital.repository.pharmacy.MedicationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PharmacyService {

    private final MedicationRepository medicationRepository;
    private final InventoryRepository inventoryRepository;

    public Page<Medication> getMedications(Pageable pageable) {
        return medicationRepository.findByActiveTrue(pageable);
    }

    public Page<Medication> searchMedications(String query, Pageable pageable) {
        return medicationRepository.searchMedications(query, pageable);
    }

    @Transactional
    public Medication addMedication(Medication medication) {
        return medicationRepository.save(medication);
    }

    public Page<InventoryItem> getInventory(UUID medicationId, Pageable pageable) {
        return inventoryRepository.findByMedicationId(medicationId, pageable);
    }

    public List<InventoryItem> getLowStockItems() {
        return inventoryRepository.findLowStockItems();
    }

    @Transactional
    public InventoryItem addInventory(InventoryItem item) {
        return inventoryRepository.save(item);
    }
}
