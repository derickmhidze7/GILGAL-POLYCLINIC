package com.adags.hospital.service.medicalrecord;

import com.adags.hospital.domain.appointment.Appointment;
import com.adags.hospital.domain.medicalrecord.MedicalRecord;
import com.adags.hospital.domain.patient.Patient;
import com.adags.hospital.domain.staff.Staff;
import com.adags.hospital.dto.medicalrecord.MedicalRecordRequest;
import com.adags.hospital.dto.medicalrecord.MedicalRecordResponse;
import com.adags.hospital.exception.ResourceNotFoundException;
import com.adags.hospital.repository.appointment.AppointmentRepository;
import com.adags.hospital.repository.medicalrecord.MedicalRecordRepository;
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
public class MedicalRecordService {

    private final MedicalRecordRepository medicalRecordRepository;
    private final PatientRepository patientRepository;
    private final StaffRepository staffRepository;
    private final AppointmentRepository appointmentRepository;

    public Page<MedicalRecordResponse> getByPatient(UUID patientId, Pageable pageable) {
        return medicalRecordRepository.findByPatientId(patientId, pageable).map(this::toResponse);
    }

    public MedicalRecordResponse getById(UUID id) {
        return toResponse(findOrThrow(id));
    }

    @Transactional
    public MedicalRecordResponse create(MedicalRecordRequest request) {
        Patient patient = patientRepository.findById(request.patientId())
                .orElseThrow(() -> new ResourceNotFoundException("Patient", "id", request.patientId()));
        Staff doctor = staffRepository.findById(request.attendingDoctorId())
                .orElseThrow(() -> new ResourceNotFoundException("Staff", "id", request.attendingDoctorId()));

        Appointment appointment = null;
        if (request.appointmentId() != null) {
            appointment = appointmentRepository.findById(request.appointmentId())
                    .orElseThrow(() -> new ResourceNotFoundException("Appointment", "id", request.appointmentId()));
        }

        MedicalRecord record = MedicalRecord.builder()
                .patient(patient)
                .attendingDoctor(doctor)
                .appointment(appointment)
                .clinicalNotes(request.clinicalNotes())
                .followUpDate(request.followUpDate())
                .build();

        return toResponse(medicalRecordRepository.save(record));
    }

    @Transactional
    public MedicalRecordResponse update(UUID id, MedicalRecordRequest request) {
        MedicalRecord record = findOrThrow(id);
        record.setClinicalNotes(request.clinicalNotes());
        record.setFollowUpDate(request.followUpDate());
        return toResponse(medicalRecordRepository.save(record));
    }

    private MedicalRecord findOrThrow(UUID id) {
        return medicalRecordRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("MedicalRecord", "id", id));
    }

    private MedicalRecordResponse toResponse(MedicalRecord r) {
        return new MedicalRecordResponse(
                r.getId(),
                r.getPatient().getId(),
                r.getPatient().getFirstName() + " " + r.getPatient().getLastName(),
                r.getAttendingDoctor().getId(),
                r.getAttendingDoctor().getFirstName() + " " + r.getAttendingDoctor().getLastName(),
                r.getAppointment() != null ? r.getAppointment().getId() : null,
                r.getVisitDate(),
                r.getClinicalNotes(),
                r.getFollowUpDate(),
                r.getCreatedAt()
        );
    }
}
