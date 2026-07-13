package fr.univ_amu.iut.commun.persistence;

/// Port socle : **déclarer en base** la purge globale des originaux (#1303). [ServicePurgeOriginaux]
/// supprime les `bruts/` en balayant la convention de dossiers, volontairement **sans toucher à la
/// base** ; sans déclaration, l'audit prendrait ensuite chaque brut manquant pour une corruption.
/// La feature `passage` (qui possède les sessions) fournit l'implémentation via `OptionalBinder`
/// (même montage que `CoordonneesPoint`, #547) : le chrome consomme l'`Optional`, vide dans les
/// injecteurs partiels.
public interface DeclarationPurgeOriginaux {

    /// Déclare la purge des originaux de **toutes** les sessions (marqueur `originals_purged_at` +
    /// volume à zéro) : à appeler juste après [ServicePurgeOriginaux#purgerTout()].
    void declarerPurgeGlobale();
}
