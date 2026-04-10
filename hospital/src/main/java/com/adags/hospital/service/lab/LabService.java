package com.adags.hospital.service.lab;

import com.adags.hospital.domain.lab.*;
import com.adags.hospital.domain.medicalrecord.MedicalRecord;
import com.adags.hospital.domain.patient.Patient;
import com.adags.hospital.domain.staff.Staff;
import com.adags.hospital.dto.lab.LabRequestDto;
import com.adags.hospital.dto.lab.LabResultDto;
import com.adags.hospital.exception.BusinessRuleException;
import com.adags.hospital.exception.ResourceNotFoundException;
import com.adags.hospital.repository.lab.LabRequestRepository;
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
public class LabService {

    private final LabRequestRepository labRequestRepository;
    private final PatientRepository patientRepository;
    private final StaffRepository staffRepository;
    private final MedicalRecordRepository medicalRecordRepository;

    public Page<LabRequest> getPendingRequests(Pageable pageable) {
        return labRequestRepository.findByStatus(LabRequestStatus.PENDING, pageable);
    }

    public Page<LabRequest> getByPatient(UUID patientId, Pageable pageable) {
        return labRequestRepository.findByPatientId(patientId, pageable);
    }

    @Transactional
    public LabRequest createRequest(LabRequestDto dto) {
        Patient patient = patientRepository.findById(dto.patientId())
                .orElseThrow(() -> new ResourceNotFoundException("Patient", "id", dto.patientId()));
        Staff doctor = staffRepository.findById(dto.requestingDoctorId())
                .orElseThrow(() -> new ResourceNotFoundException("Staff", "id", dto.requestingDoctorId()));

        MedicalRecord record = null;
        if (dto.medicalRecordId() != null) {
            record = medicalRecordRepository.findById(dto.medicalRecordId())
                    .orElseThrow(() -> new ResourceNotFoundException("MedicalRecord", "id", dto.medicalRecordId()));
        }

        return labRequestRepository.save(LabRequest.builder()
                .patient(patient)
                .requestingDoctor(doctor)
                .medicalRecord(record)
                .testName(dto.testName())
                .testCode(dto.testCode())
                .urgency(dto.urgency() != null ? dto.urgency() : LabUrgency.ROUTINE)
                .build());
    }

    @Transactional
    public LabResult addResult(LabResultDto dto) {
        LabRequest request = labRequestRepository.findById(dto.labRequestId())
                .orElseThrow(() -> new ResourceNotFoundException("LabRequest", "id", dto.labRequestId()));

        if (request.getStatus() == LabRequestStatus.COMPLETED) {
            throw new BusinessRuleException("Lab result already recorded for this request");
        }

        Staff technician = staffRepository.findById(dto.performedById())
                .orElseThrow(() -> new ResourceNotFoundException("Staff", "id", dto.performedById()));

        LabResult result = LabResult.builder()
                .labRequest(request)
                .performedBy(technician)
                .resultValue(dto.resultValue())
                .referenceRange(dto.referenceRange())
                .unit(dto.unit())
                .interpretation(dto.interpretation())
                .notes(dto.notes())
                .build();

        request.setResult(result);
        request.setStatus(LabRequestStatus.COMPLETED);
        labRequestRepository.save(request);
        return result;
    }
}
