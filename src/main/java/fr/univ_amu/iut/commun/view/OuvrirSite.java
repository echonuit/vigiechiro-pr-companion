package fr.univ_amu.iut.commun.view;

/// Contrat de navigation inter-feature : « ouvrir la feature sites » (liste « Mes sites » ou détail
/// d'un carré donné).
///
/// Défini dans le socle (`commun.view`) pour qu'un autre écran — typiquement **M-Passage** — propose
/// dans son **fil d'Ariane** des segments « Mes sites » et « Carré N » **cliquables**, sans dépendre du
/// `view` de la feature `sites` (règle ArchUnit `pas_de_dependance_inter_feature_vers_la_vue`). La
/// feature `sites` en fournit l'implémentation (`NavigationSites`, bindée par `SitesModule`). Même
/// esprit que [OuvrirPassage].
public interface OuvrirSite {

    /// Ouvre la liste « Mes sites » (racine de la feature sites).
    void ouvrirListe();

    /// Ouvre le détail du site identifié par son `numeroCarre` (sans effet si introuvable).
    void ouvrirDetail(String numeroCarre);
}
