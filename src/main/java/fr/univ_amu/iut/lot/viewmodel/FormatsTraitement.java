package fr.univ_amu.iut.lot.viewmodel;

import fr.univ_amu.iut.commun.api.Traitement;
import fr.univ_amu.iut.commun.model.Horloge;
import fr.univ_amu.iut.commun.model.ReleveTraitement;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/// Mise en mots de l'état du traitement serveur (#1263) : ce que l'utilisateur lit dans la zone
/// « Traitement Vigie-Chiro » de M-Lot.
///
/// Fonctions **pures**, séparées du ViewModel : les phrases sont ce que l'on relit le plus souvent, et il
/// vaut mieux pouvoir les éprouver sans monter d'IHM.
///
/// Le vocabulaire est délibérément celui de l'observateur — « analyse », « nuit », « observations » — et
/// non celui de la plateforme (« compute », « donnees », « participation »).
final class FormatsTraitement {

    /// Au-delà de ce délai, un calcul planifié ou en cours **semble bloqué**. Le site officiel applique la
    /// même heuristique, côté navigateur : le serveur, lui, ne dit jamais qu'il a renoncé.
    private static final Duration TROP_LONG = Duration.ofHours(24);

    /// Dates du serveur : ISO 8601 avec décalage (`2026-07-13T10:05:00+00:00`).
    private static final DateTimeFormatter LISIBLE = DateTimeFormatter.ofPattern("dd/MM/yyyy à HH'h'mm");

    private FormatsTraitement() {}

    /// Où en est l'analyse, en une phrase, et ce que cela implique pour l'observateur.
    static String libelle(Traitement traitement) {
        if (traitement.estInconnu()) {
            return "Analyse non lancée : les observations n'existent pas encore côté Vigie-Chiro.";
        }
        return switch (traitement.etat()) {
            case PLANIFIE ->
                "Analyse planifiée" + le(traitement.datePlanification())
                        + " : elle attend un calculateur. Vous pouvez fermer l'application.";
            case EN_COURS ->
                "Analyse en cours" + depuis(traitement.dateDebut())
                        + ". Comptez plusieurs dizaines de minutes ; vous pouvez fermer l'application.";
            case RETRY ->
                "Un premier essai a échoué : Vigie-Chiro a relancé l'analyse" + essai(traitement) + ". Patientez.";
            case FINI ->
                "Analyse terminée" + le(traitement.dateFin()) + " : les observations sont prêtes à être importées.";
            case ERREUR -> "L'analyse a échoué côté Vigie-Chiro" + le(traitement.dateFin()) + "." + trace(traitement);
        };
    }

    /// « Dernier état connu le … » : la fraîcheur de l'information, que l'on doit à l'utilisateur — surtout
    /// hors connexion, où l'écran affiche un souvenir et non une vérité.
    static String fraicheur(ReleveTraitement releve) {
        return "Dernier état connu le " + lisible(releve.releveLe()) + ".";
    }

    /// Avertissement quand le calcul **traîne** (plus de 24 h) : le serveur ne signale jamais qu'il a
    /// renoncé, c'est donc à nous de le suggérer. Chaîne vide s'il n'y a rien à signaler.
    static String alerte(Traitement traitement, Horloge horloge) {
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

    private static String le(String date) {
        return date == null ? "" : " le " + lisible(date);
    }

    private static String depuis(String date) {
        return date == null ? "" : " depuis le " + lisible(date);
    }

    private static String essai(Traitement traitement) {
        return traitement.retry() == null ? "" : " (essai n° " + traitement.retry() + ")";
    }

    /// Trace du serveur, tronquée : c'est une pile Python, pas un message pour l'observateur — mais la
    /// masquer entièrement le laisserait sans prise pour demander de l'aide.
    private static String trace(Traitement traitement) {
        String message = traitement.message();
        if (message == null || message.isBlank()) {
            return "";
        }
        String premiere = message.strip().lines().findFirst().orElse("");
        return premiere.isBlank() ? "" : " Motif : " + premiere;
    }

    /// Date lisible par un humain, ou la date brute si elle ne se laisse pas lire (on n'invente rien).
    private static String lisible(String date) {
        try {
            return OffsetDateTime.parse(date).format(LISIBLE);
        } catch (DateTimeParseException maisPeutEtreLocale) {
            try {
                return LocalDateTime.parse(date).format(LISIBLE);
            } catch (DateTimeParseException illisible) {
                return date;
            }
        }
    }
}
