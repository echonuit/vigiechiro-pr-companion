package fr.univ_amu.iut.importation.view;

import fr.univ_amu.iut.importation.model.SuiviFichiers;
import fr.univ_amu.iut.importation.viewmodel.SuiviLignesFichiers;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executor;

/// Relais **fil JavaFX** du suivi par fichier (#947) : le service émet hors-thread et dans le désordre
/// (transformation parallèle #12) ; chaque événement est rejoué sur le fil JavaFX pour muter les lignes
/// observables du ViewModel (patron du relais de la table de dépôt, #820). Le fil JavaFX est fourni par
/// le socle ([fr.univ_amu.iut.commun.view.ExecuteurTache#surFilJavaFx()], #1256) : reposté en
/// production, immédiat en test synchrone. Extrait de [ImportationController] pour garder celui-ci en
/// pur câblage.
final class RelaisSuiviFichiers implements SuiviFichiers {

    private final SuiviLignesFichiers cible;
    private final Executor filJavaFx;

    RelaisSuiviFichiers(SuiviLignesFichiers cible, Executor filJavaFx) {
        this.cible = Objects.requireNonNull(cible, "cible");
        this.filJavaFx = Objects.requireNonNull(filJavaFx, "filJavaFx");
    }

    @Override
    public void planEtabli(List<String> noms) {
        filJavaFx.execute(() -> cible.planifier(noms));
    }

    @Override
    public void copieDemarree(int numero) {
        filJavaFx.execute(() -> cible.copieDemarree(numero));
    }

    @Override
    public void copieTerminee(int numero) {
        filJavaFx.execute(() -> cible.copieTerminee(numero));
    }

    @Override
    public void transformationDemarree(int numero) {
        filJavaFx.execute(() -> cible.transformationDemarree(numero));
    }

    @Override
    public void fichierTermine(int numero) {
        filJavaFx.execute(() -> cible.terminer(numero));
    }

    @Override
    public void fichierRejete(int numero, String raison) {
        filJavaFx.execute(() -> cible.echouer(numero, raison));
    }
}
