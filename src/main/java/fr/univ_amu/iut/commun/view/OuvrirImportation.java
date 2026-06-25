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
/// L'ouverture globale (sans site pré-sélectionné) reste, elle, déclenchée par la carte d'accueil
/// « Importer une nuit » (`ActiviteImporterNuit`).
public interface OuvrirImportation {

    /// Ouvre l'assistant « Importer une nuit » avec le site `idSite` déjà sélectionné dans le
    /// rattachement (raccourci depuis la fiche d'un site). Sans effet de pré-sélection si le site
    /// n'appartient pas (ou plus) à l'utilisateur courant.
    void ouvrirPourSite(Long idSite);
}
