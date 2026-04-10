package com.adags.hospital.repository.pharmacy;

import com.adags.hospital.domain.pharmacy.StockItem;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface StockItemRepository extends JpaRepository<StockItem, UUID> {

    /** Used for upsert logic on restock — one row per price-catalogue item. */
    @Query("SELECT s FROM StockItem s JOIN FETCH s.priceItem WHERE s.priceItem.id = :priceItemId")
    Optional<StockItem> findByPriceItemId(@Param("priceItemId") UUID priceItemId);

    /**
     * Full stock list with priceItem and addedBy eagerly fetched so Thymeleaf
     * can render them safely with spring.jpa.open-in-view=false.
     */
    @Query("SELECT s FROM StockItem s JOIN FETCH s.priceItem LEFT JOIN FETCH s.addedBy " +
           "ORDER BY s.priceItem.productName ASC")
    List<StockItem> findAllWithDetails();

    /** Pageable version for future use. */
    @Query(value = "SELECT DISTINCT s FROM StockItem s JOIN FETCH s.priceItem LEFT JOIN FETCH s.addedBy",
           countQuery = "SELECT COUNT(s) FROM StockItem s")
    Page<StockItem> findAllWithDetails(Pageable pageable);

    /** Search in-stock pharmacy items by keyword (for prescription autocomplete). */
    @Query("SELECT s FROM StockItem s JOIN FETCH s.priceItem " +
           "WHERE s.currentQuantity > 0 " +
           "AND LOWER(s.priceItem.type) = 'pharmacy' " +
           "AND (LOWER(s.priceItem.productName) LIKE LOWER(CONCAT('%',:keyword,'%')) " +
           "     OR LOWER(s.priceItem.classification) LIKE LOWER(CONCAT('%',:keyword,'%'))) " +
           "ORDER BY s.priceItem.productName ASC")
    List<StockItem> searchInStockPharmacyItems(@Param("keyword") String keyword);

    /** All in-stock pharmacy items for the catalogue page (no keyword filter). */
    @Query("SELECT s FROM StockItem s JOIN FETCH s.priceItem " +
           "WHERE s.currentQuantity > 0 " +
           "AND LOWER(s.priceItem.type) = 'pharmacy' " +
           "ORDER BY s.priceItem.productName ASC")
    List<StockItem> findAllInStockPharmacyItems();
}
