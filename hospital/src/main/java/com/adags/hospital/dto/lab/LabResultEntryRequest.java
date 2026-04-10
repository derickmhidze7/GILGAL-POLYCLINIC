package com.adags.hospital.dto.lab;

import com.adags.hospital.domain.lab.LabInterpretation;
import com.adags.hospital.domain.lab.SampleQuality;
import lombok.Data;

import java.util.List;

@Data
public class LabResultEntryRequest {
    private String sampleType;
    private SampleQuality sampleQuality = SampleQuality.ADEQUATE;
    private String resultValue;       // summary / single-value result
    private String notes;
    private LabInterpretation interpretation = LabInterpretation.NORMAL;
    private List<LabResultParameterRequest> parameters;
}
