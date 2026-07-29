package fr.univ_amu.iut.commun.model;

/// Les trois quantiles d'une espèce pour une déclinaison donnée du référentiel d'activité (#2351), et
/// ce qu'ils valent.
///
/// **Les quantiles s'affichent à côté de la classe**, jamais seuls derrière elle. Une classe seule est
/// un verdict ; une classe accompagnée de « Q75 = 480 · Q98 = 1 240 » est une lecture que l'utilisateur
/// peut contester — ce qui est exactement ce qu'on attend d'un outil scientifique.
///
/// @param q25 premier quartile : en dessous, l'activité est faible
/// @param q75 troisième quartile : au-delà, l'activité est forte
/// @param q98 98e centile : au-delà, l'activité est très forte
/// @param occurrences nombre de nuits ayant servi au calcul
/// @param confiance ce que le référentiel dit de la solidité de ces seuils
/// @param declinaison la déclinaison retenue, telle qu'elle se nomme dans la ressource
///     (`national`, `region:…`, `habitat:…`) — à afficher, pour que l'utilisateur sache **à quoi** son
///     nombre a été comparé
/// @param saison la saison retenue (`toutes`, `printemps`, `ete`, `automne`)
public record SeuilsActivite(
        int q25, int q75, int q98, int occurrences, ConfianceReferentiel confiance, String declinaison, String saison) {

    /// Ces seuils sont-ils assez solides pour qu'on s'arrête là ? La règle de repli retient la
    /// **première déclinaison fiable**, pas la plus fine.
    public boolean fiable() {
        return confiance.fiable();
    }

    /// Faut-il accompagner la classe d'une mention **(indicatif)** ? Vrai quand on n'a trouvé que des
    /// seuils peu fiables : on les montre plutôt que de ne rien dire, mais on ne les présente pas
    /// comme une mesure.
    public boolean indicatif() {
        return !confiance.fiable();
    }
}
