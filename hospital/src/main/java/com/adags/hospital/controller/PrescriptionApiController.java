package com.adags.hospital.controller;

import com.adags.hospital.domain.visit.VisitPrescription;
import com.adags.hospital.domain.visit.VisitPrescriptionStatus;
import com.adags.hospital.domain.billing.LineItemCategory;
import com.adags.hospital.domain.staff.Staff;
import com.adags.hospital.domain.user.AppUser;
import com.adags.hospital.repository.user.UserRepository;
import com.adags.hospital.repository.staff.StaffRepository;
import com.adags.hospital.service.visit.PrescriptionLabService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.*;

/**
 * REST API for the rebuilt Visit Prescription system.
 * Base path: /doctor/api/rx  (covered by /doctor/** security rule)
 *
 * Rules:
 *  GET  /doctor/api/rx/visit/{visitId}              — list prescriptions for a visit
 *  POST /doctor/api/rx/visit/{visitId}              — add prescription (always allowed)
 *  PUT  /doctor/api/rx/{id}                         — edit (locked after payment)
 *  DELETE /doctor/api/rx/{id}                       — delete (locked after payment)
 *  GET  /doctor/api/rx/visit/{visitId}/lock-status  — check payment lock
 */
@Slf4j
@RestController
@RequestMapping("/doctor/api/rx")
@RequiredArgsConstructor
public class PrescriptionApiController {

    private final PrescriptionLabService    prescriptionLabService;
    private final UserRepository            userRepository;
    private final StaffRepository           staffRepository;

    // -----------------------------------------------------------------------
    // GET  /doctor/api/rx/visit/{visitId}
    // -----------------------------------------------------------------------
    @GetMapping("/visit/{visitId}")
    public ResponseEntity<List<Map<String, Object>>> listPrescriptions(
            @PathVariable UUID visitId) {
        List<VisitPrescription> list = prescriptionLabService.getPrescriptionsForVisit(visitId);
        boolean locked     = prescriptionLabService.isVisitLocked(visitId);
        boolean discharged = prescriptionLabService.isVisitDischarged(visitId);
        List<Map<String, Object>> result = list.stream().map(px -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id",             px.getId());
            m.put("medicationName", px.getMedicationName());
            m.put("dosage",         nullSafe(px.getDosage()));
            m.put("frequency",      nullSafe(px.getFrequency()));
            m.put("duration",       nullSafe(px.getDuration()));
            m.put("route",          nullSafe(px.getRoute()));
            m.put("instructions",   nullSafe(px.getInstructions()));
            m.put("status",         px.getStatus().name());
            m.put("dispensedQty",   px.getDispensedQty());
            m.put("price",          px.getPriceItem() != null && px.getPriceItem().getPrice() != null
                                        ? px.getPriceItem().getPrice() : BigDecimal.ZERO);
            m.put("locked",         locked);
            m.put("discharged",     discharged);
            return m;
        }).toList();
        return ResponseEntity.ok(result);
    }

    // -----------------------------------------------------------------------
    // GET  /doctor/api/rx/visit/{visitId}/lock-status
    // -----------------------------------------------------------------------
    @GetMapping("/visit/{visitId}/lock-status")
    public ResponseEntity<Map<String, Object>> lockStatus(@PathVariable UUID visitId) {
        boolean locked      = prescriptionLabService.isVisitLocked(visitId);
        boolean discharged  = prescriptionLabService.isVisitDischarged(visitId);
        return ResponseEntity.ok(Map.of("visitId", visitId, "locked", locked, "discharged", discharged));
    }

    // -----------------------------------------------------------------------
    // POST /doctor/api/rx/visit/{visitId}  — ADD (always allowed)
    // -----------------------------------------------------------------------
    @PostMapping("/visit/{visitId}")
    public ResponseEntity<Map<String, Object>> addPrescription(
            @PathVariable UUID visitId,
            @RequestBody Map<String, String> body,
            Authentication auth) {

        Staff doctor = getDoctor(auth);
        if (doctor == null) return unauthorized();

        try {
            UUID priceItemId = UUID.fromString(body.get("priceItemId"));
            Integer totalQty = body.get("totalQuantityToDispense") != null
                    ? Integer.parseInt(body.get("totalQuantityToDispense")) : null;
            VisitPrescription px = prescriptionLabService.addPrescription(
                    visitId,
                    priceItemId,
                    body.get("dosage"),
                    body.get("frequency"),
                    body.get("duration"),
                    body.get("route"),
                    body.get("instructions"),
                    totalQty,
                    doctor.getId());

            return ResponseEntity.ok(Map.of(
                    "success",        true,
                    "id",             px.getId(),
                    "medicationName", px.getMedicationName(),
                    "status",         px.getStatus().name(),
                    "message",        "Prescription added and sent to invoice."
            ));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(423).body(Map.of("success", false, "message", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        } catch (Exception e) {
            log.error("Error adding prescription to visit {}: {}", visitId, e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("success", false, "message", "An error occurred. Please try again."));
        }
    }

    // -----------------------------------------------------------------------
    // PUT /doctor/api/rx/{id}  — EDIT (locked if visit is PAID)
    // -----------------------------------------------------------------------
    @PutMapping("/{id}")
    public ResponseEntity<Map<String, Object>> updatePrescription(
            @PathVariable UUID id,
            @RequestBody Map<String, String> body,
            Authentication auth) {

        if (getDoctor(auth) == null) return unauthorized();

        try {
            VisitPrescription px = prescriptionLabService.updatePrescription(
                    id,
                    body.get("dosage"),
                    body.get("frequency"),
                    body.get("duration"),
                    body.get("route"),
                    body.get("instructions"));

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "id",      px.getId(),
                    "message", "Prescription updated."
            ));
        } catch (IllegalStateException e) {
            // Payment lock
            return ResponseEntity.status(423).body(Map.of("success", false, "message", e.getMessage()));
        } catch (Exception e) {
            log.error("Error updating prescription {}: {}", id, e.getMessage(), e);
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    // -----------------------------------------------------------------------
    // DELETE /doctor/api/rx/{id}  — DELETE (locked if visit is PAID)
    // -----------------------------------------------------------------------
    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> deletePrescription(
            @PathVariable UUID id,
            Authentication auth) {

        if (getDoctor(auth) == null) return unauthorized();

        try {
            prescriptionLabService.deletePrescription(id);
            return ResponseEntity.ok(Map.of("success", true, "message", "Prescription removed."));
        } catch (IllegalStateException e) {
            // Payment lock
            return ResponseEntity.status(423).body(Map.of("success", false, "message", e.getMessage()));
        } catch (Exception e) {
            log.error("Error deleting prescription {}: {}", id, e.getMessage(), e);
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    // -----------------------------------------------------------------------
    // POST /doctor/api/rx/visit/{visitId}/send-to-payment
    // -----------------------------------------------------------------------
    @PostMapping("/visit/{visitId}/send-to-payment")
    public ResponseEntity<Map<String, Object>> sendToPayment(
            @PathVariable UUID visitId,
            Authentication auth) {

        if (getDoctor(auth) == null) return unauthorized();

        try {
            Map<String, Object> result = prescriptionLabService.confirmSentToPayment(visitId, LineItemCategory.PHARMACY);
            int count = (int) result.get("count");
            return ResponseEntity.ok(Map.of(
                    "success",        true,
                    "invoiceNumbers", result.get("invoiceNumbers"),
                    "total",          result.get("total"),
                    "message",        count + " pharmacy invoice(s) confirmed to reception: " + result.get("invoiceNumbers")
            ));
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        } catch (Exception e) {
            log.error("Error confirming rx payment for visit {}: {}", visitId, e.getMessage(), e);
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
