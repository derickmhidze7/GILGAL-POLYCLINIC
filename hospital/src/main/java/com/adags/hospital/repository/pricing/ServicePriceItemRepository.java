package com.adags.hospital.repository.pricing;

import com.adags.hospital.domain.pricing.ServicePriceItem;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ServicePriceItemRepository extends JpaRepository<ServicePriceItem, UUID> {

    List<ServicePriceItem> findByTypeIgnoreCase(String type);

    List<ServicePriceItem> findByCategoryIgnoreCase(String category);

    List<ServicePriceItem> findByTypeIgnoreCaseAndCategoryIgnoreCase(String type, String category);

    @Query("SELECT DISTINCT s.type FROM ServicePriceItem s ORDER BY s.type")
    List<String> findDistinctTypes();

    @Query("SELECT DISTINCT s.category FROM ServicePriceItem s WHERE s.type = :type ORDER BY s.category")
    List<String> findDistinctCategoriesByType(String type);

    List<ServicePriceItem> findByProductNameContainingIgnoreCase(String keyword);

    @Query("SELECT s FROM ServicePriceItem s WHERE LOWER(s.type) = 'laboratory' " +
           "AND (LOWER(s.productName) LIKE LOWER(CONCAT('%',:keyword,'%')) " +
           "     OR LOWER(s.classification) LIKE LOWER(CONCAT('%',:keyword,'%'))) " +
           "ORDER BY s.productName")
    List<ServicePriceItem> searchLabTests(String keyword);

    @Query("SELECT s FROM ServicePriceItem s WHERE LOWER(s.type) = 'laboratory' ORDER BY s.productName")
    List<ServicePriceItem> findAllLabTests();

    @Query("SELECT s FROM ServicePriceItem s WHERE LOWER(s.type) = 'pharmacy' " +
           "AND (LOWER(s.productName) LIKE LOWER(CONCAT('%',:keyword,'%')) " +
           "     OR LOWER(s.classification) LIKE LOWER(CONCAT('%',:keyword,'%'))) " +
           "ORDER BY s.productName")
    List<ServicePriceItem> searchPharmacyItems(String keyword);

    @Query("SELECT s FROM ServicePriceItem s WHERE LOWER(s.type) IN ('surgery', 'procedure') " +
           "AND (LOWER(s.productName) LIKE LOWER(CONCAT('%',:keyword,'%')) " +
           "     OR LOWER(s.classification) LIKE LOWER(CONCAT('%',:keyword,'%'))) " +
           "ORDER BY s.productName")
    List<ServicePriceItem> searchSurgeryAndProcedure(String keyword);

    @Query("SELECT s FROM ServicePriceItem s WHERE LOWER(s.type) IN ('surgery', 'procedure') " +
           "ORDER BY s.productName")
    List<ServicePriceItem> findAllSurgeryAndProcedure();

    boolean existsByProductCodeIgnoreCase(String productCode);

    // ---- Paginated queries (for admin pricing page) ----

    Page<ServicePriceItem> findAllByOrderByProductNameAsc(Pageable pageable);

    Page<ServicePriceItem> findByTypeIgnoreCaseOrderByProductNameAsc(String type, Pageable pageable);

    Page<ServicePriceItem> findByProductNameContainingIgnoreCaseOrderByProductNameAsc(String keyword, Pageable pageable);

    Page<ServicePriceItem> findByTypeIgnoreCaseAndProductNameContainingIgnoreCaseOrderByProductNameAsc(
            String type, String keyword, Pageable pageable);

    @Query("SELECT s FROM ServicePriceItem s WHERE LOWER(s.type) = 'pharmacy' " +
           "AND LOWER(s.productName) LIKE LOWER(CONCAT('%',:name,'%')) " +
           "ORDER BY s.productName")
    List<ServicePriceItem> findPharmacyByMedicationName(String name);

    java.util.Optional<ServicePriceItem> findTopByProductCodeStartingWithOrderByProductCodeDesc(String prefix);

    java.util.Optional<ServicePriceItem> findTopByItemIdNotNullOrderByItemIdDesc();
}
