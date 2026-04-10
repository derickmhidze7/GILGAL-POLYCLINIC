package com.adags.hospital.service.patient;

import com.adags.hospital.domain.common.Address;
import com.adags.hospital.domain.patient.NextOfKin;
import com.adags.hospital.domain.patient.Patient;
import java.time.LocalDate;
import java.time.Period;
import com.adags.hospital.dto.patient.PatientRequest;
import com.adags.hospital.dto.patient.PatientResponse;
import com.adags.hospital.exception.DuplicateResourceException;
import com.adags.hospital.exception.ResourceNotFoundException;
import com.adags.hospital.repository.patient.PatientRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PatientService {

    private final PatientRepository patientRepository;

    public Page<PatientResponse> getAll(Pageable pageable) {
        return patientRepository.findByActiveTrue(pageable).map(this::toResponse);
    }

    /** Returns all patients including discharged (active = false). Used by admin and receptionist listings. */
    public Page<PatientResponse> getAllIncludingDischarged(Pageable pageable) {
        return patientRepository.findAll(pageable).map(this::toResponse);
    }

    public Page<PatientResponse> search(String query, Pageable pageable) {
        return patientRepository.searchPatients(query, pageable).map(this::toResponse);
    }

    public Page<PatientResponse> searchAll(String query, Pageable pageable) {
        return patientRepository.searchAllPatients(query, pageable).map(this::toResponse);
    }

    public List<PatientResponse> getIdlePatients(Set<UUID> busyPatientIds) {
        List<Patient> raw = busyPatientIds.isEmpty()
                ? patientRepository.findByActiveTrue()
                : patientRepository.findByActiveTrueAndIdNotIn(busyPatientIds);
        return raw.stream().map(this::toResponse).collect(Collectors.toList());
    }

    public PatientResponse getById(UUID id) {
        return toResponse(findOrThrow(id));
    }

    @Transactional
    public PatientResponse create(PatientRequest request) {
        if (StringUtils.hasText(request.nationalId())) {
            patientRepository.findByNationalId(request.nationalId()).ifPresent(existing -> {
                String date = existing.getRegistrationDate()
                        .format(DateTimeFormatter.ofPattern("dd MMM yyyy"));
                throw new DuplicateResourceException(
                        "A patient with national ID '" + request.nationalId() +
                        "' is already registered (registered on " + date + ").");
            });
        }

        patientRepository.findByFullName(request.firstName(), request.middleName(), request.lastName())
                .ifPresent(existing -> {
                    String fullName = request.firstName() +
                            (StringUtils.hasText(request.middleName()) ? " " + request.middleName() : "") +
                            " " + request.lastName();
                    String date = existing.getRegistrationDate()
                            .format(DateTimeFormatter.ofPattern("dd MMM yyyy"));
                    throw new DuplicateResourceException(
                            "A patient named '" + fullName + "' is already registered (registered on " + date +
                            "). Please verify this is not a duplicate before proceeding.");
                });

        Address currentAddr = null;
        if (StringUtils.hasText(request.currentStreet()) || StringUtils.hasText(request.currentCity())) {
            currentAddr = Address.builder()
                    .street(request.currentStreet())
                    .city(request.currentCity())
                    .build();
        }

        Address permAddr = null;
        if (StringUtils.hasText(request.permStreet()) || StringUtils.hasText(request.permCity())) {
            permAddr = Address.builder()
                    .street(request.permStreet())
                    .city(request.permCity())
                    .build();
        }

        Patient patient = Patient.builder()
                .firstName(request.firstName())
                .middleName(request.middleName())
                .lastName(request.lastName())
                .dateOfBirth(request.dateOfBirth())
                .gender(request.gender())
                .maritalStatus(request.maritalStatus())
                .nationalId(request.nationalId())
                .phone(request.phone())
                .email(request.email())
                .occupation(request.occupation())
                .insuranceProvider(request.insuranceProvider())
                .insurancePolicyNumber(request.insurancePolicyNumber())
                .insuranceMemberNumber(request.insuranceMemberNumber())
                .address(currentAddr)
                .permanentAddress(permAddr)
                .build();

        patient.setNextOfKin(NextOfKin.builder()
                .fullName(request.nextOfKinFullName())
                .relationship(request.nextOfKinRelationship())
                .phone(request.nextOfKinPhone())
                .build());

        return toResponse(patientRepository.save(patient));
    }

    @Transactional
    public PatientResponse update(UUID id, PatientRequest request) {
        Patient patient = findOrThrow(id);

        patient.setFirstName(request.firstName());
        patient.setMiddleName(request.middleName());
        patient.setLastName(request.lastName());
        patient.setDateOfBirth(request.dateOfBirth());
        patient.setGender(request.gender());
        patient.setMaritalStatus(request.maritalStatus());
        patient.setPhone(request.phone());
        patient.setEmail(request.email());
        patient.setOccupation(request.occupation());
        patient.setInsuranceProvider(request.insuranceProvider());
        patient.setInsurancePolicyNumber(request.insurancePolicyNumber());
        patient.setInsuranceMemberNumber(request.insuranceMemberNumber());

        if (StringUtils.hasText(request.currentStreet()) || StringUtils.hasText(request.currentCity())) {
            patient.setAddress(Address.builder()
                    .street(request.currentStreet())
                    .city(request.currentCity())
                    .build());
        }

        if (StringUtils.hasText(request.permStreet()) || StringUtils.hasText(request.permCity())) {
            patient.setPermanentAddress(Address.builder()
                    .street(request.permStreet())
                    .city(request.permCity())
                    .build());
        }

        return toResponse(patientRepository.save(patient));
    }

    @Transactional
    public void deactivate(UUID id) {
        Patient patient = findOrThrow(id);
        patient.setActive(false);
        patientRepository.save(patient);
    }

    // -----------------------------------------------------------------------
    //  Private helpers
    // -----------------------------------------------------------------------

    private Patient findOrThrow(UUID id) {
        return patientRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Patient", "id", id));
    }

    private PatientResponse toResponse(Patient p) {
        int age = (p.getDateOfBirth() != null)
                ? Period.between(p.getDateOfBirth(), LocalDate.now()).getYears()
                : 0;

        Address addr = p.getAddress();
        Address perm = p.getPermanentAddress();
        NextOfKin nok  = p.getNextOfKin();

        return new PatientResponse(
                p.getId(),
                p.getFirstName(),
                p.getMiddleName(),
                p.getLastName(),
                p.getDateOfBirth(),
                age,
                p.getGender(),
                p.getMaritalStatus(),
                p.getNationalId(),
                p.getPhone(),
                p.getEmail(),
                p.getOccupation(),
                p.getRegistrationDate(),
                p.isActive(),
                // current address
                addr != null ? addr.getStreet()     : null,
                addr != null ? addr.getCity()       : null,
                addr != null ? addr.getProvince()   : null,
                addr != null ? addr.getCountry()    : null,
                addr != null ? addr.getPostalCode() : null,
                // permanent address
                perm != null ? perm.getStreet()     : null,
                perm != null ? perm.getCity()       : null,
                perm != null ? perm.getProvince()   : null,
                perm != null ? perm.getCountry()    : null,
                perm != null ? perm.getPostalCode() : null,
                // next of kin
                nok != null ? nok.getFullName()     : null,
                nok != null ? nok.getRelationship() : null,
                nok != null ? nok.getPhone()        : null,
                nok != null ? nok.getEmail()        : null,
                // insurance
                p.getInsuranceProvider(),
                p.getInsurancePolicyNumber(),
                p.getInsuranceMemberNumber()
        );
    }
}
