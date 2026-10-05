package fr.univ_amu.iut.commun.viewmodel;

import fr.univ_amu.iut.commun.api.Traitement;
import fr.univ_amu.iut.commun.model.Horloge;
import fr.univ_amu.iut.commun.model.Horodatage;
import fr.univ_amu.iut.commun.model.ImportApresReleve;
import fr.univ_amu.iut.commun.model.ReleveTraitement;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;

/// Mise en mots de l'état du traitement serveur (#1263) : ce que l'utilisateur lit dans la zone
/// « Traitement Vigie-Chiro » de M-Lot, et dans le bandeau de la vue d'un passage (#5862). Les deux écrans
/// lisent ces phrases ici, pour ne pas dire deux choses du même relevé.
///
/// Fonctions **pures**, séparées du ViewModel : les phrases sont ce que l'on relit le plus souvent, et il
/// vaut mieux pouvoir les éprouver sans monter d'IHM.
///
/// Le vocabulaire est délibérément celui de l'observateur (« analyse », « nuit », « observations ») et
/// non celui de la plateforme (« compute », « donnees », « participation »).
public final class FormatsTraitement {

    /// Au-delà de ce délai, un calcul planifié ou en cours **semble bloqué**. Le site officiel applique la
    /// même heuristique, côté navigateur : le serveur, lui, ne dit jamais qu'il a renoncé.
    private static final Duration TROP_LONG = Duration.ofHours(24);

    private FormatsTraitement() {}

    /// Où en est l'analyse, en une phrase, et ce que cela implique pour l'observateur. Les instants du
    /// serveur se lisent à l'heure de `fuseau`, celui du poste en production (#5683).
    public static String libelle(Traitement traitement, ZoneId fuseau) {
        if (traitement.estInconnu()) {
            return "Analyse non lancée : les observations n'existent pas encore côté Vigie-Chiro.";
        }
        return switch (traitement.etat()) {
            case PLANIFIE ->
                "Analyse planifiée" + le(traitement.datePlanification(), fuseau)
                        + " : elle attend un calculateur. Vous pouvez fermer l'application.";
            case EN_COURS ->
                "Analyse en cours" + depuis(traitement.dateDebut(), fuseau)
                        + ". Comptez plusieurs dizaines de minutes ; vous pouvez fermer l'application.";
            case RETRY ->
                "Un premier essai a échoué : Vigie-Chiro a relancé l'analyse" + essai(traitement) + ". Patientez.";
            case FINI -> "Analyse terminée" + le(traitement.dateFin(), fuseau) + ".";
            case ERREUR ->
                "L'analyse a échoué côté Vigie-Chiro" + le(traitement.dateFin(), fuseau) + "." + trace(traitement);
        };
    }

    /// Les observations de la nuit sont déjà en base : la carte ne réimporte pas (#5784).
    public static final String OBSERVATIONS_DEJA_IMPORTEES = "Les observations de cette nuit sont déjà importées."
            + " Pour les remplacer, passez par « Sons & validation ».";

    /// L'analyse est terminée et rien n'est importé : l'état vient du cache, seul un relevé importe.
    public static final String OBSERVATIONS_A_IMPORTER = "Cliquez « Actualiser » pour importer les observations.";

    /// Ce qu'un relevé a fait des observations (#5784). Vide quand il n'y avait rien à en faire.
    ///
    /// @param geste le nom du bouton qui relève, tel que l'écran appelant le montre : la phrase d'échec
    ///     invite à le cliquer de nouveau, et un écran ne nomme pas le bouton d'un autre (#5862)
    public static String importObservations(ImportApresReleve.Issue issue, String geste) {
        return switch (issue) {
            case ImportApresReleve.Issue.SansObjet sansObjet -> "";
            case ImportApresReleve.Issue.Fait fait -> fait.compteRendu();
            case ImportApresReleve.Issue.DejaLa dejaLa -> OBSERVATIONS_DEJA_IMPORTEES;
            case ImportApresReleve.Issue.Echoue echoue ->
                "L'import des observations a échoué : " + echoue.motif() + ". Cliquez de nouveau « " + geste
                        + " », ou importez depuis « Sons & validation ».";
        };
    }

    /// « Dernier état connu le … » : la fraîcheur de l'information, que l'on doit à l'utilisateur, surtout
    /// hors connexion, où l'écran affiche un souvenir et non une vérité.
    public static String fraicheur(ReleveTraitement releve, ZoneId fuseau) {
        return "Dernier état connu le " + lisible(releve.releveLe(), fuseau) + ".";
    }

    /// Avertissement quand le calcul **traîne** (plus de 24 h) : le serveur ne signale jamais qu'il a
    /// renoncé, c'est donc à nous de le suggérer. Chaîne vide s'il n'y a rien à signaler.
    public static String alerte(Traitement traitement, Horloge horloge) {
        if (traitement.estInconnu() || !traitement.enAttente()) {
            return "";
        }
        String debut = traitement.etat() == fr.univ_amu.iut.commun.api.EtatTraitement.PLANIFIE
                ? traitement.datePlanification()
                : traitement.dateDebut();
        return traineDepuis(debut, horloge)
                ? "Cette analyse dure depuis plus de 24 h : elle semble bloquée. Relancez-la avec"
                        + " « Lancer la participation »."
                : "";
    }

    /// L'analyse a-t-elle démarré il y a plus de 24 h ? Date illisible ou absente → on ne présume rien.
    private static boolean traineDepuis(String date, Horloge horloge) {
        if (date == null) {
            return false;
        }
        try {
            OffsetDateTime debut = OffsetDateTime.parse(date);
            OffsetDateTime maintenant = horloge.maintenant().atOffset(ZoneOffset.UTC);
            return Duration.between(debut, maintenant).compareTo(TROP_LONG) > 0;
        } catch (DateTimeParseException illisible) {
            return false;
        }
    }

    private static String le(String date, ZoneId fuseau) {
        return date == null ? "" : " le " + lisible(date, fuseau);
    }

    private static String depuis(String date, ZoneId fuseau) {
        return date == null ? "" : " depuis le " + lisible(date, fuseau);
    }

    private static String essai(Traitement traitement) {
        return traitement.retry() == null ? "" : " (essai n° " + traitement.retry() + ")";
    }

    /// Motif de l'échec, rendu par le [Traitement] lui-même (une seule extraction pour toute l'application).
    private static String trace(Traitement traitement) {
        return traitement.motifCourt().map(motif -> " Motif : " + motif).orElse("");
    }

    /// Date lisible par un humain, à l'heure de `fuseau`, ou la date brute si elle ne se laisse pas lire
    /// (on n'invente rien). Une heure sans décalage est déjà locale, et se lit telle quelle.
    private static String lisible(String date, ZoneId fuseau) {
        return Horodatage.instantDansUnePhrase(date, fuseau);
    }
}
