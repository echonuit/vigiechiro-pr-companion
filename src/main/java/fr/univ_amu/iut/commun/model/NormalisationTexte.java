package fr.univ_amu.iut.commun.model;

import java.text.Normalizer;
import java.util.Locale;

/// Normalisation de texte pour une **recherche tolérante** (#144) : insensible à la **casse** et aux
/// **accents/diacritiques**. `normaliser("Étang de la Tuilière")` → `etang de la tuiliere`. Réutilisable
/// par tout filtre textuel (recherche globale, filtres de tableaux…).
public final class NormalisationTexte {

    /// Marques diacritiques combinantes (accents) produites par la décomposition NFD.
    private static final String DIACRITIQUES = "\\p{M}+";

    private NormalisationTexte() {}

    /// Forme normalisée d'un texte : décomposé (NFD), accents retirés, en minuscules et sans espaces de
    /// bord. `null` → chaîne vide (jamais de `NullPointerException` côté appelant).
    public static String normaliser(String texte) {
        if (texte == null) {
            return "";
        }
        String sansAccents = Normalizer.normalize(texte, Normalizer.Form.NFD).replaceAll(DIACRITIQUES, "");
        return sansAccents.toLowerCase(Locale.ROOT).strip();
    }

    /// Vrai si `texte` **contient** `requete` une fois les deux normalisés (casse/accents ignorés). Une
    /// `requete` vide n'est jamais considérée comme contenue (évite de tout faire correspondre).
    public static boolean contient(String texte, String requete) {
        String aiguille = normaliser(requete);
        return !aiguille.isEmpty() && normaliser(texte).contains(aiguille);
    }
}
