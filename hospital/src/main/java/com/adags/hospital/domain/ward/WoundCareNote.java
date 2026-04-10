package com.adags.hospital.domain.ward;

import com.adags.hospital.domain.common.BaseEntity;
import com.adags.hospital.domain.patient.Patient;
import com.adags.hospital.domain.staff.Staff;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "wound_care_notes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WoundCareNote extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ward_assignment_id", nullable = false)
    private WardPatientAssignment wardAssignment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "patient_id", nullable = false)
    private Patient patient;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recorded_by_id")
    private Staff recordedBy;

    @Column(name = "recorded_at", nullable = false)
    @Builder.Default
    private LocalDateTime recordedAt = LocalDateTime.now();

    @Column(name = "wound_appearance", columnDefinition = "TEXT")
    private String woundAppearance;

    @Column(name = "dressing_changed", nullable = false)
    @Builder.Default
    private Boolean dressingChanged = false;

    @Column(name = "dressing_type", length = 100)
    private String dressingType;

    @Column(name = "signs_of_infection", nullable = false)
    @Builder.Default
    private Boolean signsOfInfection = false;

    @Column(name = "infection_description", columnDefinition = "TEXT")
    private String infectionDescription;

    @Column(name = "remarks", columnDefinition = "TEXT")
    private String remarks;
}
