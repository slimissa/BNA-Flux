package com.bna.flux;

import com.bna.flux.dto.NotificationWebSocket;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests unitaires pour les notifications WebSocket.
 *
 * @author Slim Issa — Projet Stage BNA
 * @since 2026-08-07
 */
@DisplayName("Notifications WebSocket")
class WebSocketNotificationTest {

    @Test
    @DisplayName("Doit créer une notification TRANSACTION_BLOQUEE")
    void doitCreerNotificationBloquee() {
        NotificationWebSocket notif = NotificationWebSocket.transactionBloquee(
            1L, 85, "BNA-2026-0001"
        );

        assertThat(notif.type()).isEqualTo("TRANSACTION_BLOQUEE");
        assertThat(notif.niveau()).isEqualTo("CRITIQUE");
        assertThat(notif.scoreRisque()).isEqualTo(85);
        assertThat(notif.urlNavigation()).isEqualTo("/transactions/1");
        assertThat(notif.horodatage()).isNotNull();
    }

    @Test
    @DisplayName("Doit créer une notification TRANSACTION_SURVEILLEE")
    void doitCreerNotificationSurveillee() {
        NotificationWebSocket notif = NotificationWebSocket.transactionSurveillee(
            2L, 50, "BNA-2026-0002"
        );

        assertThat(notif.type()).isEqualTo("TRANSACTION_SURVEILLEE");
        assertThat(notif.niveau()).isEqualTo("MOYEN");
        assertThat(notif.scoreRisque()).isEqualTo(50);
    }

    @Test
    @DisplayName("Doit créer une notification ALERTE_CRITIQUE")
    void doitCreerNotificationAlerteCritique() {
        NotificationWebSocket notif = NotificationWebSocket.alerteCritique(
            3L, "Virement suspect", "BNA-2026-0003"
        );

        assertThat(notif.type()).isEqualTo("ALERTE_CRITIQUE");
        assertThat(notif.niveau()).isEqualTo("CRITIQUE");
        assertThat(notif.message()).contains("Virement suspect");
    }

    @Test
    @DisplayName("Doit créer une notification DISJONCTEUR_OUVERT")
    void doitCreerNotificationDisjoncteur() {
        NotificationWebSocket notif = NotificationWebSocket.disjoncteurOuvert(
            "COMPTE_SOURCE", "08601000191000748054"
        );

        assertThat(notif.type()).isEqualTo("DISJONCTEUR_OUVERT");
        assertThat(notif.niveau()).isEqualTo("ELEVE");
        assertThat(notif.urlNavigation()).isEqualTo("/disjoncteurs");
    }
}
