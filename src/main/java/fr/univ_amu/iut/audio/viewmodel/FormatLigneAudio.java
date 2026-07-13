package fr.univ_amu.iut.audio.viewmodel;

import fr.univ_amu.iut.commun.model.CertitudeObservateur;
import fr.univ_amu.iut.validation.model.LigneObservationAudio;
import fr.univ_amu.iut.validation.model.StatutObservation;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

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
                + valeurOuAbsente(o.taxonTadarida())
                + " ("
                + proba(o.probTadarida())
                + ") · Observateur : "
                + valeurOuAbsente(o.taxonObservateur())
                + " ("
                + proba(o.probObservateur())
                + ") · Fréquence médiane : "
                + frequence(o.frequenceKHz())
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
    /// proposé par Tadarida s'il est connu, sinon le **code** (souche hors référentiel), ou **tiret**
    /// « — » pour une séquence non identifiée (aucune proposition Tadarida).
    public static String tadarida(LigneObservationAudio o) {
        return o.nomTadarida() != null && !o.nomTadarida().isBlank() ? o.nomTadarida() : ouTiret(o.taxonTadarida());
    }

    /// Probabilité de détection formatée pour la colonne (« 81 % »), tiret si absente.
    public static String probabilite(Double probabilite) {
        return probabilite == null ? "—" : Math.round(probabilite * 100) + " %";
    }

    /// Fréquence médiane formatée pour la **colonne** (« 52 kHz »), tiret si absente. La valeur Tadarida est
    /// en **kHz** (fréquences de pic des chiroptères : dizaines de kHz), pas en Hz. (Le pendant privé
    /// [#frequence(Integer)] sert au panneau de détail avec le libellé « non renseigné ».)
    public static String frequenceColonne(Integer frequenceKHz) {
        return frequenceKHz == null ? "—" : frequenceKHz + " kHz";
    }

    /// Durée du cri formatée pour la colonne, en unité **adaptative** : millisecondes sous 1 s (« 120 ms »),
    /// secondes au-delà (« 2,1 s ») ; tiret si les temps sont absents. Les bornes `debutS`/`finS` sont **déjà
    /// en secondes réelles** (temps Tadarida au sein de la tranche de 5 s), donc `finS − debutS` est la durée
    /// réelle, **sans** division : l'audio-view affiche la même échelle (axe réel via `setTimeExpansionFactor`).
    public static String dureeColonne(Double debutS, Double finS) {
        if (debutS == null || finS == null) {
            return "—";
        }
        double secondes = finS - debutS;
        if (secondes < 1.0) {
            return Math.round(secondes * 1000) + " ms";
        }
        return String.format(Locale.FRENCH, "%.1f s", secondes);
    }

    /// Position du début du cri dans la tranche, en secondes **réelles** (« 0,40 s »), tiret si absente. La
    /// borne `debutS` est déjà réelle (temps Tadarida), affichée telle quelle — cohérente avec l'axe de
    /// l'audio-view. Situe le cri et distingue les lignes d'un même fichier (plusieurs cris → plusieurs
    /// positions).
    public static String positionColonne(Double debutS) {
        if (debutS == null) {
            return "—";
        }
        return String.format(Locale.FRENCH, "%.2f s", debutS);
    }

    /// Motif d'affichage de l'heure de capture dans la colonne (« 22:37 »).
    private static final DateTimeFormatter HEURE_COLONNE = DateTimeFormatter.ofPattern("HH:mm");

    /// Heure de capture formatée pour la **colonne** (« 22:37 », heure de la nuit extraite de l'instant),
    /// tiret si absente (#530).
    public static String heureColonne(LocalDateTime heureCapture) {
        return heureCapture == null ? "—" : HEURE_COLONNE.format(heureCapture);
    }

    /// Valeur d'une colonne texte, ou **tiret** « — » si absente/blanche (colonnes de contexte de la table).
    public static String ouTiret(String valeur) {
        return valeur == null || valeur.isBlank() ? "—" : valeur;
    }

    /// Libellé de la colonne « Certitude » (#1139) : la déclaration manuelle de l'observateur, ou le
    /// tiret « — » tant qu'elle n'est pas renseignée (vide par défaut, jamais préremplie).
    public static String libelleCertitude(CertitudeObservateur certitude) {
        return certitude == null ? "—" : certitude.libelle();
    }

    /// Libellé d'affichage du statut de revue (partagé avec la colonne « Statut » de la vue).
    public static String libelleStatut(StatutObservation statut) {
        return switch (statut) {
            case NON_TOUCHEE -> "À revoir";
            case VALIDEE -> "Validée";
            case CORRIGEE -> "Corrigée";
        };
    }

    /// Classe CSS du badge de statut de revue (`badge-observation-…`) pour la colonne « Statut » : la
    /// couleur est dérivée du statut, jamais stockée (pendant de [#libelleStatut]). Le mapping vit côté
    /// feature audio, pas dans le socle `ColonneBadge` : `commun` ne doit pas dépendre d'un enum de feature
    /// (`StatutObservation` vit dans `validation.model`), sous peine de cycle d'architecture. Convention
    /// identique à `Fraicheur.classeBadge` / `LignePassage::statutClasseCss`.
    public static String classeBadgeStatut(StatutObservation statut) {
        return "badge-observation-" + statut.name().toLowerCase(Locale.ROOT);
    }

    private static String proba(Double probabilite) {
        return probabilite == null ? NON_RENSEIGNE : Math.round(probabilite * 100) + " %";
    }

    private static String valeurOuAbsente(String code) {
        return code == null || code.isBlank() ? NON_RENSEIGNE : code;
    }

    private static String frequence(Integer khz) {
        return khz == null ? NON_RENSEIGNE : khz + " kHz";
    }
}
