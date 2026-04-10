package com.adags.hospital.domain.patient;

import com.adags.hospital.domain.common.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "next_of_kin")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NextOfKin extends BaseEntity {

    @Column(name = "full_name", nullable = false, length = 150)
    private String fullName;

    @Column(name = "relationship", nullable = false, length = 60)
    private String relationship;

    @Column(name = "phone", nullable = false, length = 30)
    private String phone;

    @Column(name = "email", length = 150)
    private String email;
}
