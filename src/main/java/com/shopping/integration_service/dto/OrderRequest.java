package com.shopping.integration_service.dto;

import lombok.Data;
import java.util.List;

@Data
public class OrderRequest {
    private Long   customerId;
    private String customerEmail;
    private String customerName;  // populated from auth token or frontend
    private Long   orderId;       // populated by OrderProcessor after saving
    private List<OrderItem> items;

    @Data
    public static class OrderItem {
        private Long    productId;
        private String  productName;
        private Integer quantity;
        private Double  unitPrice;
    }
}
