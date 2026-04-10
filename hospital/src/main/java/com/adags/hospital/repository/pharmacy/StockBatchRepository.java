package com.adags.hospital.repository.pharmacy;

import com.adags.hospital.domain.pharmacy.StockBatch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface StockBatchRepository extends JpaRepository<StockBatch, UUID> {

    /**
     * Returns all batches that still have stock for a given price-catalogue item,
     * ordered by expiry date ascending (FEFO — First-Expired, First-Out).
     * Used to populate the dispense form batch selector.
     */
    @Query("SELECT b FROM StockBatch b " +
           "JOIN FETCH b.stockItem s " +
           "JOIN FETCH s.priceItem " +
           "WHERE s.priceItem.id = :priceItemId " +
           "  AND b.remainingQuantity > 0 " +
           "ORDER BY b.expiryDate ASC")
    List<StockBatch> findAvailableByPriceItemId(@Param("priceItemId") UUID priceItemId);

    /**
     * All batches (including exhausted) for a stock item, newest first.
     * Used in the stock management detail view.
     */
    @Query("SELECT b FROM StockBatch b " +
           "JOIN FETCH b.stockItem s " +
           "LEFT JOIN FETCH b.addedBy " +
           "WHERE s.id = :stockItemId " +
           "ORDER BY b.expiryDate ASC")
    List<StockBatch> findAllByStockItemId(@Param("stockItemId") UUID stockItemId);

    /** Count of available (non-exhausted) batches for a price item. */
    @Query("SELECT COUNT(b) FROM StockBatch b " +
           "JOIN b.stockItem s " +
           "WHERE s.priceItem.id = :priceItemId AND b.remainingQuantity > 0")
    long countAvailableByPriceItemId(@Param("priceItemId") UUID priceItemId);

    /**
     * All non-exhausted batches whose expiry date is on or before the given cutoff date,
     * ordered by expiry date ascending. Used for near-expiry alerts.
     */
    @Query("SELECT b FROM StockBatch b " +
           "JOIN FETCH b.stockItem s " +
           "JOIN FETCH s.priceItem " +
           "WHERE b.remainingQuantity > 0 " +
           "  AND b.expiryDate <= :cutoff " +
           "ORDER BY b.expiryDate ASC")
    List<StockBatch> findExpiringSoon(@Param("cutoff") java.time.LocalDate cutoff);
}
