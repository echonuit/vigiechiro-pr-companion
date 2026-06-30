package fr.univ_amu.iut.audio.viewmodel;

import fr.univ_amu.iut.validation.model.LigneObservationAudio;
import fr.univ_amu.iut.validation.model.StatutObservation;

/// Formatages d'affichage d'une [LigneObservationAudio] pour la vue audio unifiée : détail multi-ligne
/// du panneau d'écoute et libellé de statut de la colonne « Statut ». Pendant de
/// `validation.viewmodel.FormatObservation`, mais pour le record unifié (avec la mention « référence »).
///
/// Classe utilitaire sans état : la logique d'affichage est sortie du [AudioViewModel] pour qu'il garde
/// une seule responsabilité (orchestrer la revue) et reste sous le seuil de cohésion (PMD GodClass).
public final class FormatLigneAudio {

    /// Affichage des valeurs optionnelles absentes (probabilité, taxon observateur non saisi).
    private static final String NON_RENSEIGNE = "non renseigné";

    private FormatLigneAudio() {}

    /// Détail multi-ligne d'une observation sélectionnée (proposition Tadarida, saisie observateur,
    /// fréquence médiane, statut de revue, et mention « référence » si archivée).
    public static String detail(LigneObservationAudio o) {
        return "Tadarida : "
                + o.taxonTadarida()
                + " ("
                + proba(o.probTadarida())
                + ")\nObservateur : "
                + valeurOuAbsente(o.taxonObservateur())
                + " ("
                + proba(o.probObservateur())
                + ")\nFréquence médiane : "
                + frequence(o.frequenceHz())
                + "\nStatut : "
                + libelleStatut(o.statut())
                + (o.reference() ? "\nRéférence : oui" : "");
    }

    /// Libellé d'**espèce** affiché en colonne : le **nom vernaculaire** de l'espèce retenue s'il est
    /// connu (plus lisible), sinon le **code** Tadarida retenu (observateur sinon proposition) — cas d'une
    /// souche hors référentiel sans nom vernaculaire.
    public static String espece(LigneObservationAudio o) {
        if (o.nomEspece() != null && !o.nomEspece().isBlank()) {
            return o.nomEspece();
        }
        String observateur = o.taxonObservateur();
        return observateur != null && !observateur.isBlank() ? observateur : o.taxonTadarida();
    }

    /// Libellé de la **proposition Tadarida** affiché en colonne : le **nom vernaculaire** du taxon
    /// proposé par Tadarida s'il est connu, sinon le **code** (souche hors référentiel).
    public static String tadarida(LigneObservationAudio o) {
        return o.nomTadarida() != null && !o.nomTadarida().isBlank() ? o.nomTadarida() : o.taxonTadarida();
    }

    /// Probabilité de détection formatée pour la colonne (« 81 % »), tiret si absente.
    public static String probabilite(Double probabilite) {
        return probabilite == null ? "—" : Math.round(probabilite * 100) + " %";
    }

    /// Libellé d'affichage du statut de revue (partagé avec la colonne « Statut » de la vue).
    public static String libelleStatut(StatutObservation statut) {
        return switch (statut) {
            case NON_TOUCHEE -> "À revoir";
            case VALIDEE -> "Validée";
            case CORRIGEE -> "Corrigée";
        };
    }

    private static String proba(Double probabilite) {
        return probabilite == null ? NON_RENSEIGNE : Math.round(probabilite * 100) + " %";
    }

    private static String valeurOuAbsente(String code) {
        return code == null || code.isBlank() ? NON_RENSEIGNE : code;
    }

    private static String frequence(Integer hz) {
        return hz == null ? NON_RENSEIGNE : hz + " Hz";
    }
}
