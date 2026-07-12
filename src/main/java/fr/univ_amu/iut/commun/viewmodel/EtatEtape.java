package fr.univ_amu.iut.commun.viewmodel;

/// État d'avancement d'une étape dans un **stepper**, relatif à l'étape courante. Partagé par les
/// steppers de M-Passage (workflow d'un passage) et de M-Lot (dépôt) : c'est un état d'IHM générique,
/// hébergé dans `commun` puisque deux features l'utilisent (une feature ne peut pas dépendre du
/// `viewmodel` d'une autre). Le nom en minuscule sert de **suffixe de classe CSS**
/// (`etape-franchie` / `etape-courante` / `etape-a_venir`), rendu par [fr.univ_amu.iut.commun.view.Stepper].
public enum EtatEtape {
    /// Étape déjà accomplie (statut antérieur à l'étape courante).
    FRANCHIE,
    /// Étape en cours (là où se trouve l'observateur, action éventuellement requise).
    COURANTE,
    /// Étape pas encore atteinte.
    A_VENIR
}
