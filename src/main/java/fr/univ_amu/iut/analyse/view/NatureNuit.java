package fr.univ_amu.iut.analyse.view;

import java.util.List;
import java.util.Set;

/// La **nature d'une nuit** telle qu'elle se lit dans un filtre des vues agrégées (#2614) : nuit du
/// **protocole**, ou **participation opportuniste** réalisée sur le carré d'un tiers (#2525).
///
/// Une nuit opportuniste porte les mêmes données qu'une autre, mais ne compte pas de la même façon :
/// elle est exemptée de R3 (fenêtre calendaire) et R4 (intervalle conseillé), et sort du solde de
/// saison. Sans cette dimension, elle se mêlait sans le dire aux nuits du protocole dans l'activité
/// comme dans l'inventaire.
///
/// Helper partagé par les deux catalogues de critères de ce paquet ([CriteresActivite],
/// [CriteresAnalyse]) : les deux écrans filtrent des lignes différentes, mais nomment la même chose.
final class NatureNuit {

    /// Libellé de la nuit menée dans le cadre du protocole : le cas courant.
    static final String PROTOCOLE = "Protocole";

    /// Libellé de la participation opportuniste (#2525).
    static final String OPPORTUNISTE = "Opportuniste";

    /// Les deux natures, dans l'ordre où la liste déroulante les propose.
    static final List<String> VALEURS = List.of(PROTOCOLE, OPPORTUNISTE);

    private NatureNuit() {}

    /// La nature de la nuit d'où vient une ligne, d'après l'ensemble des passages **marqués**
    /// opportunistes.
    ///
    /// L'absence de marquage vaut **protocole** : c'est le sens même de la table de présence
    /// `passage_opportuniste` (V34), où seule l'exception coûte une ligne. Une ligne sans passage
    /// rattaché (`idPassage` nul) suit la même règle plutôt que de disparaître des deux lectures : un
    /// filtre ne doit pas escamoter en silence les lignes qu'il ne sait pas classer.
    static String de(Long idPassage, Set<Long> opportunistes) {
        return idPassage != null && opportunistes.contains(idPassage) ? OPPORTUNISTE : PROTOCOLE;
    }
}
