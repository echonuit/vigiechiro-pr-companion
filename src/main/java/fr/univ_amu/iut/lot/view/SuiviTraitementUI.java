package fr.univ_amu.iut.lot.view;

import fr.univ_amu.iut.commun.view.ExecuteurTache;
import fr.univ_amu.iut.lot.viewmodel.DepotViewModel;
import fr.univ_amu.iut.lot.viewmodel.TraitementViewModel;
import java.util.Objects;
import java.util.function.Supplier;
import javafx.beans.binding.Bindings;
import javafx.beans.value.ObservableBooleanValue;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

/// Câblage de la zone **« Traitement Vigie-Chiro »** de M-Lot (#1263), extrait de [LotController] : celui-ci
/// n'a plus qu'à la construire, et reste sous les plafonds de complexité.
///
/// La zone n'apparaît que lorsqu'une **participation est liée** — autrement dit une fois la nuit déposée
/// par l'application : avant cela, il n'y a rien à suivre, et une carte vide n'apprendrait rien.
///
/// **Aucun sondage** : l'état est relu à l'ouverture de l'écran (depuis le cache, sans réseau), puis sur
/// demande explicite (« Actualiser ») ou après un lancement. Le relevé réseau passe par le socle
/// [ExecuteurTache] — hors du fil JavaFX, et déterministe en test.
final class SuiviTraitementUI {

    private final TraitementViewModel viewModel;
    private final ExecuteurTache executeur;
    private final Supplier<Long> idPassage;

    /// Installe la zone et la rend autonome : elle n'apparaît que si le suivi est **disponible**
    /// (application connectée) **et** qu'une participation est liée (nuit déposée par l'application).
    /// Hors de ces conditions, la carte reste absente — et le composant, inerte.
    static SuiviTraitementUI installer(
            TraitementViewModel viewModel,
            ExecuteurTache executeur,
            Supplier<Long> idPassage,
            ObservableBooleanValue participationLiee,
            VBox zone,
            Label etat,
            Label fraicheur,
            Label alerte,
            Button actualiser) {
        SuiviTraitementUI composant = new SuiviTraitementUI(viewModel, executeur, idPassage);
        composant.cabler(zone, etat, fraicheur, alerte, actualiser);
        if (viewModel.disponible()) {
            zone.visibleProperty().bind(participationLiee);
            zone.managedProperty().bind(zone.visibleProperty());
        }
        return composant;
    }

    private SuiviTraitementUI(TraitementViewModel viewModel, ExecuteurTache executeur, Supplier<Long> idPassage) {
        this.viewModel = Objects.requireNonNull(viewModel, "viewModel");
        this.executeur = Objects.requireNonNull(executeur, "executeur");
        this.idPassage = Objects.requireNonNull(idPassage, "idPassage");
    }

    /// Lie la zone au ViewModel : textes, visibilité de l'avertissement, bouton en attente pendant un relevé.
    private void cabler(VBox zone, Label etat, Label fraicheur, Label alerte, Button actualiser) {
        etat.textProperty().bind(viewModel.messageProperty());
        fraicheur.textProperty().bind(viewModel.fraicheurProperty());
        alerte.textProperty().bind(viewModel.alerteProperty());
        // Un avertissement vide ne doit pas laisser un blanc dans la carte.
        alerte.visibleProperty().bind(viewModel.alerteProperty().isNotEmpty());
        alerte.managedProperty().bind(alerte.visibleProperty());
        fraicheur.visibleProperty().bind(viewModel.fraicheurProperty().isNotEmpty());
        fraicheur.managedProperty().bind(fraicheur.visibleProperty());
        actualiser.disableProperty().bind(viewModel.enCoursProperty());
        actualiser
                .textProperty()
                .bind(Bindings.when(viewModel.enCoursProperty())
                        .then("Relevé en cours…")
                        .otherwise("🔄 Actualiser"));
        actualiser.setOnAction(evenement -> actualiser());
        zone.setVisible(false);
        zone.setManaged(false);
    }

    /// Affiche le **dernier état connu** (cache, sans réseau) : à l'ouverture de l'écran, la zone dit déjà
    /// quelque chose, y compris hors connexion. Sans suivi (hors connexion), ne fait rien : la carte est de
    /// toute façon absente.
    void rehydrater() {
        if (viewModel.disponible()) {
            viewModel.chargerDernierReleve(idPassage.get());
        }
    }

    /// Demande au serveur où il en est, **hors du fil JavaFX**.
    void actualiser() {
        Long passage = idPassage.get();
        viewModel.marquerEnCours();
        executeur.executer(
                () -> viewModel.relever(passage), viewModel::appliquer, erreur -> viewModel.echec(erreur.getMessage()));
    }

    /// Lance le **traitement serveur** (compute, #984) **hors du fil JavaFX**, puis **relit l'état** : le
    /// serveur vient de changer d'avis sur cette nuit (planifiée, ou déjà en cours), autant le lui demander
    /// plutôt que de le déduire.
    ///
    /// Le lancement appartient à cette zone, comme le reste de ce qui se passe après le dépôt — le compte
    /// rendu, lui, reste celui du dépôt (c'est son message d'étape ④).
    void lancer(DepotViewModel depot) {
        Long passage = idPassage.get();
        depot.marquerLancementEnCours();
        executeur.executer(
                () -> depot.lancerTraitement(passage),
                resultat -> {
                    depot.restituerLancement(resultat);
                    actualiser();
                },
                erreur -> depot.echec(erreur.getMessage()));
    }
}
