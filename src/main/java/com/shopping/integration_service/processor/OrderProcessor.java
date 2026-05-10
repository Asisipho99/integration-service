package com.shopping.integration_service.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shopping.integration_service.dto.OrderRequest;
import lombok.RequiredArgsConstructor;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
@RequiredArgsConstructor
public class OrderProcessor implements Processor {

    private final RestTemplate  restTemplate;
    private final ObjectMapper  objectMapper;

    @Value("${services.order-service}")
    private String orderServiceUrl;

    @Override
    public void process(Exchange exchange) throws Exception {
        OrderRequest order = exchange.getIn().getBody(OrderRequest.class);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<OrderRequest> request = new HttpEntity<>(order, headers);

        // Save the order in the Order Service
        ResponseEntity<String> response = restTemplate.postForEntity(
                orderServiceUrl + "/api/orders",
                request,
                String.class
        );

        // Extract the orderId from the response and store it on the original
        // OrderRequest so EmailProcessor can include it in the confirmation email
        try {
            JsonNode json = objectMapper.readTree(response.getBody());
            if (json.has("orderId")) {
                order.setOrderId(json.get("orderId").asLong());
                // Update the exchange property so EmailProcessor gets the enriched order
                exchange.setProperty("originalOrder", order);
            }
        } catch (Exception e) {
            // Non-critical — email will still send, just without the order ID
            System.err.println("Could not parse orderId from order-service response: "
                    + e.getMessage());
        }

        exchange.getIn().setBody(response.getBody());
        exchange.getIn().setHeader("orderResponse", response.getBody());
    }
}
