package fr.univ_amu.iut.audio.viewmodel;

import fr.univ_amu.iut.validation.model.LigneObservationAudio;
import fr.univ_amu.iut.validation.model.StatutObservation;
import java.util.Comparator;

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
        // Une seule ligne séparée par « · » : utilise la largeur disponible plutôt que d'empiler
        // verticalement (gain d'espace pour le spectrogramme).
        String detail = "Tadarida : "
                + o.taxonTadarida()
                + " ("
                + proba(o.probTadarida())
                + ") · Observateur : "
                + valeurOuAbsente(o.taxonObservateur())
                + " ("
                + proba(o.probObservateur())
                + ") · Fréquence médiane : "
                + frequence(o.frequenceHz())
                + " · Statut : "
                + libelleStatut(o.statut());
        return o.reference() ? detail + " · Référence : oui" : detail;
    }

    /// Libellé de **votre taxon** (décision de l'observateur) affiché en colonne : tiret tant que
    /// l'observation n'a pas été revue (aucun taxon observateur), sinon le **nom vernaculaire** du taxon
    /// retenu (plus lisible), avec repli sur le **code** si ce taxon est une souche hors référentiel sans
    /// vernaculaire. Comme `nomEspece` projette le vernaculaire de `COALESCE(observateur, Tadarida)`, il
    /// vaut le vernaculaire de l'observateur dès que celui-ci est renseigné.
    public static String votreTaxon(LigneObservationAudio o) {
        String observateur = o.taxonObservateur();
        if (observateur == null || observateur.isBlank()) {
            return "—";
        }
        return o.nomEspece() != null && !o.nomEspece().isBlank() ? o.nomEspece() : observateur;
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

    /// Fréquence médiane formatée pour la **colonne** (« 45000 Hz »), tiret si absente. (Le pendant privé
    /// [#frequence(Integer)] sert au panneau de détail avec le libellé « non renseigné ».)
    public static String frequenceColonne(Integer frequenceHz) {
        return frequenceHz == null ? "—" : frequenceHz + " Hz";
    }

    /// Libellé d'affichage du statut de revue (partagé avec la colonne « Statut » de la vue).
    public static String libelleStatut(StatutObservation statut) {
        return switch (statut) {
            case NON_TOUCHEE -> "À revoir";
            case VALIDEE -> "Validée";
            case CORRIGEE -> "Corrigée";
        };
    }

    /// Comparateur de tri de la colonne « Proba. » : ordonne selon la **valeur numérique** du pourcentage
    /// affiché (« 100 % » > « 83 % »), et non alphabétiquement (où « 100 % » précèderait « 83 % »). Une
    /// probabilité absente (« — ») est classée avant toute valeur (traitée comme -1).
    public static Comparator<String> comparateurPourcentage() {
        return Comparator.comparingInt(FormatLigneAudio::premierEntierOuMoinsUn);
    }

    /// Comparateur de tri de la colonne « Passage » : ordonne selon le **numéro** (« N°2 » < « N°10 »), et
    /// non alphabétiquement.
    public static Comparator<String> comparateurNumeroPassage() {
        return Comparator.comparingInt(FormatLigneAudio::premierEntierOuMoinsUn);
    }

    /// Comparateur de tri de la colonne « Fréquence » : ordonne selon la valeur en Hz (« 9000 Hz » <
    /// « 45000 Hz »), et non alphabétiquement ; absente (« — ») classée en tête.
    public static Comparator<String> comparateurFrequence() {
        return Comparator.comparingInt(FormatLigneAudio::premierEntierOuMoinsUn);
    }

    /// Comparateur de tri de la colonne « Statut » : ordonne selon l'**ordre de revue** (À revoir → Validée
    /// → Corrigée), l'ordre naturel de [StatutObservation], plutôt qu'alphabétiquement.
    public static Comparator<String> comparateurStatut() {
        return Comparator.comparingInt(FormatLigneAudio::ordreStatut);
    }

    /// Premier entier lu dans l'affichage (chiffres extraits : « 83 % » → 83, « N°10 » → 10), ou -1 si
    /// aucun chiffre (« — », vide, nul). Support des comparateurs numériques ci-dessus.
    private static int premierEntierOuMoinsUn(String affichage) {
        if (affichage == null) {
            return -1;
        }
        String chiffres = affichage.replaceAll("\\D", "");
        return chiffres.isEmpty() ? -1 : Integer.parseInt(chiffres);
    }

    /// Rang de revue d'un libellé de statut (inverse de [#libelleStatut(StatutObservation)]), ou -1 si le
    /// libellé n'est pas reconnu.
    private static int ordreStatut(String libelle) {
        for (StatutObservation statut : StatutObservation.values()) {
            if (libelleStatut(statut).equals(libelle)) {
                return statut.ordinal();
            }
        }
        return -1;
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
