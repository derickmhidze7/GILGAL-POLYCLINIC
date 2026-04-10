package com.adags.hospital.domain.pharmacy;

import com.adags.hospital.domain.common.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "medications")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Medication extends BaseEntity {

    @Column(name = "generic_name", nullable = false, length = 150)
    private String genericName;

    @Column(name = "brand_name", length = 150)
    private String brandName;

    @Column(name = "category", length = 100)
    private String category;

    @Enumerated(EnumType.STRING)
    @Column(name = "form", nullable = false, length = 20)
    private MedicationForm form;

    @Column(name = "strength", length = 50)
    private String strength;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "active", nullable = false)
    @Builder.Default
    private boolean active = true;
}
