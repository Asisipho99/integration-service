package com.shopping.integration_service.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

@Configuration
@EnableRetry
public class CamelConfig {

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }

    /**
     * Short-timeout client for non-critical decoration calls.
     *
     * The shared {@link #restTemplate()} has no timeouts, which is tolerable
     * for the calls that must succeed - creating the order, deducting stock,
     * sending the email - because failing those is worse than waiting. It is
     * not tolerable for product image lookups, which happen inline on the
     * synchronous checkout path and are purely cosmetic. Without a bound, a
     * cold-starting or wedged product-service would hold a customer's
     * checkout open indefinitely to decide whether a thumbnail appears in an
     * email.
     *
     * Deliberately a separate bean rather than timeouts on the shared one, so
     * this cannot shorten the deadline on the order-creation path as a side
     * effect.
     */
    @Bean("productImageRestTemplate")
    public RestTemplate productImageRestTemplate(RestTemplateBuilder builder) {
        return builder
                .connectTimeout(Duration.ofSeconds(2))
                .readTimeout(Duration.ofSeconds(3))
                .build();
    }

    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }
}
