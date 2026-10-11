package com.shopping.integration_service.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class EmailRequest {
    private String to;
    private String subject;
    private String body;
    private String type; // REGISTRATION, ORDER_CONFIRMATION, STATUS_UPDATE

    // Template enrichment fields — populated by EmailProcessor
    private String customerName;
    private String orderId;
    private String orderStatus;
    private String productList;
    private Double totalAmount;
    private List<OrderItemLine> items;

    // One purchased line, rendered as a row in the ORDER_CONFIRMATION email.
    // Wire-compatible with com.shopping.email_service.dto.EmailRequest.OrderItemLine.
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class OrderItemLine {
        private String     productName;
        private Integer    quantity;
        private BigDecimal unitPrice;
        private BigDecimal lineTotal;
        private String     imageUrl;   // absolute https Cloudinary URL, or null
    }
}
