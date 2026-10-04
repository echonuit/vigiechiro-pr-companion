package fr.univ_amu.iut.lot.viewmodel;

import fr.univ_amu.iut.commun.model.StatutWorkflow;
import fr.univ_amu.iut.lot.model.ModeDepot;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

/// L'**état du stepper** du dépôt (#251) : la liste observable des étapes, et les trois entrées dont
/// leur calcul dépend.
///
/// [EtapesDepot] sait *calculer* les étapes, mais reste pur : il ne retient rien. Or le calcul a trois
/// entrées qui changent à des moments différents - le statut workflow (au chargement du lot), la
/// présence d'archives (à la génération), et la disponibilité du dépôt automatique (au câblage de la
/// vue, #1998). Il fallait donc les mémoriser quelque part pour pouvoir recalculer quand l'une bouge,
/// et [LotViewModel] s'en chargeait, au prix de deux champs et de trois points de rappel.
///
/// Extraite lors du chantier #1991 : l'ajout de la troisième entrée a fait franchir à `LotViewModel` le
/// plafond `GodClass` du portail qualité. Le regroupement n'est pas qu'un moyen de repasser sous le
/// seuil - « ce dont dépend le stepper » est un concept, et il se lit mieux d'un seul tenant.
final class SuiviEtapesLot {

    private final ObservableList<EtapeDepot> etapes = FXCollections.observableArrayList();

    /// Presence d'archives generees, lue au moment du calcul : elle vit dans les lignes de suivi du
    /// ViewModel, qui bougent independamment du statut.
    private final BooleanSupplier archivesGenerees;

    private StatutWorkflow statutCourant;
    private boolean depotAutomatiqueDisponible;

    /// La forme du depot de la nuit ouverte, relue a chaque recalcul (#5824) : un depot entame garde la
    /// sienne, donc elle peut changer apres un televersement. Fournie plutot que poussee, pour que
    /// [LotViewModel] n'ait pas a la porter a chaque rechargement.
    private final Supplier<ModeDepot> forme;

    SuiviEtapesLot(BooleanSupplier archivesGenerees, Supplier<ModeDepot> forme) {
        this.archivesGenerees = Objects.requireNonNull(archivesGenerees, "archivesGenerees");
        this.forme = Objects.requireNonNull(forme, "forme");
    }

    /// Les etapes, dans l'ordre, telles que la vue les rend.
    ObservableList<EtapeDepot> etapes() {
        return etapes;
    }

    /// Le statut workflow courant, ou `null` tant qu'aucun lot n'est charge.
    StatutWorkflow statutCourant() {
        return statutCourant;
    }

    /// Recalcule depuis un nouveau statut, et le retient pour les recalculs suivants.
    void appliquer(StatutWorkflow statut) {
        this.statutCourant = statut;
        recalculer();
    }

    /// Declare si le depot automatique est disponible (application connectee) : l'etape ② cesse alors
    /// d'etre un passage oblige (#1998).
    void declarerDepotAutomatiqueDisponible(boolean disponible) {
        this.depotAutomatiqueDisponible = disponible;
        recalculer();
    }

    /// Recalcule a statut inchange : appele quand les archives apparaissent ou disparaissent.
    void recalculer() {
        if (statutCourant == null) {
            return;
        }
        // Seule une forme WAV averee retire l'etape des archives, et seulement si l'application peut
        // televerser : hors connexion, les archives sont la seule voie du depot manuel (#5824).
        boolean etapeArchivesOfferte = !depotAutomatiqueDisponible || forme.get() != ModeDepot.SEQUENCES_WAV;
        etapes.setAll(EtapesDepot.calculer(
                statutCourant, archivesGenerees.getAsBoolean(), depotAutomatiqueDisponible, etapeArchivesOfferte));
    }

    /// Oublie tout : plus d'etapes, plus de statut. La disponibilite du depot, elle, est une propriete
    /// de l'application et non du lot : elle survit au changement de passage.
    void reinitialiser() {
        statutCourant = null;
        etapes.clear();
    }
}
