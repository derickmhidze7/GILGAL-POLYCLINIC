package com.adags.hospital.service.user;

import com.adags.hospital.domain.staff.Department;
import com.adags.hospital.domain.staff.Staff;
import com.adags.hospital.domain.user.AppUser;
import com.adags.hospital.domain.user.Role;
import com.adags.hospital.dto.user.ChangePasswordRequest;
import com.adags.hospital.dto.user.CreateNurseRequest;
import com.adags.hospital.dto.user.CreateUserRequest;
import com.adags.hospital.exception.BusinessRuleException;
import com.adags.hospital.exception.DuplicateResourceException;
import com.adags.hospital.exception.ResourceNotFoundException;
import com.adags.hospital.repository.staff.DepartmentRepository;
import com.adags.hospital.repository.staff.StaffRepository;
import com.adags.hospital.repository.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;
    private final StaffRepository staffRepository;
    private final DepartmentRepository departmentRepository;
    private final PasswordEncoder passwordEncoder;

    public Page<AppUser> getAll(Pageable pageable) {
        return userRepository.findAll(pageable);
    }

    public AppUser getById(UUID id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", id));
    }

    @Transactional
    public AppUser createUser(CreateUserRequest request) {
        if (userRepository.existsByUsername(request.username())) {
            throw new DuplicateResourceException("Username '" + request.username() + "' is already taken");
        }
        if (userRepository.existsByEmail(request.email())) {
            throw new DuplicateResourceException("Email '" + request.email() + "' is already registered");
        }

        Staff staff = null;
        if (request.staffId() != null) {
            staff = staffRepository.findById(request.staffId())
                    .orElseThrow(() -> new ResourceNotFoundException("Staff", "id", request.staffId()));
            // Sync the Staff record's role so it appears in role-based dropdowns (e.g. triage)
            staff.setStaffRole(request.role());
            staff.setActive(true);
            staffRepository.save(staff);
        }

        AppUser user = AppUser.builder()
                .username(request.username())
                .email(request.email())
                .passwordHash(passwordEncoder.encode(request.password()))
                .role(request.role())
                .staff(staff)
                .enabled(true)
                .accountNonLocked(true)
                .build();

        return userRepository.save(user);
    }

    @Transactional
    public AppUser toggleStatus(UUID id) {
        AppUser user = getById(id);
        user.setEnabled(!user.isEnabled());
        return userRepository.save(user);
    }

    @Transactional
    public void changePassword(UUID id, ChangePasswordRequest request, String requestingUsername) {
        AppUser user = getById(id);

        // Only the user themselves can change their own password (or ADMIN)
        if (!user.getUsername().equals(requestingUsername)) {
            throw new BusinessRuleException("You can only change your own password");
        }

        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new BusinessRuleException("Current password is incorrect");
        }

        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);
    }

    @Transactional
    public void changePasswordByUsername(ChangePasswordRequest request, String username) {
        AppUser user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User", "username", username));

        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new BusinessRuleException("Current password is incorrect");
        }

        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);
    }

    @Transactional
    public void resetPassword(UUID id, String newPassword) {
        AppUser user = getById(id);
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);
    }

    @Transactional
    public AppUser updateUser(UUID id, String username, String email, Role role) {
        AppUser user = getById(id);
        if (!user.getUsername().equalsIgnoreCase(username) && userRepository.existsByUsername(username)) {
            throw new DuplicateResourceException("Username '" + username + "' is already taken");
        }
        if (!user.getEmail().equalsIgnoreCase(email) && userRepository.existsByEmail(email)) {
            throw new DuplicateResourceException("Email '" + email + "' is already registered");
        }
        user.setUsername(username);
        user.setEmail(email);
        user.setRole(role);
        // Keep linked Staff record in sync so role-based queries (e.g. triage dropdown) stay correct
        if (user.getStaff() != null) {
            user.getStaff().setStaffRole(role);
            staffRepository.save(user.getStaff());
        }
        return userRepository.save(user);
    }

    @Transactional
    public void deleteUser(UUID id) {
        AppUser user = getById(id);
        userRepository.delete(user);
    }

    // ─── Create Nurse (staff + account in one transaction) ───────────────────
    // Supports WARD_NURSE (ward/surgery only) and NURSE (triage + ward/surgery)

    @Transactional
    public AppUser createWardNurse(CreateNurseRequest request) {
        return createNurseWithRole(request, Role.WARD_NURSE);
    }

    @Transactional
    public AppUser createNurseWithRole(CreateNurseRequest request, Role nurseRole) {
        if (nurseRole != Role.WARD_NURSE && nurseRole != Role.NURSE && nurseRole != Role.TRIAGE_NURSE) {
            throw new IllegalArgumentException("Invalid nurse role: " + nurseRole);
        }
        if (userRepository.existsByUsername(request.username())) {
            throw new DuplicateResourceException("Username '" + request.username() + "' is already taken");
        }
        if (userRepository.existsByEmail(request.email())) {
            throw new DuplicateResourceException("Email '" + request.email() + "' is already registered as a user account");
        }
        if (staffRepository.existsByEmail(request.email())) {
            throw new DuplicateResourceException("Email '" + request.email() + "' is already registered as a staff member");
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
                .staffRole(nurseRole)
                .licenseNumber(request.licenseNumber())
                .employmentDate(request.employmentDate())
                .active(true)
                .build();
        staff = staffRepository.save(staff);

        AppUser user = AppUser.builder()
                .username(request.username())
                .email(request.email())
                .passwordHash(passwordEncoder.encode(request.password()))
                .role(nurseRole)
                .staff(staff)
                .build();

        return userRepository.save(user);
    }
}
