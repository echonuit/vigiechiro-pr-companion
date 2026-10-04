package fr.univ_amu.iut.lot.viewmodel;

import fr.univ_amu.iut.commun.model.StatutWorkflow;
import fr.univ_amu.iut.commun.viewmodel.EtatEtape;
import java.util.ArrayList;
import java.util.List;

/// Calcule le **stepper du dépôt** (#251) : les 4 étapes ordonnées (① Préparer · ② Générer les
/// archives · ③ Téléverser · ④ Marquer déposé) avec leur état d'avancement (franchie / courante / à
/// venir), déduit du statut workflow et de la génération d'archives. Pur (aucun état JavaFX), extrait de
/// [LotViewModel] pour garder le ViewModel mince.
///
/// **L'étape ② n'est plus un passage obligé** (#1998). Depuis que le téléversement produit lui-même les
/// archives dont il a besoin, au fil de l'eau et en les libérant (#1995), générer d'abord ne sert plus
/// qu'au **dépôt manuel**, donc hors connexion. Le stepper le dit : connecté, l'étape courante après la
/// préparation est ③ « Téléverser ».
final class EtapesDepot {

    private static final List<String> LIBELLES =
            List.of("Préparer", "Générer les archives", "Téléverser", "Marquer déposé");

    /// Les étapes d'un dépôt qui ne produit aucune archive : connecté, en séquences WAV (#5824).
    private static final List<String> LIBELLES_SANS_ARCHIVES = List.of("Préparer", "Téléverser", "Marquer déposé");

    private EtapesDepot() {}

    /// Étapes ordonnées avec leur état relatif à l'étape courante (préfixées de leur rang « N · »).
    ///
    /// @param statut statut workflow du passage
    /// @param archivesGenerees `true` si des archives ont déjà été générées dans la session
    /// @param depotAutomatiqueDisponible `true` si l'application est connectée (étape ③ atteignable
    ///     directement, l'étape ② n'étant plus qu'une option pour le dépôt manuel)
    /// Les étapes telles que l'écran les offre (#5824) : sans celle des archives quand elle ne sert pas,
    /// c'est-à-dire connecté pour un dépôt en séquences WAV. Les rangs se suivent alors de 1 à 3.
    ///
    /// @param etapeArchivesOfferte `false` quand l'étape « Générer les archives » n'a pas lieu d'être
    static List<EtapeDepot> calculer(
            StatutWorkflow statut,
            boolean archivesGenerees,
            boolean depotAutomatiqueDisponible,
            boolean etapeArchivesOfferte) {
        if (etapeArchivesOfferte) {
            return calculer(statut, archivesGenerees, depotAutomatiqueDisponible);
        }
        return numeroter(LIBELLES_SANS_ARCHIVES, rangCourantSansArchives(statut));
    }

    /// Rang (1..3) de l'étape courante quand celle des archives est absente, ou 4 quand tout est accompli.
    private static int rangCourantSansArchives(StatutWorkflow statut) {
        if (statut.estSurLaPlateforme()) {
            return LIBELLES_SANS_ARCHIVES.size() + 1;
        }
        return statut == StatutWorkflow.DEPOT_EN_COURS || statut == StatutWorkflow.PRET_A_DEPOSER ? 2 : 1;
    }

    private static List<EtapeDepot> numeroter(List<String> libelles, int courante) {
        List<EtapeDepot> etapes = new ArrayList<>(libelles.size());
        for (int i = 0; i < libelles.size(); i++) {
            int rang = i + 1;
            EtatEtape etat =
                    rang < courante ? EtatEtape.FRANCHIE : rang == courante ? EtatEtape.COURANTE : EtatEtape.A_VENIR;
            etapes.add(new EtapeDepot(rang + " · " + libelles.get(i), etat));
        }
        return etapes;
    }

    static List<EtapeDepot> calculer(
            StatutWorkflow statut, boolean archivesGenerees, boolean depotAutomatiqueDisponible) {
        return numeroter(LIBELLES, rangCourant(statut, archivesGenerees, depotAutomatiqueDisponible));
    }

    /// Rang (1..4) de l'étape courante, ou 5 quand tout est accompli (passage déposé). L'étape ③
    /// « Téléverser » (manuelle) ne devient courante qu'une fois des archives générées ; sinon on en est
    /// encore à ② « Générer les archives ». Un dépôt automatique **en cours ou interrompu** (#980) reste
    /// à l'étape ③ (le téléversement n'est pas terminé, reprenable). Une alerte bloquante (R14) à
    /// l'étape ① est signalée à part.
    private static int rangCourant(
            StatutWorkflow statut, boolean archivesGenerees, boolean depotAutomatiqueDisponible) {
        if (statut.estSurLaPlateforme()) {
            // Une nuit récupérée est au bout de ce chemin sans l'avoir parcouru (#2581) : rien de ce
            // stepper ne la concerne, et l'afficher à l'étape ① « Vérifier » inviterait à préparer le
            // dépôt d'une nuit déjà déposée.
            return 5;
        }
        if (statut == StatutWorkflow.DEPOT_EN_COURS) {
            return 3;
        }
        if (statut == StatutWorkflow.PRET_A_DEPOSER) {
            // Connecté : le téléversement génère lui-même ce dont il a besoin (#1995), donc l'étape ③ est
            // atteignable sans passer par ② : qui ne sert plus qu'à préparer un dépôt manuel. Annoncer
            // « Générer les archives » comme étape courante ferait attendre pour rien.
            return depotAutomatiqueDisponible || archivesGenerees ? 3 : 2;
        }
        return 1;
    }
}
