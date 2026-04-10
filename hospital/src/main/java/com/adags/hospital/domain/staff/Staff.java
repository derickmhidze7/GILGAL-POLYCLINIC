package com.adags.hospital.domain.staff;

import com.adags.hospital.domain.common.BaseEntity;
import com.adags.hospital.domain.common.Gender;
import com.adags.hospital.domain.user.Role;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

@Entity
@Table(name = "staff")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Staff extends BaseEntity {

    @Column(name = "first_name", nullable = false, length = 80)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 80)
    private String lastName;

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    @Enumerated(EnumType.STRING)
    @Column(name = "gender", length = 20)
    private Gender gender;

    @Column(name = "phone", length = 30)
    private String phone;

    @Column(name = "email", unique = true, nullable = false, length = 150)
    private String email;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "department_id")
    private Department department;

    /**
     * Role this staff member holds in the system (used for reference display;
     * actual access control is via AppUser.role).
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "staff_role", length = 30)
    private Role staffRole;

    @Column(name = "specialization", length = 150)
    private String specialization;

    @Column(name = "license_number", length = 80)
    private String licenseNumber;

    @Column(name = "employment_date")
    private LocalDate employmentDate;

    @Column(name = "active", nullable = false)
    @Builder.Default
    private boolean active = true;
}
