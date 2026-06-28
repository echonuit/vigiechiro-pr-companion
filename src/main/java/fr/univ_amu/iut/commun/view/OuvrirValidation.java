package fr.univ_amu.iut.commun.view;

import fr.univ_amu.iut.commun.viewmodel.ContextePassage;

/// Contrat socle d'**ouverture de la validation Tadarida** (M-Vision-Tadarida) d'un passage.
///
/// Même esprit que [OuvrirVerification] / [OuvrirDiagnostic] : le socle inverse la dépendance.
/// La feature `validation` en fournit l'implémentation (`NavigationValidation`) et la lie dans son
/// module ; la feature `passage` (M-Passage, carte « Validation Tadarida ») l'injecte pour ouvrir
/// l'écran **sans dépendre** de `validation.view` (le graphe de slices reste acyclique).
public interface OuvrirValidation {

    /// Ouvre l'écran de validation taxonomique des résultats Tadarida du passage décrit par `passage`
    /// (identité + contexte site, pour le fil d'Ariane). Méthode principale du contrat (SAM) : les
    /// appelants historiques (M-Passage) et les doublures de test la fournissent telle quelle.
    void ouvrir(ContextePassage passage);

    /// Ouvre la validation du `passage`, **pré-focalisée sur l'observation** `idObservationCible` si elle
    /// est non nulle : la ligne correspondante est sélectionnée à l'ouverture (ce qui déclenche l'écoute
    /// de sa séquence), prête à être validée/corrigée. Permet d'arriver depuis l'écran transverse
    /// « Espèces & observations » droit sur la détection à réécouter.
    ///
    /// Par défaut, la cible est **ignorée** (ouverture classique) : seules les implémentations réelles
    /// (`NavigationValidation`) la prennent en compte, ce qui évite d'alourdir les doublures de test.
    default void ouvrir(ContextePassage passage, Long idObservationCible) {
        ouvrir(passage);
    }
}
