package fr.univ_amu.iut.lot.view;

import fr.univ_amu.iut.lot.model.DepotUnite;
import fr.univ_amu.iut.lot.model.SuiviDepot;
import fr.univ_amu.iut.lot.viewmodel.SuiviLignesDepot;
import java.util.List;
import java.util.Objects;
import javafx.application.Platform;

/// Relais **fil JavaFX** du suivi de dépôt par unité (#983) : le moteur reprenable (#982) émet
/// hors-thread ; chaque événement est rejoué sur le fil JavaFX via `Platform.runLater` pour muter les
/// lignes observables du ViewModel (patron des relais de la génération #820 et de l'import #947).
final class RelaisSuiviDepot implements SuiviDepot {

    private final SuiviLignesDepot cible;

    RelaisSuiviDepot(SuiviLignesDepot cible) {
        this.cible = Objects.requireNonNull(cible, "cible");
    }

    @Override
    public void planEtabli(List<DepotUnite> unites) {
        Platform.runLater(() -> cible.planifier(unites));
    }

    @Override
    public void uniteDemarree(String identifiant) {
        Platform.runLater(() -> cible.demarree(identifiant));
    }

    @Override
    public void uniteDeposee(DepotUnite unite) {
        Platform.runLater(() -> cible.deposee(unite.identifiantUnite()));
    }

    @Override
    public void uniteEchouee(String identifiant, String raison) {
        Platform.runLater(() -> cible.echouee(identifiant, raison));
    }

    @Override
    public void uniteProgresse(String identifiant, double fraction) {
        Platform.runLater(() -> cible.progresse(identifiant, fraction));
    }
}
