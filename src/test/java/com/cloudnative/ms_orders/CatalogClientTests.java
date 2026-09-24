package com.cloudnative.ms_orders;

import com.cloudnative.ms_orders.client.CatalogClient;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.*;
import org.springframework.web.server.ResponseStatusException;
import static org.assertj.core.api.Assertions.*;

class CatalogClientTests {
    HttpServer server;
    CatalogClient client;
    int status;
    String body;
    AtomicReference<String> authorization = new AtomicReference<>();
    static final String PRODUCT = """
        {"id":1,"name":"Cafe","price":10.25,"stock":5,"active":true}
        """;
    @BeforeEach void setup() throws Exception {
        status = 200;
        body = PRODUCT;
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/catalog/products/1", exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            var bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            try (var output = exchange.getResponseBody()) { output.write(bytes); }
        });
        server.start();
        client = new CatalogClient("http://127.0.0.1:" + server.getAddress().getPort());
    }
    @AfterEach void close() { server.stop(0); }
    @Test void forwardsBearerAndPreservesDecimals() {
        assertThat(client.getProductById(1, "access-token").price()).isEqualByComparingTo("10.25");
        assertThat(authorization.get()).isEqualTo("Bearer access-token");
    }
    @Test void distinguishesMissingProductFromUpstreamFailure() {
        for (int code : new int[]{404, 401, 403, 500}) {
            status = code;
            assertThatThrownBy(() -> client.getProductById(1, "token"))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                    error -> assertThat(error.getStatusCode().value()).isEqualTo(code == 404 ? 400 : 502));
        }
    }
    @Test void rejectsMalformedOrInvalidCatalogResponses() {
        for (String invalid : new String[]{"{}", "broken", PRODUCT.replace("10.25", "-1"),
                PRODUCT.replace("10.25", "1.234"), PRODUCT.replace("\"id\":1", "\"id\":2")}) {
            body = invalid;
            assertThatThrownBy(() -> client.getProductById(1, "token"))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                    error -> assertThat(error.getStatusCode().value()).isEqualTo(502));
        }
    }
    @Test void unavailableCatalogIs502() {
        server.stop(0);
        assertThatThrownBy(() -> client.getProductById(1, "token"))
            .isInstanceOfSatisfying(ResponseStatusException.class,
                error -> assertThat(error.getStatusCode().value()).isEqualTo(502));
    }
}
