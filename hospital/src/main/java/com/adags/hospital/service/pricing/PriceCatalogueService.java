package com.adags.hospital.service.pricing;

import com.adags.hospital.domain.pricing.PriceChangeProposal;
import com.adags.hospital.domain.pricing.PriceProposalStatus;
import com.adags.hospital.domain.pricing.ServicePriceItem;
import com.adags.hospital.domain.staff.Staff;
import com.adags.hospital.dto.pricing.ExcelUploadResult;
import com.adags.hospital.dto.pricing.ServicePriceItemRequest;
import com.adags.hospital.dto.pricing.ServicePriceItemResponse;
import com.adags.hospital.repository.pricing.PriceChangeProposalRepository;
import com.adags.hospital.repository.pricing.ServicePriceItemRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PriceCatalogueService {

    private final ServicePriceItemRepository      repository;
    private final PriceChangeProposalRepository   proposalRepository;

    // ----------------------------------------------------------------
    //  Queries
    // ----------------------------------------------------------------

    public List<ServicePriceItemResponse> getAll() {
        return repository.findAll().stream().map(this::toResponse).toList();
    }

    public List<ServicePriceItemResponse> getByType(String type) {
        return repository.findByTypeIgnoreCase(type).stream().map(this::toResponse).toList();
    }

    public List<ServicePriceItemResponse> search(String keyword) {
        return repository.findByProductNameContainingIgnoreCase(keyword).stream().map(this::toResponse).toList();
    }

    /**
     * Autocomplete search scoped to the relevant price-catalogue type
     * based on the expense category being entered.
     * - PHARMACY_RESTOCK / MEDICAL_SUPPLIES / EQUIPMENT → searches PHARMACY items
     * - LAB_SUPPLIES → searches LAB / LABORATORY items
     * - anything else → generic product-name search
     */
    public List<ServicePriceItemResponse> searchByExpenseCategory(String query, String expenseCategory) {
        if (query == null || query.isBlank()) return List.of();
        List<ServicePriceItem> items = switch (expenseCategory.toUpperCase()) {
            case "PHARMACY_RESTOCK", "MEDICAL_SUPPLIES", "EQUIPMENT" ->
                    repository.searchPharmacyItems(query);
            case "LAB_SUPPLIES" ->
                    repository.searchLabTests(query);
            default ->
                    repository.findByProductNameContainingIgnoreCase(query);
        };
        return items.stream().limit(12).map(this::toResponse).toList();
    }

    public List<String> getDistinctTypes() {
        return repository.findDistinctTypes();
    }

    /**
     * Single entry-point for the admin pricing page.
     * Handles all combinations of search / type filter with pagination.
     * Page size is fixed at 20 in the controller.
     */
    public Page<ServicePriceItemResponse> getPage(String search, String type, Pageable pageable) {
        boolean hasSearch = search != null && !search.isBlank();
        boolean hasType   = type   != null && !type.isBlank();

        Page<ServicePriceItem> page;
        if (hasSearch && hasType) {
            page = repository.findByTypeIgnoreCaseAndProductNameContainingIgnoreCaseOrderByProductNameAsc(
                    type, search, pageable);
        } else if (hasSearch) {
            page = repository.findByProductNameContainingIgnoreCaseOrderByProductNameAsc(search, pageable);
        } else if (hasType) {
            page = repository.findByTypeIgnoreCaseOrderByProductNameAsc(type, pageable);
        } else {
            page = repository.findAllByOrderByProductNameAsc(pageable);
        }
        return page.map(this::toResponse);
    }

    // ----------------------------------------------------------------
    //  Mutations
    // ----------------------------------------------------------------

    @Transactional
    public ServicePriceItemResponse create(ServicePriceItemRequest req) {
        ServicePriceItem item = ServicePriceItem.builder()
                .itemId(req.getItemId())
                .productCode(req.getProductCode())
                .productName(req.getProductName())
                .classification(req.getClassification())
                .type(req.getType().toUpperCase())
                .category(req.getCategory())
                .price(req.getPrice())
                .build();
        return toResponse(repository.save(item));
    }

    @Transactional
    public ServicePriceItemResponse update(UUID id, ServicePriceItemRequest req) {
        ServicePriceItem item = repository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Price item not found: " + id));
        item.setItemId(req.getItemId());
        item.setProductCode(req.getProductCode());
        item.setProductName(req.getProductName());
        item.setClassification(req.getClassification());
        item.setType(req.getType().toUpperCase());
        item.setCategory(req.getCategory());
        item.setPrice(req.getPrice());
        return toResponse(repository.save(item));
    }

    @Transactional
    public void delete(UUID id) {
        if (!repository.existsById(id)) {
            throw new EntityNotFoundException("Price item not found: " + id);
        }
        repository.deleteById(id);
    }

    // ----------------------------------------------------------------
    //  Excel Import
    //  Expected columns (0-based): ITEM ID | PRODUCT CODE | PRODUCT NAME
    //                              | CLASSIFICATION | TYPE | CATEGORY | PRICE
    // ----------------------------------------------------------------

    @Transactional
    public ExcelUploadResult importFromExcel(MultipartFile file) throws IOException {
        List<String> errors  = new ArrayList<>();
        List<ServicePriceItem> batch = new ArrayList<>();
        int totalRows = 0;
        int skipped   = 0;

        try (Workbook workbook = new XSSFWorkbook(file.getInputStream())) {
            Sheet sheet = workbook.getSheetAt(0);

            for (int rowIndex = 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) { // skip header row 0
                Row row = sheet.getRow(rowIndex);
                if (row == null || isRowBlank(row)) { skipped++; continue; }

                totalRows++;
                try {
                    String itemId         = cellStr(row, 0);
                    String productCode    = cellStr(row, 1);
                    String productName    = cellStr(row, 2);
                    String classification = cellStr(row, 3);
                    String type           = cellStr(row, 4);
                    String category       = cellStr(row, 5);
                    BigDecimal price      = cellDecimal(row, 6);

                    if (productName == null || productName.isBlank()) {
                        errors.add("Row " + (rowIndex + 1) + ": PRODUCT NAME is required");
                        skipped++;
                        continue;
                    }
                    if (type == null || type.isBlank()) {
                        errors.add("Row " + (rowIndex + 1) + ": TYPE is required");
                        skipped++;
                        continue;
                    }
                    if (price == null) {
                        errors.add("Row " + (rowIndex + 1) + ": PRICE is invalid or missing");
                        skipped++;
                        continue;
                    }

                    batch.add(ServicePriceItem.builder()
                            .itemId(itemId)
                            .productCode(productCode)
                            .productName(productName)
                            .classification(classification)
                            .type(type.toUpperCase())
                            .category(category)
                            .price(price)
                            .build());

                } catch (Exception e) {
                    errors.add("Row " + (rowIndex + 1) + ": " + e.getMessage());
                    skipped++;
                }
            }
        }

        if (!batch.isEmpty()) {
            repository.saveAll(batch);
        }

        return ExcelUploadResult.builder()
                .totalRows(totalRows)
                .imported(batch.size())
                .skipped(skipped)
                .errors(errors)
                .build();
    }

    // ----------------------------------------------------------------
    //  Price-Change Proposals
    // ----------------------------------------------------------------

    /** Submit a proposal to add a new item (priceItemId == null) or edit an existing one. */
    @Transactional
    public PriceChangeProposal submitProposal(ServicePriceItemRequest req,
                                              UUID priceItemId,
                                              Staff proposedBy) {
        ServicePriceItem existing = priceItemId != null
                ? repository.findById(priceItemId).orElse(null)
                : null;

        PriceChangeProposal proposal = PriceChangeProposal.builder()
                .priceItem(existing)
                .productName(req.getProductName())
                .productCode(req.getProductCode())
                .itemId(req.getItemId())
                .classification(req.getClassification())
                .category(req.getCategory())
                .proposedPrice(req.getPrice())
                .currentPrice(existing != null ? existing.getPrice() : null)
                .status(PriceProposalStatus.PENDING)
                .proposedBy(proposedBy)
                .notes(req.getProductCode()) // reuse field for notes if extended later
                .build();
        return proposalRepository.save(proposal);
    }

    /** Submit a lab-catalogue proposal — always sets type = LABORATORY. */
    @Transactional
    public PriceChangeProposal submitLabTestProposal(ServicePriceItemRequest req,
                                                     UUID priceItemId,
                                                     Staff proposedBy) {
        ServicePriceItem existing = priceItemId != null
                ? repository.findById(priceItemId).orElse(null)
                : null;

        PriceChangeProposal proposal = PriceChangeProposal.builder()
                .priceItem(existing)
                .productName(req.getProductName())
                .classification(req.getClassification())
                .proposedPrice(req.getPrice())
                .currentPrice(existing != null ? existing.getPrice() : null)
                .status(PriceProposalStatus.PENDING)
                .proposedBy(proposedBy)
                .notes(req.getProductCode())
                .type("LABORATORY")
                .build();
        return proposalRepository.save(proposal);
    }

    /** Submit proposals in bulk from an Excel upload — forces type = PHARMACY. */
    @Transactional
    public int submitProposalsFromExcel(MultipartFile file, Staff proposedBy) throws java.io.IOException {
        List<PriceChangeProposal> batch = new ArrayList<>();
        try (org.apache.poi.ss.usermodel.Workbook workbook =
                     new org.apache.poi.xssf.usermodel.XSSFWorkbook(file.getInputStream())) {
            org.apache.poi.ss.usermodel.Sheet sheet = workbook.getSheetAt(0);
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                org.apache.poi.ss.usermodel.Row row = sheet.getRow(i);
                if (row == null || isRowBlank(row)) continue;
                String productName = cellStr(row, 2);
                if (productName == null || productName.isBlank()) continue;
                BigDecimal price = cellDecimal(row, 6);
                if (price == null) continue;

                batch.add(PriceChangeProposal.builder()
                        .productName(productName)
                        .productCode(cellStr(row, 1))
                        .itemId(cellStr(row, 0))
                        .classification(cellStr(row, 3))
                        .category(cellStr(row, 5))
                        .proposedPrice(price)
                        .status(PriceProposalStatus.PENDING)
                        .proposedBy(proposedBy)
                        .build());
            }
        }
        if (!batch.isEmpty()) proposalRepository.saveAll(batch);
        return batch.size();
    }

    public List<PriceChangeProposal> getPendingProposals() {
        return proposalRepository.findByStatusWithDetails(PriceProposalStatus.PENDING);
    }

    public List<PriceChangeProposal> getAllProposals() {
        return proposalRepository.findAllWithDetails();
    }

    public long countPendingProposals() {
        return proposalRepository.countByStatus(PriceProposalStatus.PENDING);
    }

    public List<PriceChangeProposal> getMyProposals(UUID staffId) {
        return proposalRepository.findByProposedByIdOrderByCreatedAtDesc(staffId);
    }

    /** Approve: create/update the live ServicePriceItem then mark proposal APPROVED. */
    @Transactional
    public void approveProposal(UUID proposalId, Staff admin) {
        PriceChangeProposal proposal = proposalRepository.findById(proposalId)
                .orElseThrow(() -> new EntityNotFoundException("Proposal not found: " + proposalId));
        if (proposal.getStatus() != PriceProposalStatus.PENDING) return;

        if (proposal.getPriceItem() != null) {
            // Update existing item
            ServicePriceItem item = proposal.getPriceItem();
            item.setProductName(proposal.getProductName());
            item.setProductCode(proposal.getProductCode());
            item.setItemId(proposal.getItemId());
            item.setClassification(proposal.getClassification());
            item.setCategory(proposal.getCategory());
            item.setPrice(proposal.getProposedPrice());
            repository.save(item);
        } else {
            // Create brand-new item
            repository.save(ServicePriceItem.builder()
                    .itemId(proposal.getItemId())
                    .productCode(proposal.getProductCode())
                    .productName(proposal.getProductName())
                    .classification(proposal.getClassification())
                    .type(proposal.getType() != null ? proposal.getType() : "PHARMACY")
                    .category(proposal.getCategory())
                    .price(proposal.getProposedPrice())
                    .build());
        }

        proposal.setStatus(PriceProposalStatus.APPROVED);
        proposal.setReviewedBy(admin);
        proposal.setReviewedAt(java.time.LocalDateTime.now());
        proposalRepository.save(proposal);
    }

    /** Reject: mark proposal REJECTED with a reason. */
    @Transactional
    public void rejectProposal(UUID proposalId, String reason, Staff admin) {
        PriceChangeProposal proposal = proposalRepository.findById(proposalId)
                .orElseThrow(() -> new EntityNotFoundException("Proposal not found: " + proposalId));
        if (proposal.getStatus() != PriceProposalStatus.PENDING) return;
        proposal.setStatus(PriceProposalStatus.REJECTED);
        proposal.setRejectionReason(reason);
        proposal.setReviewedBy(admin);
        proposal.setReviewedAt(java.time.LocalDateTime.now());
        proposalRepository.save(proposal);
    }

    // ----------------------------------------------------------------
    //  Product Code Generation
    // ----------------------------------------------------------------

    /**
     * Generate the next sequential product code for a given classification.
     * Pharmaceuticals → DRG0001, DRG0002, …
     * Disposables     → DIS0001, DIS0002, …
     * Uses max of live catalogue + any existing proposals to avoid collisions.
     */
    @Transactional(readOnly = true)
    public String generateNextProductCode(String classification) {
        String prefix = "Pharmaceuticals".equalsIgnoreCase(classification) ? "DRG" : "DIS";
        int maxNum = 0;

        // Check live catalogue
        java.util.Optional<ServicePriceItem> latestItem =
                repository.findTopByProductCodeStartingWithOrderByProductCodeDesc(prefix);
        if (latestItem.isPresent()) {
            String code = latestItem.get().getProductCode();
            if (code != null && code.length() > prefix.length()) {
                try { maxNum = Math.max(maxNum, Integer.parseInt(code.substring(prefix.length()))); }
                catch (NumberFormatException ignored) {}
            }
        }

        // Check all proposals (any status) to avoid reusing a pending code
        java.util.Optional<PriceChangeProposal> latestProposal =
                proposalRepository.findTopByProductCodeStartingWithOrderByProductCodeDesc(prefix);
        if (latestProposal.isPresent()) {
            String code = latestProposal.get().getProductCode();
            if (code != null && code.length() > prefix.length()) {
                try { maxNum = Math.max(maxNum, Integer.parseInt(code.substring(prefix.length()))); }
                catch (NumberFormatException ignored) {}
            }
        }

        return String.format("%s%04d", prefix, maxNum + 1);
    }

    /**
     * Generate the next sequential item ID (e.g. 0001, 0002, …).
     * Considers both live catalogue and existing proposals to avoid gaps/collisions.
     */
    @Transactional(readOnly = true)
    public String generateNextItemId() {
        int maxNum = 0;

        java.util.Optional<ServicePriceItem> latestItem =
                repository.findTopByItemIdNotNullOrderByItemIdDesc();
        if (latestItem.isPresent()) {
            String id = latestItem.get().getItemId();
            if (id != null && !id.isBlank()) {
                try { maxNum = Math.max(maxNum, Integer.parseInt(id.trim())); }
                catch (NumberFormatException ignored) {}
            }
        }

        java.util.Optional<PriceChangeProposal> latestProposal =
                proposalRepository.findTopByItemIdNotNullOrderByItemIdDesc();
        if (latestProposal.isPresent()) {
            String id = latestProposal.get().getItemId();
            if (id != null && !id.isBlank()) {
                try { maxNum = Math.max(maxNum, Integer.parseInt(id.trim())); }
                catch (NumberFormatException ignored) {}
            }
        }

        return String.format("%04d", maxNum + 1);
    }

    // ----------------------------------------------------------------
    //  Helpers
    // ----------------------------------------------------------------

    private String cellStr(Row row, int col) {
        Cell cell = row.getCell(col, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
        if (cell == null) return null;
        return switch (cell.getCellType()) {
            case STRING  -> cell.getStringCellValue().trim();
            case NUMERIC -> String.valueOf((long) cell.getNumericCellValue());
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            default      -> null;
        };
    }

    private BigDecimal cellDecimal(Row row, int col) {
        Cell cell = row.getCell(col, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
        if (cell == null) return null;
        try {
            return switch (cell.getCellType()) {
                case NUMERIC -> BigDecimal.valueOf(cell.getNumericCellValue());
                case STRING  -> new BigDecimal(cell.getStringCellValue().trim());
                default      -> null;
            };
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private boolean isRowBlank(Row row) {
        for (Cell cell : row) {
            if (cell != null && cell.getCellType() != CellType.BLANK) return false;
        }
        return true;
    }

    private ServicePriceItemResponse toResponse(ServicePriceItem item) {
        return ServicePriceItemResponse.builder()
                .id(item.getId())
                .itemId(item.getItemId())
                .productCode(item.getProductCode())
                .productName(item.getProductName())
                .classification(item.getClassification())
                .type(item.getType())
                .category(item.getCategory())
                .price(item.getPrice())
                .createdAt(item.getCreatedAt())
                .updatedAt(item.getUpdatedAt())
                .build();
    }
}
