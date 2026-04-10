package com.adags.hospital.controller;

import com.adags.hospital.domain.medicalrecord.Prescription;
import com.adags.hospital.domain.pharmacy.InventoryItem;
import com.adags.hospital.domain.pharmacy.StockBatch;
import com.adags.hospital.domain.pharmacy.StockItem;
import com.adags.hospital.domain.staff.Staff;
import com.adags.hospital.domain.surgery.SurgeryItemList;
import com.adags.hospital.domain.user.AppUser;
import com.adags.hospital.domain.visit.VisitPrescription;
import com.adags.hospital.dto.pharmacy.DispenseRequest;
import com.adags.hospital.dto.pricing.ExcelUploadResult;
import com.adags.hospital.dto.pricing.ServicePriceItemRequest;
import com.adags.hospital.dto.pricing.ServicePriceItemResponse;
import com.adags.hospital.exception.BusinessRuleException;
import com.adags.hospital.exception.ResourceNotFoundException;
import com.adags.hospital.repository.pricing.ServicePriceItemRepository;
import com.adags.hospital.repository.surgery.SurgeryItemListRepository;
import com.adags.hospital.repository.user.UserRepository;
import com.adags.hospital.service.lab.LabStockRequestService;
import com.adags.hospital.service.pharmacy.PharmacistService;
import com.adags.hospital.service.pharmacy.StockService;
import com.adags.hospital.service.pricing.PriceCatalogueService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Controller
@RequestMapping("/pharmacist")
@RequiredArgsConstructor
public class PharmacistViewController {

    private final PharmacistService           pharmacistService;
    private final UserRepository              userRepository;
    private final StockService                stockService;
    private final ServicePriceItemRepository  servicePriceItemRepository;
    private final PriceCatalogueService       priceCatalogueService;
    private final LabStockRequestService      labStockRequestService;
    private final SurgeryItemListRepository   surgeryItemListRepository;

    // -----------------------------------------------------------------------
    //  Dashboard — dispense queue
    // -----------------------------------------------------------------------
    @GetMapping("/dashboard")
    public String dashboard(Model model, Authentication auth) {
        Staff staff = getStaff(auth);
        if (staff == null) return "redirect:/login?error=role";

        model.addAttribute("queue",                  pharmacistService.getDispenseQueue());
        model.addAttribute("visitQueue",             pharmacistService.getVisitDispenseQueue());
        model.addAttribute("visitAwaitingPayment",   pharmacistService.getVisitAwaitingPayment());
        model.addAttribute("surgeryQueue",           surgeryItemListRepository.findPaidPendingPharmacyQueue());
        model.addAttribute("surgeryAwaitingPayment", surgeryItemListRepository.findAwaitingPaymentPharmacyQueue());
        model.addAttribute("staff",                  staff);
        model.addAttribute("activePage",             "dashboard");
        return "pharmacist/dashboard";
    }

    // -----------------------------------------------------------------------
    //  Dispense form — GET
    // -----------------------------------------------------------------------
    @GetMapping("/prescription/{prescriptionId}/dispense")
    public String dispenseForm(@PathVariable UUID prescriptionId,
                               Model model, Authentication auth) {
        Staff staff = getStaff(auth);
        if (staff == null) return "redirect:/login?error=role";

        Prescription prescription = pharmacistService.getPrescriptionById(prescriptionId);
        List<InventoryItem> inventoryOptions = pharmacistService.getInventoryForPrescription(prescriptionId);
        List<StockBatch>    stockBatches     = pharmacistService.getStockBatchesForPrescription(prescriptionId);
        List<String> allergyWarnings = pharmacistService.getAllergyWarnings(prescriptionId);

        model.addAttribute("prescription",    prescription);
        model.addAttribute("inventoryItems",  inventoryOptions);
        model.addAttribute("stockBatches",    stockBatches);
        model.addAttribute("allergyWarnings", allergyWarnings);
        model.addAttribute("form",            new DispenseRequest());
        model.addAttribute("staff",           staff);
        model.addAttribute("activePage",      "dashboard");
        return "pharmacist/dispense";
    }

    // -----------------------------------------------------------------------
    //  Dispense — POST
    // -----------------------------------------------------------------------
    @PostMapping("/prescription/{prescriptionId}/dispense")
    public String dispense(@PathVariable UUID prescriptionId,
                           @ModelAttribute DispenseRequest form,
                           Authentication auth,
                           RedirectAttributes ra) {
        Staff staff = getStaff(auth);
        if (staff == null) return "redirect:/login?error=role";

        form.setPrescriptionId(prescriptionId);
        try {
            pharmacistService.dispense(form, staff);
            ra.addFlashAttribute("successMsg", "Medication dispensed successfully.");
        } catch (BusinessRuleException e) {
            ra.addFlashAttribute("errorMsg", e.getMessage());
            return "redirect:/pharmacist/prescription/" + prescriptionId + "/dispense";
        } catch (Exception e) {
            ra.addFlashAttribute("errorMsg", "Error dispensing: " + e.getMessage());
        }
        return "redirect:/pharmacist/dashboard";
    }

    // -----------------------------------------------------------------------
    //  V26 Visit Prescription — Dispense form GET
    // -----------------------------------------------------------------------
    @GetMapping("/visit-prescription/{id}/dispense")
    public String visitDispenseForm(@PathVariable UUID id,
                                    Model model, Authentication auth) {
        Staff staff = getStaff(auth);
        if (staff == null) return "redirect:/login?error=role";

        VisitPrescription vp = pharmacistService.getVisitPrescriptionById(id);
        List<StockBatch>  batches = pharmacistService.getStockBatchesForVisitPrescription(id);

        model.addAttribute("vp",          vp);
        model.addAttribute("stockBatches", batches);
        model.addAttribute("form",         new DispenseRequest());
        model.addAttribute("staff",        staff);
        model.addAttribute("activePage",   "dashboard");
        return "pharmacist/visit-dispense";
    }

    // -----------------------------------------------------------------------
    //  V26 Visit Prescription — Dispense POST
    // -----------------------------------------------------------------------
    @PostMapping("/visit-prescription/{id}/dispense")
    public String visitDispense(@PathVariable UUID id,
                                @ModelAttribute DispenseRequest form,
                                Authentication auth,
                                RedirectAttributes ra) {
        Staff staff = getStaff(auth);
        if (staff == null) return "redirect:/login?error=role";

        try {
            pharmacistService.dispenseVisit(id, form, staff);
            ra.addFlashAttribute("successMsg", "Visit prescription dispensed successfully.");
        } catch (BusinessRuleException e) {
            ra.addFlashAttribute("errorMsg", e.getMessage());
            return "redirect:/pharmacist/visit-prescription/" + id + "/dispense";
        } catch (Exception e) {
            log.warn("Visit dispense failed: {}", e.getMessage(), e);
            ra.addFlashAttribute("errorMsg", "Error dispensing: " + e.getMessage());
        }
        return "redirect:/pharmacist/dashboard";
    }

    // -----------------------------------------------------------------------
    //  Surgery items — mark dispensed
    // -----------------------------------------------------------------------
    @PostMapping("/surgery-items/{id}/mark-dispensed")
    public String markSurgeryItemDispensed(@PathVariable UUID id, RedirectAttributes ra) {
        SurgeryItemList item = surgeryItemListRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("SurgeryItemList", "id", id));
        item.setDispensed(true);
        surgeryItemListRepository.save(item);
        ra.addFlashAttribute("successMsg", "Surgery item marked as dispensed.");
        return "redirect:/pharmacist/dashboard";
    }

    // -----------------------------------------------------------------------
    //  History — items dispensed by this pharmacist
    // -----------------------------------------------------------------------
    @GetMapping("/history")
    public String history(Model model, Authentication auth) {
        Staff staff = getStaff(auth);
        if (staff == null) return "redirect:/login?error=role";

        model.addAttribute("history",    pharmacistService.getMyHistory(staff.getId()));
        model.addAttribute("staff",      staff);
        model.addAttribute("activePage", "history");
        return "pharmacist/history";
    }

    // -----------------------------------------------------------------------
    //  Stock Management — view
    // -----------------------------------------------------------------------
    @GetMapping("/stock")
    public String stock(Model model, Authentication auth) {
        Staff staff = getStaff(auth);
        if (staff == null) return "redirect:/login?error=role";

        List<StockItem> stockList = stockService.getAllStock();

        // Build a priceItemId → status map so Thymeleaf can render badges
        Map<UUID, String> statusMap = new LinkedHashMap<>();
        for (StockItem s : stockList) {
            statusMap.put(s.getPriceItem().getId(), StockService.computeStatus(s));
        }

        Map<String, Long> alertCounts = stockService.getAlertCounts();

        // Near-expiry: batches expiring within 90 days (3 months) with remaining stock
        List<StockBatch> expiringSoon = stockService.getExpiringSoonBatches(90);
        java.util.Set<UUID> expiringSoonItemIds = expiringSoon.stream()
                .map(b -> b.getStockItem().getId())
                .collect(java.util.stream.Collectors.toSet());

        model.addAttribute("stockList",           stockList);
        model.addAttribute("statusMap",           statusMap);
        model.addAttribute("alertCounts",         alertCounts);
        model.addAttribute("expiringSoon",        expiringSoon);
        model.addAttribute("expiringSoonItemIds", expiringSoonItemIds);
        model.addAttribute("staff",               staff);
        model.addAttribute("activePage",          "stock");
        return "pharmacist/stock";
    }

    // -----------------------------------------------------------------------
    //  Stock Management — restock POST
    // -----------------------------------------------------------------------
    @PostMapping("/stock/restock")
    public String restock(@RequestParam UUID priceItemId,
                          @RequestParam int quantity,
                          @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate expiryDate,
                          @RequestParam(required = false) String supplier,
                          @RequestParam(required = false) String notes,
                          Authentication auth,
                          RedirectAttributes ra) {
        Staff staff = getStaff(auth);
        if (staff == null) return "redirect:/login?error=role";

        try {
            stockService.restock(priceItemId, quantity, expiryDate, supplier, notes, staff);
            ra.addFlashAttribute("successMsg", "Stock updated successfully.");
        } catch (Exception e) {
            log.warn("Restock failed: {}", e.getMessage(), e);
            ra.addFlashAttribute("errorMsg", e.getMessage());
        }
        return "redirect:/pharmacist/stock";
    }

    // -----------------------------------------------------------------------
    //  Stock Management — price catalogue search (AJAX)
    // -----------------------------------------------------------------------
    @GetMapping("/api/catalogue/search")
    @ResponseBody
    public ResponseEntity<List<Map<String, Object>>> searchCatalogue(
            @RequestParam(defaultValue = "") String q) {
        if (q.isBlank()) return ResponseEntity.ok(List.of());

        List<Map<String, Object>> results = servicePriceItemRepository
                .findByProductNameContainingIgnoreCase(q)
                .stream()
                .filter(i -> "pharmacy".equalsIgnoreCase(i.getType())
                          || "lab".equalsIgnoreCase(i.getType()))
                .limit(20)
                .map(item -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id",         item.getId().toString());
                    m.put("name",       item.getProductName());
                    m.put("type",       item.getType());
                    m.put("classification", item.getClassification() != null ? item.getClassification() : "");
                    m.put("price",      item.getPrice());
                    return m;
                })
                .collect(Collectors.toList());
        return ResponseEntity.ok(results);
    }

    // -----------------------------------------------------------------------
    //  Pharmacy Items catalogue — pharmacist can propose additions / price edits
    // -----------------------------------------------------------------------

    @GetMapping("/pharmacy-items")
    public String pharmacyItems(
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            Model model, Authentication auth) {
        Staff staff = getStaff(auth);
        if (staff == null) return "redirect:/login?error=role";

        int pageSize = 20;
        Page<ServicePriceItemResponse> pricePage =
                priceCatalogueService.getPage(search, "PHARMACY", PageRequest.of(page, pageSize));

        model.addAttribute("page",        pricePage);
        model.addAttribute("items",       pricePage.getContent());
        model.addAttribute("search",      search);
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages",  pricePage.getTotalPages());
        model.addAttribute("totalItems",  pricePage.getTotalElements());
        model.addAttribute("newItem",     new ServicePriceItemRequest());
        model.addAttribute("myProposals", priceCatalogueService.getMyProposals(staff.getId()));
        model.addAttribute("activePage",  "pharmacy-items");
        return "pharmacist/pharmacy-items";
    }

    /** AJAX: returns auto-generated product code AND item ID for a given classification. */
    @GetMapping("/pharmacy-items/next-code")
    @ResponseBody
    public ResponseEntity<Map<String, String>> getNextProductCode(
            @RequestParam(defaultValue = "Pharmaceuticals") String classification) {
        String code   = priceCatalogueService.generateNextProductCode(classification);
        String itemId = priceCatalogueService.generateNextItemId();
        return ResponseEntity.ok(Map.of("productCode", code, "itemId", itemId));
    }

    @PostMapping("/pharmacy-items/add")
    public String addPharmacyItem(
            @Valid @ModelAttribute("newItem") ServicePriceItemRequest req,
            BindingResult result,
            RedirectAttributes ra, Authentication auth) {
        Staff staff = getStaff(auth);
        if (staff == null) return "redirect:/login?error=role";
        if (result.hasErrors()) {
            ra.addFlashAttribute("errorMsg", "Validation failed: " +
                    result.getFieldErrors().stream()
                          .map(e -> e.getField() + " — " + e.getDefaultMessage())
                          .collect(Collectors.joining("; ")));
            return "redirect:/pharmacist/pharmacy-items";
        }
        req.setType("PHARMACY");
        req.setCategory("MEDICINES AND CONSUMABLES");
        // Server-side fallback: generate product code if the client didn't supply one
        if (req.getProductCode() == null || req.getProductCode().isBlank()) {
            req.setProductCode(priceCatalogueService.generateNextProductCode(req.getClassification()));
        }
        // Server-side fallback: generate item ID if missing
        if (req.getItemId() == null || req.getItemId().isBlank()) {
            req.setItemId(priceCatalogueService.generateNextItemId());
        }
        priceCatalogueService.submitProposal(req, null, staff);
        ra.addFlashAttribute("successMsg",
                "\"" + req.getProductName() + "\" has been submitted for admin approval.");
        return "redirect:/pharmacist/pharmacy-items";
    }

    @PostMapping("/pharmacy-items/{id}/propose-price")
    public String proposePriceChange(
            @PathVariable UUID id,
            @Valid @ModelAttribute ServicePriceItemRequest req,
            BindingResult result,
            RedirectAttributes ra, Authentication auth) {
        Staff staff = getStaff(auth);
        if (staff == null) return "redirect:/login?error=role";
        if (result.hasErrors()) {
            ra.addFlashAttribute("errorMsg", "Validation failed. Please check the form.");
            return "redirect:/pharmacist/pharmacy-items";
        }
        req.setType("PHARMACY");
        priceCatalogueService.submitProposal(req, id, staff);
        ra.addFlashAttribute("successMsg",
                "Price change for \"" + req.getProductName() + "\" submitted for admin approval.");
        return "redirect:/pharmacist/pharmacy-items";
    }

    @PostMapping("/pharmacy-items/upload")
    public String uploadPharmacyItemsExcel(
            @RequestParam("file") MultipartFile file,
            RedirectAttributes ra, Authentication auth) {
        Staff staff = getStaff(auth);
        if (staff == null) return "redirect:/login?error=role";
        if (file.isEmpty()) {
            ra.addFlashAttribute("errorMsg", "Please select an Excel file to upload.");
            return "redirect:/pharmacist/pharmacy-items";
        }
        String filename = file.getOriginalFilename();
        if (filename == null || (!filename.endsWith(".xlsx") && !filename.endsWith(".xls"))) {
            ra.addFlashAttribute("errorMsg", "Only .xlsx or .xls files are accepted.");
            return "redirect:/pharmacist/pharmacy-items";
        }
        try {
            int count = priceCatalogueService.submitProposalsFromExcel(file, staff);
            ra.addFlashAttribute("successMsg",
                    count + " item proposal(s) submitted for admin approval.");
        } catch (Exception e) {
            log.error("Excel upload failed for pharmacist: {}", e.getMessage(), e);
            ra.addFlashAttribute("errorMsg", "Import failed: " + e.getMessage());
        }
        return "redirect:/pharmacist/pharmacy-items";
    }

    // -----------------------------------------------------------------------
    //  Lab Stock Requests — pharmacist approves/rejects requests from lab tech
    // -----------------------------------------------------------------------
    @GetMapping("/lab-stock-requests")
    public String labStockRequests(Model model, Authentication auth) {
        Staff staff = getStaff(auth);
        if (staff == null) return "redirect:/login?error=role";

        model.addAttribute("pendingRequests", labStockRequestService.getPendingRequests());
        model.addAttribute("handledRequests", labStockRequestService.getHandledRequests());
        model.addAttribute("activePage",      "lab-stock-requests");
        return "pharmacist/lab-stock-requests";
    }

    @PostMapping("/lab-stock-requests/{id}/release")
    public String releaseLabStock(@PathVariable UUID id,
                                  @RequestParam Integer releasedQuantity,
                                  @RequestParam(required = false) String responseNotes,
                                  Authentication auth,
                                  RedirectAttributes ra) {
        Staff staff = getStaff(auth);
        if (staff == null) return "redirect:/login?error=role";
        try {
            labStockRequestService.releaseStock(id, releasedQuantity, responseNotes, staff);
            ra.addFlashAttribute("successMsg", "Stock released successfully.");
        } catch (BusinessRuleException | ResourceNotFoundException e) {
            ra.addFlashAttribute("errorMsg", e.getMessage());
        }
        return "redirect:/pharmacist/lab-stock-requests";
    }

    @PostMapping("/lab-stock-requests/{id}/reject")
    public String rejectLabStock(@PathVariable UUID id,
                                 @RequestParam(required = false) String responseNotes,
                                 Authentication auth,
                                 RedirectAttributes ra) {
        Staff staff = getStaff(auth);
        if (staff == null) return "redirect:/login?error=role";
        try {
            labStockRequestService.rejectRequest(id, responseNotes, staff);
            ra.addFlashAttribute("successMsg", "Request rejected.");
        } catch (BusinessRuleException | ResourceNotFoundException e) {
            ra.addFlashAttribute("errorMsg", e.getMessage());
        }
        return "redirect:/pharmacist/lab-stock-requests";
    }

    // -----------------------------------------------------------------------
    //  Error handler
    // -----------------------------------------------------------------------
    @ExceptionHandler(Exception.class)
    public String handleError(Exception ex, RedirectAttributes ra) {
        log.error("Pharmacist portal error: {}", ex.getMessage(), ex);
        ra.addFlashAttribute("errorMsg", "An error occurred: " + ex.getMessage());
        return "redirect:/pharmacist/dashboard";
    }

    // -----------------------------------------------------------------------
    //  Helper
    // -----------------------------------------------------------------------
    private Staff getStaff(Authentication auth) {
        if (auth == null) return null;
        AppUser user = userRepository.findByUsernameWithStaff(auth.getName()).orElse(null);
        return user != null ? user.getStaff() : null;
    }
}
