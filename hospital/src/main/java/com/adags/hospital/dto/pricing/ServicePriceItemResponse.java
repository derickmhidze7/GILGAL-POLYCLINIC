package com.adags.hospital.dto.pricing;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ServicePriceItemResponse {

    private UUID id;
    private String itemId;
    private String productCode;
    private String productName;
    private String classification;
    private String type;
    private String category;
    private BigDecimal price;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
