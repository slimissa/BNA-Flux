package com.bna.flux.service;

import com.bna.flux.entity.Utilisateur;
import com.bna.flux.repository.UtilisateurRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@Profile({"dev", "docker"})
public class InitialisateurTestData implements CommandLineRunner {
    private final UtilisateurRepository repo;
    private final PasswordEncoder encoder;

    public InitialisateurTestData(UtilisateurRepository repo, PasswordEncoder encoder) {
        this.repo = repo;
        this.encoder = encoder;
    }

    @Override
    public void run(String... args) {
        if (repo.count() > 0) return;

        String mdp = encoder.encode("BnaFlux2026!");

        repo.save(Utilisateur.builder().email("admin@bna.com.tn").motDePasse(mdp).nom("Administrateur BNA").role(Utilisateur.Role.ADMIN).actif(true).build());
        repo.save(Utilisateur.builder().email("superviseur@bna.com.tn").motDePasse(mdp).nom("Superviseur Agence 601").role(Utilisateur.Role.SUPERVISEUR).codeAgence("601").actif(true).build());
        repo.save(Utilisateur.builder().email("operateur@bna.com.tn").motDePasse(mdp).nom("Operateur Agence 601").role(Utilisateur.Role.OPERATEUR).codeAgence("601").actif(true).build());

        System.out.println("=== Utilisateurs de test créés ===");
        System.out.println("admin@bna.com.tn / BnaFlux2026! (ADMIN)");
        System.out.println("superviseur@bna.com.tn / BnaFlux2026! (SUPERVISEUR)");
        System.out.println("operateur@bna.com.tn / BnaFlux2026! (OPERATEUR)");
    }
}
