package com.adags.hospital.domain.appointment;

import com.adags.hospital.domain.common.BaseEntity;
import com.adags.hospital.domain.patient.Patient;
import com.adags.hospital.domain.staff.Staff;
import com.adags.hospital.domain.user.AppUser;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "appointments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Appointment extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "patient_id", nullable = false)
    private Patient patient;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "doctor_id")
    private Staff doctor;

    @Column(name = "scheduled_date_time", nullable = false)
    private LocalDateTime scheduledDateTime;

    @Enumerated(EnumType.STRING)
    @Column(name = "appointment_type", nullable = false, length = 30)
    private AppointmentType appointmentType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private AppointmentStatus status = AppointmentStatus.SCHEDULED;

    @Column(name = "notes", length = 1000)
    private String notes;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "booked_by_id")
    private AppUser bookedBy;

    @Column(name = "cancellation_reason", length = 500)
    private String cancellationReason;
}
