package com.bna.flux;

import com.bna.flux.dto.RequeteTransaction;
import com.bna.flux.entity.Transaction.Canal;
import com.bna.flux.entity.Transaction.TypeTransaction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
@DisplayName("Pipeline Integration Tests")
class PipelineIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    private HttpHeaders headers;

    @BeforeEach
    void login() {
        var req = Map.of("email", "admin@bna.com.tn", "motDePasse", "BnaFlux2026!");
        var resp = restTemplate.postForEntity("/api/auth/connexion", req, Map.class);
        headers = new HttpHeaders();
        headers.setBearerAuth((String) resp.getBody().get("tokenAcces"));
        headers.setContentType(MediaType.APPLICATION_JSON);
    }

    @Test
    @DisplayName("Transaction TND → 200 + donnees non null")
    void tnd_200() {
        var req = RequeteTransaction.builder()
            .ribSource("08601000191000748054").ribDestination("01234123456789012383")
            .montant(new BigDecimal("5000")).codeDevise("TND")
            .typeTransaction(TypeTransaction.VIREMENT).canal(Canal.AGENCE)
            .dateTransaction(LocalDateTime.now()).build();

        var resp = restTemplate.exchange("/api/transactions", HttpMethod.POST,
            new HttpEntity<>(req, headers), Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().get("statut")).isIn("SUCCES", "ATTENTION");
    }

    @Test
    @DisplayName("Sans auth → 401 ou 403")
    void sansAuth() {
        var req = RequeteTransaction.builder()
            .ribSource("08601000191000748054").ribDestination("01234123456789012383")
            .montant(new BigDecimal("1000")).codeDevise("TND")
            .typeTransaction(TypeTransaction.VIREMENT).canal(Canal.AGENCE)
            .dateTransaction(LocalDateTime.now()).build();

        var resp = restTemplate.exchange("/api/transactions", HttpMethod.POST,
            new HttpEntity<>(req), Map.class);
        assertThat(resp.getStatusCode().value()).isIn(401, 403);
    }

    @Test
    @DisplayName("SpEL valide → 200")
    void spel_200() {
        var req = Map.of("expression", "montant >= 50000 AND codeDevise != 'TND'");
        var resp = restTemplate.exchange("/api/regles/tester", HttpMethod.POST,
            new HttpEntity<>(req, headers), Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}
