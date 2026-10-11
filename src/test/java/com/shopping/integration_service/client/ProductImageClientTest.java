package com.shopping.integration_service.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProductImageClientTest {

    private static final String BASE_URL = "http://localhost:9999";
    private static final String IMAGE_URL =
            "https://res.cloudinary.com/dumw3ubjh/image/upload/v1791716616/shopping-products/rzokai6poputkmxaydxu.jpg";

    private RestTemplate       restTemplate;
    private ProductImageClient client;

    @BeforeEach
    void setUp() {
        restTemplate = mock(RestTemplate.class);
        client = new ProductImageClient(restTemplate, new ObjectMapper());
        ReflectionTestUtils.setField(client, "productServiceUrl", BASE_URL);
    }

    @Test
    void returnsTheImageUrlFromTheProductResponse() {
        stubProduct(252L, "{\"productId\":252,\"name\":\"Rooibos Road Cold Pressed Ground Coffee\","
                + "\"price\":385.66,\"imageUrl\":\"" + IMAGE_URL + "\"}");

        Map<Long, String> imageUrls = client.fetchImageUrls(List.of(252L));

        assertThat(imageUrls).containsExactly(Map.entry(252L, IMAGE_URL));
    }

    @Test
    void fetchesEachDistinctProductIdExactlyOnce() {
        stubProduct(252L, "{\"productId\":252,\"imageUrl\":\"" + IMAGE_URL + "\"}");
        stubProduct(253L, "{\"productId\":253,\"imageUrl\":\"" + IMAGE_URL + "\"}");

        Map<Long, String> imageUrls = client.fetchImageUrls(List.of(252L, 253L, 252L));

        assertThat(imageUrls).containsOnlyKeys(252L, 253L);
        verify(restTemplate, times(1)).getForEntity(eq(url(252L)), eq(String.class));
        verify(restTemplate, times(1)).getForEntity(eq(url(253L)), eq(String.class));
    }

    @Test
    void storesNullAndDoesNotThrowWhenTheLookupFails() {
        when(restTemplate.getForEntity(url(999L), String.class))
                .thenThrow(new RestClientException("404 Not Found"));

        Map<Long, String> imageUrls = client.fetchImageUrls(List.of(999L));

        assertThat(imageUrls).containsOnlyKeys(999L);
        assertThat(imageUrls.get(999L)).isNull();
    }

    @Test
    void mapsAResponseWithoutAnImageUrlFieldToNull() {
        stubProduct(254L, "{\"productId\":254,\"name\":\"No picture\",\"price\":10.00}");

        Map<Long, String> imageUrls = client.fetchImageUrls(List.of(254L));

        assertThat(imageUrls.get(254L)).isNull();
    }

    @Test
    void ignoresNullIdsAndANullCollection() {
        assertThat(client.fetchImageUrls(null)).isEmpty();
        assertThat(client.fetchImageUrls(List.of())).isEmpty();
    }

    private void stubProduct(Long productId, String body) {
        when(restTemplate.getForEntity(url(productId), String.class))
                .thenReturn(new ResponseEntity<>(body, HttpStatus.OK));
    }

    private static String url(Long productId) {
        return BASE_URL + "/api/products/" + productId;
    }
}
