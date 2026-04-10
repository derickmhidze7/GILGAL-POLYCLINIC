package com.adags.hospital.domain.ward;

import com.adags.hospital.domain.common.BaseEntity;
import com.adags.hospital.domain.patient.Patient;
import com.adags.hospital.domain.staff.Staff;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "medication_administration_records")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MedicationAdministrationRecord extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ward_assignment_id", nullable = false)
    private WardPatientAssignment wardAssignment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "patient_id", nullable = false)
    private Patient patient;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "administered_by_id")
    private Staff administeredBy;

    @Column(name = "medication_name", nullable = false, length = 200)
    private String medicationName;

    @Column(name = "scheduled_time", nullable = false)
    private LocalDateTime scheduledTime;

    @Column(name = "administered_at")
    private LocalDateTime administeredAt;

    @Column(name = "dose_given", length = 100)
    private String doseGiven;

    @Column(name = "route", length = 50)
    private String route;

    @Column(name = "was_given", nullable = false)
    @Builder.Default
    private Boolean wasGiven = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "skip_reason", length = 50)
    private MedicationSkipReason skipReason;

    @Column(name = "skip_notes", columnDefinition = "TEXT")
    private String skipNotes;

    @Column(name = "remarks", columnDefinition = "TEXT")
    private String remarks;
}
