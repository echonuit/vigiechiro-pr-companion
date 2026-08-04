package fr.univ_amu.iut.commun.api;

import java.net.http.HttpTimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;

/// **Comment un échange réseau se consigne** (#1845), extrait de `TransportVigieChiro` où cette
/// question cohabitait avec « comment un échange s'émet ».
///
/// Le journal était **muet sur le réseau** : face à « l'application dit *envoyées* mais la plateforme
/// n'affiche rien » (#1844), il ne permettait de trancher aucune hypothèse, et le diagnostic a dû se
/// faire en lisant les sources de l'API.
///
/// Ce qui est consigné : méthode, **chemin**, issue, durée. Ce qui ne l'est **jamais** : le jeton et
/// les en-têtes (le secret ne doit pas fuir dans un journal joint à un rapport d'anomalie), le corps
/// **envoyé**, et l'URL complète - une URL S3 pré-signée porte sa signature dans sa requête.
final class JournalEchange {

    private static final Logger LOG = Logger.getLogger(TransportVigieChiro.class.getName());

    /// Longueur retenue du corps d'un refus : assez pour lire l'explication du serveur, pas assez pour
    /// déverser une réponse entière dans le journal.
    private static final int CORPS_REFUS_MAX = 300;

    private JournalEchange() {}

    /// Consigne l'issue d'un échange, avec l'exception qui l'a causée s'il y en a une.
    static void consigner(String methode, String chemin, ReponseApi<String> reponse, long debutNanos, Exception echec) {
        Level niveau = niveauDe(reponse);
        if (!LOG.isLoggable(niveau)) {
            return;
        }
        long millis = (System.nanoTime() - debutNanos) / 1_000_000L;
        if (echec == null) {
            LOG.log(niveau, () -> resume(methode, chemin, reponse, millis));
        } else {
            LOG.log(niveau, echec, () -> resume(methode, chemin, reponse, millis));
        }
    }

    /// Sévérité de l'issue, décidée **à l'émission** (ADR 0008) : un échange nominal - ou un appel non
    /// émis faute de jeton - reste au détail ; une anomalie (refus du serveur, plateforme injoignable)
    /// monte à `WARNING` pour être visible sans avoir à régler quoi que ce soit.
    static Level niveauDe(ReponseApi<String> reponse) {
        return switch (reponse) {
            case ReponseApi.Succes<String> ignore -> Level.FINE;
            case ReponseApi.NonConnecte<String> ignore -> Level.FINE;
            case ReponseApi.Injoignable<String> ignore -> Level.WARNING;
            case ReponseApi.Refuse<String> ignore -> Level.WARNING;
        };
    }

    /// Résumé consigné d'un échange. Le corps d'un **refus** y figure, tronqué : c'est l'explication du
    /// serveur (`_issues`, « invalid field »…), l'élément le plus diagnostique qui soit - et c'est
    /// précisément ce qui manquait pour comprendre #1844.
    static String resume(String methode, String chemin, ReponseApi<String> reponse, long millis) {
        return "Vigie-Chiro " + methode + " " + chemin + " → " + issue(reponse) + " (" + millis + " ms)";
    }

    /// Cause lisible d'une indisponibilité, pour le message « VigieChiro injoignable : ... ».
    static String cause(Exception indisponible) {
        if (indisponible instanceof HttpTimeoutException) {
            return "délai d'attente dépassé";
        }
        String message = indisponible.getMessage();
        return message == null || message.isBlank() ? indisponible.getClass().getSimpleName() : message;
    }

    private static String issue(ReponseApi<String> reponse) {
        return switch (reponse) {
            case ReponseApi.Succes<String> ignore -> "succès";
            case ReponseApi.NonConnecte<String> ignore -> "non connecté (appel non émis)";
            case ReponseApi.Injoignable<String>(String cause) -> "injoignable : " + cause;
            case ReponseApi.Refuse<String>(int statut, String corps) ->
                "refusé HTTP " + statut + " : " + extrait(corps);
        };
    }

    private static String extrait(String corps) {
        if (corps == null || corps.isBlank()) {
            return "(corps vide)";
        }
        String net = corps.strip();
        return net.length() <= CORPS_REFUS_MAX ? net : net.substring(0, CORPS_REFUS_MAX) + "…";
    }
}
