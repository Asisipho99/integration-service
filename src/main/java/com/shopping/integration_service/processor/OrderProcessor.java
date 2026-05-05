package com.shopping.integration_service.processor;

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

    private final RestTemplate restTemplate;

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

        // Pass the response body (orderId etc) forward in the route
        exchange.getIn().setBody(response.getBody());
        exchange.getIn().setHeader("orderResponse", response.getBody());
    }
}