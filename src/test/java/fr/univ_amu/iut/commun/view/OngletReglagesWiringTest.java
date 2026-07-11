package fr.univ_amu.iut.commun.view;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.TypeLiteral;
import fr.univ_amu.iut.commun.di.RacineInjecteur;
import java.nio.file.Path;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/// Câblage Guice du point d'extension « onglets de réglages » (#927) : le socle déclare le
/// `Multibinder<OngletReglages>` (dans `CommunModule`), donc `Set<OngletReglages>` est **toujours
/// injectable**, même sans aucune contribution. Tant qu'aucune feature n'ajoute d'onglet (P1.3), le
/// set est vide et l'entrée ☰ « Réglages » se grise ([NavigationReglages#aDesReglages()] faux).
class OngletReglagesWiringTest {

    @AfterEach
    void nettoyer() {
        System.clearProperty("vigiechiro.workspace");
    }

    @Test
    @DisplayName("Set<OngletReglages> est injectable et vide tant qu'aucune feature ne contribue")
    void set_injectable_et_vide(@TempDir Path tmp) {
        System.setProperty("vigiechiro.workspace", tmp.toString());
        Injector injector = RacineInjecteur.creer();

        Set<OngletReglages> onglets = injector.getInstance(Key.get(new TypeLiteral<Set<OngletReglages>>() {}));

        assertThat(onglets).isEmpty();
        assertThat(injector.getInstance(NavigationReglages.class).aDesReglages())
                .isFalse();
    }
}
