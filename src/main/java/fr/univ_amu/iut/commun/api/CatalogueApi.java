package fr.univ_amu.iut.commun.api;

import java.util.List;

/// La **carte des lectures** de l'API VigieChiro : quelles ressources existent, par quels chemins on
/// les lit, et ce qu'il faut savoir avant d'essayer.
///
/// ## Pourquoi une carte en code
///
/// Il a fallu écrire du `curl` à la main pour vérifier un fait sur les données réelles, faute de
/// savoir **quoi taper** : la connaissance des chemins vivait dans la prose de
/// `dev-docs/api-vigiechiro.md` et dans la tête de qui avait lu le source. Une carte en code
/// s'affiche (`vigiechiro api ressources`), se compare au source, et **se fait contredire** par les
/// sondes du contrat live. Une carte en prose vieillit sans prévenir.
///
/// ## D'où elle vient
///
/// Extraite du source de l'API (<https://github.com/Scille/vigiechiro-api>, dossier
/// `vigiechiro/resources/`) : **63 routes** déclarées, dont **33 en lecture**, chacune précédée de
/// son rôle requis. La commande d'extraction est consignée dans `dev-docs/api-vigiechiro.md` pour
/// être rejouée quand le miroir bouge.
///
/// ## Ce qu'elle n'est pas
///
/// Elle ne dit pas ce que le serveur **répond aujourd'hui** : un chemin déclaré peut refuser (rôle
/// insuffisant) ou tomber (503). C'est le rôle de `api ressources --sonder`, et des sondes
/// `-Papi-live`, de confronter la carte au réel.
public final class CatalogueApi {

    /// Un chemin de lecture et le rôle qu'il exige. `{id}` marque un identifiant à substituer.
    public record RouteApi(String chemin, String role) {}

    /// Une ressource de l'API : son nom, ses chemins de lecture, et ce qu'il faut en savoir.
    public record RessourceApi(String nom, List<RouteApi> lectures, String note) {

        public RessourceApi {
            lectures = List.copyOf(lectures);
        }
    }

    /// Rôle exigé par **toutes** les lectures de cette API : le source ne distingue les rôles qu'en
    /// écriture (suppression et édition demandent `Administrateur` ou la propriété).
    private static final String OBSERVATEUR = "Observateur";

    private CatalogueApi() {}

    /// Les neuf ressources et leurs chemins de lecture, dans l'ordre où on les rencontre en pratique.
    public static List<RessourceApi> ressources() {
        return List.of(
                new RessourceApi(
                        "sites",
                        List.of(
                                lire("/sites"),
                                lire("/sites/{id}"),
                                lire("/sites/liste"),
                                lire("/moi/sites"),
                                lire("/protocoles/{id}/sites"),
                                lire("/protocoles/{id}/sites/grille_stoc"),
                                lire("/protocoles/{id}/sites/tracet")),
                        "Le catalogue entier est lisible et paginé (20 572 sites au 2026-08-05)."
                                + " ⚠️ « /sites/liste » trompe deux fois : chaque document y est réduit à son"
                                + " « _id » (ni titre ni localité), et son enveloppe n'est pas celle d'Eve"
                                + " (« _items » contient les documents PUIS le total, en deux blocs). Elle ne"
                                + " dispense donc pas de paginer « /sites » pour recenser les points."),
                new RessourceApi(
                        "participations",
                        List.of(
                                lire("/participations"),
                                lire("/participations/{id}"),
                                lire("/participations/{id}/pieces_jointes"),
                                lire("/moi/participations"),
                                lire("/sites/{id}/participations")),
                        "« /moi/participations » embarque le site de chaque participation : c'est de là que"
                                + " le client dérive vos sites, « /moi/sites » filtrant sur le propriétaire (#718)."),
                new RessourceApi(
                        "donnees",
                        List.of(
                                lire("/donnees"),
                                lire("/donnees/{id}"),
                                lire("/donnees/{id}/fichiers"),
                                lire("/participations/{id}/donnees")),
                        "⚠️ La collection nue « /donnees » est déclarée mais répond 503 en pratique :"
                                + " passer par « /participations/{id}/donnees », paginée."),
                new RessourceApi(
                        "taxons",
                        List.of(lire("/taxons"), lire("/taxons/{id}"), lire("/taxons/liste")),
                        "« /taxons/liste » rend le référentiel entier, sans pagination."),
                new RessourceApi(
                        "protocoles",
                        List.of(
                                lire("/protocoles"),
                                lire("/protocoles/{id}"),
                                lire("/protocoles/{id}/observateurs"),
                                lire("/protocoles/liste"),
                                lire("/moi/protocoles")),
                        "Le protocole d'un site (« Vigiechiro - Point Fixe », « Routier »…) détermine la"
                                + " forme de ses localités."),
                new RessourceApi(
                        "utilisateurs",
                        List.of(lire("/utilisateurs"), lire("/utilisateurs/{id}"), lire("/moi")),
                        "« /moi » valide le jeton et rend votre profil : c'est le contrôle de connexion."),
                new RessourceApi(
                        "fichiers",
                        List.of(lire("/fichiers/{id}"), lire("/fichiers/{id}/acces")),
                        "⚠️ Aucune route de collection : « /fichiers » n'existe pas en lecture (d'où son"
                                + " refus). « /acces » rend une URL S3 signée, à suivre SANS en-tête"
                                + " d'authentification."),
                new RessourceApi(
                        "grille_stoc",
                        List.of(lire("/grille_stoc/rectangle"), lire("/grille_stoc/cercle")),
                        "⚠️ Aucune route de collection : la grille s'interroge par emprise"
                                + " (« ?lng=&lat=&r= » pour le cercle), jamais en liste."),
                new RessourceApi(
                        "actualites",
                        List.of(lire("/moi/actualites"), lire("/actualites/validations")),
                        "⚠️ Aucune route de collection : seules les actualités qui vous concernent"
                                + " sont lisibles."));
    }

    /// Pièges communs à **toutes** les lectures, que la carte rappelle là où on la lit.
    public static List<String> pieges() {
        return List.of(
                "« max_results » est plafonné à 100 : au-delà, le serveur rejette la requête (422),"
                        + " il ne tronque pas.",
                "« where= » est accepté puis IGNORÉ en silence : un filtre serveur ne filtre rien,"
                        + " et le total annoncé ne bouge pas. Filtrer se fait chez soi, après lecture.",
                "La pagination est tout-ou-rien : une panne en page 3 rend l'issue, jamais les pages"
                        + " 1 et 2 comme si la collection était complète.");
    }

    private static RouteApi lire(String chemin) {
        return new RouteApi(chemin, OBSERVATEUR);
    }
}
