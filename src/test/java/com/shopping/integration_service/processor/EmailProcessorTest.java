package com.shopping.integration_service.processor;

import com.shopping.integration_service.client.ProductImageClient;
import com.shopping.integration_service.dto.EmailRequest;
import com.shopping.integration_service.dto.OrderRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EmailProcessorTest {

    private static final String COFFEE_IMAGE =
            "https://res.cloudinary.com/dumw3ubjh/image/upload/v1791716616/shopping-products/rzokai6poputkmxaydxu.jpg";
    private static final String CAR_IMAGE =
            "https://res.cloudinary.com/dumw3ubjh/image/upload/v1791716616/shopping-products/qgg88bgokojs7thdcmwz.jpg";

    private ProductImageClient productImageClient;
    private EmailProcessor     processor;

    @BeforeEach
    void setUp() {
        productImageClient = mock(ProductImageClient.class);
        processor = new EmailProcessor(mock(RestTemplate.class), productImageClient);
    }

    @Test
    void buildsOneItemLinePerOrderedItemWithResolvedImages() {
        when(productImageClient.fetchImageUrls(any()))
                .thenReturn(Map.of(252L, COFFEE_IMAGE, 253L, CAR_IMAGE));

        EmailRequest req = processor.buildEmailRequest(order());

        assertThat(req.getItems()).hasSize(2);

        EmailRequest.OrderItemLine coffee = req.getItems().get(0);
        assertThat(coffee.getProductName()).isEqualTo("Rooibos Road Cold Pressed Ground Coffee");
        assertThat(coffee.getQuantity()).isEqualTo(2);
        assertThat(coffee.getUnitPrice()).isEqualByComparingTo("385.66");
        assertThat(coffee.getLineTotal()).isEqualByComparingTo("771.32");
        assertThat(coffee.getImageUrl()).isEqualTo(COFFEE_IMAGE);

        EmailRequest.OrderItemLine car = req.getItems().get(1);
        assertThat(car.getProductName()).isEqualTo("Playgrove Collaborative Remote Control Car");
        assertThat(car.getQuantity()).isEqualTo(1);
        assertThat(car.getUnitPrice()).isEqualByComparingTo("802.13");
        assertThat(car.getLineTotal()).isEqualByComparingTo("802.13");
        assertThat(car.getImageUrl()).isEqualTo(CAR_IMAGE);
    }

    @Test
    void lineTotalIsUnitPriceTimesQuantity() {
        when(productImageClient.fetchImageUrls(any())).thenReturn(Map.of());

        EmailRequest req = processor.buildEmailRequest(order());

        for (EmailRequest.OrderItemLine line : req.getItems()) {
            assertThat(line.getLineTotal()).isEqualByComparingTo(
                    line.getUnitPrice().multiply(BigDecimal.valueOf(line.getQuantity())));
        }
    }

    @Test
    void aFailingImageLookupStillProducesACompleteRequest() {
        when(productImageClient.fetchImageUrls(any()))
                .thenThrow(new RestClientException("product-service unreachable"));

        EmailRequest req = processor.buildEmailRequest(order());

        assertThat(req.getItems()).hasSize(2);
        assertThat(req.getItems()).allSatisfy(line -> {
            assertThat(line.getImageUrl()).isNull();
            assertThat(line.getProductName()).isNotBlank();
            assertThat(line.getLineTotal()).isNotNull();
        });
        assertThat(req.getTotalAmount()).isCloseTo(1573.45, within(0.001));
        assertThat(req.getProductList()).isEqualTo(
                "Rooibos Road Cold Pressed Ground Coffee x2 @ R385.66, "
                        + "Playgrove Collaborative Remote Control Car x1 @ R802.13");
        assertThat(req.getType()).isEqualTo("ORDER_CONFIRMATION");
        assertThat(req.getTo()).isEqualTo("thandi@example.com");
        assertThat(req.getCustomerName()).isEqualTo("Thandi Mokoena");
        assertThat(req.getOrderId()).isEqualTo("1042");
    }

    @Test
    void plainTextBodyListsEveryItemWithSpacedRandAmounts() {
        when(productImageClient.fetchImageUrls(any())).thenReturn(Map.of());

        EmailRequest req = processor.buildEmailRequest(order());

        assertThat(req.getBody())
                .contains("- Rooibos Road Cold Pressed Ground Coffee x2 @ R 385.66 = R 771.32")
                .contains("- Playgrove Collaborative Remote Control Car x1 @ R 802.13 = R 802.13")
                .contains("Total: R 1,573.45");
    }

    private static OrderRequest order() {
        OrderRequest order = new OrderRequest();
        order.setCustomerEmail("thandi@example.com");
        order.setCustomerName("Thandi Mokoena");
        order.setOrderId(1042L);
        order.setItems(List.of(
                item(252L, "Rooibos Road Cold Pressed Ground Coffee", 2, 385.66),
                item(253L, "Playgrove Collaborative Remote Control Car", 1, 802.13)
        ));
        return order;
    }

    private static OrderRequest.OrderItem item(Long productId, String name,
                                               Integer quantity, Double unitPrice) {
        OrderRequest.OrderItem item = new OrderRequest.OrderItem();
        item.setProductId(productId);
        item.setProductName(name);
        item.setQuantity(quantity);
        item.setUnitPrice(unitPrice);
        return item;
    }
}
