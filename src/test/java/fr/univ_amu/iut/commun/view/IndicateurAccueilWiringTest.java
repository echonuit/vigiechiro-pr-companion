package fr.univ_amu.iut.commun.view;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.TypeLiteral;
import fr.univ_amu.iut.commun.di.RacineInjecteur;
import fr.univ_amu.iut.commun.viewmodel.IndicateurAccueil;
import java.nio.file.Path;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/// Test d'INTÉGRATION du câblage Guice : la racine de composition agrège, via le
/// `Multibinder<IndicateurAccueil>`, les compteurs publiés par chaque feature : sans que le socle
/// ne dépende d'aucune feature.
class IndicateurAccueilWiringTest {

    @AfterEach
    void nettoyer() {
        System.clearProperty("vigiechiro.workspace");
    }

    @Test
    @DisplayName("Le socle agrège les compteurs des features (sites, points, passages, observations)")
    void le_socle_agrege_les_compteurs_des_features(@TempDir Path tmp) {
        System.setProperty("vigiechiro.workspace", tmp.toString());
        Injector injector = RacineInjecteur.creer();

        Set<IndicateurAccueil> indicateurs =
                injector.getInstance(Key.get(new TypeLiteral<Set<IndicateurAccueil>>() {}));

        assertThat(indicateurs)
                .extracting(i -> i.getClass().getSimpleName())
                .contains("IndicateurSites", "IndicateurPoints", "IndicateurPassages", "IndicateurObservations");
    }
}
