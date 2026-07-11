package fr.univ_amu.iut.commun.di;

import java.util.Objects;

/// Identité et métadonnées d'une **feature** (plugin) du socle : un **id stable** (clé du système de
/// feature-flags #1057, qui remplace le filtrage par nom de classe simple), un **libellé** lisible et
/// une [Categorie] (désactivable ? active par défaut ?). Déclarée par chaque [ModuleDeFeature] via
/// `fonctionnalite()`.
public record Fonctionnalite(String id, String libelle, Categorie categorie) {

    public Fonctionnalite {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("id de fonctionnalité vide");
        }
        if (libelle == null || libelle.isBlank()) {
            throw new IllegalArgumentException("libellé de fonctionnalité vide");
        }
        Objects.requireNonNull(categorie, "categorie");
    }
}
