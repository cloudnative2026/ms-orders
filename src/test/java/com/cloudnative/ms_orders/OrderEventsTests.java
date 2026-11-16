package com.cloudnative.ms_orders;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import com.cloudnative.ms_orders.client.CatalogClient;
import com.cloudnative.ms_orders.client.NotificationClient;
import com.cloudnative.ms_orders.dto.ProductDTO;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.kafka.KafkaException;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OrderEventsTests {
    @Autowired MockMvc mvc;
    @Autowired JsonMapper json;
    @MockitoBean JwtDecoder decoder;
    @MockitoBean CatalogClient catalog;
    @MockitoBean NotificationClient notifications;
    @MockitoBean KafkaTemplate<String, String> kafka;
    static final String BODY = """
        {"customerId":101,"items":[{"productId":1,"quantity":2}]}
        """;

    @BeforeEach void setup() {
        when(decoder.decode("Admin")).thenReturn(Jwt.withTokenValue("Admin").header("alg", "RS256")
            .issuer("https://issuer.example").subject("admin-sub").claim("name", "Ana Admin")
            .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(300))
            .claim("roles", List.of("Admin")).build());
        when(catalog.getProductById(eq(1L), anyString())).thenReturn(
            new ProductDTO(1L, "Cafe", null, new BigDecimal("10.25"), 20, null, true));
        when(kafka.send(anyString(), anyString(), anyString())).thenReturn(new CompletableFuture<>());
    }

    List<JsonNode> sent(String topic) {
        var topics = ArgumentCaptor.forClass(String.class);
        var values = ArgumentCaptor.forClass(String.class);
        verify(kafka, atLeast(0)).send(topics.capture(), anyString(), values.capture());
        var result = new java.util.ArrayList<JsonNode>();
        for (int i = 0; i < topics.getAllValues().size(); i++)
            if (topics.getAllValues().get(i).equals(topic)) result.add(json.readTree(values.getAllValues().get(i)));
        return result;
    }

    @Test void everyCommittedChangeIsPublishedOnceToBothTopics() throws Exception {
        String location = mvc.perform(post("/api/v1/orders").header("Authorization", "Bearer Admin")
                .header("X-Forwarded-For", "203.0.113.7, 10.0.0.1").header("User-Agent", "JUnit")
                .contentType("application/json").content(BODY))
            .andExpect(status().isCreated()).andReturn().getResponse().getHeader("Location");
        mvc.perform(put(location).header("Authorization", "Bearer Admin")
            .contentType("application/json").content(BODY.replace(":2", ":3"))).andExpect(status().isOk());
        for (String state : List.of("ACEPTADO", "EN_PREPARACION", "DESPACHADO", "ENTREGADO", "ENTREGADO"))
            mvc.perform(patch(location + "/status").param("status", state).header("Authorization", "Bearer Admin"))
                .andExpect(status().isOk());
        // Rejected transitions are rolled back and never published.
        mvc.perform(patch(location + "/status").param("status", "CANCELADO").header("Authorization", "Bearer Admin"))
            .andExpect(status().isConflict());
        mvc.perform(delete(location).header("Authorization", "Bearer Admin")).andExpect(status().isNoContent());

        var orders = sent("orders.events");
        assertThat(orders).extracting(n -> n.get("type").asString()).containsExactly("OrderCreated", "OrderUpdated",
            "OrderAccepted", "OrderPreparing", "OrderDispatched", "OrderDelivered", "OrderDeleted");
        var created = orders.getFirst();
        assertThat(created.get("status").asString()).isEqualTo("CREADO");
        assertThat(created.get("totalAmount").decimalValue()).isEqualByComparingTo("20.50");
        assertThat(created.get("items").get(0).get("quantity").asInt()).isEqualTo(2);
        assertThat(orders.get(1).get("totalAmount").decimalValue()).isEqualByComparingTo("30.75");
        assertThat(created.get("createdAt").asString()).endsWith("Z");

        var audit = sent("audit.timeline");
        assertThat(audit).hasSize(7);
        var entry = audit.getFirst();
        assertThat(entry.get("eventId").asString()).isEqualTo(created.get("eventId").asString());
        assertThat(entry.get("actorSubject").asString()).isEqualTo("admin-sub");
        assertThat(entry.get("actorName").asString()).isEqualTo("Ana Admin");
        assertThat(entry.get("actorRoles").get(0).asString()).isEqualTo("Admin");
        assertThat(entry.get("sourceIp").asString()).isEqualTo("203.0.113.7");
        assertThat(entry.get("userAgent").asString()).isEqualTo("JUnit");
        assertThat(audit.get(2).get("detail").asString()).isEqualTo("CREADO -> ACEPTADO");
    }

    @Test void brokerFailureDoesNotFailTheRequest() throws Exception {
        when(kafka.send(anyString(), anyString(), anyString())).thenThrow(new KafkaException("broker down"));
        mvc.perform(post("/api/v1/orders").header("Authorization", "Bearer Admin")
            .contentType("application/json").content(BODY)).andExpect(status().isCreated());
    }
}
