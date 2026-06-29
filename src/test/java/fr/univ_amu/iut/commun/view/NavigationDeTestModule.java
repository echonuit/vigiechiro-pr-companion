package fr.univ_amu.iut.commun.view;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;

/// Module Guice de **test** fournissant des implémentations no-op des contrats de navigation
/// inter-écran ([OuvrirSite], [OuvrirPassage]).
///
/// Les écrans enfants d'un passage (diagnostic, validation, lot, qualification) injectent ces contrats
/// pour construire leur fil d'Ariane. Dans les tests d'intégration qui chargent un écran isolément via
/// Guice, on n'exerce pas la navigation (le fil est vérifié par ses libellés, pas en cliquant) : ces
/// no-op satisfont les dépendances sans déclencher d'ouverture d'écran.
public final class NavigationDeTestModule extends AbstractModule {

    @Provides
    OuvrirSite ouvrirSite() {
        return new OuvrirSite() {
            @Override
            public void ouvrirListe() {}

            @Override
            public void ouvrirDetail(String numeroCarre) {}
        };
    }

    @Provides
    OuvrirPassage ouvrirPassage() {
        return (idPassage, contexte) -> {};
    }

    @Provides
    OuvrirMultisite ouvrirMultisite() {
        return numeroCarre -> {};
    }

    @Provides
    OuvrirAnalyse ouvrirAnalyse() {
        return () -> {};
    }
}
