package com.shopping.integration_service.route;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shopping.integration_service.dto.OrderRequest;
import com.shopping.integration_service.processor.*;
import lombok.RequiredArgsConstructor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.rest.RestBindingMode;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OrderIntegrationRoute extends RouteBuilder {

    private final InventoryProcessor inventoryProcessor;
    private final OrderProcessor     orderProcessor;
    private final EmailProcessor     emailProcessor;
    private final ObjectMapper       objectMapper;

    @Override
    public void configure() throws Exception {

        // ── Global error handler ──────────────────────────────────────
        onException(Exception.class)
                .handled(true)
                .setHeader("CamelHttpResponseCode", constant(400))
                .setBody(simple("{\"error\": \"${exception.message}\", \"cause\": \"${exception.class.name}\"}"))
                .log("ERROR in order route: ${exception.class.name}: ${exception.message}\n${exception.stackTrace}");

        // ── REST configuration ────────────────────────────────────────
        restConfiguration()
                .component("platform-http")
                .bindingMode(RestBindingMode.off) // ← turn off auto binding
                .dataFormatProperty("prettyPrint", "true");

        // ── Expose POST /api/orders ──────────────────────────────────
        rest("/api/orders")
                .post()
                .consumes("application/json")
                .produces("application/json")
                .to("direct:processOrder");

        // ── Order Processing Route ────────────────────────────────────
        from("direct:processOrder")
                .routeId("order-integration-route")

                // Manually deserialize JSON body into OrderRequest
                .process(exchange -> {
                    String json = exchange.getIn().getBody(String.class);
                    OrderRequest order = objectMapper.readValue(json, OrderRequest.class);
                    exchange.getIn().setBody(order);
                    // Save the original order so EmailProcessor can access it after
                    // OrderProcessor replaces the body with the order-service response
                    exchange.setProperty("originalOrder", order);
                })

                .log("📦 New order received for customer: ${body.customerId}")

                .log("🔄 Step 1: Deducting stock from Inventory Service...")
                .process(inventoryProcessor)
                .log("✅ Stock deducted successfully")

                .log("🔄 Step 2: Saving order in Order Service...")
                .process(orderProcessor)
                .log("✅ Order saved successfully")

                .log("🔄 Step 3: Sending order confirmation email...")
                .process(emailProcessor)
                .log("✅ Confirmation email sent")

                .log("🎉 Order integration complete!")
                .setHeader("CamelHttpResponseCode", constant(201))
                .setBody(simple("{\"message\": \"Order placed successfully\"}"));
    }
}
