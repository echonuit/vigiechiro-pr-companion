package fr.univ_amu.iut.analyse.view;

import fr.univ_amu.iut.validation.model.EspeceAgregee;
import fr.univ_amu.iut.validation.model.ObservationEspece;
import fr.univ_amu.iut.validation.model.StatutObservation;

/// Formatage des **libellés** de l'écran « Espèces & observations » (colonnes de tables, panneau détail,
/// sélecteur de statut) : fonctions **pures**, extraites de [AnalyseController] pour alléger sa cohésion
/// (garde-fou PMD GodClass), sur le modèle de `FormatLigneAudio` côté audio.
public final class FormatAnalyse {

    private FormatAnalyse() {}

    /// Libellé du passage d'une observation : `date · n°X`.
    public static String libellePassage(ObservationEspece observation) {
        return observation.dateEnregistrement() + " · n°" + observation.numeroPassage();
    }

    /// Taxon suivi de sa probabilité si présente (`Pippip (0,92)`) ; `—` si pas de taxon (non touchée).
    public static String taxonEtProb(String taxon, Double probabilite) {
        if (taxon == null || taxon.isBlank()) {
            return "—";
        }
        if (probabilite == null) {
            return taxon;
        }
        return taxon + " (" + String.format("%.2f", probabilite) + ")";
    }

    /// Libellé d'une espèce : nom vernaculaire (sinon latin, sinon code) suivi du code entre parenthèses.
    public static String libelleEspece(EspeceAgregee espece) {
        String nom = premierNonVide(espece.nomVernaculaireFr(), espece.nomLatin(), espece.code());
        return nom + " (" + espece.code() + ")";
    }

    /// Période d'observation : une seule année (`2026`) ou un intervalle (`2024–2026`).
    public static String periode(int anneeMin, int anneeMax) {
        return anneeMin == anneeMax ? Integer.toString(anneeMin) : anneeMin + "–" + anneeMax;
    }

    /// Libellé du filtre de statut de revue (`null` = tous).
    public static String libelleStatut(StatutObservation statut) {
        if (statut == null) {
            return "Tous les statuts";
        }
        return switch (statut) {
            case NON_TOUCHEE -> "Non touchée";
            case VALIDEE -> "Validée";
            case CORRIGEE -> "Corrigée";
        };
    }

    /// Classe CSS du badge de statut de revue d'une observation (familles sémantiques génériques de
    /// design.css) : validée = succès, corrigée = info, non touchée = neutre.
    public static String classeStatut(StatutObservation statut) {
        if (statut == null) {
            return "badge-neutre";
        }
        return switch (statut) {
            case NON_TOUCHEE -> "badge-neutre";
            case VALIDEE -> "badge-succes";
            case CORRIGEE -> "badge-info";
        };
    }

    /// Valeur telle quelle, ou tiret cadratin `—` si elle est nulle ou vide.
    public static String ouTiret(String valeur) {
        return valeur == null || valeur.isBlank() ? "—" : valeur;
    }

    private static String premierNonVide(String... candidats) {
        for (String candidat : candidats) {
            if (candidat != null && !candidat.isBlank()) {
                return candidat;
            }
        }
        return "";
    }
}
