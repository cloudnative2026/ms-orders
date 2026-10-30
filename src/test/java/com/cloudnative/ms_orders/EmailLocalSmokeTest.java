package com.cloudnative.ms_orders;

import com.cloudnative.ms_orders.client.*;
import com.cloudnative.ms_orders.dto.ProductDTO;
import com.cloudnative.ms_orders.repository.StatusEmailRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@EnabledIfEnvironmentVariable(named="RUN_LOCAL_MAIL_SMOKE", matches="true")
@SpringBootTest(properties={
    "spring.datasource.url=jdbc:h2:mem:email-smoke;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
    "app.email.worker-enabled=true", "app.email.dispatch-delay=1000",
    "spring.rabbitmq.listener.simple.auto-startup=true",
    "spring.rabbitmq.host=rabbitmq", "spring.rabbitmq.virtual-host=codex-mail-smoke",
    "spring.mail.host=mailpit", "spring.mail.port=1025"
})
@AutoConfigureMockMvc @ActiveProfiles("test")
class EmailLocalSmokeTest {
    @Autowired MockMvc mvc;
    @Autowired StatusEmailRepository emails;
    @MockitoBean JwtDecoder decoder;
    @MockitoBean CatalogClient catalog;
    @MockitoBean NotificationClient notifications;

    @Test void realStatusChangeTravelsThroughRabbitAndSmtp() throws Exception {
        for(String role:List.of("Cliente","Admin")) {
            when(decoder.decode(role)).thenReturn(Jwt.withTokenValue(role).header("alg","RS256")
                .issuer("https://local-test.example").subject(role).claim("roles",List.of(role))
                .claim("email",role.equals("Cliente") ? "cliente-prueba@example.test" : "administrador@example.test")
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(300)).build());
        }
        when(catalog.getProductById(eq(1L),anyString())).thenReturn(
            new ProductDTO(1L,"Producto de prueba",null,new BigDecimal("11.25"),20,null,true));
        var location=mvc.perform(post("/api/v1/orders").header("Authorization","Bearer Cliente")
            .contentType("application/json").content("{\"customerId\":101,\"items\":[{\"productId\":1,\"quantity\":2}]}"))
            .andExpect(status().isCreated()).andReturn().getResponse().getHeader("Location");
        mvc.perform(patch(location+"/status").param("status","ACEPTADO").header("Authorization","Bearer Admin"))
            .andExpect(status().isOk());
        var deadline=Instant.now().plusSeconds(20);
        while(Instant.now().isBefore(deadline)) {
            if(emails.findAll().stream().anyMatch(e->"SENT".equals(e.getState()))) break;
            Thread.sleep(250);
        }
        var event=emails.findAll().getFirst();
        assertThat(event.getRecipient()).isEqualTo("cliente-prueba@example.test");
        assertThat(event.getState()).isEqualTo("SENT");
        assertThat(event.getAttempts()).isEqualTo(1);
        mvc.perform(patch(location+"/status").param("status","ACEPTADO").header("Authorization","Bearer Admin"))
            .andExpect(status().isOk());
        assertThat(emails.count()).isEqualTo(1);
    }
}
