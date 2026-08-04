package com.bna.flux.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO pour la requête d'authentification (login).
 * <p>
 * Reçu par {@link com.bna.flux.controller.AuthController#connexion(RequeteConnexion)}
 * lorsqu'un utilisateur tente de s'authentifier avec son email et mot de passe.
 * </p>
 *
 * <p><b>Validation :</b></p>
 * <ul>
 *   <li>L'email doit être non vide et au format valide.</li>
 *   <li>Le mot de passe doit être non vide et avoir entre 8 et 100 caractères
 *       (le mot de passe en clair, avant hachage BCrypt).</li>
 * </ul>
 *
 * <p><b>Sécurité :</b></p>
 * <ul>
 *   <li>Ce DTO ne doit jamais être loggué (contient le mot de passe en clair).</li>
 *   <li>Le mot de passe est comparé avec le hash BCrypt dans
 *       {@link com.bna.flux.service.ServiceAuthentification}.</li>
 *   <li>En cas d'échec d'authentification, un message générique est retourné
 *       ("Email ou mot de passe incorrect") sans distinguer si l'email existe
 *       ou si le mot de passe est erroné (protection contre l'énumération).</li>
 * </ul>
 *
 * @author Slim Issa — Projet Stage BNA
 * @version 1.0.0
 * @since 2026-08-04
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RequeteConnexion {

    /**
     * Adresse email de l'utilisateur.
     * <p>
     * Sert d'identifiant de connexion. Doit correspondre à un utilisateur
     * actif en base de données.
     * </p>
     */
    @NotBlank(message = "L'email est obligatoire")
    @Email(message = "Le format de l'email est invalide")
    @Size(max = 150, message = "L'email ne doit pas dépasser 150 caractères")
    private String email;

    /**
     * Mot de passe en clair.
     * <p>
     * Sera comparé avec le hash BCrypt stocké en base lors de l'authentification.
     * La longueur minimale (8) est vérifiée côté client ET côté serveur.
     * </p>
     * <p>
     * <b>Ne jamais logger ce champ !</b>
     * </p>
     */
    @NotBlank(message = "Le mot de passe est obligatoire")
    @Size(min = 8, max = 100, message = "Le mot de passe doit contenir entre 8 et 100 caractères")
    private String motDePasse;
}