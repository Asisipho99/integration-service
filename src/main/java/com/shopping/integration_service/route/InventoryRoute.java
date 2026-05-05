package com.shopping.integration_service.route;

import org.apache.camel.builder.RouteBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class InventoryRoute extends RouteBuilder {

    @Value("${services.inventory-service}")
    private String inventoryServiceUrl;

    @Override
    public void configure() throws Exception {

        rest("/camel/stock")
                .get("/{productId}")
                .to("direct:checkStock");

        from("direct:checkStock")
                .routeId("check-stock-route")
                .log("🔍 Checking stock for product: ${header.productId}")
                // Remove all Camel internal headers to prevent method confusion
                .removeHeaders("CamelHttp*")
                // Explicitly set HTTP method to GET
                .setHeader("CamelHttpMethod", constant("GET"))
                .toD(inventoryServiceUrl
                        + "/api/inventory/product/${header.productId}"
                        + "?bridgeEndpoint=true&throwExceptionOnFailure=false")
                .log("✅ Stock check complete — response: ${body}");
    }
}