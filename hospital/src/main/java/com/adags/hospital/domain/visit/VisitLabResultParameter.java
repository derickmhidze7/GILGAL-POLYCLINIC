package com.adags.hospital.domain.visit;

import com.adags.hospital.domain.common.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

/**
 * A single parameter / analyte within a VisitLabRequest result.
 * e.g.  Haemoglobin → 13.5 g/dL  [ref: 13.0–17.0]  flag: N
 */
@Entity
@Table(name = "visit_lab_result_parameters")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VisitLabResultParameter extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "visit_lab_request_id", nullable = false)
    private VisitLabRequest visitLabRequest;

    @Column(name = "parameter_name", nullable = false, length = 100)
    private String parameterName;

    @Column(name = "result_value", length = 200)
    private String resultValue;

    @Column(name = "unit", length = 50)
    private String unit;

    @Column(name = "reference_range", length = 100)
    private String referenceRange;

    /** H = High, L = Low, N = Normal, C = Critical */
    @Column(name = "flag", length = 10)
    private String flag;

    /** Method used specifically for this parameter (optional) */
    @Column(name = "method", length = 100)
    private String method;

    @Column(name = "sort_order", nullable = false)
    @Builder.Default
    private int sortOrder = 0;
}
