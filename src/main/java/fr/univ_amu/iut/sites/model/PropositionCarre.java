package fr.univ_amu.iut.sites.model;

import fr.univ_amu.iut.commun.model.CarreCandidat;
import fr.univ_amu.iut.commun.model.CarroyageNational;
import java.util.List;
import java.util.Objects;

/// Propose un carré depuis une position **collée depuis une carte** (#4577).
///
/// Enchaîne deux lectures qui ne demandent **rien au réseau** : [PositionCollee] pour le texte, puis le
/// carroyage embarqué pour la géométrie. « Quel carré couvre cette position » est une question de
/// géométrie ; « ce carré existe-t-il en Point Fixe » en est une autre, et c'est le bouton
/// « Vérifier sur Vigie-Chiro » qui la pose.
public final class PropositionCarre {

    /// Écart de distance en deçà duquel deux carrés ne se départagent pas.
    ///
    /// Dérivé de la géométrie plutôt que choisi : pour un point à `x` mètres d'un bord, l'écart entre
    /// les deux distances vaut environ `2x`. Cinquante mètres désignent donc les points à moins de 25 m
    /// d'une frontière, l'ordre de grandeur de ce qu'on vise en cliquant sur une carte.
    ///
    /// La stricte inégalité ne se distingue pas de son contraire : un écart de 50,0 m *exact* n'est pas
    /// atteignable sur des distances calculées depuis des degrés, et PIT laisse donc survivre la
    /// mutation de cette borne. Ce qui se teste est la VALEUR du seuil, encadrée à 40 m et à 200 m.
    private static final double ECART_INDISCERNABLE_METRES = 50;

    private final CarroyageNational carroyage;

    public PropositionCarre(CarroyageNational carroyage) {
        this.carroyage = Objects.requireNonNull(carroyage, "carroyage");
    }

    /// Ce que ce texte permet de proposer.
    public VerdictProposition pour(String texteColle) {
        LecturePosition lecture = PositionCollee.lire(texteColle);
        if (!(lecture instanceof LecturePosition.Lue position)) {
            return new VerdictProposition.PositionIllisible(lecture);
        }
        List<CarreCandidat> candidats = carroyage.candidats(position.latitude(), position.longitude());
        if (candidats.isEmpty()) {
            return new VerdictProposition.HorsGrille();
        }
        List<String> indiscernables = candidats.stream()
                .filter(candidat ->
                        candidat.distanceMetres() - candidats.getFirst().distanceMetres() < ECART_INDISCERNABLE_METRES)
                .map(CarreCandidat::numero)
                .toList();
        return indiscernables.size() == 1
                ? new VerdictProposition.Propose(indiscernables.getFirst())
                : new VerdictProposition.Frontiere(indiscernables);
    }
}
