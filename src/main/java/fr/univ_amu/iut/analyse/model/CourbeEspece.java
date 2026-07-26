package fr.univ_amu.iut.analyse.model;

import java.util.List;

/// La **courbe d'activité d'une espèce** sur une nuit (#2352) : ses tranches non vides en ordre
/// chronologique, plus son **total** de contacts. Le total classe les espèces (les cinq plus contactées
/// sont sélectionnées par défaut à l'écran, au-delà le graphe devient illisible) ; les [PointActivite]
/// tracent la ligne.
///
/// Seules les tranches **non vides** sont portées : une espèce absente d'une tranche n'y produit pas de
/// point à zéro. C'est à la vue de décider comment relier les points.
///
/// @param taxon code du taxon retenu
/// @param nomEspece nom vernaculaire de l'espèce retenue, ou `null` (la vue affiche alors le code)
/// @param groupe nom du groupe taxonomique parent, ou `null`
/// @param total nombre total de contacts de l'espèce sur la nuit (somme des tranches)
/// @param points tranches non vides, triées par début croissant
public record CourbeEspece(String taxon, String nomEspece, String groupe, int total, List<PointActivite> points) {}
