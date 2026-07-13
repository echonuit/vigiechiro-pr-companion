package fr.univ_amu.iut.passage.viewmodel;

import fr.univ_amu.iut.commun.model.StatutWorkflow;
import fr.univ_amu.iut.commun.viewmodel.EtatEtape;
import java.util.ArrayList;
import java.util.List;

/// Projections **pures** du statut workflow pour l'écran M-Passage : les jalons du stepper et la
/// prochaine action recommandée. Extraites de [PassageViewModel] pour qu'il garde une seule
/// responsabilité (porter l'état observable de l'écran) et reste cohésif (PMD GodClass).
final class EtapesWorkflow {

    private EtapesWorkflow() {}

    /// Étapes du stepper : les 5 statuts **jalons** du workflow. Le statut technique « Dépôt en
    /// cours » (#980) n'est pas un jalon : tant que le dépôt automatique n'est pas terminé, le jalon
    /// courant reste « Prêt à déposer » (le détail du dépôt — unités téléversées, reprise — vit dans
    /// M-Lot).
    static List<EtapeWorkflow> construire(StatutWorkflow courant) {
        StatutWorkflow jalon = courant == StatutWorkflow.DEPOT_EN_COURS ? StatutWorkflow.PRET_A_DEPOSER : courant;
        List<EtapeWorkflow> liste = new ArrayList<>();
        for (StatutWorkflow etape : StatutWorkflow.values()) {
            if (etape == StatutWorkflow.DEPOT_EN_COURS) {
                continue;
            }
            liste.add(new EtapeWorkflow(etape, etatDe(etape, jalon)));
        }
        return liste;
    }

    private static EtatEtape etatDe(StatutWorkflow etape, StatutWorkflow jalon) {
        if (etape.ordinal() < jalon.ordinal()) {
            return EtatEtape.FRANCHIE;
        }
        return etape == jalon ? EtatEtape.COURANTE : EtatEtape.A_VENIR;
    }

    /// Prochaine action recommandée selon le statut (progression linéaire du workflow) : la carte
    /// correspondante est mise en avant dans M-Passage.
    static ActionRecommandee prochaineAction(StatutWorkflow statut) {
        return switch (statut) {
            case IMPORTE -> ActionRecommandee.AUCUNE;
            case TRANSFORME -> ActionRecommandee.VERIFIER;
            // « Dépôt en cours » (#980) : un dépôt interrompu se reprend depuis M-Lot → même mise en
            // avant que « déposer » (la carte Lot porte la reprise).
            case VERIFIE, PRET_A_DEPOSER, DEPOT_EN_COURS -> ActionRecommandee.DEPOSER;
            case DEPOSE -> ActionRecommandee.VALIDER;
        };
    }
}
