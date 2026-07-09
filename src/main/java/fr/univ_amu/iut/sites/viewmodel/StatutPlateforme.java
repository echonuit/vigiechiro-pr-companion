package fr.univ_amu.iut.sites.viewmodel;

/// État d'un site local vis-à-vis de la plateforme VigieChiro (#718), pour le badge de la carte
/// « Mes sites » :
/// - [#ABSENT] : aucune correspondance (le site n'est pas connu de la plateforme) ;
/// - [#ENREGISTRE] : correspondance établie mais site **non** verrouillé (en attente de validation) ;
/// - [#VERROUILLE] : site **verrouillé** côté plateforme — le dépôt d'une participation est possible.
public enum StatutPlateforme {
    ABSENT,
    ENREGISTRE,
    VERROUILLE
}
