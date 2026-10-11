package com.shopping.integration_service.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Resolves product image URLs from the Product Service.
 *
 * product-service exposes no batch-by-ids endpoint, so one
 * GET /api/products/{id} is issued per DISTINCT product id — orders have very
 * few lines. GET /api/products/** is permitAll, so no auth header is sent.
 *
 * Image lookups are decoration, never a reason to fail an order: every call is
 * individually guarded, a failing id simply maps to null, and this method
 * never throws.
 */
@Component
@RequiredArgsConstructor
public class ProductImageClient {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${services.product-service}")
    private String productServiceUrl;

    /**
     * @param productIds ids to resolve; nulls and duplicates are ignored
     * @return productId → imageUrl, with a null value when it could not be resolved
     */
    public Map<Long, String> fetchImageUrls(Collection<Long> productIds) {
        Map<Long, String> imageUrls = new LinkedHashMap<>();
        if (productIds == null) {
            return imageUrls;
        }

        for (Long productId : productIds) {
            if (productId == null || imageUrls.containsKey(productId)) {
                continue;
            }
            imageUrls.put(productId, fetchImageUrl(productId));
        }
        return imageUrls;
    }

    private String fetchImageUrl(Long productId) {
        try {
            ResponseEntity<String> response = restTemplate.getForEntity(
                    productServiceUrl + "/api/products/" + productId,
                    String.class
            );
            JsonNode imageUrl = objectMapper.readTree(response.getBody())
                    .path("imageUrl");
            // A missing field and an explicit JSON null both mean "no image"
            return imageUrl.isMissingNode() || imageUrl.isNull()
                    ? null
                    : imageUrl.asText(null);
        } catch (Exception e) {
            // Non-critical — the email row renders without an image
            System.err.println("Could not resolve image for product "
                    + productId + ": " + e.getMessage());
            return null;
        }
    }
}
