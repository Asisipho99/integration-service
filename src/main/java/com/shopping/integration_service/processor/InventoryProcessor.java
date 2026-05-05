package com.shopping.integration_service.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shopping.integration_service.dto.OrderRequest;
import com.shopping.integration_service.dto.StockDeductRequest;
import lombok.RequiredArgsConstructor;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
@RequiredArgsConstructor
public class InventoryProcessor implements Processor {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${services.inventory-service}")
    private String inventoryServiceUrl;

    @Override
    public void process(Exchange exchange) throws Exception {
        OrderRequest order = exchange.getIn().getBody(OrderRequest.class);

        // Deduct stock for each item in the order
        for (OrderRequest.OrderItem item : order.getItems()) {
            StockDeductRequest deductRequest =
                    new StockDeductRequest(item.getProductId(), item.getQuantity());

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<StockDeductRequest> request =
                    new HttpEntity<>(deductRequest, headers);

            try {
                restTemplate.postForEntity(
                        inventoryServiceUrl + "/api/inventory/deduct",
                        request,
                        String.class
                );
            } catch (Exception e) {
                // If stock deduction fails, throw exception to trigger rollback
                throw new RuntimeException(
                        "Stock deduction failed for product "
                                + item.getProductId() + ": " + e.getMessage());
            }
        }

        // Pass the order along to the next step in the route
        exchange.getIn().setBody(order);
    }
}
