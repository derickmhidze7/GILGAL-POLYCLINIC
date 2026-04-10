package com.adags.hospital.service.lab;

import com.adags.hospital.domain.lab.LabStockRequest;
import com.adags.hospital.domain.lab.LabStockRequestStatus;
import com.adags.hospital.domain.pharmacy.StockItem;
import com.adags.hospital.domain.staff.Staff;
import com.adags.hospital.dto.lab.LabStockRequestForm;
import com.adags.hospital.exception.BusinessRuleException;
import com.adags.hospital.exception.ResourceNotFoundException;
import com.adags.hospital.repository.lab.LabStockRequestRepository;
import com.adags.hospital.repository.pharmacy.StockItemRepository;
import com.adags.hospital.service.pharmacy.StockService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class LabStockRequestService {

    private final LabStockRequestRepository labStockRequestRepository;
    private final StockItemRepository        stockItemRepository;
    private final StockService               stockService;

    // -----------------------------------------------------------------------
    // Lab tech — submit a new request
    // -----------------------------------------------------------------------
    @Transactional
    public LabStockRequest submitRequest(LabStockRequestForm form, Staff requestedBy) {
        StockItem stockItem = stockItemRepository.findById(form.stockItemId())
                .orElseThrow(() -> new ResourceNotFoundException("Stock item not found"));

        if (stockItem.getCurrentQuantity() <= 0) {
            throw new BusinessRuleException("Item '" + stockItem.getPriceItem().getProductName() + "' is currently out of stock.");
        }
        if (form.requestedQuantity() > stockItem.getCurrentQuantity()) {
            throw new BusinessRuleException("Requested quantity (" + form.requestedQuantity() +
                    ") exceeds available stock (" + stockItem.getCurrentQuantity() + ").");
        }

        LabStockRequest request = LabStockRequest.builder()
                .requestedBy(requestedBy)
                .stockItem(stockItem)
                .requestedQuantity(form.requestedQuantity())
                .requestNotes(form.requestNotes())
                .status(LabStockRequestStatus.PENDING)
                .build();

        return labStockRequestRepository.save(request);
    }

    // -----------------------------------------------------------------------
    // Lab tech — view own requests
    // -----------------------------------------------------------------------
    public List<LabStockRequest> getMyRequests(UUID staffId) {
        return labStockRequestRepository.findByRequestedByIdWithDetails(staffId);
    }

    // -----------------------------------------------------------------------
    // Pharmacist — pending queue
    // -----------------------------------------------------------------------
    public List<LabStockRequest> getPendingRequests() {
        return labStockRequestRepository.findByStatusWithDetails(LabStockRequestStatus.PENDING);
    }

    // -----------------------------------------------------------------------
    // Pharmacist — handled history (released + rejected)
    // -----------------------------------------------------------------------
    public List<LabStockRequest> getHandledRequests() {
        return labStockRequestRepository.findHandledWithDetails();
    }

    // -----------------------------------------------------------------------
    // Pharmacist — release stock
    // -----------------------------------------------------------------------
    @Transactional
    public void releaseStock(UUID requestId, int releasedQuantity, String responseNotes, Staff pharmacist) {
        LabStockRequest request = labStockRequestRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Stock request not found"));

        if (request.getStatus() != LabStockRequestStatus.PENDING) {
            throw new BusinessRuleException("Request has already been handled.");
        }
        if (releasedQuantity <= 0) {
            throw new BusinessRuleException("Released quantity must be greater than zero.");
        }

        stockService.decreaseStock(request.getStockItem().getPriceItem().getId(), releasedQuantity);

        request.setReleasedQuantity(releasedQuantity);
        request.setResponseNotes(responseNotes);
        request.setStatus(LabStockRequestStatus.RELEASED);
        request.setHandledBy(pharmacist);
        request.setHandledAt(LocalDateTime.now());

        log.info("Pharmacist {} released {} units of {} for lab tech {}",
                pharmacist.getId(), releasedQuantity,
                request.getStockItem().getPriceItem().getProductName(),
                request.getRequestedBy() != null ? request.getRequestedBy().getId() : "unknown");
    }

    // -----------------------------------------------------------------------
    // Pharmacist — reject request
    // -----------------------------------------------------------------------
    @Transactional
    public void rejectRequest(UUID requestId, String responseNotes, Staff pharmacist) {
        LabStockRequest request = labStockRequestRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Stock request not found"));

        if (request.getStatus() != LabStockRequestStatus.PENDING) {
            throw new BusinessRuleException("Request has already been handled.");
        }

        request.setResponseNotes(responseNotes);
        request.setStatus(LabStockRequestStatus.REJECTED);
        request.setHandledBy(pharmacist);
        request.setHandledAt(LocalDateTime.now());

        log.info("Pharmacist {} rejected stock request {} for lab tech {}",
                pharmacist.getId(), requestId,
                request.getRequestedBy() != null ? request.getRequestedBy().getId() : "unknown");
    }
}
