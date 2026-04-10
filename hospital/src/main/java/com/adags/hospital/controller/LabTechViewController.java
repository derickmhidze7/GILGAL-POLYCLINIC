package com.adags.hospital.controller;

import com.adags.hospital.domain.lab.*;
import com.adags.hospital.domain.pharmacy.StockItem;
import com.adags.hospital.domain.staff.Staff;
import com.adags.hospital.domain.user.AppUser;
import com.adags.hospital.domain.visit.VisitLabRequest;
import com.adags.hospital.dto.lab.LabResultEntryRequest;
import com.adags.hospital.dto.lab.LabStockRequestForm;
import com.adags.hospital.dto.lab.VisitLabResultRequest;
import com.adags.hospital.dto.pricing.ServicePriceItemRequest;
import com.adags.hospital.exception.BusinessRuleException;
import com.adags.hospital.exception.ResourceNotFoundException;
import com.adags.hospital.repository.pharmacy.StockItemRepository;
import com.adags.hospital.repository.pricing.ServicePriceItemRepository;
import com.adags.hospital.repository.user.UserRepository;
import com.adags.hospital.repository.visit.VisitLabRequestRepository;
import com.adags.hospital.service.lab.LabStockRequestService;
import com.adags.hospital.service.lab.LabTechService;
import com.adags.hospital.service.pharmacy.StockService;
import com.adags.hospital.service.pricing.PriceCatalogueService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.beans.propertyeditors.StringTrimmerEditor;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Controller
@RequestMapping("/labtech")
@RequiredArgsConstructor
public class LabTechViewController {

    private final LabTechService             labTechService;
    private final UserRepository             userRepository;
    private final VisitLabRequestRepository  visitLabRequestRepository;
    private final PriceCatalogueService      priceCatalogueService;
    private final ServicePriceItemRepository servicePriceItemRepository;
    private final LabStockRequestService     labStockRequestService;
    private final StockItemRepository        stockItemRepository;
    private final StockService               stockService;

    @Value("${app.upload.path:uploads}")
    private String uploadBasePath;

    // -----------------------------------------------------------------------
    //  Dashboard — queue of pending/in-progress requests
    // -----------------------------------------------------------------------
    @GetMapping("/dashboard")
    public String dashboard(Model model, Authentication auth) {
        Staff staff = getStaff(auth);
        if (staff == null) return "redirect:/login?error=role";

        model.addAttribute("queue",           labTechService.getQueue());
        model.addAttribute("awaitingPayment",  labTechService.getAwaitingPayment());
        model.addAttribute("staff",            staff);
        model.addAttribute("activePage", "dashboard");
        return "labtech/dashboard";
    }

    // -----------------------------------------------------------------------
    //  Accept sample — mark PENDING → IN_PROGRESS
    // -----------------------------------------------------------------------
    @PostMapping("/request/{requestId}/accept")
    public String acceptSample(@PathVariable UUID requestId,
                               @RequestParam(required = false) String sampleType,
                               @RequestParam(required = false, defaultValue = "ADEQUATE") String sampleQuality,
                               Authentication auth,
                               RedirectAttributes ra) {
        Staff staff = getStaff(auth);
        if (staff == null) return "redirect:/login?error=role";
        try {
            SampleQuality sq = SampleQuality.valueOf(sampleQuality.toUpperCase());
            labTechService.acceptSample(requestId, sampleType, sq, staff);
            ra.addFlashAttribute("successMsg", "Sample accepted and marked IN PROGRESS.");
        } catch (Exception e) {
            ra.addFlashAttribute("errorMsg", "Could not accept sample: " + e.getMessage());
        }
        return "redirect:/labtech/dashboard";
    }

    // -----------------------------------------------------------------------
    //  Result entry form — GET
    // -----------------------------------------------------------------------
    @GetMapping("/request/{requestId}/result")
    public String resultEntryForm(@PathVariable UUID requestId,
                                  Model model, Authentication auth) {
        Staff staff = getStaff(auth);
        if (staff == null) return "redirect:/login?error=role";

        LabRequest request = labTechService.getRequestById(requestId);
        model.addAttribute("request",         request);
        model.addAttribute("existingResult",  request.getResult());
        model.addAttribute("staff",           staff);
        model.addAttribute("interpretations", LabInterpretation.values());
        model.addAttribute("sampleQualities", SampleQuality.values());
        model.addAttribute("form",            new LabResultEntryRequest());
        model.addAttribute("activePage",      "dashboard");
        return "labtech/result-entry";
    }

    // -----------------------------------------------------------------------
    //  InitBinder — convert empty strings to null (prevents BindException
    //  when the "—" option sends "" for enum fields like interpretation)
    // -----------------------------------------------------------------------
    @InitBinder
    public void initBinder(WebDataBinder binder) {
        binder.registerCustomEditor(String.class, new StringTrimmerEditor(true));
    }

    // -----------------------------------------------------------------------
    //  Save result (draft, not yet submitted)
    // -----------------------------------------------------------------------
    @PostMapping("/request/{requestId}/result/save")
    public String saveResult(@PathVariable UUID requestId,
                             @ModelAttribute("form") LabResultEntryRequest form,
                             BindingResult bindingResult,
                             @RequestParam(value = "reportPdf", required = false) MultipartFile reportPdf,
                             @RequestParam(value = "machinePdf", required = false) MultipartFile machinePdf,
                             Authentication auth,
                             RedirectAttributes ra) {
        Staff staff = getStaff(auth);
        if (staff == null) return "redirect:/login?error=role";
        try {
            labTechService.saveResult(requestId, form, reportPdf, machinePdf, staff);
            ra.addFlashAttribute("successMsg", "Result saved as draft.");
        } catch (Exception e) {
            log.error("Error saving lab result for request {}: {} — {}", requestId,
                    e.getClass().getSimpleName(), e.getMessage(), e);
            ra.addFlashAttribute("errorMsg",
                    "Error saving result: " + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
        return "redirect:/labtech/request/" + requestId + "/result";
    }

    // -----------------------------------------------------------------------
    //  Download lab report PDF
    // -----------------------------------------------------------------------
    @GetMapping("/request/{requestId}/result/pdf")
    public ResponseEntity<Resource> downloadLabReportPdf(@PathVariable UUID requestId,
                                                         Authentication auth) {
        Staff staff = getStaff(auth);
        if (staff == null) return ResponseEntity.status(403).build();
        try {
            LabRequest request = labTechService.getRequestById(requestId);
            LabResult result = request.getResult();
            if (result == null || result.getReportPdfPath() == null) {
                return ResponseEntity.notFound().build();
            }
            Path filePath = Paths.get(uploadBasePath).resolve(result.getReportPdfPath()).normalize();
            Resource resource = new UrlResource(filePath.toUri());
            if (!resource.exists() || !resource.isReadable()) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_PDF)
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "inline; filename=\"lab-report-" + requestId + ".pdf\"")
                    .body(resource);
        } catch (Exception e) {
            log.error("Error downloading lab report PDF for request {}: {}", requestId, e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    // -----------------------------------------------------------------------
    //  Download machine output PDF (for reading parameters off the machine)
    // -----------------------------------------------------------------------
    @GetMapping("/request/{requestId}/result/machine-pdf")
    public ResponseEntity<Resource> downloadMachinePdf(@PathVariable UUID requestId,
                                                       Authentication auth) {
        Staff staff = getStaff(auth);
        if (staff == null) return ResponseEntity.status(403).build();
        try {
            LabRequest request = labTechService.getRequestById(requestId);
            LabResult result = request.getResult();
            if (result == null || result.getMachinePdfPath() == null) {
                return ResponseEntity.notFound().build();
            }
            Path filePath = Paths.get(uploadBasePath).resolve(result.getMachinePdfPath()).normalize();
            Resource resource = new UrlResource(filePath.toUri());
            if (!resource.exists() || !resource.isReadable()) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_PDF)
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "inline; filename=\"machine-output-" + requestId + ".pdf\"")
                    .body(resource);
        } catch (Exception e) {
            log.error("Error downloading machine PDF for request {}: {}", requestId, e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    // -----------------------------------------------------------------------
    //  Submit & verify result — marks request COMPLETED
    // -----------------------------------------------------------------------
    @PostMapping("/request/{requestId}/result/submit")
    public String submitResult(@PathVariable UUID requestId,
                               Authentication auth,
                               RedirectAttributes ra) {
        Staff staff = getStaff(auth);
        if (staff == null) return "redirect:/login?error=role";
        try {
            labTechService.submitResult(requestId, staff);
            ra.addFlashAttribute("successMsg", "Result submitted and locked. Request marked COMPLETED.");
        } catch (BusinessRuleException e) {
            ra.addFlashAttribute("errorMsg", e.getMessage());
        } catch (Exception e) {
            ra.addFlashAttribute("errorMsg", "Error submitting result: " + e.getMessage());
        }
        return "redirect:/labtech/dashboard";
    }

    // -----------------------------------------------------------------------
    //  V26 — Accept sample (PENDING → IN_PROGRESS)
    // -----------------------------------------------------------------------
    @PostMapping("/visit-request/{id}/accept")
    public String acceptVisitSample(@PathVariable UUID id,
                                    Authentication auth,
                                    RedirectAttributes ra) {
        Staff staff = getStaff(auth);
        if (staff == null) return "redirect:/login?error=role";
        try {
            labTechService.acceptVisitSample(id, staff);
            ra.addFlashAttribute("successMsg", "Sample accepted and marked IN PROGRESS.");
        } catch (Exception e) {
            ra.addFlashAttribute("errorMsg", "Could not accept sample: " + e.getMessage());
        }
        return "redirect:/labtech/dashboard";
    }

    // -----------------------------------------------------------------------
    //  V26 — Result entry form (GET)
    // -----------------------------------------------------------------------
    @GetMapping("/visit-request/{id}/result")
    public String visitResultEntryForm(@PathVariable UUID id,
                                       Model model, Authentication auth) {
        Staff staff = getStaff(auth);
        if (staff == null) return "redirect:/login?error=role";

        VisitLabRequest visitRequest = visitLabRequestRepository.findByIdWithDetails(id)
                .orElseThrow(() -> new ResourceNotFoundException("VisitLabRequest", "id", id));
        model.addAttribute("visitRequest", visitRequest);
        model.addAttribute("staff",        staff);
        model.addAttribute("activePage",   "dashboard");
        return "labtech/visit-result-entry";
    }

    // -----------------------------------------------------------------------
    //  V26 — Save result (rich JSON body from fetch())
    // -----------------------------------------------------------------------
    @PostMapping("/visit-request/{id}/result/save")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> saveVisitResult(
            @PathVariable UUID id,
            @RequestBody VisitLabResultRequest req,
            Authentication auth) {
        Staff staff = getStaff(auth);
        if (staff == null)
            return ResponseEntity.status(401).body(Map.of("message", "Not authenticated."));
        try {
            labTechService.saveVisitResult(id, req);
            return ResponseEntity.ok(Map.of("message", "Result saved as draft."));
        } catch (BusinessRuleException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            log.error("Error saving visit result: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of("message", "Error saving result: " + e.getMessage()));
        }
    }

    // -----------------------------------------------------------------------
    //  Upload machine-output PDF for visit lab request (stored in DB)
    // -----------------------------------------------------------------------
    @PostMapping("/visit-request/{id}/result/machine-pdf")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> uploadVisitMachinePdf(
            @PathVariable UUID id,
            @RequestParam("machinePdf") MultipartFile file,
            Authentication auth) {
        Staff staff = getStaff(auth);
        if (staff == null) return ResponseEntity.status(401).body(Map.of("message", "Not authenticated."));
        try {
            boolean saved = labTechService.saveVisitMachinePdfToDb(file, id);
            if (!saved) return ResponseEntity.internalServerError().body(Map.of("message", "Failed to save file."));
            return ResponseEntity.ok(Map.of("message", "Machine PDF uploaded.", "stored", "database"));
        } catch (Exception e) {
            log.error("Error uploading machine PDF for visit request {}: {}", id, e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of("message", "Upload failed: " + e.getMessage()));
        }
    }

    // -----------------------------------------------------------------------
    //  Serve machine-output PDF for visit lab request (from DB)
    // -----------------------------------------------------------------------
    @GetMapping("/visit-request/{id}/result/machine-pdf")
    public ResponseEntity<byte[]> serveVisitMachinePdf(@PathVariable UUID id, Authentication auth) {
        Staff staff = getStaff(auth);
        if (staff == null) return ResponseEntity.status(403).build();
        try {
            byte[] data = labTechService.getVisitLabMachinePdfBytes(id);
            if (data == null || data.length == 0) return ResponseEntity.notFound().build();
            String name = labTechService.getVisitLabMachinePdfName(id);
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_PDF)
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + name + "\"")
                    .body(data);
        } catch (Exception e) {
            log.error("Error serving machine PDF for visit request {}: {}", id, e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    // -----------------------------------------------------------------------
    //  V26 — Submit result (lock) via JSON
    // -----------------------------------------------------------------------
    @PostMapping("/visit-request/{id}/result/submit")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> submitVisitResult(
            @PathVariable UUID id,
            Authentication auth) {
        Staff staff = getStaff(auth);
        if (staff == null)
            return ResponseEntity.status(401).body(Map.of("message", "Not authenticated."));
        try {
            labTechService.submitVisitResult(id, staff);
            return ResponseEntity.ok(Map.of("message", "Result submitted and locked. Request marked COMPLETED."));
        } catch (BusinessRuleException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            log.error("Error submitting visit result: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of("message", "Error submitting result: " + e.getMessage()));
        }
    }

    // -----------------------------------------------------------------------
    //  History — results entered by this technician
    // -----------------------------------------------------------------------
    @GetMapping("/history")
    public String history(Model model, Authentication auth) {
        Staff staff = getStaff(auth);
        if (staff == null) return "redirect:/login?error=role";

        model.addAttribute("history",      labTechService.getMyHistory(staff.getId()));
        model.addAttribute("visitHistory", labTechService.getVisitLabHistory());
        model.addAttribute("staff",        staff);
        model.addAttribute("activePage", "history");
        return "labtech/history";
    }

    // -----------------------------------------------------------------------
    //  View / print full lab report for a specific result
    // -----------------------------------------------------------------------
    @GetMapping("/result/{resultId}/report")
    public String viewReport(@PathVariable UUID resultId,
                             Model model, Authentication auth) {
        Staff staff = getStaff(auth);
        if (staff == null) return "redirect:/login?error=role";

        model.addAttribute("result",     labTechService.getLabResultForReport(resultId));
        model.addAttribute("staff",      staff);
        model.addAttribute("activePage", "history");
        return "labtech/lab-report";
    }

    // -----------------------------------------------------------------------
    //  Lab Catalogue — browse LABORATORY items and propose edits / new tests
    // -----------------------------------------------------------------------
    @GetMapping("/lab-catalogue")
    public String labCatalogue(Model model, Authentication auth) {
        Staff staff = getStaff(auth);
        if (staff == null) return "redirect:/login?error=role";

        model.addAttribute("labItems",    servicePriceItemRepository.findByTypeIgnoreCase("LABORATORY"));
        model.addAttribute("myProposals", priceCatalogueService.getMyProposals(staff.getId()));
        model.addAttribute("staff",       staff);
        model.addAttribute("activePage",  "lab-catalogue");
        return "labtech/lab-catalogue";
    }

    @PostMapping("/lab-catalogue/propose")
    public String proposeCatalogueItem(
            @RequestParam(required = false) UUID priceItemId,
            @RequestParam String productName,
            @RequestParam(required = false) String classification,
            @RequestParam(required = false) String customClassification,
            @RequestParam BigDecimal price,
            @RequestParam(required = false) String notes,
            Authentication auth,
            RedirectAttributes ra) {
        Staff staff = getStaff(auth);
        if (staff == null) return "redirect:/login?error=role";
        try {
            String effectiveClassification = "OTHERS".equals(classification)
                    && customClassification != null && !customClassification.isBlank()
                    ? customClassification.trim().toUpperCase()
                    : classification;
            ServicePriceItemRequest req = new ServicePriceItemRequest(
                    null, notes, productName, effectiveClassification, "LABORATORY", null, price);
            priceCatalogueService.submitLabTestProposal(req, priceItemId, staff);
            ra.addFlashAttribute("successMsg", "Proposal submitted — awaiting admin approval.");
        } catch (Exception e) {
            ra.addFlashAttribute("errorMsg", "Could not submit proposal: " + e.getMessage());
        }
        return "redirect:/labtech/lab-catalogue";
    }

    // -----------------------------------------------------------------------
    //  Error handler — catches any uncaught exception from this controller
    // -----------------------------------------------------------------------
    //  Stock Requests — lab tech requests items from pharmacy
    // -----------------------------------------------------------------------
    @GetMapping("/stock-requests")
    public String stockRequests(Model model, Authentication auth) {
        Staff staff = getStaff(auth);
        if (staff == null) return "redirect:/login?error=role";

        model.addAttribute("requests",   labStockRequestService.getMyRequests(staff.getId()));
        model.addAttribute("activePage", "stock-requests");
        return "labtech/stock-requests";
    }

    @PostMapping("/stock-requests/new")
    public String submitStockRequest(@RequestParam UUID stockItemId,
                                     @RequestParam Integer requestedQuantity,
                                     @RequestParam(required = false) String requestNotes,
                                     Authentication auth,
                                     RedirectAttributes ra) {
        Staff staff = getStaff(auth);
        if (staff == null) return "redirect:/login?error=role";
        try {
            LabStockRequestForm form = new LabStockRequestForm(stockItemId, requestedQuantity, requestNotes);
            labStockRequestService.submitRequest(form, staff);
            ra.addFlashAttribute("successMsg", "Stock request submitted successfully.");
        } catch (BusinessRuleException | ResourceNotFoundException e) {
            ra.addFlashAttribute("errorMsg", e.getMessage());
        }
        return "redirect:/labtech/stock-requests";
    }

    @GetMapping("/api/pharmacy-stock/search")
    @ResponseBody
    public List<Map<String, Object>> searchPharmacyStock(@RequestParam String q) {
        if (q == null || q.trim().length() < 2) return List.of();
        return stockItemRepository.searchInStockPharmacyItems(q.trim()).stream()
                .map(item -> {
                    Map<String, Object> m = new java.util.LinkedHashMap<>();
                    m.put("id",              item.getId().toString());
                    m.put("name",            item.getPriceItem().getProductName());
                    m.put("currentQuantity", item.getCurrentQuantity());
                    return m;
                })
                .collect(java.util.stream.Collectors.toList());
    }

    // -----------------------------------------------------------------------
    @ExceptionHandler(Exception.class)
    public String handleError(Exception ex, RedirectAttributes ra) {
        log.error("Lab tech portal unhandled error: {} — {}", ex.getClass().getSimpleName(), ex.getMessage(), ex);
        ra.addFlashAttribute("errorMsg",
                "An unexpected error occurred: " + ex.getClass().getSimpleName() + ": " + ex.getMessage());
        return "redirect:/labtech/dashboard";
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
