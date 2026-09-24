package com.cloudnative.ms_orders;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import com.cloudnative.ms_orders.client.CatalogClient;
import com.cloudnative.ms_orders.dto.ProductDTO;
import com.cloudnative.ms_orders.repository.OrderRepository;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MsOrdersApplicationTests {
    @Autowired MockMvc mvc;
    @Autowired OrderRepository orders;
    @MockitoBean JwtDecoder decoder;
    @MockitoBean CatalogClient catalog;
    static final String URL = "/api/v1/orders";
    static final String BODY = """
        {"customerId":101,"items":[{"productId":1,"quantity":2}]}
        """;
    @BeforeEach void setup() {
        orders.deleteAll();
        for (String role : List.of("Admin", "Operador", "Cliente", "Auditor")) token(role, role, role);
        token("other", "Cliente", "other");
        when(decoder.decode("invalid")).thenThrow(new BadJwtException("invalid"));
        when(catalog.getProductById(eq(1L), anyString())).thenReturn(
            new ProductDTO(1L, "Cafe", null, new BigDecimal("10.25"), 20, null, true));
    }
    void token(String token, String role, String subject) {
        when(decoder.decode(token)).thenReturn(Jwt.withTokenValue(token).header("alg", "RS256")
            .issuer("https://issuer.example").subject(subject)
            .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(300))
            .claim("roles", List.of(role)).build());
    }
    String create() throws Exception {
        return mvc.perform(post(URL).header("Authorization", "Bearer Admin")
            .contentType("application/json").content(BODY))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.totalAmount").value(20.50))
            .andExpect(jsonPath("$.status").value("CREADO"))
            .andExpect(jsonPath("$.createdAt").isNotEmpty())
            .andReturn().getResponse().getHeader("Location");
    }
    @Test void fullLifecycleAndHistoricalPrices() throws Exception {
        String location = create();
        verify(catalog).getProductById(1L, "Admin");
        clearInvocations(catalog);
        mvc.perform(get(location).header("Authorization", "Bearer Operador"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].unitPrice").value(10.25));
        verifyNoInteractions(catalog);
        mvc.perform(put(location).header("Authorization", "Bearer Operador")
            .contentType("application/json").content(BODY.replace(":2", ":3")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.totalAmount").value(30.75));
        for (String state : List.of("ACEPTADO", "EN_PREPARACION", "DESPACHADO", "ENTREGADO")) {
            mvc.perform(patch(location + "/status").param("status", state).header("Authorization", "Bearer Operador"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value(state));
        }
        mvc.perform(put(location).header("Authorization", "Bearer Admin").contentType("application/json").content(BODY))
            .andExpect(status().isConflict());
        mvc.perform(delete(location).header("Authorization", "Bearer Admin")).andExpect(status().isNoContent());
        mvc.perform(get(location).header("Authorization", "Bearer Admin")).andExpect(status().isNotFound());
    }
    @Test void invalidTransitionsAndIdempotentStatus() throws Exception {
        String location = create();
        mvc.perform(patch(location + "/status").param("status", "DESPACHADO").header("Authorization", "Bearer Admin"))
            .andExpect(status().isConflict());
        for (int i = 0; i < 2; i++) mvc.perform(patch(location + "/status").param("status", "CANCELADO")
            .header("Authorization", "Bearer Admin")).andExpect(status().isOk());
        mvc.perform(patch(location + "/status").param("status", "ACEPTADO").header("Authorization", "Bearer Admin"))
            .andExpect(status().isConflict());
        mvc.perform(patch(location + "/status").param("status", "UNKNOWN").header("Authorization", "Bearer Admin"))
            .andExpect(status().isBadRequest());
    }
    @Test void validationAndServerControlledFields() throws Exception {
        for (String body : List.of("{}", "{", BODY.replace(":2", ":0"), BODY.replace(":1,", ":-1,"),
                "{\"customerId\":101,\"items\":[null]}", "{\"customerId\":101,\"items\":[]}")) {
            mvc.perform(post(URL).header("Authorization", "Bearer Admin").contentType("application/json").content(body))
                .andExpect(status().isBadRequest());
        }
        String malicious = BODY.replace("\"customerId\"", "\"id\":999,\"status\":\"ENTREGADO\",\"totalAmount\":1,\"customerId\"");
        mvc.perform(post(URL).header("Authorization", "Bearer Admin").contentType("application/json").content(malicious))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("CREADO"))
            .andExpect(jsonPath("$.totalAmount").value(20.50));
    }
    @Test void rolePermissionsAndOwnership() throws Exception {
        mvc.perform(get(URL)).andExpect(status().isUnauthorized());
        mvc.perform(get(URL).header("Authorization", "Bearer invalid")).andExpect(status().isUnauthorized());
        mvc.perform(get(URL).header("Authorization", "Bearer Auditor")).andExpect(status().isForbidden());
        mvc.perform(post(URL).header("Authorization", "Bearer Auditor").contentType("application/json").content(BODY))
            .andExpect(status().isForbidden());
        String location = create();
        mvc.perform(delete(location).header("Authorization", "Bearer Operador")).andExpect(status().isForbidden());
        mvc.perform(patch(location + "/status").param("status", "ACEPTADO").header("Authorization", "Bearer Cliente"))
            .andExpect(status().isForbidden());
        mvc.perform(get(location).header("Authorization", "Bearer Cliente")).andExpect(status().isNotFound());
        mvc.perform(get(URL).header("Authorization", "Bearer Cliente"))
            .andExpect(status().isOk()).andExpect(content().json("[]"));
        token("owner", "Cliente", "Admin");
        mvc.perform(get(location).header("Authorization", "Bearer owner")).andExpect(status().isOk());
        mvc.perform(get(location).header("Authorization", "Bearer other")).andExpect(status().isNotFound());
    }
    @Test void rejectsInactiveDuplicateAndUnavailableProductsWithoutSaving() throws Exception {
        when(catalog.getProductById(1L, "Admin")).thenReturn(new ProductDTO(1L, "Cafe", null, BigDecimal.ONE, 2, null, false));
        mvc.perform(post(URL).header("Authorization", "Bearer Admin").contentType("application/json").content(BODY))
            .andExpect(status().isBadRequest());
        assertThat(orders.count()).isZero();
        when(catalog.getProductById(1L, "Admin")).thenThrow(new org.springframework.web.server.ResponseStatusException(
            org.springframework.http.HttpStatus.BAD_GATEWAY, "Catalogo no disponible"));
        mvc.perform(post(URL).header("Authorization", "Bearer Admin").contentType("application/json").content(BODY))
            .andExpect(status().isBadGateway());
        assertThat(orders.count()).isZero();
    }
    @Test void missingOrdersDocsAndCors() throws Exception {
        mvc.perform(get(URL + "/99999").header("Authorization", "Bearer Admin")).andExpect(status().isNotFound());
        mvc.perform(delete(URL + "/99999").header("Authorization", "Bearer Admin")).andExpect(status().isNotFound());
        mvc.perform(patch(URL + "/99999/status").param("status", "ACEPTADO").header("Authorization", "Bearer Admin"))
            .andExpect(status().isNotFound());
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
        mvc.perform(options(URL).header("Origin", "http://localhost:5173")
            .header("Access-Control-Request-Method", "POST").header("Access-Control-Request-Headers", "authorization,content-type"))
            .andExpect(status().isOk()).andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));
    }
    @Test void duplicateProductsAreRejected() throws Exception {
        String body = """
            {"customerId":101,"items":[{"productId":1,"quantity":2},{"productId":1,"quantity":1}]}
            """;
        mvc.perform(post(URL).header("Authorization", "Bearer Admin").contentType("application/json").content(body))
            .andExpect(status().isBadRequest());
        assertThat(orders.count()).isZero();
    }
    @Test void concurrentUpdatesCannotOverwriteEachOther() throws Exception {
        create();
        var first = orders.findAll().getFirst();
        var stale = orders.findById(first.getId()).orElseThrow();
        first.setStatus(com.cloudnative.ms_orders.model.OrderStatus.ACEPTADO);
        orders.saveAndFlush(first);
        stale.setStatus(com.cloudnative.ms_orders.model.OrderStatus.CANCELADO);
        assertThatThrownBy(() -> orders.saveAndFlush(stale))
            .isInstanceOf(org.springframework.dao.OptimisticLockingFailureException.class);
        assertThat(orders.findById(first.getId()).orElseThrow().getStatus())
            .isEqualTo(com.cloudnative.ms_orders.model.OrderStatus.ACEPTADO);
    }
    @Test void customerCanCreateAndOnlyReadOwnOrders() throws Exception {
        String location = mvc.perform(post(URL).header("Authorization", "Bearer Cliente")
            .contentType("application/json").content(BODY))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.totalAmount").value(20.50))
            .andReturn().getResponse().getHeader("Location");
        mvc.perform(get(location).header("Authorization", "Bearer Cliente")).andExpect(status().isOk());
        mvc.perform(get(URL).header("Authorization", "Bearer Cliente")).andExpect(jsonPath("$.length()").value(1));
        mvc.perform(get(location).header("Authorization", "Bearer other")).andExpect(status().isNotFound());
        mvc.perform(get(URL).header("Authorization", "Bearer other")).andExpect(content().json("[]"));
        mvc.perform(get(URL).header("Authorization", "Bearer Admin")).andExpect(jsonPath("$.length()").value(1));
        mvc.perform(put(location).header("Authorization", "Bearer Cliente").contentType("application/json").content(BODY)).andExpect(status().isForbidden());
        mvc.perform(delete(location).header("Authorization", "Bearer Cliente")).andExpect(status().isForbidden());
    }
    @Test void missingSubjectIsRejected() throws Exception {
        when(decoder.decode("no-subject")).thenReturn(Jwt.withTokenValue("no-subject")
            .header("alg", "RS256").issuer("https://issuer.example").claim("roles", List.of("Admin")).build());
        mvc.perform(get(URL).header("Authorization", "Bearer no-subject")).andExpect(status().isUnauthorized());
    }
}
