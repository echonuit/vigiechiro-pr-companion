package fr.univ_amu.iut.importation.view;

import fr.univ_amu.iut.importation.model.SuiviFichiers;
import fr.univ_amu.iut.importation.viewmodel.SuiviLignesFichiers;
import java.util.List;
import java.util.Objects;
import javafx.application.Platform;

/// Relais **fil JavaFX** du suivi par fichier (#947) : le service émet hors-thread et dans le désordre
/// (transformation parallèle #12) ; chaque événement est rejoué sur le fil JavaFX via
/// `Platform.runLater` pour muter les lignes observables du ViewModel (patron du relais de la table de
/// dépôt, #820). Extrait de [ImportationController] pour garder celui-ci en pur câblage.
final class RelaisSuiviFichiers implements SuiviFichiers {

    private final SuiviLignesFichiers cible;

    RelaisSuiviFichiers(SuiviLignesFichiers cible) {
        this.cible = Objects.requireNonNull(cible, "cible");
    }

    @Override
    public void planEtabli(List<String> noms) {
        Platform.runLater(() -> cible.planifier(noms));
    }

    @Override
    public void copieDemarree(int numero) {
        Platform.runLater(() -> cible.copieDemarree(numero));
    }

    @Override
    public void copieTerminee(int numero) {
        Platform.runLater(() -> cible.copieTerminee(numero));
    }

    @Override
    public void transformationDemarree(int numero) {
        Platform.runLater(() -> cible.transformationDemarree(numero));
    }

    @Override
    public void fichierTermine(int numero) {
        Platform.runLater(() -> cible.terminer(numero));
    }

    @Override
    public void fichierRejete(int numero, String raison) {
        Platform.runLater(() -> cible.echouer(numero, raison));
    }
}
