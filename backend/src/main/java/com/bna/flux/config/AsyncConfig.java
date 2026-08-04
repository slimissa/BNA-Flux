package com.bna.flux.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.lang.reflect.Method;
import java.util.concurrent.Executor;

/**
 * Configuration du pool de threads asynchrones pour BNA-FLUX.
 * <p>
 * Implémente {@link AsyncConfigurer} pour fournir un {@link Executor}
 * personnalisé à Spring lors de l'utilisation de {@code @Async}.
 * </p>
 *
 * <p><b>Tâches asynchrones dans BNA-FLUX :</b></p>
 * <ul>
 *   <li><b>Envoi d'emails d'alerte</b> — Non bloquant pour le pipeline.
 *       Les emails CRITIQUE sont envoyés immédiatement, les emails ELEVE
 *       sont groupés toutes les 15 minutes.</li>
 *   <li><b>Vérification périodique des disjoncteurs</b> — Tâche planifiée
 *       via {@code @Scheduled} qui vérifie si des disjoncteurs OUVERT
 *       doivent passer en MI_OUVERT.</li>
 *   <li><b>Nettoyage des logs d'audit anciens</b> — Archivage périodique
 *       (fonctionnalité future).</li>
 * </ul>
 *
 * <p><b>Configuration du pool :</b></p>
 * <ul>
 *   <li><b>Core pool size</b> : 4 threads (nombre minimal de threads actifs)</li>
 *   <li><b>Max pool size</b> : 8 threads (capacité maximale)</li>
 *   <li><b>Queue capacity</b> : 100 tâches (avant rejet)</li>
 *   <li><b>Politique de rejet</b> : {@code CallerRunsPolicy} — la tâche
 *       est exécutée par le thread appelant si la file est pleine</li>
 * </ul>
 *
 * <p><b>Nommage des threads :</b> {@code bna-async-} suivi du numéro.
 * Facilite le débogage dans les logs.</p>
 *
 * @author Slim Issa — Projet Stage BNA
 * @version 1.0.0
 * @since 2026-08-04
 */
@Slf4j
@Configuration
@EnableAsync
public class AsyncConfig implements AsyncConfigurer {

    /**
     * Taille minimale du pool de threads (core pool size).
     */
    @Value("${bna.async.core-pool-size:4}")
    private int corePoolSize;

    /**
     * Taille maximale du pool de threads (max pool size).
     */
    @Value("${bna.async.max-pool-size:8}")
    private int maxPoolSize;

    /**
     * Capacité de la file d'attente avant rejet.
     */
    @Value("${bna.async.queue-capacity:100}")
    private int queueCapacity;

    /**
     * Préfixe pour le nom des threads asynchrones.
     */
    private static final String PREFIXE_THREAD = "bna-async-";

    // Executor asynchrone principal

    /**
     * Fournit l'{@link Executor} principal pour les tâches asynchrones.
     * <p>
     * Cet executor est utilisé par défaut pour toutes les méthodes
     * annotées {@code @Async} sans qualifier spécifique.
     * </p>
     *
     * @return le {@link ThreadPoolTaskExecutor} configuré
     */
    @Override
    @Bean(name = "taskExecutor")
    public Executor getAsyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        // Taille du pool
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(queueCapacity);

        // Nom des threads pour le débogage
        executor.setThreadNamePrefix(PREFIXE_THREAD);

        // Attendre la fin des tâches avant l'arrêt de l'application
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);

        // Politique de rejet : exécuter dans le thread appelant
        executor.setRejectedExecutionHandler(
                new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy()
        );

        // Les threads sont créés à la demande, pas au démarrage
        executor.setPrestartAllCoreThreads(false);

        executor.initialize();

        log.info("Pool de threads asynchrones initialisé — core={}, max={}, queue={}",
                corePoolSize, maxPoolSize, queueCapacity);

        return executor;
    }

    // Gestion des exceptions asynchrones

    /**
     * Gère les exceptions non capturées dans les méthodes {@code @Async}.
     * <p>
     * Par défaut, les exceptions dans les méthodes asynchrones sont silencieuses.
     * Ce handler les loggue avec le niveau ERROR pour faciliter le débogage.
     * </p>
     *
     * @return le handler d'exceptions asynchrones
     */
    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return new AsyncExceptionHandler();
    }

    /**
     * Handler personnalisé pour les exceptions non capturées dans les
     * méthodes asynchrones.
     */
    private static class AsyncExceptionHandler implements AsyncUncaughtExceptionHandler {

        @Override
        public void handleUncaughtException(Throwable throwable, Method method, Object... params) {
            log.error("Exception asynchrone non capturée dans la méthode '{}' : {}",
                    method.getName(), throwable.getMessage(), throwable);
        }
    }

    // Executor spécifique pour les emails

    /**
     * Fournit un {@link Executor} dédié pour l'envoi d'emails.
     * <p>
     * Pool réduit (2-4 threads) car l'envoi d'emails est une opération I/O
     * peu consommatrice en CPU. Permet d'isoler les tâches d'email du pool
     * principal.
     * </p>
     * <p>
     * Utilisation : {@code @Async("emailExecutor")}
     * </p>
     *
     * @return le {@link ThreadPoolTaskExecutor} pour les emails
     */
    @Bean(name = "emailExecutor")
    public Executor emailExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("bna-email-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(10);
        executor.setRejectedExecutionHandler(
                new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy()
        );
        executor.initialize();

        log.info("Pool de threads email initialisé — core=2, max=4, queue=50");
        return executor;
    }

    // Executor spécifique pour les tâches planifiées

    /**
     * Fournit un {@link Executor} dédié pour les tâches planifiées.
     * <p>
     * Pool réduit (2 threads) car les tâches planifiées sont légères
     * et peu fréquentes (vérification des disjoncteurs toutes les minutes,
     * envoi groupé d'emails toutes les 15 minutes).
     * </p>
     * <p>
     * Utilisation : via {@code @Scheduled} avec ce qualifier.
     * </p>
     *
     * @return le {@link ThreadPoolTaskExecutor} pour les tâches planifiées
     */
    @Bean(name = "scheduledExecutor")
    public Executor scheduledExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(10);
        executor.setThreadNamePrefix("bna-scheduled-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(10);
        executor.setRejectedExecutionHandler(
                new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy()
        );
        executor.initialize();

        log.info("Pool de threads planifiés initialisé — core=2, max=2");
        return executor;
    }
}