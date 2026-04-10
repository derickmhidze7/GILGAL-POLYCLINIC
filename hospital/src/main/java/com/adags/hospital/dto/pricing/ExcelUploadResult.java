package com.adags.hospital.dto.pricing;

import lombok.*;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExcelUploadResult {

    private int totalRows;
    private int imported;
    private int skipped;
    private List<String> errors;

    public boolean hasErrors() {
        return errors != null && !errors.isEmpty();
    }
}
