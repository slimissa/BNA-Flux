package com.bna.flux.service;

import com.bna.flux.entity.EntreeAudit;
import com.bna.flux.entity.Transaction;
import com.bna.flux.repository.EntreeAuditRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Service de gestion de la piste d'audit hash-chaînée.
 * <p>
 * Chaque étape du pipeline (Validation, Enrichissement, Évaluation des règles,
 * Notation, Persistance) génère une entrée d'audit immuable. Les entrées sont
 * chaînées par hachage SHA-256 pour garantir l'intégrité et détecter toute
 * tentative de falsification.
 * </p>
 *
 * <p><b>Principe de la chaîne de hachage :</b></p>
 * <ol>
 *   <li>La première entrée d'audit a {@code hashPrecedent = null}</li>
 *   <li>Chaque entrée suivante référence le {@code hashCourant} de l'entrée précédente</li>
 *   <li>{@code hashCourant = SHA-256(hashPrecedent + "|" + transactionId + "|" + etape + "|" + action + "|" + detail + "|" + horodatage + "|" + operateur)}</li>
 *   <li>Toute modification d'une entrée brise la chaîne — détectable par recalcul</li>
 * </ol>
 *
 * <p><b>Sécurité :</b></p>
 * <ul>
 *   <li>Les entrées d'audit sont immuables : pas de modification ni suppression possible.</li>
 *   <li>La vérification de la chaîne est accessible via l'API REST.</li>
 *   <li>Le hash est calculé côté serveur — aucun client ne peut le forger.</li>
 *   <li>Les entrées sont sauvegardées dans une transaction séparée (REQUIRES_NEW)
 *       pour garantir leur persistance même en cas d'échec du pipeline.</li>
 * </ul>
 *
 * @author Slim Issa — Projet Stage BNA
 * @version 1.0.0
 * @since 2026-08-04
 */
@Slf4j
@Service
public class ServiceAudit {
    private static final java.time.format.DateTimeFormatter HASH_DTF = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSSSS");

    private final EntreeAuditRepository entreeAuditRepository;
    private final ObjectMapper objectMapper;

    /**
     * Algorithme de hachage utilisé.
     */
    @Value("${bna.audit.algorithme-hash:SHA-256}")
    private String algorithmeHash;

    /**
     * Audit activé ou non.
     */
    @Value("${bna.audit.actif:true}")
    private boolean auditActif;

    public ServiceAudit(EntreeAuditRepository entreeAuditRepository, ObjectMapper objectMapper) {
        this.entreeAuditRepository = entreeAuditRepository;
        this.objectMapper = objectMapper;
    }

    // Création d'entrée d'audit

    /**
     * Enregistre une entrée d'audit pour une transaction.
     * <p>
     * Cette méthode est exécutée dans une transaction séparée (REQUIRES_NEW)
     * pour garantir que l'audit est persisté même si la transaction principale
     * échoue.
     * </p>
     *
     * @param transaction la transaction concernée
     * @param etape       l'étape du pipeline (VALIDATION, ENRICHISSEMENT, etc.)
     * @param action      l'action effectuée
     * @param detail      les détails au format JSON
     * @param operateur   l'opérateur (ou "SYSTEME")
     * @return l'entrée d'audit créée et sauvegardée
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public EntreeAudit enregistrer(Transaction transaction, String etape, String action,
                                    String detail, String operateur) {
        if (!auditActif) {
            log.debug("Audit désactivé — aucune entrée créée");
            return null;
        }

        // Récupérer la dernière entrée pour obtenir le hash précédent
        EntreeAudit derniereEntree = entreeAuditRepository.findLastByTransactionId(transaction.getId());
        String hashPrecedent = (derniereEntree != null) ? derniereEntree.getHashCourant() : null;

        // Créer l'entrée d'audit
        EntreeAudit entree = new EntreeAudit(transaction, etape, action, detail, operateur);
        entree.setHashPrecedent(hashPrecedent);
        entree.setHorodatage(LocalDateTime.now());

        // Calculer et définir le hash courant
        String hashCourant = calculerHash(entree);
        entree.setHashCourant(hashCourant);

        // Sauvegarder
        EntreeAudit entreeSauvegardee = entreeAuditRepository.save(entree);

        log.debug("Entrée d'audit créée — transaction={}, étape={}, action={}, hash={}",
                transaction.getId(), etape, action,
                hashCourant != null ? hashCourant.substring(0, 8) + "..." : "null");

        return entreeSauvegardee;
    }

    /**
     * Enregistre une entrée d'audit avec un détail sous forme d'objet (sérialisé en JSON).
     *
     * @param transaction la transaction
     * @param etape       l'étape du pipeline
     * @param action      l'action effectuée
     * @param detailObj   l'objet de détail (sérialisé en JSON)
     * @param operateur   l'opérateur
     * @return l'entrée d'audit créée
     */
    public EntreeAudit enregistrer(Transaction transaction, String etape, String action,
                                    Map<String, Object> detailObj, String operateur) {
        String detailJson;
        try {
            detailJson = objectMapper.writeValueAsString(detailObj);
        } catch (JsonProcessingException e) {
            log.warn("Erreur de sérialisation JSON pour l'audit — utilisation de toString() : {}", e.getMessage());
            detailJson = detailObj != null ? detailObj.toString() : "{}";
        }
        return enregistrer(transaction, etape, action, detailJson, operateur);
    }

    // Calcul de hash

    /**
     * Calcule le hash SHA-256 d'une entrée d'audit.
     * <p>
     * Formule : {@code SHA-256(hashPrecedent + "|" + transactionId + "|" + etape + "|" + action + "|" + detail + "|" + horodatage + "|" + operateur)}
     * </p>
     *
     * @param entree l'entrée d'audit
     * @return le hash hexadécimal (64 caractères)
     */
    private String calculerHash(EntreeAudit entree) {
        try {
            String donnees = (entree.getHashPrecedent() != null ? entree.getHashPrecedent() : "")
                    + "|" + (entree.getTransaction() != null ? entree.getTransaction().getId() : "null")
                    + "|" + (entree.getEtape() != null ? entree.getEtape() : "")
                    + "|" + (entree.getAction() != null ? entree.getAction() : "")
                    + "|" + (entree.getDetail() != null ? entree.getDetail() : "")
                    + "|" + (entree.getHorodatage() != null ? entree.getHorodatage().format(HASH_DTF) : "")
                    + "|" + (entree.getOperateur() != null ? entree.getOperateur() : "");

            MessageDigest digest = MessageDigest.getInstance(algorithmeHash);
            byte[] hashBytes = digest.digest(donnees.getBytes(StandardCharsets.UTF_8));

            // Convertir en hexadécimal
            StringBuilder hexString = new StringBuilder();
            for (byte b : hashBytes) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }

            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            log.error("Algorithme de hachage non supporté : {}", algorithmeHash, e);
            throw new RuntimeException("Algorithme de hachage non supporté : " + algorithmeHash, e);
        }
    }

    // Vérification de la chaîne

    /**
     * Vérifie l'intégrité de la chaîne d'audit d'une transaction.
     * <p>
     * Recalcule chaque hash et compare avec la valeur stockée.
     * Retourne le résultat détaillé de la vérification.
     * </p>
     *
     * @param transactionId l'identifiant de la transaction
     * @return le résultat de la vérification
     */
    public ResultatVerification verifierChaine(Long transactionId) {
        List<EntreeAudit> entrees = entreeAuditRepository.findByTransactionIdOrderByHorodatageAsc(transactionId);

        if (entrees.isEmpty()) {
            return new ResultatVerification(true, 0, null, "Aucune entrée d'audit trouvée pour cette transaction.");
        }

        Map<Integer, VerificationEntree> entreesVerifiees = new HashMap<>();
        String hashPrecedentAttendu = null;
        boolean chaineIntacte = true;
        Integer premiereEntreeCorrompue = null;
        StringBuilder erreurs = new StringBuilder();

        for (int i = 0; i < entrees.size(); i++) {
            EntreeAudit entree = entrees.get(i);

            // Vérifier le chaînage
            String hashPrecedentStocke = entree.getHashPrecedent();
            if (i > 0 && !java.util.Objects.equals(hashPrecedentStocke, hashPrecedentAttendu)) {
                chaineIntacte = false;
                if (premiereEntreeCorrompue == null) {
                    premiereEntreeCorrompue = i + 1; // 1-based
                }
                erreurs.append("Entrée ").append(i + 1)
                        .append(" : hash précédent incorrect. Attendu : ")
                        .append(hashPrecedentAttendu != null ? hashPrecedentAttendu.substring(0, 8) : "null")
                        .append("..., Stocké : ")
                        .append(hashPrecedentStocke != null ? hashPrecedentStocke.substring(0, 8) : "null")
                        .append("...\n");
            }

            // Recalculer le hash de cette entrée
            String hashRecalcule = recalculerHash(entree);
            boolean hashCorrect = java.util.Objects.equals(hashRecalcule, entree.getHashCourant());

            if (!hashCorrect) {
                chaineIntacte = false;
                if (premiereEntreeCorrompue == null) {
                    premiereEntreeCorrompue = i + 1;
                }
                erreurs.append("Entrée ").append(i + 1)
                        .append(" : hash incorrect. Stocké : ")
                        .append(entree.getHashCourant() != null ? entree.getHashCourant().substring(0, 8) : "null")
                        .append("..., Recalculé : ")
                        .append(hashRecalcule != null ? hashRecalcule.substring(0, 8) : "null")
                        .append("...\n");
            }

            // Préparer pour l'entrée suivante
            hashPrecedentAttendu = entree.getHashCourant();

            // Stocker le résultat
            entreesVerifiees.put(i + 1, new VerificationEntree(
                    entree.getId(),
                    entree.getEtape(),
                    entree.getAction(),
                    entree.getOperateur(),
                    entree.getHorodatage(),
                    entree.getHashCourant(),
                    hashRecalcule,
                    hashCorrect,
                    entree.getHashPrecedent()
            ));
        }

        String message = chaineIntacte
                ? "Chaîne d'audit intacte. " + entrees.size() + " entrée(s) vérifiée(s)."
                : "Chaîne d'audit corrompue ! " + erreurs.toString().trim();

        return new ResultatVerification(
                chaineIntacte,
                entrees.size(),
                premiereEntreeCorrompue,
                message,
                entreesVerifiees
        );
    }

    /**
     * Recalcule le hash d'une entrée existante pour vérification.
     * <p>
     * Note : cette méthode ne modifie pas l'entrée, elle recalcule uniquement.
     * </p>
     *
     * @param entree l'entrée dont on veut recalculer le hash
     * @return le hash recalculé
     */
    private String recalculerHash(EntreeAudit entree) {
        try {
            String donnees = (entree.getHashPrecedent() != null ? entree.getHashPrecedent() : "")
                    + "|" + (entree.getTransaction() != null ? entree.getTransaction().getId() : "null")
                    + "|" + (entree.getEtape() != null ? entree.getEtape() : "")
                    + "|" + (entree.getAction() != null ? entree.getAction() : "")
                    + "|" + (entree.getDetail() != null ? entree.getDetail() : "")
                    + "|" + (entree.getHorodatage() != null ? entree.getHorodatage().format(HASH_DTF) : "")
                    + "|" + (entree.getOperateur() != null ? entree.getOperateur() : "");

            MessageDigest digest = MessageDigest.getInstance(algorithmeHash);
            byte[] hashBytes = digest.digest(donnees.getBytes(StandardCharsets.UTF_8));

            StringBuilder hexString = new StringBuilder();
            for (byte b : hashBytes) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Algorithme de hachage non supporté : " + algorithmeHash, e);
        }
    }

    // Consultation

    /**
     * Récupère la piste d'audit complète d'une transaction.
     *
     * @param transactionId l'identifiant de la transaction
     * @return la liste des entrées d'audit dans l'ordre chronologique
     */
    public List<EntreeAudit> getPisteAudit(Long transactionId) {
        return entreeAuditRepository.findByTransactionIdOrderByHorodatageAsc(transactionId);
    }

    /**
     * Compte le nombre d'entrées d'audit pour une transaction.
     *
     * @param transactionId l'identifiant de la transaction
     * @return le nombre d'entrées
     */
    public long compterEntrees(Long transactionId) {
        return entreeAuditRepository.countByTransactionId(transactionId);
    }

    // Classes internes

    /**
     * Résultat de la vérification de la chaîne d'audit.
     */
    public static class ResultatVerification {
        private final boolean chaineIntacte;
        private final int nombreEntrees;
        private final Integer entreeCorrompue;
        private final String message;
        private final Map<Integer, VerificationEntree> entrees;

        public ResultatVerification(boolean chaineIntacte, int nombreEntrees,
                                     Integer entreeCorrompue, String message) {
            this(chaineIntacte, nombreEntrees, entreeCorrompue, message, new HashMap<>());
        }

        public ResultatVerification(boolean chaineIntacte, int nombreEntrees,
                                     Integer entreeCorrompue, String message,
                                     Map<Integer, VerificationEntree> entrees) {
            this.chaineIntacte = chaineIntacte;
            this.nombreEntrees = nombreEntrees;
            this.entreeCorrompue = entreeCorrompue;
            this.message = message;
            this.entrees = entrees;
        }

        public boolean isChaineIntacte() { return chaineIntacte; }
        public int getNombreEntrees() { return nombreEntrees; }
        public Integer getEntreeCorrompue() { return entreeCorrompue; }
        public String getMessage() { return message; }
        public Map<Integer, VerificationEntree> getEntrees() { return entrees; }
    }

    /**
     * Résultat de vérification d'une entrée individuelle.
     */
    public static class VerificationEntree {
        private final Long id;
        private final String etape;
        private final String action;
        private final String operateur;
        private final LocalDateTime horodatage;
        private final String hashStocke;
        private final String hashCalcule;
        private final boolean hashVerifie;
        private final String hashPrecedent;

        public VerificationEntree(Long id, String etape, String action, String operateur,
                                   LocalDateTime horodatage, String hashStocke,
                                   String hashCalcule, boolean hashVerifie, String hashPrecedent) {
            this.id = id;
            this.etape = etape;
            this.action = action;
            this.operateur = operateur;
            this.horodatage = horodatage;
            this.hashStocke = hashStocke;
            this.hashCalcule = hashCalcule;
            this.hashVerifie = hashVerifie;
            this.hashPrecedent = hashPrecedent;
        }

        public Long getId() { return id; }
        public String getEtape() { return etape; }
        public String getAction() { return action; }
        public String getOperateur() { return operateur; }
        public LocalDateTime getHorodatage() { return horodatage; }
        public String getHashStocke() { return hashStocke; }
        public String getHashCalcule() { return hashCalcule; }
        public boolean isHashVerifie() { return hashVerifie; }
        public String getHashPrecedent() { return hashPrecedent; }
    }
}