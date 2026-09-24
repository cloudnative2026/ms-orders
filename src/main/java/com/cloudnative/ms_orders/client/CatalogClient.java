package com.cloudnative.ms_orders.client;

import com.cloudnative.ms_orders.dto.ProductDTO;
import java.time.Duration;
import java.net.http.HttpClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.*;
import org.springframework.web.server.ResponseStatusException;

@Component
public class CatalogClient {
    private final RestClient client;
    public CatalogClient(@Value("${catalog.service.url}") String url) {
        var factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3)).build());
        factory.setReadTimeout(Duration.ofSeconds(5));
        client = RestClient.builder().baseUrl(url).requestFactory(factory).build();
    }
    public ProductDTO getProductById(long id, String token) {
        try {
            ProductDTO product = client.get().uri("/api/catalog/products/{id}", id)
                .headers(headers -> headers.setBearerAuth(token)).retrieve().body(ProductDTO.class);
            if (product == null || product.id() == null || product.id() != id || product.price() == null
                    || product.price().signum() < 0 || product.price().scale() > 2
                    || product.price().precision() - product.price().scale() > 17) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Respuesta de catalogo invalida.");
            }
            return product;
        } catch (HttpClientErrorException.NotFound exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Producto " + id + " no encontrado.");
        } catch (RestClientException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "No se pudo consultar el catalogo.");
        }
    }
}
