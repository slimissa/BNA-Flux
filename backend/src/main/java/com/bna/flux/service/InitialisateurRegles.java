package com.bna.flux.service;

import com.bna.flux.entity.Regle;
import com.bna.flux.repository.RegleRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.List;

@Component
public class InitialisateurRegles implements CommandLineRunner {
    private final RegleRepository regleRepository;
    private final ResourceLoader resourceLoader;
    private final ObjectMapper objectMapper;

    public InitialisateurRegles(RegleRepository regleRepository, ResourceLoader resourceLoader, ObjectMapper objectMapper) {
        this.regleRepository = regleRepository;
        this.resourceLoader = resourceLoader;
        this.objectMapper = objectMapper;
    }

    @Override
    public void run(String... args) throws Exception {
        if (regleRepository.count() > 0) {
            System.out.println("=== " + regleRepository.count() + " règle(s) déjà en base ===");
            return;
        }

        var resource = resourceLoader.getResource("classpath:regles-par-defaut.json");
        if (!resource.exists()) {
            System.out.println("=== Fichier regles-par-defaut.json introuvable ===");
            return;
        }

        try (InputStream is = resource.getInputStream()) {
            List<Regle> regles = objectMapper.readValue(is, new TypeReference<List<Regle>>() {});
            for (Regle r : regles) {
                regleRepository.save(r);
            }
            System.out.println("=== " + regles.size() + " règles créées depuis regles-par-defaut.json ===");
        }
    }
}
