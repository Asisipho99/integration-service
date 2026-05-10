package com.shopping.integration_service.route;

import org.apache.camel.builder.RouteBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class RecommendationRoute extends RouteBuilder {

    @Value("${services.ai-service}")
    private String aiServiceUrl;

    @Override
    public void configure() throws Exception {

        // GET /camel/recommendations/{customerId}
        rest("/camel/recommendations")
                .get("/{customerId}")
                .to("direct:getRecommendations");

        from("direct:getRecommendations")
                .routeId("recommendation-route")
                .log("🤖 Getting recommendations for customer: "
                        + "${header.customerId}")
                .removeHeaders("CamelHttp*")
                .setHeader("CamelHttpMethod", constant("GET"))
                .toD(aiServiceUrl
                        + "/api/recommendations/${header.customerId}"
                        + "?bridgeEndpoint=true"
                        + "&throwExceptionOnFailure=false")
                .log("✅ Recommendations retrieved");
    }
}