package com.shopping.integration_service.processor;

import com.shopping.integration_service.client.ProductImageClient;
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

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class EmailProcessor implements Processor {

    private final RestTemplate restTemplate;
    private final ProductImageClient productImageClient;

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

    // Package-private so EmailProcessorTest can exercise the enrichment directly
    EmailRequest buildEmailRequest(OrderRequest order) {
        double total = order.getItems().stream()
                .mapToDouble(i -> i.getQuantity() * i.getUnitPrice())
                .sum();

        String productList = order.getItems().stream()
                .map(i -> i.getProductName() + " x" + i.getQuantity()
                        + " @ R" + i.getUnitPrice())
                .reduce("", (a, b) -> a.isEmpty() ? b : a + ", " + b);

        EmailRequest req = new EmailRequest();
        req.setTo(order.getCustomerEmail());
        req.setSubject("Order Confirmation - Your order has been placed!");
        req.setBody(buildEmailBody(order));
        req.setType("ORDER_CONFIRMATION");
        req.setCustomerName(order.getCustomerName());
        req.setOrderId(order.getOrderId() != null ? String.valueOf(order.getOrderId()) : null);
        req.setProductList(productList);
        req.setTotalAmount(total);
        req.setItems(buildItemLines(order));
        return req;
    }

    /**
     * One rendered line per ordered item, with the product image resolved from
     * the Product Service. Image resolution is decoration: when it fails the
     * lines are still built with a null imageUrl, so nothing here can throw
     * into the Camel route and turn a placed order into a 400.
     */
    private List<EmailRequest.OrderItemLine> buildItemLines(OrderRequest order) {
        Map<Long, String> imageUrls = resolveImageUrls(order);

        List<EmailRequest.OrderItemLine> lines = new ArrayList<>();
        for (OrderRequest.OrderItem item : order.getItems()) {
            BigDecimal unitPrice = item.getUnitPrice() != null
                    ? BigDecimal.valueOf(item.getUnitPrice())
                    : null;
            BigDecimal lineTotal = (unitPrice != null && item.getQuantity() != null)
                    ? unitPrice.multiply(BigDecimal.valueOf(item.getQuantity()))
                    : null;

            lines.add(new EmailRequest.OrderItemLine(
                    item.getProductName(),
                    item.getQuantity(),
                    unitPrice,
                    lineTotal,
                    imageUrls.get(item.getProductId())
            ));
        }
        return lines;
    }

    private Map<Long, String> resolveImageUrls(OrderRequest order) {
        try {
            Set<Long> productIds = new LinkedHashSet<>();
            for (OrderRequest.OrderItem item : order.getItems()) {
                if (item.getProductId() != null) {
                    productIds.add(item.getProductId());
                }
            }
            return productImageClient.fetchImageUrls(productIds);
        } catch (Exception e) {
            // Non-critical — the email still lists every item, just without images
            System.err.println("Could not resolve product images for order "
                    + order.getOrderId() + ": " + e.getMessage());
            return Collections.emptyMap();
        }
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
                    .append(" @ ").append(formatRand(item.getUnitPrice()))
                    .append(" = ").append(formatRand(itemTotal)).append("\n");
            total += itemTotal;
        }

        sb.append("\nTotal: ").append(formatRand(total));
        sb.append("\n\nYour order is being processed.");
        sb.append("\n\nThank you for shopping with us!");
        return sb.toString();
    }

    /**
     * Rand amounts as R 1,234.56. The locale is pinned so the plain-text part
     * matches the HTML the email service renders, whatever the JVM default is.
     */
    private static String formatRand(double amount) {
        return "R " + String.format(Locale.US, "%,.2f", amount);
    }
}
