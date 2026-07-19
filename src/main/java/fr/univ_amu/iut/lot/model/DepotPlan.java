package fr.univ_amu.iut.lot.model;

/// Le plan de depot d'un passage, au niveau du **passage** et non de chaque unite (#1993) : quelle
/// liste source a servi a le poser, et quand.
///
/// [DepotUnite] dit ou en est chaque fichier ; ce record dit **de quoi le plan a ete derive**. La
/// distinction compte a la reprise : retrouver les memes unites ne prouve pas qu'elles designent le
/// meme contenu, puisque les archives sont nommees par leur rang dans une partition qui depend de
/// la liste source ([EmpreinteLot]).
///
/// @param passageId passage dont le depot a ete planifie
/// @param empreinte empreinte de la liste source au moment de la pose ([EmpreinteLot#de])
/// @param poseLe horodatage ISO de la pose du plan
public record DepotPlan(Long passageId, String empreinte, String poseLe) {}
