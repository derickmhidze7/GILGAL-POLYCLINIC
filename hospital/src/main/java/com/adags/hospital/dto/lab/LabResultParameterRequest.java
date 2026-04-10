package com.adags.hospital.dto.lab;

import com.adags.hospital.domain.lab.LabInterpretation;
import lombok.Data;

@Data
public class LabResultParameterRequest {
    private String parameterName;
    private String resultValue;
    private String unit;
    private String referenceRange;
    private LabInterpretation interpretation;
}
