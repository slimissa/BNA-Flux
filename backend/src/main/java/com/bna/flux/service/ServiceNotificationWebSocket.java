package com.bna.flux.service;

import com.bna.flux.dto.NotificationWebSocket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

/**
 * Service d'envoi de notifications temps réel via WebSocket STOMP.
 * <p>
 * Point central pour toutes les notifications push. Les contrôleurs
 * et services du pipeline appellent ce service pour notifier
 * instantanément les utilisateurs connectés.
 * </p>
 *
 * <p><b>Canaux de diffusion :</b></p>
 * <ul>
 *   <li>{@code /topic/alertes} — Tous les utilisateurs (broadcast)</li>
 *   <li>{@code /topic/agences/{codeAgence}} — Agence spécifique</li>
 *   <li>{@code /topic/disjoncteurs} — État des circuit breakers</li>
 * </ul>
 *
 * @author Slim Issa — Projet Stage BNA
 * @version 1.0.0
 * @since 2026-08-07
 */
@Service
public class ServiceNotificationWebSocket {

    private static final Logger log = LoggerFactory.getLogger(ServiceNotificationWebSocket.class);

    private final SimpMessagingTemplate messagingTemplate;

    public ServiceNotificationWebSocket(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    /**
     * Diffuse une notification à TOUS les utilisateurs connectés.
     *
     * @param notification la notification à envoyer
     */
    public void diffuserAlerte(NotificationWebSocket notification) {
        messagingTemplate.convertAndSend("/topic/alertes", notification);
        log.info("WebSocket diffusé [/topic/alertes] — {} : {}",
                notification.type(), notification.titre());
    }

    /**
     * Envoie une notification à une agence spécifique.
     *
     * @param codeAgence   le code de l'agence (ex: "001")
     * @param notification la notification
     */
    public void notifierAgence(String codeAgence, NotificationWebSocket notification) {
        messagingTemplate.convertAndSend("/topic/agences/" + codeAgence, notification);
        log.info("WebSocket agence [/topic/agences/{}] — {}", codeAgence, notification.type());
    }

    /**
     * Diffuse un changement d'état de disjoncteur.
     *
     * @param notification la notification
     */
    public void diffuserChangementDisjoncteur(NotificationWebSocket notification) {
        messagingTemplate.convertAndSend("/topic/disjoncteurs", notification);
        log.info("WebSocket diffusé [/topic/disjoncteurs] — {}", notification.message());
    }

    /**
     * Vérifie si des clients sont actuellement connectés.
     *
     * @return true si au moins un client est abonné
     */
    public boolean aDesClientsConnectes() {
        // SimpMessagingTemplate ne permet pas de compter les abonnés directement.
        // Pour le moment, on log juste ; en production, on utiliserait
        // un ApplicationListener<SessionConnectedEvent>.
        return true;
    }
}
