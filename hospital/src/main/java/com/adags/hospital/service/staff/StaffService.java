package com.adags.hospital.service.staff;

import com.adags.hospital.domain.staff.Department;
import com.adags.hospital.domain.staff.Staff;
import com.adags.hospital.dto.staff.StaffRequest;
import com.adags.hospital.dto.staff.StaffResponse;
import com.adags.hospital.exception.DuplicateResourceException;
import com.adags.hospital.exception.ResourceNotFoundException;
import com.adags.hospital.repository.staff.DepartmentRepository;
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
public class StaffService {

    private final StaffRepository staffRepository;
    private final DepartmentRepository departmentRepository;

    public Page<StaffResponse> getAll(Pageable pageable) {
        return staffRepository.findByActiveTrue(pageable).map(this::toResponse);
    }

    public Page<StaffResponse> search(String query, Pageable pageable) {
        return staffRepository.searchStaff(query, pageable).map(this::toResponse);
    }

    public StaffResponse getById(UUID id) {
        return toResponse(findOrThrow(id));
    }

    @Transactional
    public StaffResponse create(StaffRequest request) {
        if (staffRepository.existsByEmail(request.email())) {
            throw new DuplicateResourceException(
                    "Staff member with email '" + request.email() + "' already exists");
        }

        Department department = null;
        if (request.departmentId() != null) {
            department = departmentRepository.findById(request.departmentId())
                    .orElseThrow(() -> new ResourceNotFoundException("Department", "id", request.departmentId()));
        }

        Staff staff = Staff.builder()
                .firstName(request.firstName())
                .lastName(request.lastName())
                .dateOfBirth(request.dateOfBirth())
                .gender(request.gender())
                .phone(request.phone())
                .email(request.email())
                .department(department)
                .staffRole(request.staffRole())
                .specialization(request.specialization())
                .licenseNumber(request.licenseNumber())
                .employmentDate(request.employmentDate())
                .build();

        return toResponse(staffRepository.save(staff));
    }

    @Transactional
    public StaffResponse update(UUID id, StaffRequest request) {
        Staff staff = findOrThrow(id);

        Department department = null;
        if (request.departmentId() != null) {
            department = departmentRepository.findById(request.departmentId())
                    .orElseThrow(() -> new ResourceNotFoundException("Department", "id", request.departmentId()));
        }

        staff.setFirstName(request.firstName());
        staff.setLastName(request.lastName());
        staff.setDateOfBirth(request.dateOfBirth());
        staff.setGender(request.gender());
        staff.setPhone(request.phone());
        staff.setDepartment(department);
        staff.setStaffRole(request.staffRole());
        staff.setSpecialization(request.specialization());
        staff.setLicenseNumber(request.licenseNumber());
        staff.setEmploymentDate(request.employmentDate());

        return toResponse(staffRepository.save(staff));
    }

    @Transactional
    public void deactivate(UUID id) {
        Staff staff = findOrThrow(id);
        staff.setActive(false);
        staffRepository.save(staff);
    }

    // -----------------------------------------------------------------------

    private Staff findOrThrow(UUID id) {
        return staffRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Staff", "id", id));
    }

    private StaffResponse toResponse(Staff s) {
        return new StaffResponse(
                s.getId(),
                s.getFirstName(),
                s.getLastName(),
                s.getDateOfBirth(),
                s.getGender(),
                s.getPhone(),
                s.getEmail(),
                s.getDepartment() != null ? s.getDepartment().getId() : null,
                s.getDepartment() != null ? s.getDepartment().getName() : null,
                s.getStaffRole(),
                s.getSpecialization(),
                s.getLicenseNumber(),
                s.getEmploymentDate(),
                s.isActive()
        );
    }
}
