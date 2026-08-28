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
        List<String> indiscernables =
                BandeDesIndiscernables.dans(candidats, BandeDesIndiscernables.POUR_PROPOSER).stream()
                        .map(CarreCandidat::numero)
                        .toList();
        return indiscernables.size() == 1
                ? new VerdictProposition.Propose(indiscernables.getFirst())
                : new VerdictProposition.Frontiere(indiscernables);
    }
}
