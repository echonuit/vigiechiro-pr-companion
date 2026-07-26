package fr.univ_amu.iut.lot.view;

import fr.univ_amu.iut.lot.model.DepotUnite;
import fr.univ_amu.iut.lot.model.SuiviDepot;
import fr.univ_amu.iut.lot.viewmodel.SuiviLignesDepot;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executor;

/// Relais **fil JavaFX** du suivi de dépôt par unité (#983) : le moteur reprenable (#982) émet
/// hors-thread ; chaque événement est rejoué sur le fil JavaFX pour muter les lignes observables du
/// ViewModel (patron des relais de la génération #820 et de l'import #947). Le fil JavaFX est fourni
/// par le socle ([fr.univ_amu.iut.commun.view.ExecuteurTache#surFilJavaFx()], #1253) : reposté en
/// production, immédiat en test synchrone.
final class RelaisSuiviDepot implements SuiviDepot {

    private final SuiviLignesDepot cible;
    private final Executor filJavaFx;

    RelaisSuiviDepot(SuiviLignesDepot cible, Executor filJavaFx) {
        this.cible = Objects.requireNonNull(cible, "cible");
        this.filJavaFx = Objects.requireNonNull(filJavaFx, "filJavaFx");
    }

    @Override
    public void planEtabli(List<DepotUnite> unites) {
        filJavaFx.execute(() -> cible.planifier(unites));
    }

    @Override
    public void uniteDemarree(String identifiant) {
        filJavaFx.execute(() -> cible.demarree(identifiant));
    }

    @Override
    public void uniteDeposee(DepotUnite unite) {
        filJavaFx.execute(() -> cible.deposee(unite.identifiantUnite()));
    }

    @Override
    public void uniteEchouee(String identifiant, String raison) {
        filJavaFx.execute(() -> cible.echouee(identifiant, raison));
    }

    @Override
    public void uniteProgresse(String identifiant, double fraction) {
        filJavaFx.execute(() -> cible.progresse(identifiant, fraction));
    }

    @Override
    public void uniteReprise(String identifiant, java.time.Duration delai) {
        filJavaFx.execute(() -> cible.reprise(identifiant, delai));
    }
}
