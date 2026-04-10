package com.adags.hospital.domain.pricing;

import com.adags.hospital.domain.common.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "service_price_items")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ServicePriceItem extends BaseEntity {

    @Column(name = "item_id", length = 100)
    private String itemId;

    @Column(name = "product_code", length = 100)
    private String productCode;

    @Column(name = "product_name", nullable = false, length = 255)
    private String productName;

    /**
     * Brand / drug group within a type.
     * E.g. for type=PHARMACY → "PANADOL", "AZUMA"
     * For type=LAB → "HAEMATOLOGY", "BIOCHEMISTRY"
     */
    @Column(name = "classification", length = 200)
    private String classification;

    /**
     * Broad item type: PHARMACY | LAB | SURGERY
     */
    @Column(name = "type", nullable = false, length = 100)
    private String type;

    @Column(name = "category", length = 200)
    private String category;

    @Column(name = "price", nullable = false, precision = 12, scale = 2)
    private BigDecimal price;
}
