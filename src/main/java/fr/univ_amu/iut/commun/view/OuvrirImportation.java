package fr.univ_amu.iut.commun.view;

/// Contrat de navigation inter-feature : « ouvrir l'assistant M-Import en pré-rattachant la nuit à
/// un site donné ».
///
/// Défini dans le socle (`commun.view`) pour permettre à `sites` (M-Site-detail) d'ouvrir M-Import
/// **sans dépendre du `view` de la feature `importation`** (règle ArchUnit
/// `pas_de_dependance_inter_feature_vers_la_vue`). La feature `importation` en fournit
/// l'implémentation (`NavigationImportation`, bindée par `ImportationModule`). Même esprit que
/// [OuvrirPassage].
///
/// L'import est une **action contextuelle** : on importe la nuit *d'un site* précis. Le point d'entrée
/// est donc la fiche d'un site (M-Site-detail), pré-rattachée — il n'y a volontairement plus de carte
/// d'accueil « Importer une nuit » (l'ouverture générique sans site reste possible par programme via
/// `NavigationImportation.ouvrir()`, mais n'est plus exposée à l'accueil).
public interface OuvrirImportation {

    /// Ouvre l'assistant « Importer une nuit » avec le site `idSite` déjà sélectionné dans le
    /// rattachement (raccourci depuis la fiche d'un site). Sans effet de pré-sélection si le site
    /// n'appartient pas (ou plus) à l'utilisateur courant.
    void ouvrirPourSite(Long idSite);
}
