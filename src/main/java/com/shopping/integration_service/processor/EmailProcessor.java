package com.shopping.integration_service.processor;

import com.shopping.integration_service.dto.EmailRequest;
import com.shopping.integration_service.dto.OrderRequest;
import lombok.RequiredArgsConstructor;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
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

        EmailRequest emailRequest = buildEmailRequest(order);

        // sendWithRetry is @Retryable — Spring AOP handles the retry loop
        sendWithRetry(emailRequest);

        exchange.getIn().setBody(order);
    }

    /**
     * Attempts to send the email up to 3 times with exponential backoff:
     *   attempt 1 — immediate
     *   attempt 2 — 2 seconds later
     *   attempt 3 — 4 seconds later
     *
     * Retries on any RestClientException (connection refused, timeout, 5xx).
     */
    @Retryable(
        retryFor  = RestClientException.class,
        maxAttempts = 3,
        backoff   = @Backoff(delay = 2000, multiplier = 2.0)
    )
    public void sendWithRetry(EmailRequest emailRequest) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<EmailRequest> request = new HttpEntity<>(emailRequest, headers);

        ResponseEntity<String> response = restTemplate.postForEntity(
                emailServiceUrl + "/api/email/send",
                request,
                String.class
        );

        if (!response.getStatusCode().is2xxSuccessful()) {
            // Force a retry on non-2xx responses
            throw new RestClientException(
                    "Email service returned: " + response.getStatusCode());
        }
    }

    /**
     * Called after all 3 attempts have failed.
     * Logs the failure but does NOT fail the order — email is non-critical.
     */
    @Recover
    public void recoverFromEmailFailure(RestClientException e,
                                        EmailRequest emailRequest) {
        System.err.println("⚠️ Email delivery failed after 3 attempts for: "
                + emailRequest.getTo()
                + " — " + e.getMessage());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private EmailRequest buildEmailRequest(OrderRequest order) {
        double total = order.getItems().stream()
                .mapToDouble(i -> i.getQuantity() * i.getUnitPrice())
                .sum();

        String productList = order.getItems().stream()
                .map(i -> i.getProductName() + " x" + i.getQuantity()
                        + " @ R" + i.getUnitPrice())
                .reduce("", (a, b) -> a.isEmpty() ? b : a + ", " + b);

        EmailRequest req = new EmailRequest(
                order.getCustomerEmail(),
                "Order Confirmation - Your order has been placed!",
                buildEmailBody(order),
                "ORDER_CONFIRMATION"
        );
        req.setCustomerName(order.getCustomerName());
        req.setOrderId(String.valueOf(order.getOrderId()));
        req.setProductList(productList);
        req.setTotalAmount(total);
        return req;
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
