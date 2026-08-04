package com.bna.flux.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Entité représentant un utilisateur du système BNA-FLUX.
 * <p>
 * Gère l'authentification JWT et le contrôle d'accès basé sur les rôles.
 * Les mots de passe sont hashés avec BCrypt avant la persistance.
 * </p>
 *
 * <p><b>Règles de gestion :</b></p>
 * <ul>
 *   <li>Un utilisateur possède un rôle unique : OPERATEUR, SUPERVISEUR, ou ADMIN.</li>
 *   <li>L'email sert d'identifiant de connexion.</li>
 *   <li>Le mot de passe n'est jamais stocké en clair — hashé avec BCrypt.</li>
 *   <li>Un utilisateur inactif ne peut pas s'authentifier.</li>
 *   <li>La suppression physique est interdite — utiliser le flag {@code actif}.</li>
 * </ul>
 *
 * <p><b>Relations :</b></p>
 * <ul>
 *   <li>Aucune relation directe avec d'autres entités.</li>
 *   <li>L'audit (EntreeAudit) référence l'utilisateur par son nom d'utilisateur
 *       (champ {@code operateur}), pas par clé étrangère.</li>
 * </ul>
 *
 * @author Slim Issa — Projet Stage BNA
 * @version 1.0.0
 * @since 2026-08-04
 */
@Entity
@Table(name = "utilisateurs")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Utilisateur {

    /**
     * Identifiant unique généré automatiquement.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Adresse email de l'utilisateur.
     * <p>
     * Utilisée comme identifiant de connexion (login).
     * Doit être unique dans le système.
     * </p>
     */
    @NotBlank(message = "L'email est obligatoire")
    @Email(message = "L'email doit être valide")
    @Size(max = 150, message = "L'email ne doit pas dépasser 150 caractères")
    @Column(name = "email", length = 150, nullable = false, unique = true)
    private String email;

    /**
     * Mot de passe hashé avec BCrypt.
     * <p>
     * Ne jamais stocker en clair. Le hachage est effectué par Spring Security
     * via {@code BCryptPasswordEncoder} avant la persistance.
     * </p>
     * <p>
     * Le champ en base peut contenir jusqu'à 255 caractères pour accommoder
     * les hash BCrypt (60 caractères) avec une marge pour les évolutions futures.
     * </p>
     */
    @NotBlank(message = "Le mot de passe est obligatoire")
    @Size(min = 60, max = 255, message = "Le mot de passe hashé doit être valide")
    @Column(name = "mot_de_passe", length = 255, nullable = false)
    private String motDePasse;

    /**
     * Nom complet de l'utilisateur pour l'affichage et les logs.
     */
    @NotBlank(message = "Le nom est obligatoire")
    @Size(max = 150, message = "Le nom ne doit pas dépasser 150 caractères")
    @Column(name = "nom", length = 150, nullable = false)
    private String nom;

    /**
     * Rôle de l'utilisateur déterminant ses permissions.
     *
     * <p><b>Hiérarchie des rôles :</b></p>
     * <ul>
     *   <li>{@code OPERATEUR} — Visualisation des transactions et alertes de son agence,
     *       acquittement des alertes. Pas de modification des règles.</li>
     *   <li>{@code SUPERVISEUR} — Tous les droits OPERATEUR + création/modification
     *       des règles, réinitialisation des disjoncteurs.</li>
     *   <li>{@code ADMIN} — Tous les droits sans restriction. Gestion des utilisateurs,
     *       accès à toutes les agences.</li>
     * </ul>
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "role", length = 20, nullable = false)
    @Builder.Default
    private Role role = Role.OPERATEUR;

    /**
     * Code de l'agence à laquelle l'utilisateur est rattaché.
     * <p>
     * Les utilisateurs OPERATEUR et SUPERVISEUR voient principalement les données
     * de leur agence. Les ADMIN voient toutes les agences.
     * </p>
     * <p>
     * Format : 3 chiffres correspondant au code guichet BNA.
     * </p>
     */
    @Size(max = 3, message = "Le code agence doit contenir 3 caractères maximum")
    @Column(name = "code_agence", length = 3)
    private String codeAgence;

    /**
     * Indique si l'utilisateur peut se connecter au système.
     * <p>
     * Un utilisateur inactif :
     * </p>
     * <ul>
     *   <li>Ne peut pas s'authentifier (même avec des identifiants valides)</li>
     *   <li>Conserve son historique d'actions dans les logs d'audit</li>
     *   <li>Peut être réactivé par un ADMIN</li>
     * </ul>
     */
    @Column(name = "actif", nullable = false)
    @Builder.Default
    private boolean actif = true;

    /**
     * Date de création du compte utilisateur.
     */
    @Column(name = "date_creation", nullable = false, updatable = false)
    private LocalDateTime dateCreation;

    /**
     * Date de la dernière modification du compte.
     */
    @Column(name = "date_modification")
    private LocalDateTime dateModification;

    /**
     * Date de la dernière connexion réussie.
     * <p>
     * Mis à jour à chaque authentification JWT réussie.
     * Utile pour les audits de sécurité et la détection de comptes inactifs.
     * </p>
     */
    @Column(name = "derniere_connexion")
    private LocalDateTime derniereConnexion;

    // Callbacks JPA

    /**
     * Initialise les dates avant la première persistance.
     */
    @PrePersist
    protected void avantCreation() {
        this.dateCreation = LocalDateTime.now();
        this.dateModification = LocalDateTime.now();
    }

    /**
     * Met à jour la date de modification avant chaque mise à jour.
     */
    @PreUpdate
    protected void avantModification() {
        this.dateModification = LocalDateTime.now();
    }

    // Enum interne

    /**
     * Rôles disponibles dans le système BNA-FLUX.
     * <p>
     * Les rôles sont utilisés par Spring Security pour l'autorisation.
     * Le préfixe {@code ROLE_} est automatiquement ajouté par Spring Security
     * lors de la construction des autorités.
     * </p>
     */
    public enum Role {
        /**
         * Accès en lecture seule aux transactions et alertes de son agence.
         */
        OPERATEUR,

        /**
         * Accès OPERATEUR + gestion des règles et disjoncteurs.
         */
        SUPERVISEUR,

        /**
         * Accès complet sans restriction d'agence.
         */
        ADMIN
    }

    // Méthodes métier

    /**
     * Vérifie si l'utilisateur peut accéder aux données d'une agence spécifique.
     *
     * @param codeAgenceCible le code de l'agence cible
     * @return {@code true} si l'utilisateur est ADMIN ou appartient à cette agence
     */
    public boolean peutAccederAgence(String codeAgenceCible) {
        if (role == Role.ADMIN) {
            return true;
        }
        return this.codeAgence != null && this.codeAgence.equals(codeAgenceCible);
    }

    /**
     * Vérifie si l'utilisateur a un rôle suffisant pour modifier les règles.
     *
     * @return {@code true} si le rôle est SUPERVISEUR ou ADMIN
     */
    public boolean peutGererRegles() {
        return role == Role.SUPERVISEUR || role == Role.ADMIN;
    }

    /**
     * Vérifie si l'utilisateur a un rôle suffisant pour gérer les disjoncteurs.
     *
     * @return {@code true} si le rôle est SUPERVISEUR ou ADMIN
     */
    public boolean peutGererDisjoncteurs() {
        return role == Role.SUPERVISEUR || role == Role.ADMIN;
    }

    /**
     * Vérifie si l'utilisateur a un rôle suffisant pour gérer les utilisateurs.
     *
     * @return {@code true} si le rôle est ADMIN
     */
    public boolean peutGererUtilisateurs() {
        return role == Role.ADMIN;
    }
}