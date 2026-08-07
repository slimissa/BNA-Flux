package com.bna.flux.dto;

import java.time.Instant;

/**
 * Payload de notification temps réel envoyé via WebSocket.
 * <p>
 * Structure légère conçue pour être sérialisée en JSON et
 * consommée immédiatement par le frontend Angular.
 * </p>
 *
 * @param type          Type d'événement (TRANSACTION_BLOQUEE, ALERTE_CRITIQUE, DISJONCTEUR_OUVERT)
 * @param titre         Titre court pour toast notification
 * @param message       Message descriptif
 * @param niveau        Sévérité (CRITIQUE, ELEVE, MOYEN, INFO)
 * @param horodatage    Instant de l'événement
 * @param transactionId ID de la transaction concernée (peut être null)
 * @param scoreRisque   Score de risque (peut être null)
 * @param urlNavigation  URL Angular vers laquelle rediriger au clic
 *
 * @author Slim Issa — Projet Stage BNA
 * @since 2026-08-07
 */
public record NotificationWebSocket(
    String type,
    String titre,
    String message,
    String niveau,
    Instant horodatage,
    Long transactionId,
    Integer scoreRisque,
    String urlNavigation
) {

    // ——— Usines statiques pour les types d'événements courants ———

    public static NotificationWebSocket transactionBloquee(
            Long transactionId, int scoreRisque, String reference) {
        return new NotificationWebSocket(
            "TRANSACTION_BLOQUEE",
            "Transaction bloquée",
            String.format("Transaction %s bloquée — Score de risque : %d/100", reference, scoreRisque),
            scoreRisque >= 80 ? "CRITIQUE" : "ELEVE",
            Instant.now(),
            transactionId,
            scoreRisque,
            "/transactions/" + transactionId
        );
    }

    public static NotificationWebSocket alerteCritique(
            Long transactionId, String nomRegle, String reference) {
        return new NotificationWebSocket(
            "ALERTE_CRITIQUE",
            "Alerte critique",
            String.format("Règle « %s » déclenchée sur la transaction %s", nomRegle, reference),
            "CRITIQUE",
            Instant.now(),
            transactionId,
            null,
            "/transactions/" + transactionId
        );
    }

    public static NotificationWebSocket disjoncteurOuvert(
            String typeCible, String identifiantCible) {
        return new NotificationWebSocket(
            "DISJONCTEUR_OUVERT",
            "Disjoncteur ouvert",
            String.format("Circuit breaker activé — %s : %s", typeCible, identifiantCible),
            "ELEVE",
            Instant.now(),
            null,
            null,
            "/disjoncteurs"
        );
    }

    public static NotificationWebSocket transactionSurveillee(
            Long transactionId, int scoreRisque, String reference) {
        return new NotificationWebSocket(
            "TRANSACTION_SURVEILLEE",
            "Transaction sous surveillance",
            String.format("Transaction %s marquée SURVEILLE — Score : %d/100", reference, scoreRisque),
            "MOYEN",
            Instant.now(),
            transactionId,
            scoreRisque,
            "/transactions/" + transactionId
        );
    }
}
