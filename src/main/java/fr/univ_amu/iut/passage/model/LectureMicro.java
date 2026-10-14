package fr.univ_amu.iut.passage.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/// Lecture du matériel micro d'une participation (`configuration.micro0_*`), pour le passage local.
///
/// La position se lit sans tenir compte de la casse ni des blancs (#6228), la hauteur par
/// [LectureHauteur] (#6211). Une valeur renseignée qu'aucune des deux ne traduit, comme la position
/// « hauteur » ou la hauteur « 2-3 », reste vide localement et se nomme dans [MaterielRelu#nonRelus],
/// pour que la récupération et la reconstruction l'annoncent.
final class LectureMicro {

    private LectureMicro() {}

    /// Le matériel micro relu de `configuration`, et ce que la lecture n'a pas su prendre.
    static MaterielRelu depuis(long idPassage, Map<String, String> configuration) {
        List<String> nonRelus = new ArrayList<>();
        String positionSaisie = configuration.get("micro0_position");
        PositionMicro position = PositionMicro.depuisTexte(positionSaisie);
        if (position == null && renseignee(positionSaisie)) {
            nonRelus.add("position du micro « " + positionSaisie.strip() + " »");
        }
        String hauteurSaisie = configuration.get("micro0_hauteur");
        Double hauteur = LectureHauteur.enMetres(hauteurSaisie).orElse(null);
        if (hauteur == null && renseignee(hauteurSaisie)) {
            nonRelus.add("hauteur du micro « " + hauteurSaisie.strip() + " »");
        }
        return new MaterielRelu(
                new MaterielMicro(idPassage, position, hauteur, configuration.get("micro0_type")), nonRelus);
    }

    private static boolean renseignee(String valeur) {
        return valeur != null && !valeur.isBlank();
    }
}
