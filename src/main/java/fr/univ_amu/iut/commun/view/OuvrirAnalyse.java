package fr.univ_amu.iut.commun.view;

/// Contrat socle d'**ouverture de la vue transverse « Espèces & observations »** (`analyse`).
///
/// Introduit pour le **retour** depuis la vue audio unifiée (#audio) : quand l'écran audio est ouvert sur
/// une source `ParEspece` (les observations d'une espèce, depuis l'analyse), son fil d'Ariane propose un
/// segment « Espèces & observations » cliquable qui **rouvre l'analyse** sans que `audio.view` dépende de
/// `analyse.view` (le socle inverse la dépendance, comme `OuvrirSite`/`OuvrirPassage`). La feature
/// `analyse` en fournit l'implémentation (`NavigationAnalyse`) et la lie dans son module.
public interface OuvrirAnalyse {

    /// Ouvre (ou rouvre) l'écran « Espèces & observations » dans la zone centrale du chrome.
    void ouvrir();
}
