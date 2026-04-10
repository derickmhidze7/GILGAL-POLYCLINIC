package com.adags.hospital.service.appointment;

import com.adags.hospital.domain.appointment.Appointment;
import com.adags.hospital.domain.appointment.AppointmentStatus;
import com.adags.hospital.domain.patient.Patient;
import com.adags.hospital.domain.staff.Staff;
import com.adags.hospital.dto.appointment.AppointmentRequest;
import com.adags.hospital.dto.appointment.AppointmentResponse;
import com.adags.hospital.exception.ResourceNotFoundException;
import com.adags.hospital.repository.appointment.AppointmentRepository;
import com.adags.hospital.repository.patient.PatientRepository;
import com.adags.hospital.repository.staff.StaffRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AppointmentService {

    private final AppointmentRepository appointmentRepository;
    private final PatientRepository patientRepository;
    private final StaffRepository staffRepository;

    public Page<AppointmentResponse> getAll(Pageable pageable) {
        return appointmentRepository.findAll(pageable).map(this::toResponse);
    }

    public Page<AppointmentResponse> getByPatient(UUID patientId, Pageable pageable) {
        return appointmentRepository.findByPatientId(patientId, pageable).map(this::toResponse);
    }

    public Page<AppointmentResponse> getByStatus(AppointmentStatus status, Pageable pageable) {
        return appointmentRepository.findByStatus(status, pageable).map(this::toResponse);
    }

    public AppointmentResponse getById(UUID id) {
        return toResponse(findOrThrow(id));
    }

    @Transactional
    public AppointmentResponse create(AppointmentRequest request) {
        Patient patient = patientRepository.findById(request.patientId())
                .orElseThrow(() -> new ResourceNotFoundException("Patient", "id", request.patientId()));

        Staff doctor = null;
        if (request.doctorId() != null) {
            doctor = staffRepository.findById(request.doctorId())
                    .orElseThrow(() -> new ResourceNotFoundException("Staff", "id", request.doctorId()));
        }

        Appointment appointment = Appointment.builder()
                .patient(patient)
                .doctor(doctor)
                .scheduledDateTime(request.scheduledDateTime())
                .appointmentType(request.appointmentType())
                .notes(request.notes())
                .build();

        return toResponse(appointmentRepository.save(appointment));
    }

    @Transactional
    public AppointmentResponse updateStatus(UUID id, AppointmentStatus status, String cancellationReason) {
        Appointment appointment = findOrThrow(id);
        appointment.setStatus(status);
        if (status == AppointmentStatus.CANCELLED) {
            appointment.setCancellationReason(cancellationReason);
        }
        return toResponse(appointmentRepository.save(appointment));
    }

    @Transactional
    public AppointmentResponse update(UUID id, AppointmentRequest request) {
        Appointment appointment = findOrThrow(id);

        if (request.doctorId() != null) {
            Staff doctor = staffRepository.findById(request.doctorId())
                    .orElseThrow(() -> new ResourceNotFoundException("Staff", "id", request.doctorId()));
            appointment.setDoctor(doctor);
        }
        appointment.setScheduledDateTime(request.scheduledDateTime());
        appointment.setAppointmentType(request.appointmentType());
        appointment.setNotes(request.notes());
        if (request.status() != null) appointment.setStatus(request.status());

        return toResponse(appointmentRepository.save(appointment));
    }

    // -----------------------------------------------------------------------

    private Appointment findOrThrow(UUID id) {
        return appointmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Appointment", "id", id));
    }

    private AppointmentResponse toResponse(Appointment a) {
        String patientName = a.getPatient().getFirstName() + " " + a.getPatient().getLastName();
        String doctorName = a.getDoctor() != null
                ? a.getDoctor().getFirstName() + " " + a.getDoctor().getLastName()
                : null;
        return new AppointmentResponse(
                a.getId(),
                a.getPatient().getId(),
                patientName,
                a.getDoctor() != null ? a.getDoctor().getId() : null,
                doctorName,
                a.getScheduledDateTime(),
                a.getAppointmentType(),
                a.getStatus(),
                a.getNotes(),
                a.getCreatedAt()
        );
    }
}
