package fr.univ_amu.iut.commun.model;

import java.util.Objects;
import java.util.Optional;

/// Une commune française, identifiée par son **code INSEE** (qui fait foi) et portée par son nom
/// (affichage et filtre). Attachée à un **point d'écoute** via la table latérale `point_commune`
/// (#2791) - jamais à un carré, qui peut chevaucher plusieurs communes.
///
/// Département et région ne sont pas stockés : ils se **dérivent** du code INSEE, la région via la
/// table partagée [RegionsFrancaises] (mêmes clés de jointure que le référentiel d'activité).
public record Commune(String nom, String codeInsee) {

    public Commune {
        exigerNonBlanc(nom, "nom");
        exigerNonBlanc(codeInsee, "codeInsee");
        if (codeInsee.length() < 2) {
            throw new IllegalArgumentException("Code INSEE illisible : « " + codeInsee + " ».");
        }
    }

    /// Le code département porté par le code INSEE : trois caractères pour l'outre-mer (`97x`),
    /// deux sinon (`2A`/`2B` compris pour la Corse).
    public String departement() {
        if (codeInsee.startsWith("97") || codeInsee.startsWith("98")) {
            return codeInsee.substring(0, Math.min(3, codeInsee.length()));
        }
        return codeInsee.substring(0, 2);
    }

    /// La région (clé de jointure du référentiel d'activité), ou vide hors métropole.
    /// ⚠️ La valeur rendue est une **clé de référentiel** (`Provence-Alpes-Cote dAzur`), sans accents
    /// ni apostrophe. Pour l'afficher à un utilisateur, passer par
    /// [LibellesReferentiel#region(String)] : c'est l'oubli de cette traduction qui a fait lire
    /// « region Provence-Alpes-Cote dAzur » en pied d'écran pendant des mois (#3049).
    public Optional<String> region() {
        return RegionsFrancaises.pourDepartement(departement());
    }

    private static void exigerNonBlanc(String valeur, String champ) {
        Objects.requireNonNull(valeur, champ);
        if (valeur.isBlank()) {
            throw new IllegalArgumentException("Le champ « " + champ + " » d'une commune ne peut pas être vide.");
        }
    }
}
