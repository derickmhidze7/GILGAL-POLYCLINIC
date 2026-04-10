package com.adags.hospital.domain.lab;

import com.adags.hospital.domain.common.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "lab_result_parameters")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LabResultParameter extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lab_result_id", nullable = false)
    private LabResult labResult;

    @Column(name = "parameter_name", nullable = false, length = 100)
    private String parameterName;

    @Column(name = "result_value", length = 200)
    private String resultValue;

    @Column(name = "unit", length = 50)
    private String unit;

    @Column(name = "reference_range", length = 100)
    private String referenceRange;

    @Enumerated(EnumType.STRING)
    @Column(name = "interpretation", length = 15)
    private LabInterpretation interpretation;
}
