package fr.univ_amu.iut.audio.viewmodel;

import fr.univ_amu.iut.validation.model.StatutObservation;
import java.util.Comparator;

/// Comparateurs de tri des colonnes de la table audio dont l'**affichage est une chaîne formatée** : sans
/// eux, le tri serait alphabétique (« 100 % » précèderait « 83 % », « N°10 » précèderait « N°2 »). Extrait
/// de [FormatLigneAudio] pour garder chaque classe sous les seuils de cohésion PMD.
public final class ComparateursAudio {

    private ComparateursAudio() {}

    /// Comparateur **numérique** commun aux colonnes dont l'affichage est un nombre préfixé/suffixé
    /// (« 90 % », « 45 kHz », « 0,20 s », « N°2 ») : ordonne selon le premier entier lu plutôt
    /// qu'alphabétiquement ; valeur absente (« — ») classée en tête (traitée comme -1).
    public static Comparator<String> comparateurNumerique() {
        return Comparator.comparingInt(ComparateursAudio::premierEntierOuMoinsUn);
    }

    /// Comparateur de la colonne « Durée » : ordonne selon la **durée réelle**, en ramenant les deux unités
    /// affichées à une échelle commune (les millisecondes). Sans cela, le tri naïf mêlerait « 120 ms » et
    /// « 2,1 s » (chiffres bruts 120 vs 21). Absente (« — ») classée en tête.
    public static Comparator<String> comparateurDuree() {
        return Comparator.comparingDouble(ComparateursAudio::dureeEnMillisecondes);
    }

    /// Comparateur de la colonne « Statut » : ordonne selon l'**ordre de revue** (À revoir → Validée →
    /// Corrigée), l'ordre naturel de [StatutObservation], plutôt qu'alphabétiquement.
    public static Comparator<String> comparateurStatut() {
        return Comparator.comparingInt(ComparateursAudio::ordreStatut);
    }

    /// Premier entier lu dans l'affichage (« 83 % » → 83, « N°10 » → 10), ou -1 si aucun chiffre (« — »,
    /// vide, nul).
    private static int premierEntierOuMoinsUn(String affichage) {
        if (affichage == null) {
            return -1;
        }
        String chiffres = affichage.replaceAll("\\D", "");
        return chiffres.isEmpty() ? -1 : Integer.parseInt(chiffres);
    }

    /// Durée d'affichage (« 120 ms » ou « 2,1 s ») ramenée en **millisecondes** pour le tri ; -1 si absente.
    /// Le « ms » distingue l'unité (sinon secondes → ×1000). Virgule décimale FR gérée.
    private static double dureeEnMillisecondes(String affichage) {
        if (affichage == null || affichage.isBlank() || affichage.equals("—")) {
            return -1;
        }
        String nombre = affichage.replaceAll("[^0-9,.]", "").replace(',', '.');
        if (nombre.isEmpty()) {
            return -1;
        }
        double valeur = Double.parseDouble(nombre);
        return affichage.contains("ms") ? valeur : valeur * 1000;
    }

    /// Rang de revue d'un libellé de statut (inverse de [FormatLigneAudio#libelleStatut]), ou -1 si inconnu.
    private static int ordreStatut(String libelle) {
        for (StatutObservation statut : StatutObservation.values()) {
            if (FormatLigneAudio.libelleStatut(statut).equals(libelle)) {
                return statut.ordinal();
            }
        }
        return -1;
    }
}
