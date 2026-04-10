package com.adags.hospital.controller;

import com.adags.hospital.domain.visit.VisitLabRequest;
import com.adags.hospital.domain.billing.LineItemCategory;
import com.adags.hospital.domain.staff.Staff;
import com.adags.hospital.domain.user.AppUser;
import com.adags.hospital.repository.user.UserRepository;
import com.adags.hospital.service.visit.PrescriptionLabService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.*;

/**
 * REST API for the rebuilt Visit Lab Request system.
 * Base path: /doctor/api/labtest  (covered by /doctor/** security rule)
 *
 * Rules:
 *  GET    /doctor/api/labtest/visit/{visitId}             — list lab tests for a visit
 *  POST   /doctor/api/labtest/visit/{visitId}             — add lab test (always allowed)
 *  PUT    /doctor/api/labtest/{id}                        — edit (locked after payment)
 *  DELETE /doctor/api/labtest/{id}                        — delete (locked after payment)
 *  GET    /doctor/api/labtest/visit/{visitId}/lock-status — check payment lock
 */
@Slf4j
@RestController
@RequestMapping("/doctor/api/labtest")
@RequiredArgsConstructor
public class LabRequestApiController {

    private final PrescriptionLabService prescriptionLabService;
    private final UserRepository         userRepository;

    // -----------------------------------------------------------------------
    // GET  /doctor/api/labtest/visit/{visitId}
    // -----------------------------------------------------------------------
    @GetMapping("/visit/{visitId}")
    public ResponseEntity<List<Map<String, Object>>> listLabRequests(
            @PathVariable UUID visitId) {
        List<VisitLabRequest> list = prescriptionLabService.getLabRequestsForVisit(visitId);
        boolean locked     = prescriptionLabService.isVisitLocked(visitId);
        boolean discharged = prescriptionLabService.isVisitDischarged(visitId);
        List<Map<String, Object>> result = list.stream().map(lr -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id",                  lr.getId());
            m.put("testName",            lr.getTestName());
            m.put("urgency",             lr.getUrgency().name());
            m.put("clinicalNotes",       nullSafe(lr.getClinicalNotes()));
            m.put("specialInstructions", nullSafe(lr.getSpecialInstructions()));
            m.put("status",              lr.getStatus().name());
            m.put("resultSummary",       nullSafe(lr.getResultSummary()));
            m.put("price",               lr.getPriceItem() != null && lr.getPriceItem().getPrice() != null
                                             ? lr.getPriceItem().getPrice() : BigDecimal.ZERO);
            m.put("locked",              locked);
            m.put("discharged",          discharged);
            return m;
        }).toList();
        return ResponseEntity.ok(result);
    }

    // -----------------------------------------------------------------------
    // GET  /doctor/api/labtest/visit/{visitId}/lock-status
    // -----------------------------------------------------------------------
    @GetMapping("/visit/{visitId}/lock-status")
    public ResponseEntity<Map<String, Object>> lockStatus(@PathVariable UUID visitId) {
        boolean locked      = prescriptionLabService.isVisitLocked(visitId);
        boolean discharged  = prescriptionLabService.isVisitDischarged(visitId);
        return ResponseEntity.ok(Map.of("visitId", visitId, "locked", locked, "discharged", discharged));
    }

    // -----------------------------------------------------------------------
    // POST /doctor/api/labtest/visit/{visitId}  — ADD (always allowed)
    // -----------------------------------------------------------------------
    @PostMapping("/visit/{visitId}")
    public ResponseEntity<Map<String, Object>> addLabRequest(
            @PathVariable UUID visitId,
            @RequestBody Map<String, String> body,
            Authentication auth) {

        Staff doctor = getDoctor(auth);
        if (doctor == null) return unauthorized();

        try {
            UUID priceItemId = UUID.fromString(body.get("priceItemId"));
            VisitLabRequest lr = prescriptionLabService.addLabRequest(
                    visitId,
                    priceItemId,
                    body.getOrDefault("urgency", "ROUTINE"),
                    body.get("clinicalNotes"),
                    body.get("specialInstructions"),
                    doctor.getId());

            return ResponseEntity.ok(Map.of(
                    "success",  true,
                    "id",       lr.getId(),
                    "testName", lr.getTestName(),
                    "urgency",  lr.getUrgency().name(),
                    "status",   lr.getStatus().name(),
                    "message",  "Lab test added and sent to invoice."
            ));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(423).body(Map.of("success", false, "message", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        } catch (Exception e) {
            log.error("Error adding lab request to visit {}: {}", visitId, e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("success", false, "message", "An error occurred. Please try again."));
        }
    }

    // -----------------------------------------------------------------------
    // PUT /doctor/api/labtest/{id}  — EDIT (locked if visit is PAID)
    // -----------------------------------------------------------------------
    @PutMapping("/{id}")
    public ResponseEntity<Map<String, Object>> updateLabRequest(
            @PathVariable UUID id,
            @RequestBody Map<String, String> body,
            Authentication auth) {

        if (getDoctor(auth) == null) return unauthorized();

        try {
            VisitLabRequest lr = prescriptionLabService.updateLabRequest(
                    id,
                    body.get("urgency"),
                    body.get("clinicalNotes"),
                    body.get("specialInstructions"));

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "id",      lr.getId(),
                    "message", "Lab request updated."
            ));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(423).body(Map.of("success", false, "message", e.getMessage()));
        } catch (Exception e) {
            log.error("Error updating lab request {}: {}", id, e.getMessage(), e);
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    // -----------------------------------------------------------------------
    // DELETE /doctor/api/labtest/{id}  — DELETE (locked if visit is PAID)
    // -----------------------------------------------------------------------
    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> deleteLabRequest(
            @PathVariable UUID id,
            Authentication auth) {

        if (getDoctor(auth) == null) return unauthorized();

        try {
            prescriptionLabService.deleteLabRequest(id);
            return ResponseEntity.ok(Map.of("success", true, "message", "Lab request removed."));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(423).body(Map.of("success", false, "message", e.getMessage()));
        } catch (Exception e) {
            log.error("Error deleting lab request {}: {}", id, e.getMessage(), e);
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    // -----------------------------------------------------------------------
    // POST /doctor/api/labtest/visit/{visitId}/send-to-payment
    // -----------------------------------------------------------------------
    @PostMapping("/visit/{visitId}/send-to-payment")
    public ResponseEntity<Map<String, Object>> sendToPayment(
            @PathVariable UUID visitId,
            Authentication auth) {

        if (getDoctor(auth) == null) return unauthorized();

        try {
            Map<String, Object> result = prescriptionLabService.confirmSentToPayment(visitId, LineItemCategory.LAB);
            int count = (int) result.get("count");
            return ResponseEntity.ok(Map.of(
                    "success",        true,
                    "invoiceNumbers", result.get("invoiceNumbers"),
                    "total",          result.get("total"),
                    "message",        count + " lab invoice(s) confirmed to reception: " + result.get("invoiceNumbers")
            ));
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        } catch (Exception e) {
            log.error("Error confirming lab payment for visit {}: {}", visitId, e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("success", false, "message", "An error occurred. Please try again."));
        }
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private Staff getDoctor(Authentication auth) {
        if (auth == null) return null;
        AppUser user = userRepository.findByUsernameWithStaff(auth.getName()).orElse(null);
        return (user != null) ? user.getStaff() : null;
    }

    private ResponseEntity<Map<String, Object>> unauthorized() {
        return ResponseEntity.status(403).body(Map.of("success", false, "message", "Unauthorized"));
    }

    private static String nullSafe(String s) {
        return s != null ? s : "";
    }
}
