package com.shopping.integration_service.processor;

import com.shopping.integration_service.dto.EmailRequest;
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
public class EmailProcessor implements Processor {

    private final RestTemplate restTemplate;

    @Value("${services.email-service}")
    private String emailServiceUrl;

    @Override
    public void process(Exchange exchange) throws Exception {
        // OrderProcessor replaces the body with the order-service response,
        // so retrieve the original OrderRequest from the exchange property
        OrderRequest order = exchange.getProperty("originalOrder", OrderRequest.class);
        if (order == null) {
            order = exchange.getIn().getBody(OrderRequest.class);
        }

        // Build the order confirmation email
        String subject = "Order Confirmation - Your order has been placed!";
        String body    = buildEmailBody(order);

        EmailRequest emailRequest = new EmailRequest(
                order.getCustomerEmail(),
                subject,
                body,
                "ORDER_CONFIRMATION"
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<EmailRequest> request = new HttpEntity<>(emailRequest, headers);

        try {
            restTemplate.postForEntity(
                    emailServiceUrl + "/api/email/send",
                    request,
                    String.class
            );
        } catch (Exception e) {
            // Log the error but don't fail the order if email fails
            System.err.println("Email notification failed: " + e.getMessage());
        }

        exchange.getIn().setBody(order);
    }

    private String buildEmailBody(OrderRequest order) {
        StringBuilder sb = new StringBuilder();
        sb.append("Dear Customer,\n\n");
        sb.append("Thank you for your order! Here are your order details:\n\n");

        double total = 0;
        for (OrderRequest.OrderItem item : order.getItems()) {
            double itemTotal = item.getQuantity() * item.getUnitPrice();
            sb.append("- ").append(item.getProductName())
                    .append(" x").append(item.getQuantity())
                    .append(" @ R").append(item.getUnitPrice())
                    .append(" = R").append(itemTotal).append("\n");
            total += itemTotal;
        }

        sb.append("\nTotal: R").append(total);
        sb.append("\n\nYour order is being processed.");
        sb.append("\n\nThank you for shopping with us!");
        return sb.toString();
    }
}