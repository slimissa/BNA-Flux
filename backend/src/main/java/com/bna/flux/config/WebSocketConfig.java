package com.bna.flux.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * Configuration WebSocket / STOMP pour les notifications en temps réel.
 * <p>
 * Permet au backend de pousser instantanément des alertes, changements
 * d'état de disjoncteurs et notifications vers le frontend sans polling.
 * </p>
 *
 * <p><b>Architecture des canaux :</b></p>
 * <ul>
 *   <li><b>/topic/alertes</b> — Alertes broadcast (tous les superviseurs connectés)</li>
 *   <li><b>/topic/agences/{code}</b> — Alertes spécifiques à une agence</li>
 *   <li><b>/topic/disjoncteurs</b> — Changements d'état des circuit breakers</li>
 *   <li><b>/app/accuser</b> — Accusé de réception depuis le frontend</li>
 * </ul>
 *
 * <p><b>Endpoint de connexion :</b></p>
 * <pre>/ws</pre> avec fallback SockJS pour les navigateurs sans WebSocket natif.
 *
 * @author Slim Issa — Projet Stage BNA
 * @version 1.0.0
 * @since 2026-08-07
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private static final Logger log = LoggerFactory.getLogger(WebSocketConfig.class);

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // Broker mémoire in-process — pas besoin de RabbitMQ/ActiveMQ
        registry.enableSimpleBroker("/topic", "/queue");

        // Préfixe pour les messages CLIENT → SERVEUR
        registry.setApplicationDestinationPrefixes("/app");

        log.info("WebSocket STOMP configuré — Broker simple activé sur /topic, /queue");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*")  // Toutes origines en dev
                .withSockJS()                   // Fallback pour navigateurs anciens
                .setStreamBytesLimit(512 * 1024)
                .setHttpMessageCacheSize(1000)
                .setDisconnectDelay(30 * 1000);

        log.info("Endpoint WebSocket enregistré : /ws (avec fallback SockJS)");
    }
}
