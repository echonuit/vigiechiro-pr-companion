package fr.univ_amu.iut.audio.viewmodel;

import fr.univ_amu.iut.validation.model.LigneObservationAudio;
import fr.univ_amu.iut.validation.model.StatutObservation;
import java.util.Comparator;
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

    /// Facteur d'expansion temporelle ×10 du protocole Vigie-Chiro : les séquences transformées sont
    /// ralenties ×10, donc les temps du CSV (et `debutS`/`finS`) sont dans la timeline **transformée**. La
    /// durée **réelle** du cri s'obtient en divisant par ce facteur (cohérent avec `setTimeExpansionFactor`).
    private static final int FACTEUR_EXPANSION_TEMPS = 10;

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
    /// proposé par Tadarida s'il est connu, sinon le **code** (souche hors référentiel).
    public static String tadarida(LigneObservationAudio o) {
        return o.nomTadarida() != null && !o.nomTadarida().isBlank() ? o.nomTadarida() : o.taxonTadarida();
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

    /// Durée **réelle** du cri formatée pour la colonne (« 12 ms »), tiret si les temps sont absents.
    /// Calculée à partir des bornes **transformées** `(finS − debutS)` ramenées à la timeline réelle (÷
    /// [#FACTEUR_EXPANSION_TEMPS]). C'est un discriminant utile là où la fréquence médiane l'est peu.
    public static String dureeColonne(Double debutS, Double finS) {
        if (debutS == null || finS == null) {
            return "—";
        }
        return Math.round((finS - debutS) / FACTEUR_EXPANSION_TEMPS * 1000) + " ms";
    }

    /// Position **réelle** du début du cri dans le fichier, formatée pour la colonne (« 0,03 s »), tiret si
    /// absente. La borne stockée `debutS` est sur la timeline **transformée** ; on la ramène à la timeline
    /// réelle (÷ [#FACTEUR_EXPANSION_TEMPS]). Situe le cri dans le fichier et distingue les lignes d'un même
    /// enregistrement (plusieurs cris → plusieurs positions).
    public static String positionColonne(Double debutS) {
        if (debutS == null) {
            return "—";
        }
        return String.format(Locale.FRENCH, "%.2f s", debutS / FACTEUR_EXPANSION_TEMPS);
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

    /// Comparateur de tri de la colonne « Fréquence » : ordonne selon la valeur en kHz (« 9 kHz » <
    /// « 45 kHz »), et non alphabétiquement ; absente (« — ») classée en tête.
    public static Comparator<String> comparateurFrequence() {
        return Comparator.comparingInt(FormatLigneAudio::premierEntierOuMoinsUn);
    }

    /// Comparateur de tri de la colonne « Durée » : ordonne selon la valeur en ms (« 5 ms » < « 12 ms »),
    /// et non alphabétiquement ; absente (« — ») classée en tête.
    public static Comparator<String> comparateurDuree() {
        return Comparator.comparingInt(FormatLigneAudio::premierEntierOuMoinsUn);
    }

    /// Comparateur de tri de la colonne « Début » : ordonne selon la position réelle (« 0,50 s » < « 3,20 s
    /// »), et non alphabétiquement ; absente (« — ») classée en tête. Le format à deux décimales rend les
    /// chiffres extraits (centièmes de seconde) monotones vis-à-vis de la valeur.
    public static Comparator<String> comparateurPosition() {
        return Comparator.comparingInt(FormatLigneAudio::premierEntierOuMoinsUn);
    }

    /// Comparateur de tri **numérique** commun aux colonnes dont l'affichage est une chaîne préfixée/suffixée
    /// d'un nombre (« 90 % », « 45 kHz », « 0,02 s », « 12 ms », « N°2 ») : ordonne selon le premier entier
    /// lu ([#premierEntierOuMoinsUn]) plutôt qu'alphabétiquement ; valeur absente (« — ») classée en tête.
    /// Les alias par colonne ([#comparateurPourcentage], [#comparateurFrequence], etc.) délèguent tous ici.
    public static Comparator<String> comparateurNumerique() {
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

    private static String frequence(Integer khz) {
        return khz == null ? NON_RENSEIGNE : khz + " kHz";
    }
}
