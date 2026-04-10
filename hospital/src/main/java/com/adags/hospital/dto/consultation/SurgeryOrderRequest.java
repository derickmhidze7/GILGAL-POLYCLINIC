package com.adags.hospital.dto.consultation;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Surgery order portion of a consultation form — populated when nextStep = SCHEDULE_SURGERY.
 */
@Data
public class SurgeryOrderRequest {

    /** ID from service_price_items where type='SURGERY' */
    private UUID servicePriceItemId;

    /** Human-readable procedure name (pre-filled from catalog, editable) */
    private String procedureName;

    /** e.g. GENERAL, ORTHOPAEDIC, CARDIAC, GYNAECOLOGICAL */
    private String surgeryType;

    /** ELECTIVE | URGENT | EMERGENCY */
    private String urgency;

    /** GENERAL | SPINAL | EPIDURAL | LOCAL | SEDATION | NONE */
    private String anesthesiaType;

    /** Proposed date/time for the surgery */
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime scheduledDate;

    /** Theater name (optional) */
    private String operatingTheater;

    /** Estimated duration in minutes */
    private Integer estimatedDurationMinutes;

    /** Price from catalog (may be overridden) */
    private BigDecimal price;

    /** Nurse IDs to assign (scrub/circulating) */
    private java.util.List<UUID> nurseIds;

    /** Pre-op instructions / notes */
    private String preopNotes;

    /** Whether patient consent was obtained */
    private boolean consentObtained;
}
