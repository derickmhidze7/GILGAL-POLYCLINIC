package com.adags.hospital.dto.pricing;

import jakarta.validation.constraints.*;
import lombok.*;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ServicePriceItemRequest {

    private String itemId;
    private String productCode;

    @NotBlank(message = "Product name is required")
    private String productName;

    private String classification;

    @NotBlank(message = "Type is required (PHARMACY, LAB, or SURGERY)")
    private String type;

    private String category;

    @NotNull(message = "Price is required")
    @DecimalMin(value = "0.00", message = "Price must be non-negative")
    private BigDecimal price;
}
