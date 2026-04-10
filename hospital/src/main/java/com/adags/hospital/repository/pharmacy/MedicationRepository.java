package com.adags.hospital.repository.pharmacy;

import com.adags.hospital.domain.pharmacy.Medication;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface MedicationRepository extends JpaRepository<Medication, UUID> {

    @Query("SELECT m FROM Medication m WHERE m.active = true AND " +
           "(LOWER(m.genericName) LIKE LOWER(CONCAT('%',:query,'%')) OR " +
           "LOWER(m.brandName) LIKE LOWER(CONCAT('%',:query,'%')))")
    Page<Medication> searchMedications(String query, Pageable pageable);

    Page<Medication> findByActiveTrue(Pageable pageable);

    List<Medication> findAllByActiveTrueOrderByGenericNameAsc();

    /** Find an exact-match medication by generic name (case-insensitive). */
    java.util.Optional<Medication> findFirstByGenericNameIgnoreCase(String genericName);
}
