package fr.univ_amu.iut.qualification.view;

import fr.univ_amu.iut.commun.viewmodel.ContextePassage;
import fr.univ_amu.iut.commun.viewmodel.ZonesStatut;
import fr.univ_amu.iut.qualification.viewmodel.QualificationViewModel;
import fr.univ_amu.iut.qualification.viewmodel.SelectionEcouteViewModel;

/// Compose les 3 zones de la barre de statut de **M-Qualification** (#1021, EPIC #1016), sur le même
/// modèle que M-Lot : identité du passage à **gauche**, statut + volumétrie au **centre**, état vivant
/// **prioritaire** à droite (anomalie de pré-check &gt; progression d'écoute). Extrait de
/// [QualificationController] pour le garder sous le plafond de taille (PMD `NcssCount`).
final class StatutQualification {

    private StatutQualification() {}

    static ZonesStatut zones(
            ContextePassage contexte, QualificationViewModel verdict, SelectionEcouteViewModel selection) {
        return new ZonesStatut(gauche(contexte), centre(verdict, selection), droite(verdict, selection));
    }

    private static String gauche(ContextePassage contexte) {
        return contexte == null ? "" : contexte.identiteStatut();
    }

    private static String centre(QualificationViewModel verdict, SelectionEcouteViewModel selection) {
        var statutWorkflow = verdict.statutProperty().get();
        String statut = statutWorkflow == null ? "" : statutWorkflow.libelle();
        String volumetrie = selection.volumetrieProperty().get();
        if (volumetrie == null || volumetrie.isBlank()) {
            return statut;
        }
        return statut.isBlank() ? volumetrie : statut + " · " + volumetrie;
    }

    /// Zone droite = une seule information vivante à la fois : une **anomalie** de pré-check (R13) prime,
    /// sinon la **progression d'écoute** (« N / M écoutées »).
    ///
    /// Sans le « ⚠ » d'antan (#2221) : une barre de statut est **neutre** (ADR 0039), elle dit *où l'on en
    /// est*, pas si c'est bien ou mal. La sévérité de l'anomalie est déjà portée, ailleurs et pleinement,
    /// par l'encart `lblAnomalie` qui nomme le feu en cause (#1506) ; l'écrire une seconde fois ici, en
    /// glyphe, la disait à un endroit qui n'a pas à la porter.
    private static String droite(QualificationViewModel verdict, SelectionEcouteViewModel selection) {
        if (verdict.preCheckAnomalieProperty().get()) {
            return "Anomalie au pré-check";
        }
        return selection.progressionTexteProperty().get();
    }
}
