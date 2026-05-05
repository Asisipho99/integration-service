package com.shopping.integration_service.dto;

import lombok.Data;
import java.util.List;

@Data
public class OrderRequest {
    private Long customerId;
    private String customerEmail;
    private List<OrderItem> items;

    @Data
    public static class OrderItem {
        private Long productId;
        private String productName;
        private Integer quantity;
        private Double unitPrice;
    }
}
