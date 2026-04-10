package com.adags.hospital.domain.surgery;

import com.adags.hospital.domain.common.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "surgery_intraoperative")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SurgeryIntraoperative extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "surgery_order_id", nullable = false, unique = true)
    private SurgeryOrder surgeryOrder;

    @Column(name = "start_time")
    private LocalDateTime startTime;

    @Column(name = "end_time")
    private LocalDateTime endTime;

    @Column(name = "lead_surgeon", length = 255)
    private String leadSurgeon;

    @Column(name = "anesthesiologist", length = 255)
    private String anesthesiologist;

    @Column(name = "blood_loss_ml")
    private Integer bloodLossMl;

    @Column(name = "fluids_given_ml")
    private Integer fluidsGivenMl;

    @Column(name = "complications", columnDefinition = "TEXT")
    private String complications;

    @Column(name = "intraop_notes", columnDefinition = "TEXT")
    private String intraopNotes;

    @Column(name = "anesthesia_notes", columnDefinition = "TEXT")
    private String anesthesiaNotes;
}
