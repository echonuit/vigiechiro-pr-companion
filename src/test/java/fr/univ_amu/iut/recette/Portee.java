package fr.univ_amu.iut.recette;

/// **Où se lit le verdict** d'un cas de recette, et donc ce qu'un clip peut en prouver (#4142).
///
/// ## Pourquoi cette question a sa place dans l'annotation
///
/// L'EPIC #4133 pose qu'un cas de recette se filme par défaut. La règle a une limite : une partie des
/// cas ne porte **pas** sur ce que l'application affiche, mais sur ce qui arrive à l'autre bout - le
/// dépôt reçu par Vigie-Chiro, la carte SD réelle, la nuit rapatriée du serveur, l'installeur sur un
/// vrai poste, la commande dans un vrai terminal. Environ 220 des 360 cas non couverts sont dans ce
/// cas.
///
/// Filmer ceux-là avec une frontière bouchonnée donne un clip **convaincant et creux** : l'écran fait ce
/// qu'on attend, et rien de ce que le cas existe pour vérifier n'a eu lieu. Le clip ne devient pas faux,
/// il devient **muet sur son propre objet** - ce qui est pire, parce qu'on le regarde en croyant savoir.
///
/// ## Ce que la portée déclenche
///
/// [HORS_APPLICATION] oblige à écrire une **réserve** : la phrase qui dit ce que le clip ne prouve pas.
/// [CasDeRecette#reserve()] la porte, un garde l'exige, et la page des clips la rend visible à qui
/// regarde. Sans elle, le clip ment par omission.
public enum Portee {

    /// Le verdict se lit **à l'écran** : un badge, un grisé, un message, une table, un enchaînement.
    ///
    /// C'est le cas par défaut du produit, et celui où un clip prouve vraiment quelque chose : ce que
    /// le cas demande de constater est ce que la caméra enregistre.
    A_L_ECRAN,

    /// Le verdict se lit **hors de l'application**, et le scénario ne peut qu'en jouer le bord.
    ///
    /// ⚠️ Un clip reste utile - montrer le geste côté produit a de la valeur - mais il faut dire ce
    /// qu'il laisse dehors. `S4-33` le fait de lui-même depuis #4126, et c'est le modèle : « il ne
    /// prouve pas que l'écran **atteint** cet état ».
    HORS_APPLICATION
}
