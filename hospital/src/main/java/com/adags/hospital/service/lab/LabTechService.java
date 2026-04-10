package com.adags.hospital.service.lab;

import com.adags.hospital.domain.lab.*;
import com.adags.hospital.domain.staff.Staff;
import com.adags.hospital.domain.visit.VisitLabRequest;
import com.adags.hospital.domain.visit.VisitLabRequestStatus;
import com.adags.hospital.domain.visit.VisitLabResultParameter;
import com.adags.hospital.dto.lab.LabResultEntryRequest;
import com.adags.hospital.dto.lab.LabResultParameterRequest;
import com.adags.hospital.dto.lab.VisitLabResultRequest;
import com.adags.hospital.exception.BusinessRuleException;
import com.adags.hospital.exception.ResourceNotFoundException;
import com.adags.hospital.repository.billing.InvoiceRepository;
import com.adags.hospital.repository.lab.LabRequestRepository;
import com.adags.hospital.repository.lab.LabResultRepository;
import com.adags.hospital.repository.visit.VisitLabRequestRepository;
import com.adags.hospital.repository.visit.VisitLabResultParameterRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.Hibernate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LabTechService {

    private final LabRequestRepository               labRequestRepository;
    private final LabResultRepository                labResultRepository;
    private final InvoiceRepository                  invoiceRepository;
    private final VisitLabRequestRepository           visitLabRequestRepository;
    private final VisitLabResultParameterRepository   visitLabResultParameterRepository;

    @Value("${app.upload.path:uploads}")
    private String uploadBasePath;

    // ----------------------------------------------------------------
    //  View DTOs — plain records, zero JPA proxies, safe after session closes
    // ----------------------------------------------------------------
    public record LabResultParamView(
            String parameterName,
            String resultValue,
            String unit,
            String referenceRange,
            String interpretationName) {}

    public record LabRequestView(
            String testName,
            String testCode,
            String urgencyName,
            LocalDateTime requestedAt,
            String patientFullName,
            String patientNationalId,
            String sampleType,
            String sampleQualityName,
            LocalDateTime sampleCollectedAt,
            LocalDateTime sampleReceivedAt,
            String specialInstructions,
            boolean hasResult,
            String resultValue,
            String resultUnit,
            String referenceRange,
            String overallInterpretationName,
            LocalDateTime resultDateTime,
            String resultNotes,
            String performedByName,
            String verifiedByName,
            LocalDateTime verifiedAt,
            boolean locked,
            List<LabResultParamView> parameters,
        // ── V26-only extra fields (null for legacy LabRequest) ──────────────
        String methodology,
        String reagentsUsed,
        String sampleQualityNote,
        String conclusion,
        // ── Machine PDF (V26 visit requests only) ───────────────────────────
        boolean hasMachinePdf,
        UUID   visitLabRequestId) {}

    /**
     * Unified queue item DTO — abstracts over both legacy LabRequest ("LEGACY")
     * and new V26 VisitLabRequest ("V26") so the dashboard template stays source-agnostic.
     */
    public record LabQueueItem(
            UUID id,
            String source,            // "LEGACY" or "V26"
            String testName,
            String testCode,          // null for V26
            String urgencyName,
            LocalDateTime requestedAt,
            String patientFullName,
            String patientNationalId,
            String statusName,
            String sampleType,        // null for V26
            String sampleQualityName, // null for V26
            String specialInstructions,
            boolean hasResult
    ) {}

    // ----------------------------------------------------------------
    //  Completed results for a specific requesting doctor — returns DTOs
    //  so Thymeleaf never touches a JPA proxy after the session closes.
    // ----------------------------------------------------------------
    public List<LabRequestView> getDoctorLabResults(UUID doctorId) {
        List<LabRequest> requests = labRequestRepository.findCompletedByDoctorIdWithResults(doctorId);
        List<LabRequestView> views = new ArrayList<>();
        for (LabRequest req : requests) {
            // --- Patient ---
            String patientFullName = "";
            String patientNationalId = "";
            if (req.getPatient() != null) {
                patientFullName = req.getPatient().getFirstName() + " " + req.getPatient().getLastName();
                patientNationalId = req.getPatient().getNationalId();
            }

            // --- Result ---
            LabResult res = req.getResult();
            boolean hasResult = res != null;
            String resultValue = hasResult ? res.getResultValue() : null;
            String resultUnit  = hasResult ? res.getUnit()        : null;
            String referenceRange = hasResult ? res.getReferenceRange() : null;
            String overallInterp  = (hasResult && res.getInterpretation() != null)
                    ? res.getInterpretation().name() : null;
            LocalDateTime resultDateTime = hasResult ? res.getResultDateTime() : null;
            String resultNotes = hasResult ? res.getNotes() : null;
            boolean locked = hasResult && res.isLocked();
            LocalDateTime verifiedAt = hasResult ? res.getVerifiedAt() : null;

            String performedByName = "";
            if (hasResult && res.getPerformedBy() != null) {
                performedByName = res.getPerformedBy().getFirstName() + " " + res.getPerformedBy().getLastName();
            }
            String verifiedByName = "";
            if (hasResult && res.getVerifiedBy() != null) {
                verifiedByName = res.getVerifiedBy().getFirstName() + " " + res.getVerifiedBy().getLastName();
            }

            // --- Parameters ---
            List<LabResultParamView> params = Collections.emptyList();
            if (hasResult && res.getParameters() != null) {
                params = new ArrayList<>();
                for (LabResultParameter p : res.getParameters()) {
                    params.add(new LabResultParamView(
                            p.getParameterName(),
                            p.getResultValue(),
                            p.getUnit(),
                            p.getReferenceRange(),
                            p.getInterpretation() != null ? p.getInterpretation().name() : null));
                }
            }

            views.add(new LabRequestView(
                    req.getTestName(),
                    req.getTestCode(),
                    req.getUrgency() != null ? req.getUrgency().name() : "ROUTINE",
                    req.getRequestedAt(),
                    patientFullName,
                    patientNationalId,
                    req.getSampleType(),
                    req.getSampleQuality() != null ? req.getSampleQuality().name() : null,
                    req.getSampleCollectedAt(),
                    req.getSampleReceivedAt(),
                    req.getSpecialInstructions(),
                    hasResult,
                    resultValue,
                    resultUnit,
                    referenceRange,
                    overallInterp,
                    resultDateTime,
                    resultNotes,
                    performedByName,
                    verifiedByName,
                    verifiedAt,
                    locked,
                    params,
                    null, null, null, null,   // no V26 extra fields for legacy
                    false, null));             // no machine PDF / visitLabRequestId
        }

        // ── Include V26 VisitLabRequest completed results ──────────────────────
        List<VisitLabRequest> visitCompleted =
                visitLabRequestRepository.findCompletedByDoctorIdWithDetails(doctorId);
        for (VisitLabRequest vlr : visitCompleted) {
            String patientFullName = "";
            String patientNationalId = "";
            if (vlr.getMedicalRecord() != null && vlr.getMedicalRecord().getPatient() != null) {
                var p = vlr.getMedicalRecord().getPatient();
                patientFullName   = p.getFirstName() + " " + p.getLastName();
                patientNationalId = p.getNationalId();
            }

            // Map parameters — flag (H/L/N/C) → interpretation string
            List<LabResultParamView> params = new ArrayList<>();
            String worstFlag = "N";
            if (vlr.getResultParameters() != null) {
                for (VisitLabResultParameter rp : vlr.getResultParameters()) {
                    String interp = flagToInterpretation(rp.getFlag());
                    if ("CRITICAL".equals(interp)) worstFlag = "C";
                    else if ("ABNORMAL".equals(interp) && !"C".equals(worstFlag)) worstFlag = "H";
                    params.add(new LabResultParamView(
                            rp.getParameterName(),
                            rp.getResultValue(),
                            rp.getUnit(),
                            rp.getReferenceRange(),
                            interp));
                }
            }
            String overallInterp = "C".equals(worstFlag) ? "CRITICAL"
                                 : "H".equals(worstFlag) ? "ABNORMAL"
                                 : "NORMAL";

            views.add(new LabRequestView(
                    vlr.getTestName(),
                    null,           // no testCode in V26
                    vlr.getUrgency() != null ? vlr.getUrgency().name() : "ROUTINE",
                    vlr.getCreatedAt(),
                    patientFullName,
                    patientNationalId,
                    vlr.getSampleType(),
                    vlr.getSampleQuality(),
                    vlr.getSampleCollectedAt(),
                    null,           // no sampleReceivedAt in V26
                    vlr.getSpecialInstructions(),
                    true,           // has result — it's COMPLETED
                    vlr.getFindings(),
                    null,           // no unit field at request level
                    vlr.getReferenceRangeNote(),
                    overallInterp,
                    vlr.getCompletedAt(),
                    vlr.getInterpretationText(),
                    "",            // no explicit performed-by reference in V26
                    "",
                    null,
                    true,           // locked
                    params,
                    vlr.getMethodology(),
                    vlr.getReagentsUsed(),
                    vlr.getSampleQualityNote(),
                    vlr.getConclusion(),
                    vlr.getMachinePdfData() != null && vlr.getMachinePdfData().length > 0,
                    vlr.getId()));
        }

        return views;
    }

    /** Convert a V27 parameter flag (H/L/N/C/null) to an interpretation name. */
    private static String flagToInterpretation(String flag) {
        if (flag == null) return "NORMAL";
        return switch (flag.toUpperCase()) {
            case "C"  -> "CRITICAL";
            case "H", "L" -> "ABNORMAL";
            default   -> "NORMAL";
        };
    }

    // ----------------------------------------------------------------
    //  V26 history — all COMPLETED VisitLabRequests
    // ----------------------------------------------------------------
    public List<VisitLabRequest> getVisitLabHistory() {
        return visitLabRequestRepository.findAllCompletedWithDetails();
    }

    // ----------------------------------------------------------------
    //  Queue — paid lab requests ready to be processed (legacy + V26 merged)
    // ----------------------------------------------------------------
    public List<LabQueueItem> getQueue() {
        List<LabQueueItem> items = new ArrayList<>();
        for (LabRequest lr : labRequestRepository.findPaidQueueWithDetails()) {
            items.add(toQueueItem(lr));
        }
        for (VisitLabRequest vlr : visitLabRequestRepository.findPaidQueueWithDetails()) {
            items.add(toQueueItem(vlr));
        }
        items.sort(Comparator
                .comparing((LabQueueItem qi) -> urgencyOrder(qi.urgencyName()), Comparator.reverseOrder())
                .thenComparing(LabQueueItem::requestedAt));
        return items;
    }

    // ----------------------------------------------------------------
    //  Awaiting payment — PENDING requests whose invoice is not yet paid (legacy + V26)
    // ----------------------------------------------------------------
    public List<LabQueueItem> getAwaitingPayment() {
        List<LabQueueItem> items = new ArrayList<>();
        for (LabRequest lr : labRequestRepository.findAwaitingPaymentWithDetails()) {
            items.add(toQueueItem(lr));
        }
        for (VisitLabRequest vlr : visitLabRequestRepository.findAwaitingPaymentWithDetails()) {
            items.add(toQueueItem(vlr));
        }
        items.sort(Comparator.comparing(LabQueueItem::requestedAt));
        return items;
    }

    private LabQueueItem toQueueItem(LabRequest lr) {
        String patientFull = "";
        String patientNid  = "";
        if (lr.getPatient() != null) {
            patientFull = lr.getPatient().getFirstName() + " " + lr.getPatient().getLastName();
            patientNid  = lr.getPatient().getNationalId();
        }
        return new LabQueueItem(
                lr.getId(),
                "LEGACY",
                lr.getTestName(),
                lr.getTestCode(),
                lr.getUrgency() != null ? lr.getUrgency().name() : "ROUTINE",
                lr.getRequestedAt(),
                patientFull,
                patientNid,
                lr.getStatus() != null ? lr.getStatus().name() : "PENDING",
                lr.getSampleType(),
                lr.getSampleQuality() != null ? lr.getSampleQuality().name() : null,
                lr.getSpecialInstructions(),
                lr.getResult() != null
        );
    }

    private LabQueueItem toQueueItem(VisitLabRequest vlr) {
        String patientFull = "";
        String patientNid  = "";
        if (vlr.getMedicalRecord() != null && vlr.getMedicalRecord().getPatient() != null) {
            var p = vlr.getMedicalRecord().getPatient();
            patientFull = p.getFirstName() + " " + p.getLastName();
            patientNid  = p.getNationalId();
        }
        return new LabQueueItem(
                vlr.getId(),
                "V26",
                vlr.getTestName(),
                null,
                vlr.getUrgency() != null ? vlr.getUrgency().name() : "ROUTINE",
                vlr.getCreatedAt(),
                patientFull,
                patientNid,
                vlr.getStatus() != null ? vlr.getStatus().name() : "PENDING",
                null,
                null,
                vlr.getSpecialInstructions(),
                vlr.getResultSummary() != null && !vlr.getResultSummary().isBlank()
        );
    }

    private int urgencyOrder(String urgencyName) {
        return switch (urgencyName == null ? "ROUTINE" : urgencyName) {
            case "STAT"   -> 2;
            case "URGENT" -> 1;
            default       -> 0;
        };
    }

    // ----------------------------------------------------------------
    //  Accept sample — mark request IN_PROGRESS and record receipt time
    // ----------------------------------------------------------------
    @Transactional
    public LabRequest acceptSample(UUID requestId,
                                   String sampleType,
                                   SampleQuality quality,
                                   Staff techStaff) {
        LabRequest request = labRequestRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("LabRequest", "id", requestId));

        if (request.getStatus() != LabRequestStatus.PENDING) {
            throw new BusinessRuleException("Lab request is not in PENDING state.");
        }

        // Enforce payment: ensure the lab invoice for this request has been paid
        if (request.getMedicalRecord() != null) {
            List<com.adags.hospital.domain.billing.Invoice> invoices =
                    invoiceRepository.findByMedicalRecordId(request.getMedicalRecord().getId());
            boolean labInvoicePaid = invoices.stream().anyMatch(inv ->
                    (inv.getStatus() == com.adags.hospital.domain.billing.InvoiceStatus.PAID ||
                     inv.getStatus() == com.adags.hospital.domain.billing.InvoiceStatus.PARTIALLY_PAID) &&
                    inv.getLineItems().stream().anyMatch(li ->
                            li.getCategory() == com.adags.hospital.domain.billing.LineItemCategory.LAB));
            if (!labInvoicePaid) {
                throw new BusinessRuleException(
                        "Cannot accept this sample. The lab invoice for " +
                        request.getPatient().getFirstName() + " " + request.getPatient().getLastName() +
                        " has not been paid at reception yet.");
            }
        } else if (request.getSurgeryOrder() != null) {
            java.util.UUID invId = request.getSurgeryOrder().getSurgeryInvoiceId();
            boolean surgeryInvoicePaid = invId != null && invoiceRepository.findById(invId)
                    .map(inv -> inv.getStatus() == com.adags.hospital.domain.billing.InvoiceStatus.PAID
                             || inv.getStatus() == com.adags.hospital.domain.billing.InvoiceStatus.PARTIALLY_PAID)
                    .orElse(false);
            if (!surgeryInvoicePaid) {
                throw new BusinessRuleException(
                        "Cannot accept this sample. The surgery invoice for " +
                        request.getPatient().getFirstName() + " " + request.getPatient().getLastName() +
                        " has not been paid at reception yet.");
            }
        }

        request.setStatus(LabRequestStatus.IN_PROGRESS);
        request.setSampleType(sampleType);
        request.setSampleQuality(quality != null ? quality : SampleQuality.ADEQUATE);
        request.setSampleReceivedAt(LocalDateTime.now());
        return labRequestRepository.save(request);
    }

    // ----------------------------------------------------------------
    //  Save Result — create / overwrite LabResult with parameters
    // ----------------------------------------------------------------
    @Transactional
    public LabResult saveResult(UUID requestId,
                                LabResultEntryRequest form,
                                MultipartFile reportPdf,
                                MultipartFile machinePdf,
                                Staff techStaff) {
        LabRequest request = labRequestRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("LabRequest", "id", requestId));

        if (request.getStatus() == LabRequestStatus.COMPLETED) {
            throw new BusinessRuleException("Lab result for this request is already submitted and locked.");
        }

        // Get existing or build new LabResult
        LabResult result = request.getResult();
        if (result == null) {
            result = LabResult.builder()
                    .labRequest(request)
                    .performedBy(techStaff)
                    .build();
        }

        result.setPerformedBy(techStaff);
        result.setResultValue(form.getResultValue());
        result.setNotes(form.getNotes());
        result.setInterpretation(
                form.getInterpretation() != null ? form.getInterpretation() : LabInterpretation.NORMAL);
        result.setResultDateTime(LocalDateTime.now());
        result.setSubmitted(false);
        result.setLocked(false);

        // Replace parameters collection
        result.getParameters().clear();
        if (form.getParameters() != null) {
            for (LabResultParameterRequest p : form.getParameters()) {
                if (p.getParameterName() == null || p.getParameterName().isBlank()) continue;
                LabResultParameter param = LabResultParameter.builder()
                        .labResult(result)
                        .parameterName(p.getParameterName().trim())
                        .resultValue(p.getResultValue())
                        .unit(p.getUnit())
                        .referenceRange(p.getReferenceRange())
                        .interpretation(p.getInterpretation())
                        .build();
                result.getParameters().add(param);
            }
        }

        // Update sample info if provided in this step
        if (form.getSampleType() != null && !form.getSampleType().isBlank()) {
            request.setSampleType(form.getSampleType());
        }
        if (form.getSampleQuality() != null) {
            request.setSampleQuality(form.getSampleQuality());
        }

        // Save full lab report PDF if provided
        if (reportPdf != null && !reportPdf.isEmpty()) {
            String pdfPath = saveLabPdf(reportPdf, requestId, "lab-reports");
            if (pdfPath != null) {
                result.setReportPdfPath(pdfPath);
            }
        }

        // Save machine output PDF if provided
        if (machinePdf != null && !machinePdf.isEmpty()) {
            String pdfPath = saveLabPdf(machinePdf, requestId, "lab-machine-output");
            if (pdfPath != null) {
                result.setMachinePdfPath(pdfPath);
            }
        }

        LabResult saved = labResultRepository.save(result);
        labRequestRepository.save(request);
        return saved;
    }

    /** Saves a PDF file to the local filesystem. Used for legacy LabRequest reports. */
    private String saveLabPdf(MultipartFile file, UUID requestId, String folder) {
        try {
            java.nio.file.Path dir = java.nio.file.Paths.get(uploadBasePath, folder, requestId.toString());
            java.nio.file.Files.createDirectories(dir);
            String originalName = file.getOriginalFilename();
            String ext = (originalName != null && originalName.contains("."))
                    ? originalName.substring(originalName.lastIndexOf('.')) : ".pdf";
            String fileName = UUID.randomUUID() + ext;
            java.nio.file.Files.copy(file.getInputStream(), dir.resolve(fileName));
            return java.nio.file.Paths.get(folder, requestId.toString(), fileName).toString().replace("\\", "/");
        } catch (java.io.IOException e) {
            log.error("Failed to save PDF ({}) for request {}: {}", folder, requestId, e.getMessage(), e);
            return null;
        }
    }

    /** Saves the machine-output PDF bytes directly into the database. Returns true on success. */
    @Transactional
    public boolean saveVisitMachinePdfToDb(MultipartFile file, UUID visitLabRequestId) {
        VisitLabRequest vlr = visitLabRequestRepository.findById(visitLabRequestId)
                .orElse(null);
        if (vlr == null) return false;
        try {
            vlr.setMachinePdfData(file.getBytes());
            vlr.setMachinePdfName(file.getOriginalFilename() != null ? file.getOriginalFilename() : "machine-output.pdf");
            visitLabRequestRepository.save(vlr);
            return true;
        } catch (IOException e) {
            log.error("Failed to save machine PDF to DB for visit request {}: {}", visitLabRequestId, e.getMessage(), e);
            return false;
        }
    }

    /** Returns the PDF bytes for a VisitLabRequest, or null if none stored. */
    public byte[] getVisitLabMachinePdfBytes(UUID visitLabRequestId) {
        return visitLabRequestRepository.findById(visitLabRequestId)
                .map(VisitLabRequest::getMachinePdfData)
                .orElse(null);
    }

    /** Returns the original filename for the stored PDF, or a default. */
    public String getVisitLabMachinePdfName(UUID visitLabRequestId) {
        return visitLabRequestRepository.findById(visitLabRequestId)
                .map(v -> v.getMachinePdfName() != null ? v.getMachinePdfName() : "machine-output.pdf")
                .orElse("machine-output.pdf");
    }

    // ----------------------------------------------------------------
    //  Submit & Verify Result — lock it and mark request COMPLETED
    // ----------------------------------------------------------------
    @Transactional
    public LabResult submitResult(UUID requestId, Staff verifier) {
        LabRequest request = labRequestRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("LabRequest", "id", requestId));

        LabResult result = request.getResult();
        if (result == null) {
            throw new BusinessRuleException("No result entered for this lab request yet.");
        }
        if (result.isLocked()) {
            throw new BusinessRuleException("Result is already locked and submitted.");
        }

        result.setVerifiedBy(verifier);
        result.setVerifiedAt(LocalDateTime.now());
        result.setSubmitted(true);
        result.setLocked(true);

        request.setStatus(LabRequestStatus.COMPLETED);

        labResultRepository.save(result);
        labRequestRepository.save(request);
        return result;
    }

    // ----------------------------------------------------------------
    //  V26 — Accept sample (PENDING → IN_PROGRESS)
    // ----------------------------------------------------------------
    @Transactional
    public VisitLabRequest acceptVisitSample(UUID id, Staff techStaff) {
        VisitLabRequest vlr = visitLabRequestRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("VisitLabRequest", "id", id));

        if (vlr.getStatus() != VisitLabRequestStatus.PENDING) {
            throw new BusinessRuleException("Lab request is not in PENDING state.");
        }

        if (vlr.getMedicalRecord() != null) {
            List<com.adags.hospital.domain.billing.Invoice> invoices =
                    invoiceRepository.findByMedicalRecordId(vlr.getMedicalRecord().getId());
            boolean labInvoicePaid = invoices.stream().anyMatch(inv ->
                    (inv.getStatus() == com.adags.hospital.domain.billing.InvoiceStatus.PAID ||
                     inv.getStatus() == com.adags.hospital.domain.billing.InvoiceStatus.PARTIALLY_PAID) &&
                    inv.getLineItems().stream().anyMatch(li ->
                            li.getCategory() == com.adags.hospital.domain.billing.LineItemCategory.LAB));
            if (!labInvoicePaid) {
                String patientName = "";
                if (vlr.getMedicalRecord().getPatient() != null) {
                    var p = vlr.getMedicalRecord().getPatient();
                    patientName = p.getFirstName() + " " + p.getLastName();
                }
                throw new BusinessRuleException(
                        "Cannot accept this sample. The lab invoice for " + patientName +
                        " has not been paid at reception yet.");
            }
        }

        vlr.setStatus(VisitLabRequestStatus.IN_PROGRESS);
        return visitLabRequestRepository.save(vlr);
    }

    // ----------------------------------------------------------------
    //  V26 — Save result (draft) — full rich form
    // ----------------------------------------------------------------
    @Transactional
    public VisitLabRequest saveVisitResult(UUID id, VisitLabResultRequest req) {
        VisitLabRequest vlr = visitLabRequestRepository.findByIdWithDetails(id)
                .orElseThrow(() -> new ResourceNotFoundException("VisitLabRequest", "id", id));

        if (vlr.getStatus() == VisitLabRequestStatus.COMPLETED) {
            throw new BusinessRuleException("This lab request is already completed and locked.");
        }

        // ── Scalar fields ──
        vlr.setSampleType(req.getSampleType());
        if (req.getSampleCollectedAt() != null && !req.getSampleCollectedAt().isBlank()) {
            try {
                vlr.setSampleCollectedAt(
                    java.time.LocalDateTime.parse(req.getSampleCollectedAt()));
            } catch (Exception ignored) { /* invalid datetime — skip */ }
        }
        vlr.setSampleQuality(req.getSampleQuality());
        vlr.setSampleQualityNote(req.getSampleQualityNote());
        vlr.setMethodology(req.getMethodology());
        vlr.setReagentsUsed(req.getReagentsUsed());
        vlr.setFindings(req.getFindings());
        vlr.setInterpretationText(req.getInterpretationText());
        vlr.setReferenceRangeNote(req.getReferenceRangeNote());
        vlr.setConclusion(req.getConclusion());
        // Keep result_summary as a generated summary from findings
        vlr.setResultSummary(req.getFindings());

        // ── Parameters — replace all ──
        vlr.getResultParameters().clear();
        if (req.getParameters() != null) {
            int order = 0;
            for (VisitLabResultRequest.ParameterEntry pe : req.getParameters()) {
                if (pe.getParameterName() == null || pe.getParameterName().isBlank()) continue;
                VisitLabResultParameter param = VisitLabResultParameter.builder()
                        .visitLabRequest(vlr)
                        .parameterName(pe.getParameterName().trim())
                        .resultValue(pe.getResultValue())
                        .unit(pe.getUnit())
                        .referenceRange(pe.getReferenceRange())
                        .flag(pe.getFlag())
                        .method(pe.getMethod())
                        .sortOrder(order++)
                        .build();
                vlr.getResultParameters().add(param);
            }
        }

        return visitLabRequestRepository.save(vlr);
    }

    // ----------------------------------------------------------------
    //  V26 — Submit result (IN_PROGRESS → COMPLETED)
    // ----------------------------------------------------------------
    @Transactional
    public VisitLabRequest submitVisitResult(UUID id, Staff techStaff) {
        VisitLabRequest vlr = visitLabRequestRepository.findByIdWithDetails(id)
                .orElseThrow(() -> new ResourceNotFoundException("VisitLabRequest", "id", id));

        boolean hasResult = (vlr.getResultSummary() != null && !vlr.getResultSummary().isBlank())
                || (vlr.getFindings()       != null && !vlr.getFindings().isBlank())
                || (vlr.getMachinePdfData() != null && vlr.getMachinePdfData().length > 0);
        if (!hasResult) {
            throw new BusinessRuleException("Please enter result findings or upload a machine PDF before submitting.");
        }
        if (vlr.getStatus() == VisitLabRequestStatus.COMPLETED) {
            throw new BusinessRuleException("Result is already submitted and locked.");
        }

        vlr.setStatus(VisitLabRequestStatus.COMPLETED);
        vlr.setCompletedAt(LocalDateTime.now());
        return visitLabRequestRepository.save(vlr);
    }

    // ----------------------------------------------------------------
    //  History — results entered by this technician
    // ----------------------------------------------------------------
    public List<LabResult> getMyHistory(UUID staffId) {
        return labResultRepository.findByPerformedByIdWithDetails(staffId);
    }

    // ----------------------------------------------------------------
    //  Full lab result for the printable report page
    // ----------------------------------------------------------------
    public LabResult getLabResultForReport(UUID resultId) {
        LabResult result = labResultRepository.findByIdWithAllDetails(resultId)
                .orElseThrow(() -> new ResourceNotFoundException("LabResult", "id", resultId));
        Hibernate.initialize(result.getLabRequest());
        Hibernate.initialize(result.getLabRequest().getPatient());
        Hibernate.initialize(result.getLabRequest().getRequestingDoctor());
        Hibernate.initialize(result.getPerformedBy());
        Hibernate.initialize(result.getVerifiedBy());
        Hibernate.initialize(result.getParameters());
        return result;
    }

    // ----------------------------------------------------------------
    //  Get a single lab request by id (for the result-entry form)
    // ----------------------------------------------------------------
    public LabRequest getRequestById(UUID requestId) {
        LabRequest request = labRequestRepository.findByIdWithDetails(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("LabRequest", "id", requestId));
        // Explicitly initialize all associations needed by the Thymeleaf template
        // so that they remain accessible after the Hibernate session closes
        Hibernate.initialize(request.getPatient());
        LabResult result = request.getResult();
        if (result != null) {
            Hibernate.initialize(result);
            Hibernate.initialize(result.getParameters());
            Hibernate.initialize(result.getPerformedBy());
            Hibernate.initialize(result.getVerifiedBy());
        }
        return request;
    }
}
