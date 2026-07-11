package fr.univ_amu.iut.commun.view;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.TypeLiteral;
import fr.univ_amu.iut.commun.di.RacineInjecteur;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/// Onglet « Fonctionnalités » (#1057) : contribué par le socle, il liste un interrupteur par feature
/// désactivable et masque les features COEUR.
class OngletReglagesFonctionnalitesTest {

    @AfterEach
    void nettoyer() {
        System.clearProperty("vigiechiro.workspace");
    }

    @Test
    @DisplayName("l'onglet porte l'identité « fonctionnalites », ses descripteurs, et l'échappatoire")
    void contrat_de_l_onglet() {
        DescripteurReglage.Booleen toggle = new DescripteurReglage.Booleen(
                "feature.import-vigiechiro.active", "Import depuis VigieChiro", "", true);
        OngletReglagesFonctionnalites onglet = new OngletReglagesFonctionnalites(List.of(toggle));

        assertThat(onglet.idFeature()).isEqualTo("fonctionnalites");
        assertThat(onglet.titre()).isEqualTo("Fonctionnalités");
        assertThat(onglet.reglages()).containsExactly(toggle);
        assertThat(onglet).isInstanceOf(OngletReglagesPersonnalise.class);
    }

    @Test
    @DisplayName("le socle contribue l'onglet avec un interrupteur pour import-vigiechiro, sans les COEUR")
    void contribue_par_le_socle(@TempDir Path tmp) {
        System.setProperty("vigiechiro.workspace", tmp.toString());
        Injector injector = RacineInjecteur.creer();

        Set<OngletReglages> onglets = injector.getInstance(Key.get(new TypeLiteral<Set<OngletReglages>>() {}));
        OngletReglages fonctionnalites = onglets.stream()
                .filter(onglet -> onglet.idFeature().equals("fonctionnalites"))
                .findFirst()
                .orElseThrow();

        List<String> cles =
                fonctionnalites.reglages().stream().map(DescripteurReglage::cle).toList();
        assertThat(cles).contains("feature.import-vigiechiro.active");
        assertThat(cles).doesNotContain("feature.sites.active", "feature.passage.active");
    }
}
