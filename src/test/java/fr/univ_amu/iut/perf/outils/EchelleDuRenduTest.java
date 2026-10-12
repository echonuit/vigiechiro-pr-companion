package fr.univ_amu.iut.perf.outils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import fr.univ_amu.iut.perf.outils.InstrumentsDuBanc.Releve;
import fr.univ_amu.iut.recette.SansEcritureHorsDuFil;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;

/// Le relevé du banc de l'IHM porte l'échelle à laquelle il a dessiné, lue dans le processus, et le
/// banc refuse une autre échelle que 1 sans dérogation (#6177).
///
/// Ces cas vivent hors de l'étiquette `banc-ihm` : ils se jouent à chaque build, en Headless. Le banc
/// lui-même ne tourne jamais en CI, et un témoin posé dans le banc ne rougirait que le jour où
/// quelqu'un le tire.
@ExtendWith({ApplicationExtension.class, SansEcritureHorsDuFil.class})
class EchelleDuRenduTest {

    private static final String REGLAGES = "glass.platform=gtk · prism.order=sw · glass.gtk.uiScale=non posée";

    @TempDir
    Path racine;

    @Test
    void le_releve_ecrit_son_echelle_dans_releve_tsv() throws Exception {
        Releve releve = new Releve(racine, "témoin", new EchelleDuRendu(2, 2, 2, 2, REGLAGES, true));
        releve.noter("geste", 1, 12.5);

        releve.ecrire();

        List<String> lignes = Files.readAllLines(racine.resolve("releve.tsv"), StandardCharsets.UTF_8);
        assertThat(lignes)
                .as("le relevé s'ouvre sur l'échelle, avant ses chiffres")
                .first()
                .isEqualTo("témoin\téchelle\trendu 2.00 × 2.00 · écran 2.00 × 2.00 · " + REGLAGES
                        + " · dérogation : échelle de l'écran acceptée (-Dperf.ihm.echelle=ecran)");
        assertThat(lignes).hasSize(2);
    }

    @Test
    void l_echelle_se_lit_sur_la_fenetre_et_non_dans_une_constante(FxRobot robot) {
        // Une fenêtre à soi, jamais montrée : le Stage du harnais est partagé par le fork (ADR 4475),
        // et une échelle de rendu posée sur lui doublerait chaque capture des classes suivantes.
        EchelleDuRendu[] lue = new EchelleDuRendu[1];
        robot.interact(() -> {
            Stage fenetre = new Stage();
            fenetre.setRenderScaleX(2);
            fenetre.setRenderScaleY(1.5);
            lue[0] = EchelleDuRendu.lire(fenetre);
        });

        assertThat(lue[0].renduX()).isEqualTo(2);
        assertThat(lue[0].renduY()).isEqualTo(1.5);
        assertThat(lue[0].ligne())
                .startsWith("rendu 2.00 × 1.50 · écran ")
                .contains("glass.platform=", "prism.order=", "glass.gtk.uiScale=");
    }

    @Test
    void une_echelle_de_1_passe() {
        assertThatCode(new EchelleDuRendu(1, 1, 2, 2, REGLAGES, false)::verifier)
                .as("l'échelle de rendu compte, pas celle que l'écran demande")
                .doesNotThrowAnyException();
    }

    @Test
    void une_autre_echelle_arrete_le_banc_et_dit_le_remede() {
        assertThatThrownBy(new EchelleDuRendu(2, 2, 2, 2, REGLAGES, false)::verifier)
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("le banc s'arrête : échelle de rendu 2.00 × 2.00")
                .hasMessageContaining("-Dglass.gtk.uiScale=1")
                .hasMessageContaining("-Dperf.ihm.echelle=ecran");
        assertThatThrownBy(new EchelleDuRendu(1, 1.25, 1, 1.25, REGLAGES, false)::verifier)
                .as("une seule direction hors de 1 suffit à arrêter le banc")
                .isInstanceOf(AssertionError.class);
    }

    @Test
    void la_derogation_laisse_mesurer_et_le_releve_la_dit() {
        EchelleDuRendu ecran = new EchelleDuRendu(2, 2, 2, 2, REGLAGES, true);

        assertThatCode(ecran::verifier).doesNotThrowAnyException();
        assertThat(ecran.ligne()).endsWith("dérogation : échelle de l'écran acceptée (-Dperf.ihm.echelle=ecran)");
        assertThat(new EchelleDuRendu(1, 1, 1, 1, REGLAGES, false).ligne()).doesNotContain("dérogation");
    }
}
