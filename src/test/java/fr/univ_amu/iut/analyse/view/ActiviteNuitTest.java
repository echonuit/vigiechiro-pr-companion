package fr.univ_amu.iut.analyse.view;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.TypeLiteral;
import fr.univ_amu.iut.commun.di.RacineInjecteur;
import fr.univ_amu.iut.commun.view.ActiviteAccueil;
import fr.univ_amu.iut.commun.view.Prisme;
import java.nio.file.Path;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/// Vérifie la **carte d'accueil transverse** de l'Activité ([ActiviteNuit]) : son contrat (prisme, action)
/// et sa **présence conditionnée au flag** — absente par défaut (feature EXPERIMENTALE), présente quand la
/// feature `activite-nuit` est activée.
class ActiviteNuitTest {

    @AfterEach
    void nettoyer() {
        System.clearProperty("vigiechiro.features.activite-nuit");
        System.clearProperty("vigiechiro.workspace");
    }

    @Test
    void ouvre_l_ecran_transverse_du_prisme_biodiversite() {
        NavigationActivite navigation = mock(NavigationActivite.class);
        ActiviteNuit carte = new ActiviteNuit(navigation);

        carte.ouvrir();

        verify(navigation).ouvrirTout();
        assertThat(carte.prisme()).isEqualTo(Prisme.ESPECES_BIODIVERSITE);
        assertThat(carte.titre()).isEqualTo("Activité de la nuit");
    }

    @Test
    void absente_par_defaut_presente_quand_la_feature_est_active(@TempDir Path espaceDeTravail) {
        System.setProperty("vigiechiro.workspace", espaceDeTravail.toString());

        assertThat(cartesAccueil()).noneMatch(ActiviteNuit.class::isInstance);

        System.setProperty("vigiechiro.features.activite-nuit", "on");
        assertThat(cartesAccueil())
                .as("feature activée : la carte transverse rejoint l'accueil")
                .anyMatch(ActiviteNuit.class::isInstance);
    }

    private static Set<ActiviteAccueil> cartesAccueil() {
        Injector injecteur = RacineInjecteur.creer();
        return injecteur.getInstance(Key.get(new TypeLiteral<Set<ActiviteAccueil>>() {}));
    }
}
