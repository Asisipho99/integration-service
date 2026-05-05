package com.shopping.integration_service.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class StockDeductRequest {
    private Long productId;
    private Integer quantity;
}
