package fr.univ_amu.iut.commun.view;

/// Contrat socle d'**ouverture de la vue transverse « Espèces & observations »** (`analyse`).
///
/// Introduit pour le **retour** depuis la vue audio unifiée (#audio) : quand l'écran audio est ouvert sur
/// une source `ParEspece` (les observations d'une espèce, depuis l'analyse), son fil d'Ariane propose un
/// segment « Espèces & observations » cliquable qui **rouvre l'analyse** sans que `audio.view` dépende de
/// `analyse.view` (le socle inverse la dépendance, comme `OuvrirSite`/`OuvrirPassage`). La feature
/// `analyse` en fournit l'implémentation (`NavigationAnalyse`) et la lie dans son module.
public interface OuvrirAnalyse {

    /// Ouvre (ou rouvre) l'écran « Espèces & observations » dans la zone centrale du chrome, sans filtre
    /// ni carte (comportement historique du fil d'Ariane).
    default void ouvrir() {
        ouvrir(null, false);
    }

    /// Ouvre (ou rouvre) l'analyse en **rejouant un descripteur de filtres** transporté depuis une autre vue
    /// (« Voir sur la carte » depuis l'audio, #476), et en affichant éventuellement la carte de répartition.
    /// Les critères que l'analyse ne connaît pas sont ignorés (cf. [GestionnaireFiltres#restaurer]) : seuls
    /// les critères partagés (statut, groupe) et la recherche texte sont réappliqués.
    ///
    /// @param filtres descripteur de filtres à rejouer, ou `null` pour n'appliquer aucun filtre
    /// @param afficherCarte `true` pour ouvrir directement sur la carte, `false` sur le tableau
    void ouvrir(DescripteurFiltre filtres, boolean afficherCarte);
}
