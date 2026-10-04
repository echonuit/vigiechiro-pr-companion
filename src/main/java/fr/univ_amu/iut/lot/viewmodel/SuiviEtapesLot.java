package fr.univ_amu.iut.lot.viewmodel;

import fr.univ_amu.iut.commun.model.StatutWorkflow;
import fr.univ_amu.iut.lot.model.ModeDepot;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
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

    /// La forme du depot de la nuit, ou `null` tant qu'elle n'est pas connue (#5824).
    private ModeDepot forme;

    /// L'etape « Generer les archives » sert-elle ? Vraie tant qu'on ne sait pas le contraire : on ne
    /// retire une etape que sur une forme WAV averee, application connectee.
    private final ReadOnlyBooleanWrapper etapeArchivesOfferte = new ReadOnlyBooleanWrapper(true);

    SuiviEtapesLot(BooleanSupplier archivesGenerees) {
        this.archivesGenerees = Objects.requireNonNull(archivesGenerees, "archivesGenerees");
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

    /// Declare la forme du depot de la nuit (#5824) : en sequences WAV et connecte, l'etape des archives
    /// n'a plus lieu d'etre.
    void declarerForme(ModeDepot formeDuDepot) {
        this.forme = formeDuDepot;
        recalculer();
    }

    /// L'etape des archives est-elle offerte ? Vraie en forme ZIP, et hors connexion ou elle sert au
    /// depot manuel.
    ReadOnlyBooleanProperty etapeArchivesOfferteProperty() {
        return etapeArchivesOfferte.getReadOnlyProperty();
    }

    /// Recalcule a statut inchange : appele quand les archives apparaissent ou disparaissent.
    void recalculer() {
        // Seule une forme WAV averee retire l'etape, et seulement si l'application peut televerser :
        // hors connexion, les archives sont la seule voie du depot manuel.
        etapeArchivesOfferte.set(forme != ModeDepot.SEQUENCES_WAV || !depotAutomatiqueDisponible);
        if (statutCourant == null) {
            return;
        }
        etapes.setAll(EtapesDepot.calculer(
                statutCourant,
                archivesGenerees.getAsBoolean(),
                depotAutomatiqueDisponible,
                etapeArchivesOfferte.get()));
    }

    /// Oublie tout : plus d'etapes, plus de statut. La disponibilite du depot, elle, est une propriete
    /// de l'application et non du lot : elle survit au changement de passage.
    void reinitialiser() {
        statutCourant = null;
        forme = null;
        etapeArchivesOfferte.set(true);
        etapes.clear();
    }
}
