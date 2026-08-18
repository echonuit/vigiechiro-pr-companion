package fr.univ_amu.iut.commun.model;

/// **Port** (inversion de dépendance `connexion` → `lot`) : dire au dépôt qu'un événement extérieur a
/// pu **lever la cause** d'un refus définitif (#3689).
///
/// ## Pourquoi un port, et pas un appel direct
///
/// La feature `connexion` sait quand une connexion aboutit ; elle n'a pas à connaître la table des
/// unités de dépôt. Et `lot` dépend déjà de l'API, donc le sens inverse formerait un cycle - ce
/// qu'ArchUnit refuse. Même montage que [PointsDuCarre] et [ImportObservations] : port déclaré à vide
/// dans `CommunModule`, implémentation réelle posée par la feature qui la porte.
///
/// ## Ce que le réarmement n'est pas
///
/// Ce n'est **pas** un « forcer la reprise ». Une unité ne se réarme que si l'événement pouvait
/// plausiblement lever **sa** cause : une reconnexion réussie répare des droits (401 / 403), elle ne
/// répare pas un contenu refusé (400 / 422). Les trois autres déclencheurs envisagés en #3689 - au
/// lancement, sur un geste, après un délai - réarmaient tous **à côté** de la cause, et auraient
/// ramené le bouton qui promet ce que #3687 vient de lui faire cesser de promettre.
public interface RearmementDepot {

    /// Une connexion vient d'aboutir : les unités refusées pour **authentification** redeviennent
    /// reprenables. Les autres ne bougent pas.
    void reconnexionReussie();

    /// Le port **inerte**, pour les injecteurs qui assemblent l'application sans la feature `lot`
    /// (captures, tests ciblés) : ne rien réarmer est le comportement juste, pas une dégradation.
    static RearmementDepot inerte() {
        return () -> {};
    }
}
