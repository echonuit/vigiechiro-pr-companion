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
    /// courant reste « Prêt à déposer » (le détail du dépôt (unités téléversées, reprise) vit dans
    /// M-Lot).
    static List<EtapeWorkflow> construire(StatutWorkflow courant) {
        if (courant == StatutWorkflow.RECUPERE) {
            // Une nuit rapatriée n'a parcouru AUCUNE de ces étapes (#2774). Lui dérouler le workflow
            // d'import, avec ses jalons marqués franchis, raconterait un trajet qu'elle n'a pas fait -
            // c'est l'approximation que le lot 1 avait assumée faute de mieux. Un stepper d'une seule
            // étape dit la vérité : voilà où elle en est, et elle n'est venue de nulle part d'ici.
            // Ce qu'il lui manque - son audio - est porté par l'action recommandée, qui désigne
            // « Réactiver ».
            return List.of(new EtapeWorkflow(StatutWorkflow.RECUPERE, EtatEtape.COURANTE));
        }
        StatutWorkflow jalon = jalonDe(courant);
        List<EtapeWorkflow> liste = new ArrayList<>();
        for (StatutWorkflow etape : StatutWorkflow.values()) {
            // C'est l'énumération qui dit ce qui est un jalon, pas cette boucle (#2833). Un statut ajouté
            // reste DEHORS tant qu'on ne l'y met pas - et le `switch` exhaustif de `estJalon()` oblige à
            // répondre pour lui, à la compilation. L'ancienne liste d'exceptions faisait l'inverse :
            // dedans par défaut, et l'oubli était muet.
            if (!etape.estJalon()) {
                continue;
            }
            liste.add(new EtapeWorkflow(etape, etatDe(etape, jalon)));
        }
        return liste;
    }

    /// Le jalon que le stepper met en avant pour un statut donné.
    ///
    /// « Dépôt en cours » (#980) n'est pas un jalon mais un état technique : le jalon reste « Prêt à
    /// déposer » tant que le téléversement n'est pas fini. « Récupéré » ne passe pas ici - il a son
    /// propre stepper, cf. [#construire].
    private static StatutWorkflow jalonDe(StatutWorkflow courant) {
        return courant == StatutWorkflow.DEPOT_EN_COURS ? StatutWorkflow.PRET_A_DEPOSER : courant;
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
