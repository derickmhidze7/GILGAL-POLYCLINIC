package com.adags.hospital.controller.admin;

import com.adags.hospital.dto.admin.PatientSpendingRow;
import com.adags.hospital.dto.patient.PatientResponse;
import com.adags.hospital.dto.pricing.ExcelUploadResult;
import com.adags.hospital.dto.pricing.ServicePriceItemRequest;
import com.adags.hospital.dto.pricing.ServicePriceItemResponse;
import com.adags.hospital.domain.billing.LineItemCategory;
import com.adags.hospital.domain.pricing.PriceChangeProposal;
import com.adags.hospital.domain.staff.Staff;
import com.adags.hospital.domain.user.AppUser;
import com.adags.hospital.repository.billing.InvoiceRepository;
import com.adags.hospital.repository.user.UserRepository;
import com.adags.hospital.service.billing.AdminRevenueService;
import com.adags.hospital.service.billing.BillingService;
import com.adags.hospital.service.patient.PatientService;
import com.adags.hospital.service.pricing.PriceCatalogueService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/admin")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminViewController {

    private final PriceCatalogueService  priceCatalogueService;
    private final AdminRevenueService     adminRevenueService;
    private final PatientService           patientService;
    private final InvoiceRepository        invoiceRepository;
    private final BillingService           billingService;
    private final UserRepository           userRepository;

    // ----------------------------------------------------------------
    //  Global model attributes for all admin views
    // ----------------------------------------------------------------

    @ModelAttribute("pendingProposalCount")
    public long pendingProposalCount() {
        return priceCatalogueService.countPendingProposals();
    }

    // ----------------------------------------------------------------
    //  Patients
    // ----------------------------------------------------------------

    @GetMapping("/patients")
    public String patients(@RequestParam(required = false) String search,
                           @RequestParam(defaultValue = "0") int page, Model model) {
        Sort sort = Sort.by("lastName");
        Page<PatientResponse> patientsPage;
        if (search != null && !search.isBlank()) {
            patientsPage = patientService.searchAll(search, PageRequest.of(page, 20, sort));
        } else {
            patientsPage = patientService.getAllIncludingDischarged(PageRequest.of(page, 20, sort));
        }

        // Build a map: patientId -> [totalBilled (BigDecimal), invoiceCount (Long)]
        // Loaded once for global aggregate stats (not page-scoped)
        Map<UUID, Object[]> spendingMap = invoiceRepository.patientSpendingTotals()
                .stream()
                .collect(Collectors.toMap(row -> (UUID) row[0], row -> row));

        List<PatientSpendingRow> rows = patientsPage.getContent().stream()
                .map(p -> {
                    Object[] data = spendingMap.get(p.id());
                    BigDecimal total = data != null ? (BigDecimal) data[1] : BigDecimal.ZERO;
                    long count  = data != null ? ((Number) data[2]).longValue() : 0L;
                    return new PatientSpendingRow(p, total, count);
                })
                .collect(Collectors.toList());

        model.addAttribute("patientRows", rows);
        model.addAttribute("search",      search);
        // Summary stats — computed globally from all spending data
        BigDecimal grandTotal = spendingMap.values().stream()
                .map(row -> (BigDecimal) row[1]).reduce(BigDecimal.ZERO, BigDecimal::add);
        long totalInvoices   = spendingMap.values().stream()
                .mapToLong(row -> ((Number) row[2]).longValue()).sum();
        long patientsWithBill = spendingMap.values().stream()
                .filter(row -> ((Number) row[2]).longValue() > 0).count();
        model.addAttribute("grandTotal",       grandTotal);
        model.addAttribute("totalInvoices",    totalInvoices);
        model.addAttribute("patientsWithBill", patientsWithBill);
        model.addAttribute("totalPatients",    patientsPage.getTotalElements());
        model.addAttribute("currentPage",      page);
        model.addAttribute("totalPages",       patientsPage.getTotalPages());
        model.addAttribute("activePage",   "patients");
        model.addAttribute("pendingCancellations", billingService.countPendingCancellations());
        return "admin/patients";
    }

    // ----------------------------------------------------------------
    //  Dashboard
    // ----------------------------------------------------------------

    @GetMapping({"/", "/dashboard"})
    public String dashboard(Model model) {
        List<ServicePriceItemResponse> all = priceCatalogueService.getAll();
        model.addAttribute("totalItems", all.size());
        model.addAttribute("types", priceCatalogueService.getDistinctTypes());

        long pharmacyCount = all.stream().filter(i -> "PHARMACY".equalsIgnoreCase(i.getType())).count();
        long labCount      = all.stream().filter(i -> "LABORATORY".equalsIgnoreCase(i.getType())).count();
        long surgeryCount  = all.stream().filter(i -> "SURGERY".equalsIgnoreCase(i.getType())).count();

        model.addAttribute("pharmacyCount", pharmacyCount);
        model.addAttribute("labCount",      labCount);
        model.addAttribute("surgeryCount",  surgeryCount);
        model.addAttribute("activePage",    "dashboard");
        model.addAttribute("pendingCancellations", billingService.countPendingCancellations());
        return "admin/dashboard";
    }

    // ----------------------------------------------------------------
    //  Revenue analytics
    // ----------------------------------------------------------------

    @GetMapping("/revenue")
    public String revenue(
            @RequestParam(required = false) String filterType,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate day,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate weekOf,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate rangeFrom,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate rangeTo,
            Model model) {

        AdminRevenueService.RevenueReport report =
                adminRevenueService.buildReport(filterType, year, month, day, weekOf, rangeFrom, rangeTo);

        int currentYear = LocalDate.now().getYear();

        model.addAttribute("report",        report);
        model.addAttribute("filterType",    filterType  != null ? filterType  : "month");
        model.addAttribute("selectedYear",  report.selectedYear());
        model.addAttribute("selectedMonth", report.selectedMonth());
        model.addAttribute("currentYear",   currentYear);
        model.addAttribute("activePage",    "revenue");
        model.addAttribute("pendingCancellations", billingService.countPendingCancellations());
        return "admin/revenue";
    }

    @GetMapping("/revenue/items")
    public String revenueItems(
            @RequestParam(required = false) String filterType,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate day,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate weekOf,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate rangeFrom,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate rangeTo,
            @RequestParam String category,
            Model model) {

        LineItemCategory cat;
        try {
            cat = LineItemCategory.valueOf(category.toUpperCase());
        } catch (IllegalArgumentException ex) {
            return "redirect:/admin/revenue";
        }

        AdminRevenueService.ItemsReport itemsReport =
                adminRevenueService.buildItemsReport(
                        filterType, year, month, day, weekOf, rangeFrom, rangeTo, cat);

        model.addAttribute("itemsReport",  itemsReport);
        model.addAttribute("filterType",   filterType != null ? filterType : "month");
        model.addAttribute("selectedYear", itemsReport.selectedYear());
        model.addAttribute("selectedMonth",itemsReport.selectedMonth());
        model.addAttribute("activePage",   "revenue");
        model.addAttribute("pendingCancellations", billingService.countPendingCancellations());
        return "admin/revenue-items";
    }

    // ----------------------------------------------------------------
    //  Price Catalogue — list
    // ----------------------------------------------------------------

    @GetMapping("/pricing")
    public String pricing(@RequestParam(required = false) String type,
                          @RequestParam(required = false) String search,
                          @RequestParam(defaultValue = "0") int page,
                          Model model) {

        int pageSize = 20;
        Page<ServicePriceItemResponse> pricePage =
                priceCatalogueService.getPage(search, type, PageRequest.of(page, pageSize));

        model.addAttribute("page",         pricePage);
        model.addAttribute("items",        pricePage.getContent());
        model.addAttribute("types",        priceCatalogueService.getDistinctTypes());
        model.addAttribute("selectedType", type);
        model.addAttribute("search",       search);
        model.addAttribute("currentPage",  page);
        model.addAttribute("totalPages",   pricePage.getTotalPages());
        model.addAttribute("totalItems",   pricePage.getTotalElements());
        model.addAttribute("newItem",      new ServicePriceItemRequest());
        model.addAttribute("activePage",   "pricing");
        model.addAttribute("pendingCancellations", billingService.countPendingCancellations());
        return "admin/pricing";
    }

    // ----------------------------------------------------------------
    //  Price Catalogue — add single item
    // ----------------------------------------------------------------

    @PostMapping("/pricing")
    public String addItem(@Valid @ModelAttribute("newItem") ServicePriceItemRequest req,
                          BindingResult result,
                          RedirectAttributes ra,
                          Model model) {
        if (result.hasErrors()) {
            model.addAttribute("items",      priceCatalogueService.getAll());
            model.addAttribute("types",      priceCatalogueService.getDistinctTypes());
            model.addAttribute("activePage", "pricing");
            model.addAttribute("showModal",  true);
            return "admin/pricing";
        }
        priceCatalogueService.create(req);
        ra.addFlashAttribute("successMsg", "Item \"" + req.getProductName() + "\" added successfully.");
        return "redirect:/admin/pricing";
    }

    // ----------------------------------------------------------------
    //  Price Catalogue — edit item
    // ----------------------------------------------------------------

    @PostMapping("/pricing/{id}/edit")
    public String editItem(@PathVariable UUID id,
                           @Valid @ModelAttribute ServicePriceItemRequest req,
                           BindingResult result,
                           RedirectAttributes ra) {
        if (result.hasErrors()) {
            ra.addFlashAttribute("errorMsg", "Validation failed. Please check the form.");
            return "redirect:/admin/pricing";
        }
        priceCatalogueService.update(id, req);
        ra.addFlashAttribute("successMsg", "Item updated successfully.");
        return "redirect:/admin/pricing";
    }

    // ----------------------------------------------------------------
    //  Price Catalogue — delete item
    // ----------------------------------------------------------------

    @PostMapping("/pricing/{id}/delete")
    public String deleteItem(@PathVariable UUID id, RedirectAttributes ra) {
        priceCatalogueService.delete(id);
        ra.addFlashAttribute("successMsg", "Item deleted.");
        return "redirect:/admin/pricing";
    }

    // ----------------------------------------------------------------
    //  Price Catalogue — Excel upload
    // ----------------------------------------------------------------

    @PostMapping("/pricing/upload")
    public String uploadExcel(@RequestParam("file") MultipartFile file,
                              RedirectAttributes ra) {
        if (file.isEmpty()) {
            ra.addFlashAttribute("errorMsg", "Please select an Excel file to upload.");
            return "redirect:/admin/pricing";
        }
        String filename = file.getOriginalFilename();
        if (filename == null || (!filename.endsWith(".xlsx") && !filename.endsWith(".xls"))) {
            ra.addFlashAttribute("errorMsg", "Only .xlsx or .xls files are accepted.");
            return "redirect:/admin/pricing";
        }
        try {
            ExcelUploadResult result = priceCatalogueService.importFromExcel(file);
            ra.addFlashAttribute("uploadResult", result);
        } catch (IOException e) {
            ra.addFlashAttribute("errorMsg", "Failed to process the file: " + e.getMessage());
        }
        return "redirect:/admin/pricing";
    }

    // ----------------------------------------------------------------
    //  Price-Change Proposals (submitted by pharmacists)
    // ----------------------------------------------------------------

    @GetMapping("/price-proposals")
    public String priceProposals(Model model) {
        List<PriceChangeProposal> all = priceCatalogueService.getAllProposals();
        long pendingCount = priceCatalogueService.countPendingProposals();
        model.addAttribute("proposals",    all);
        model.addAttribute("pendingCount", pendingCount);
        model.addAttribute("pendingCancellations", billingService.countPendingCancellations());
        model.addAttribute("activePage",   "price-proposals");
        return "admin/price-proposals";
    }

    @PostMapping("/price-proposals/{id}/approve")
    public String approveProposal(@PathVariable UUID id,
                                  RedirectAttributes ra, Authentication auth) {
        Staff admin = getAdminStaff(auth);
        try {
            priceCatalogueService.approveProposal(id, admin);
            ra.addFlashAttribute("successMsg", "Proposal approved — item is now live in the catalogue.");
        } catch (Exception e) {
            ra.addFlashAttribute("errorMsg", e.getMessage());
        }
        return "redirect:/admin/price-proposals";
    }

    @PostMapping("/price-proposals/{id}/reject")
    public String rejectProposal(@PathVariable UUID id,
                                 @RequestParam(required = false) String reason,
                                 RedirectAttributes ra, Authentication auth) {
        Staff admin = getAdminStaff(auth);
        try {
            priceCatalogueService.rejectProposal(id, reason, admin);
            ra.addFlashAttribute("successMsg", "Proposal rejected.");
        } catch (Exception e) {
            ra.addFlashAttribute("errorMsg", e.getMessage());
        }
        return "redirect:/admin/price-proposals";
    }

    // ----------------------------------------------------------------
    //  Invoice Cancellation Approvals
    // ----------------------------------------------------------------

    @GetMapping("/invoice-cancellations")
    public String invoiceCancellations(Model model) {
        model.addAttribute("pendingInvoices", billingService.getPendingCancellations());
        model.addAttribute("pendingCancellations", billingService.countPendingCancellations());
        model.addAttribute("activePage", "invoice-cancellations");
        return "admin/invoice-cancellations";
    }

    @PostMapping("/invoice-cancellations/{id}/approve")
    public String approveCancellation(@PathVariable UUID id, RedirectAttributes ra) {
        try {
            billingService.approveCancellation(id);
            ra.addFlashAttribute("successMsg", "Invoice cancellation approved. Invoice is now VOIDED.");
        } catch (Exception e) {
            ra.addFlashAttribute("errorMsg", e.getMessage());
        }
        return "redirect:/admin/invoice-cancellations";
    }

    @PostMapping("/invoice-cancellations/{id}/reject")
    public String rejectCancellation(@PathVariable UUID id, RedirectAttributes ra) {
        try {
            billingService.rejectCancellation(id);
            ra.addFlashAttribute("successMsg", "Cancellation request rejected. Invoice is now TERMINATED.");
        } catch (Exception e) {
            ra.addFlashAttribute("errorMsg", e.getMessage());
        }
        return "redirect:/admin/invoice-cancellations";
    }

    // ----------------------------------------------------------------
    //  Helper
    // ----------------------------------------------------------------

    private Staff getAdminStaff(Authentication auth) {
        if (auth == null) return null;
        AppUser user = userRepository.findByUsernameWithStaff(auth.getName()).orElse(null);
        return user != null ? user.getStaff() : null;
    }
}
