package com.cloudnative.ms_orders.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.cloudnative.ms_orders.dto.ProductDTO;

@Component
public class CatalogClient {

    private static final Logger log = LoggerFactory.getLogger(CatalogClient.class);
    private final RestClient restClient;

    public CatalogClient(@Value("${catalog.service.url:http://localhost:8080}") String catalogServiceUrl) {
        this.restClient = RestClient.builder()
                .baseUrl(catalogServiceUrl)
                .build();
    }

    public ProductDTO getProductById(long productId) {
        try {
            return restClient.get()
                    .uri("/api/catalog/products/{id}", productId)
                    .retrieve()
                    .body(ProductDTO.class);
        } catch (Exception e) {
            log.warn("Could not retrieve product with ID {} from catalog service: {}", productId, e.getMessage());
            return null;
        }
    }
}
