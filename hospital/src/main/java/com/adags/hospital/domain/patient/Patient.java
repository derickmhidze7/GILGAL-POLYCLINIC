package com.adags.hospital.domain.patient;

import com.adags.hospital.domain.common.Address;
import com.adags.hospital.domain.common.BaseEntity;
import com.adags.hospital.domain.common.Gender;
import com.adags.hospital.domain.patient.MaritalStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "patients")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Patient extends BaseEntity {

    @Column(name = "first_name", nullable = false, length = 80)
    private String firstName;

    @Column(name = "middle_name", length = 80)
    private String middleName;

    @Column(name = "last_name", nullable = false, length = 80)
    private String lastName;

    @Column(name = "date_of_birth", nullable = false)
    private LocalDate dateOfBirth;

    @Enumerated(EnumType.STRING)
    @Column(name = "gender", nullable = false, length = 20)
    private Gender gender;

    @Column(name = "national_id", unique = true, length = 50)
    private String nationalId;

    @Column(name = "phone", length = 30)
    private String phone;

    @Column(name = "email", length = 150)
    private String email;

    /**
     * Current (temporary) location.
     * Maps to the original street / city / province / country / postal_code columns.
     */
    @Embedded
    private Address address;

    /**
     * Permanent residence address.
     * Stored in the perm_street / perm_city columns added by V13.
     */
    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "street",     column = @Column(name = "perm_street",  length = 255)),
            @AttributeOverride(name = "city",       column = @Column(name = "perm_city",    length = 100)),
            @AttributeOverride(name = "province",   column = @Column(name = "perm_province", length = 100)),
            @AttributeOverride(name = "country",    column = @Column(name = "perm_country",  length = 100)),
            @AttributeOverride(name = "postalCode", column = @Column(name = "perm_postal_code", length = 20))
    })
    private Address permanentAddress;

    @Enumerated(EnumType.STRING)
    @Column(name = "marital_status", length = 30)
    private MaritalStatus maritalStatus;

    @Column(name = "insurance_provider", length = 150)
    private String insuranceProvider;

    @Column(name = "insurance_policy_number", length = 100)
    private String insurancePolicyNumber;

    @Column(name = "insurance_member_number", length = 100)
    private String insuranceMemberNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "blood_group", length = 10)
    private BloodGroup bloodGroup;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "patient_allergies", joinColumns = @JoinColumn(name = "patient_id"))
    @Column(name = "allergy")
    @Builder.Default
    private List<String> allergies = new ArrayList<>();

    @Column(name = "registration_date", nullable = false)
    @Builder.Default
    private LocalDate registrationDate = LocalDate.now();

    @OneToOne(cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @JoinColumn(name = "next_of_kin_id")
    private NextOfKin nextOfKin;

    @Column(name = "occupation", length = 100)
    private String occupation;

    @Column(name = "active", nullable = false)
    @Builder.Default
    private boolean active = true;
}
