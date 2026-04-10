package com.adags.hospital.controller.admin;

import com.adags.hospital.domain.billing.PaymentMethod;
import com.adags.hospital.domain.expense.*;
import com.adags.hospital.dto.expense.ExpenseAnalyticsDto;
import com.adags.hospital.dto.expense.ExpenseFormDto;
import com.adags.hospital.dto.pricing.ServicePriceItemResponse;
import com.adags.hospital.service.expense.ExpenseService;
import com.adags.hospital.service.pricing.PriceCatalogueService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Controller
@RequestMapping("/admin/expenses")
@PreAuthorize("hasRole('ADMIN')")
public class AdminExpenseController {

    private static final Map<String, List<ExpenseCategory>> EXPENSE_GROUPS = Map.of(
            "pharmacy",    List.of(ExpenseCategory.PHARMACY_RESTOCK, ExpenseCategory.MEDICAL_SUPPLIES),
            "salaries",    List.of(ExpenseCategory.SALARIES),
            "utilities",   List.of(ExpenseCategory.UTILITIES),
            "maintenance", List.of(ExpenseCategory.MAINTENANCE),
            "equipment",   List.of(ExpenseCategory.EQUIPMENT),
            "other",       List.of(ExpenseCategory.LAB_SUPPLIES, ExpenseCategory.CLEANING,
                                   ExpenseCategory.TRANSPORT, ExpenseCategory.MISCELLANEOUS)
    );

    private static final Map<String, String> GROUP_LABELS = Map.of(
            "pharmacy",    "Medication & Pharmacy",
            "salaries",    "Salaries",
            "utilities",   "Utilities",
            "maintenance", "Maintenance",
            "equipment",   "Equipment",
            "other",       "Other Expenses"
    );

    private final ExpenseService        expenseService;
    private final PriceCatalogueService priceCatalogueService;

    public AdminExpenseController(ExpenseService expenseService,
                                  PriceCatalogueService priceCatalogueService) {
        this.expenseService        = expenseService;
        this.priceCatalogueService = priceCatalogueService;
    }
    // ── Item-suggestion autocomplete endpoint ─────────────────────────────

    /**
     * Returns up to 12 matching price-catalogue items as JSON,
     * filtered by expense category so the right item type is searched.
     * Used by the autocomplete widget on the expense form.
     */
    @GetMapping("/item-suggest")
    @ResponseBody
    public List<Map<String, Object>> itemSuggest(
            @RequestParam(defaultValue = "") String q,
            @RequestParam(defaultValue = "") String cat) {
        if (q.trim().length() < 2) return List.of();
        return priceCatalogueService
                .searchByExpenseCategory(q.trim(), cat)
                .stream()
                .map(item -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("name",           item.getProductName());
                    m.put("classification", item.getClassification() != null ? item.getClassification() : "");
                    m.put("price",          item.getPrice());
                    m.put("code",           item.getProductCode()   != null ? item.getProductCode()   : "");
                    return m;
                })
                .toList();
    }
    // ── Shared model attributes ───────────────────────────────────────────────

    private void addFormRefData(Model model) {
        model.addAttribute("categories",       ExpenseCategory.values());
        model.addAttribute("paymentMethods",   PaymentMethod.values());
        model.addAttribute("recurringOptions", RecurringFrequency.values());
    }

    // ── GET /admin/expenses  (list) ───────────────────────────────────────────

    @GetMapping
    public String list(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(required = false) ExpenseCategory category,
            @RequestParam(required = false) ExpenseStatus   status,
            @RequestParam(required = false) String          group,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size,
            Model model) {

        String normGroup = (group != null && !group.isBlank()) ? group.toLowerCase() : null;
        List<ExpenseCategory> groupCats = normGroup != null ? EXPENSE_GROUPS.get(normGroup) : null;

        Page<?> pageResult;
        if (groupCats != null) {
            List<ExpenseCategory> effectiveCats;
            if (category != null) {
                effectiveCats = groupCats.contains(category) ? List.of(category) : List.of();
            } else {
                effectiveCats = groupCats;
            }
            pageResult = expenseService.getFilteredPageByCategories(fromDate, toDate, effectiveCats, status, page, size);
        } else {
            pageResult = expenseService.getFilteredPage(fromDate, toDate, category, status, page, size);
        }

        model.addAttribute("expenses",      pageResult.getContent());
        model.addAttribute("currentPage",   pageResult.getNumber());
        model.addAttribute("totalPages",    pageResult.getTotalPages());
        model.addAttribute("totalElements", pageResult.getTotalElements());

        model.addAttribute("fromDate",  fromDate);
        model.addAttribute("toDate",    toDate);
        model.addAttribute("category",  category);
        model.addAttribute("status",    status);
        model.addAttribute("group",       normGroup);
        model.addAttribute("activeGroup", normGroup);
        model.addAttribute("groupLabel",  normGroup != null ? GROUP_LABELS.getOrDefault(normGroup, "Expenses") : null);

        model.addAttribute("categories", ExpenseCategory.values());
        model.addAttribute("statuses",   ExpenseStatus.values());
        model.addAttribute("stats",      expenseService.getSummaryStats());
        model.addAttribute("activePage", "expenses");
        return "admin/expenses";
    }

    // ── GET /admin/expenses/new ───────────────────────────────────────────────

    @GetMapping("/new")
    public String newForm(Model model) {
        model.addAttribute("expenseForm", new ExpenseFormDto());
        model.addAttribute("pageTitle", "New Expense");
        model.addAttribute("activePage", "expenses");
        addFormRefData(model);
        return "admin/expense-form";
    }

    // ── POST /admin/expenses/new ──────────────────────────────────────────────

    @PostMapping("/new")
    public String createExpense(
            @Valid @ModelAttribute("expenseForm") ExpenseFormDto dto,
            BindingResult bindingResult,
            @RequestParam(value = "receiptFile", required = false) MultipartFile receiptFile,
            Authentication authentication,
            RedirectAttributes redirectAttributes,
            Model model) {

        if (bindingResult.hasErrors()) {
            model.addAttribute("pageTitle",  "New Expense");
            model.addAttribute("activePage", "expenses");
            addFormRefData(model);
            return "admin/expense-form";
        }

        try {
            expenseService.createExpense(dto, authentication.getName(), receiptFile);
            redirectAttributes.addFlashAttribute("successMsg", "Expense recorded successfully.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMsg", "Failed to save expense: " + e.getMessage());
        }
        return "redirect:/admin/expenses";
    }

    // ── GET /admin/expenses/{id}/edit ─────────────────────────────────────────

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable UUID id, Model model, RedirectAttributes ra) {
        try {
            Expense expense = expenseService.findById(id);
            if (expense.isLocked()) {
                ra.addFlashAttribute("errorMsg", "Approved expenses cannot be edited.");
                return "redirect:/admin/expenses";
            }
            ExpenseFormDto dto = new ExpenseFormDto();
            dto.setId(id.toString());
            dto.setTitle(expense.getTitle());
            dto.setCategory(expense.getCategory());
            dto.setAmount(expense.getAmount());
            dto.setExpenseDate(expense.getExpenseDate());
            dto.setPaymentMethod(expense.getPaymentMethod());
            dto.setPaidTo(expense.getPaidTo());
            dto.setReceiptNumber(expense.getReceiptNumber());
            dto.setNotes(expense.getNotes());
            dto.setRecurring(expense.isRecurring());
            if (expense.getRecurringFrequency() != null) {
                dto.setRecurringFrequency(expense.getRecurringFrequency().name());
            }
            model.addAttribute("expenseForm", dto);
            model.addAttribute("expense",    expense);
            model.addAttribute("pageTitle",  "Edit Expense");
            model.addAttribute("activePage", "expenses");
            addFormRefData(model);
        } catch (Exception e) {
            ra.addFlashAttribute("errorMsg", e.getMessage());
            return "redirect:/admin/expenses";
        }
        return "admin/expense-form";
    }

    // ── POST /admin/expenses/{id}/edit ────────────────────────────────────────

    @PostMapping("/{id}/edit")
    public String updateExpense(
            @PathVariable UUID id,
            @Valid @ModelAttribute("expenseForm") ExpenseFormDto dto,
            BindingResult bindingResult,
            @RequestParam(value = "receiptFile", required = false) MultipartFile receiptFile,
            Authentication authentication,
            RedirectAttributes redirectAttributes,
            Model model) {

        if (bindingResult.hasErrors()) {
            model.addAttribute("pageTitle",  "Edit Expense");
            model.addAttribute("activePage", "expenses");
            addFormRefData(model);
            return "admin/expense-form";
        }

        try {
            expenseService.updateExpense(id, dto, authentication.getName(), receiptFile);
            redirectAttributes.addFlashAttribute("successMsg", "Expense updated successfully.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMsg", "Failed to update expense: " + e.getMessage());
        }
        return "redirect:/admin/expenses";
    }

    // ── POST /admin/expenses/{id}/approve ─────────────────────────────────────

    @PostMapping("/{id}/approve")
    public String approve(@PathVariable UUID id, Authentication authentication,
                          RedirectAttributes redirectAttributes) {
        try {
            expenseService.approveExpense(id, authentication.getName());
            redirectAttributes.addFlashAttribute("successMsg", "Expense approved.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMsg", "Approval failed: " + e.getMessage());
        }
        return "redirect:/admin/expenses";
    }

    // ── POST /admin/expenses/{id}/reject ──────────────────────────────────────

    @PostMapping("/{id}/reject")
    public String reject(@PathVariable UUID id,
                         @RequestParam(defaultValue = "Rejected by admin") String reason,
                         Authentication authentication,
                         RedirectAttributes redirectAttributes) {
        try {
            expenseService.rejectExpense(id, reason, authentication.getName());
            redirectAttributes.addFlashAttribute("successMsg", "Expense rejected.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMsg", "Rejection failed: " + e.getMessage());
        }
        return "redirect:/admin/expenses";
    }

    // ── POST /admin/expenses/{id}/delete ──────────────────────────────────────

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable UUID id, RedirectAttributes redirectAttributes) {
        try {
            expenseService.deleteExpense(id);
            redirectAttributes.addFlashAttribute("successMsg", "Expense deleted.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMsg", "Delete failed: " + e.getMessage());
        }
        return "redirect:/admin/expenses";
    }

    // ── GET /admin/expenses/analytics ─────────────────────────────────────────

    @GetMapping("/analytics")
    public String analytics(Model model) {
        ExpenseAnalyticsDto analytics = expenseService.getAnalytics();
        model.addAttribute("analytics", analytics);
        model.addAttribute("activePage", "expense-analytics");

        // Pre-extract chart data so the template can use simple variable references
        // instead of OGNL collection projections (![property]) which are fragile with records
        List<String>     catLabels  = analytics.getCategoryBreakdown().stream()
                .map(ExpenseAnalyticsDto.CategoryTotal::label).toList();
        List<BigDecimal> catTotals  = analytics.getCategoryBreakdown().stream()
                .map(ExpenseAnalyticsDto.CategoryTotal::total).toList();
        List<String>     catColours = analytics.getCategoryBreakdown().stream()
                .map(ExpenseAnalyticsDto.CategoryTotal::colour).toList();
        List<String>     trendLabels = analytics.getMonthlyTrend().stream()
                .map(ExpenseAnalyticsDto.MonthlyTrend::monthLabel).toList();
        List<BigDecimal> trendTotals = analytics.getMonthlyTrend().stream()
                .map(ExpenseAnalyticsDto.MonthlyTrend::total).toList();

        model.addAttribute("catLabels",   catLabels);
        model.addAttribute("catTotals",   catTotals);
        model.addAttribute("catColours",  catColours);
        model.addAttribute("trendLabels", trendLabels);
        model.addAttribute("trendTotals", trendTotals);

        return "admin/expense-analytics";
    }
}
