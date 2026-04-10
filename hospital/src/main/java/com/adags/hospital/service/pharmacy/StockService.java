package com.adags.hospital.service.pharmacy;

import com.adags.hospital.domain.pharmacy.StockBatch;
import com.adags.hospital.domain.pharmacy.StockItem;
import com.adags.hospital.domain.pricing.ServicePriceItem;
import com.adags.hospital.domain.staff.Staff;
import com.adags.hospital.exception.BusinessRuleException;
import com.adags.hospital.exception.ResourceNotFoundException;
import com.adags.hospital.repository.pharmacy.StockBatchRepository;
import com.adags.hospital.repository.pharmacy.StockItemRepository;
import com.adags.hospital.repository.pricing.ServicePriceItemRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StockService {

    public static final double THRESHOLD_WARNING  = 0.50;
    public static final double THRESHOLD_CRITICAL = 0.25;
    public static final double THRESHOLD_DANGER   = 0.10;

    private static final AtomicInteger BATCH_SEQ = new AtomicInteger(0);

    private final StockItemRepository        stockItemRepository;
    private final StockBatchRepository       stockBatchRepository;
    private final ServicePriceItemRepository servicePriceItemRepository;

    // -----------------------------------------------------------------------
    // Queries
    // -----------------------------------------------------------------------

    public List<StockItem> getAllStock() {
        List<StockItem> items = stockItemRepository.findAllWithDetails();
        // Force-initialize batches within this @Transactional context.
        // Because @Fetch(SUBSELECT) is used, Hibernate loads ALL items' batches
        // in a single query the first time any item's collection is touched.
        items.forEach(item -> org.hibernate.Hibernate.initialize(item.getBatches()));
        return items;
    }

    public List<StockBatch> getAvailableBatches(UUID priceItemId) {
        return stockBatchRepository.findAvailableByPriceItemId(priceItemId);
    }

    public List<StockBatch> getAllBatchesForItem(UUID stockItemId) {
        return stockBatchRepository.findAllByStockItemId(stockItemId);
    }

    public static String computeStatus(StockItem item) {
        int batch = item.getLastBatchQuantity() == null || item.getLastBatchQuantity() <= 0
                    ? Math.max(item.getCurrentQuantity(), 1)
                    : item.getLastBatchQuantity();
        if (item.getCurrentQuantity() <= 0) return "DANGER";
        double ratio = (double) item.getCurrentQuantity() / batch;
        if (ratio <= THRESHOLD_DANGER)   return "DANGER";
        if (ratio <= THRESHOLD_CRITICAL) return "CRITICAL";
        if (ratio <= THRESHOLD_WARNING)  return "WARNING";
        return "OK";
    }

    /** Batches expiring within the next {@code days} days that still have stock. */
    public List<StockBatch> getExpiringSoonBatches(int days) {
        return stockBatchRepository.findExpiringSoon(LocalDate.now().plusDays(days));
    }

    public Map<String, Long> getAlertCounts() {
        List<StockItem> all = stockItemRepository.findAllWithDetails();
        Map<String, Long> counts = new HashMap<>();
        counts.put("OK",       0L);
        counts.put("WARNING",  0L);
        counts.put("CRITICAL", 0L);
        counts.put("DANGER",   0L);
        for (StockItem s : all) {
            counts.merge(computeStatus(s), 1L, Long::sum);
        }
        return counts;
    }

    // -----------------------------------------------------------------------
    // Commands
    // -----------------------------------------------------------------------

    @Transactional
    public StockItem restock(UUID priceItemId, int quantity, LocalDate expiryDate,
                             String supplier, String notes, Staff addedBy) {
        if (quantity <= 0) throw new IllegalArgumentException("Quantity must be greater than zero.");
        if (expiryDate == null) throw new IllegalArgumentException("Expiry date is required.");
        if (expiryDate.isBefore(LocalDate.now())) throw new IllegalArgumentException("Expiry date cannot be in the past.");

        ServicePriceItem priceItem = servicePriceItemRepository.findById(priceItemId)
                .orElseThrow(() -> new ResourceNotFoundException("ServicePriceItem", "id", priceItemId));

        StockItem stockItem = stockItemRepository.findByPriceItemId(priceItemId)
                .orElseGet(() -> StockItem.builder()
                        .priceItem(priceItem)
                        .currentQuantity(0)
                        .lastBatchQuantity(quantity)
                        .build());

        stockItem.setCurrentQuantity(stockItem.getCurrentQuantity() + quantity);
        stockItem.setLastBatchQuantity(quantity);
        stockItem.setLastRestockedAt(LocalDateTime.now());
        stockItem.setAddedBy(addedBy);
        if (supplier != null && !supplier.isBlank()) stockItem.setSupplier(supplier.trim());
        if (notes    != null && !notes.isBlank())    stockItem.setNotes(notes.trim());

        StockItem saved = stockItemRepository.save(stockItem);

        String batchNumber = generateBatchNumber(priceItem.getProductName());
        StockBatch batch = StockBatch.builder()
                .stockItem(saved)
                .batchNumber(batchNumber)
                .quantityReceived(quantity)
                .remainingQuantity(quantity)
                .expiryDate(expiryDate)
                .supplier(supplier != null && !supplier.isBlank() ? supplier.trim() : null)
                .notes(notes != null && !notes.isBlank() ? notes.trim() : null)
                .addedBy(addedBy)
                .receivedAt(LocalDateTime.now())
                .build();
        stockBatchRepository.save(batch);

        log.info("Restocked {} — batch={}, qty={}, expiry={}, total={}",
                priceItem.getProductName(), batchNumber, quantity, expiryDate, saved.getCurrentQuantity());
        return saved;
    }

    @Transactional
    public void decreaseStockBatch(UUID batchId, int quantity) {
        StockBatch batch = stockBatchRepository.findById(batchId)
                .orElseThrow(() -> new ResourceNotFoundException("StockBatch", "id", batchId));

        if (batch.getRemainingQuantity() < quantity) {
            throw new BusinessRuleException(
                    "Insufficient stock in batch " + batch.getBatchNumber() +
                    ". Available: " + batch.getRemainingQuantity() + ", requested: " + quantity + ".");
        }

        batch.setRemainingQuantity(batch.getRemainingQuantity() - quantity);
        stockBatchRepository.save(batch);

        StockItem stockItem = batch.getStockItem();
        stockItem.setCurrentQuantity(Math.max(0, stockItem.getCurrentQuantity() - quantity));
        stockItemRepository.save(stockItem);

        log.info("Dispensed {} from batch {} — remaining={}, item total={}",
                quantity, batch.getBatchNumber(), batch.getRemainingQuantity(), stockItem.getCurrentQuantity());
    }

    @Transactional
    public void decreaseStock(UUID priceItemId, int quantity) {
        List<StockBatch> batches = stockBatchRepository.findAvailableByPriceItemId(priceItemId);
        if (batches.isEmpty()) throw new BusinessRuleException("No stock available for this item.");
        decreaseStockBatch(batches.get(0).getId(), quantity);
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private static String generateBatchNumber(String productName) {
        String letters = productName == null ? "" : productName.replaceAll("[^A-Za-z]", "").toUpperCase();
        String prefix = letters.length() >= 3 ? letters.substring(0, 3) : (letters.isEmpty() ? "UNK" : letters);
        String date = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        int seq = BATCH_SEQ.incrementAndGet() % 1000;
        return String.format("BATCH-%s-%s-%03d", prefix, date, seq);
    }
}
