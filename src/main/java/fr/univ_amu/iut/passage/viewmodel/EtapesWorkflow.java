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
        StatutWorkflow jalon = jalonDe(courant);
        List<EtapeWorkflow> liste = new ArrayList<>();
        for (StatutWorkflow etape : StatutWorkflow.values()) {
            if (etape == StatutWorkflow.DEPOT_EN_COURS || etape == StatutWorkflow.RECUPERE) {
                continue;
            }
            liste.add(new EtapeWorkflow(etape, etatDe(etape, jalon)));
        }
        return liste;
    }

    /// Le jalon que le stepper met en avant pour un statut donné.
    ///
    /// Deux statuts ne sont pas des jalons. « Dépôt en cours » (#980) est technique : le jalon reste
    /// « Prêt à déposer » tant que le téléversement n'est pas fini. « Récupéré » (#2581) n'est pas
    /// sur ce chemin du tout : la nuit n'a franchi aucune de ces étapes, elle est arrivée de la
    /// plateforme - où elle **est** déposée, d'où le jalon retenu.
    ///
    /// ⚠️ **Approximation assumée, et bornée.** Marquer « Déposé » fait apparaître les étapes
    /// précédentes comme franchies, ce qu'elles ne sont pas. C'est moins faux que l'alternative
    /// mécanique - `RECUPERE` étant la dernière valeur de l'énumération, le calcul par `ordinal()`
    /// donnerait « tout franchi » sans même que « Déposé » soit courante - mais ça reste une
    /// approximation. La représentation juste (stepper à part, ou jalon distinct) est l'objet de
    /// **#2774** : c'est un choix d'affichage, pas de modèle.
    private static StatutWorkflow jalonDe(StatutWorkflow courant) {
        return switch (courant) {
            case DEPOT_EN_COURS -> StatutWorkflow.PRET_A_DEPOSER;
            case RECUPERE -> StatutWorkflow.DEPOSE;
            default -> courant;
        };
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
            case RECUPERE -> ActionRecommandee.REACTIVER;
        };
    }
}
