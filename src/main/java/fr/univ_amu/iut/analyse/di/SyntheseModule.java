package fr.univ_amu.iut.analyse.di;

import com.google.inject.multibindings.OptionalBinder;
import fr.univ_amu.iut.analyse.view.NavigationSynthese;
import fr.univ_amu.iut.commun.di.Categorie;
import fr.univ_amu.iut.commun.di.Fonctionnalite;
import fr.univ_amu.iut.commun.di.ModuleDeFeature;
import fr.univ_amu.iut.commun.view.OuvrirSynthese;

/// Module de feature qui **conditionne l'accès** à l'écran **Synthèse de la nuit** (#2351, lot 1 du
/// chantier #2348), à côté d'[AnalyseModule] et d'[ActiviteModule].
///
/// Il ne porte **que le point d'entrée** : le contrat socle [OuvrirSynthese], que `passage` injecte pour
/// ouvrir l'écran. La **machinerie** — le `SyntheseViewModel` et le `ServiceSynthese` — est fournie par
/// [AnalyseModule], toujours actif, et **non** ici : le FXML doit rester chargeable (garde-fou
/// `ChargementFxmlTest`) même l'entrée coupée. Ce module ne conditionne donc que l'**accès**, jamais les
/// composants — c'est la leçon de la clôture du lot #2352.
public class SyntheseModule extends ModuleDeFeature {

    /// `EXPERIMENTALE` le temps du chantier : l'écran est livré avant son export et sa parité CLI
    /// (tranche 4), et cette catégorie permet de fusionner les paliers intermédiaires sans exposer un
    /// écran à moitié fait. Elle passera `OPTIONNELLE` à la clôture du lot, comme `activite-nuit`.
    @Override
    public Fonctionnalite fonctionnalite() {
        return new Fonctionnalite("synthese-nuit", "Synthèse de la nuit", Categorie.EXPERIMENTALE);
    }

    @Override
    protected void configure() {
        OptionalBinder.newOptionalBinder(binder(), OuvrirSynthese.class)
                .setBinding()
                .to(NavigationSynthese.class);
    }
}
