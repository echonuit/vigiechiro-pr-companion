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
/// ouvrir l'écran. La **machinerie** (le `SyntheseViewModel` et le `ServiceSynthese`) est fournie par
/// [AnalyseModule], toujours actif, et **non** ici : le FXML doit rester chargeable (garde-fou
/// `ChargementFxmlTest`) même l'entrée coupée. Ce module ne conditionne donc que l'**accès**, jamais les
/// composants : c'est la leçon de la clôture du lot #2352.
public class SyntheseModule extends ModuleDeFeature {

    /// `OPTIONNELLE` depuis la clôture du lot : l'écran a désormais son export et sa parité CLI, et
    /// rester `EXPERIMENTALE` le livrerait **coupé par défaut** : autant ne pas l'avoir écrit. La
    /// catégorie n'avait été `EXPERIMENTALE` que le temps du chantier, pour fusionner les paliers
    /// intermédiaires sans exposer un écran à moitié fait.
    @Override
    public Fonctionnalite fonctionnalite() {
        return new Fonctionnalite("synthese-nuit", "Synthèse de la nuit", Categorie.OPTIONNELLE);
    }

    @Override
    protected void configure() {
        OptionalBinder.newOptionalBinder(binder(), OuvrirSynthese.class)
                .setBinding()
                .to(NavigationSynthese.class);
    }
}
