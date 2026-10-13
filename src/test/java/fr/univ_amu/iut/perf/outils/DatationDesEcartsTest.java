package fr.univ_amu.iut.perf.outils;

import static fr.univ_amu.iut.perf.outils.InstrumentsDuBanc.IMAGES_DE_GARDE;
import static fr.univ_amu.iut.perf.outils.InstrumentsDuBanc.attendreLesImages;
import static fr.univ_amu.iut.perf.outils.InstrumentsDuBanc.bloquerEtDater;
import static fr.univ_amu.iut.perf.outils.InstrumentsDuBanc.bloquerLeFil;
import static fr.univ_amu.iut.perf.outils.InstrumentsDuBanc.surLeFil;
import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.perf.outils.DatationDesEcarts.EcartDate;
import fr.univ_amu.iut.perf.outils.InstrumentsDuBanc.BlocageDate;
import fr.univ_amu.iut.perf.outils.InstrumentsDuBanc.Gel;
import fr.univ_amu.iut.perf.outils.InstrumentsDuBanc.SondeImages;
import fr.univ_amu.iut.recette.SansEcritureHorsDuFil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;

/// Le banc de l'IHM date ses écarts : chacun porte son début et sa fin, et se situe parmi les jalons de
/// l'import (#6175). Sans date, un écart de 465 ms dit qu'il y a eu un gel, pas quelle étape l'a fait.
///
/// Ces cas vivent hors de l'étiquette `banc-ihm`, comme ceux d'[EchelleDuRenduTest] : ils se jouent à
/// chaque build, en Headless. Le dernier est le témoin de l'instrument : un blocage posé à un instant
/// connu doit être daté à une image près, faute de quoi toute date du relevé serait décalée sans que
/// rien le dise.
@ExtendWith({ApplicationExtension.class, SansEcritureHorsDuFil.class})
class DatationDesEcartsTest {

    private static final long MS = 1_000_000L;
    private static final long CLIC = 5_000 * MS;

    @Test
    void un_ecart_se_situe_entre_le_jalon_qui_le_precede_et_celui_qui_le_suit() {
        DatationDesEcarts datation = importDate();

        String phase = datation.situer(new EcartDate(CLIC + 1_000 * MS, CLIC + 1_300 * MS));

        assertThat(phase).isEqualTo("après « plan établi », avant « premier fichier terminé »");
    }

    @Test
    void un_ecart_nomme_les_jalons_qu_il_contient() {
        DatationDesEcarts datation = importDate();

        String phase = datation.situer(new EcartDate(CLIC + 11_900 * MS, CLIC + 12_400 * MS));

        assertThat(phase)
                .as("un jalon tombé dans l'écart désigne le travail qui a tenu le fil")
                .isEqualTo("après « premier fichier terminé », contient « dernier fichier terminé » et « TERMINE »,"
                        + " avant « compte rendu »");
    }

    @Test
    void un_ecart_apres_le_dernier_jalon_le_dit() {
        DatationDesEcarts datation = importDate();

        assertThat(datation.situer(new EcartDate(CLIC + 13_000 * MS, CLIC + 13_250 * MS)))
                .isEqualTo("après « compte rendu »");
    }

    @Test
    void un_jalon_garde_son_premier_instant() {
        DatationDesEcarts datation = importDate();

        datation.marquer("plan établi", CLIC + 9_000 * MS);

        assertThat(datation.jalons()).contains("plan établi +0.400 s").doesNotContain("plan établi +9.000 s");
    }

    @Test
    void les_instants_s_ecrivent_depuis_le_premier_jalon() {
        DatationDesEcarts datation = importDate();

        assertThat(datation.jalons())
                .isEqualTo("clic +0.000 s · plan établi +0.400 s · premier fichier terminé +2.000 s"
                        + " · dernier fichier terminé +12.000 s · TERMINE +12.300 s · compte rendu +12.500 s");
        assertThat(datation.ligne(new EcartDate(CLIC + 1_000 * MS, CLIC + 1_300 * MS)))
                .isEqualTo("300 ms, de +1.000 s à +1.300 s : après « plan établi », avant « premier fichier terminé »");
    }

    @Test
    void un_blocage_pose_a_un_instant_connu_est_date_a_une_image_pres() throws Exception {
        SondeImages sonde = new SondeImages();
        surLeFil(() -> {
            sonde.start();
            return null;
        });
        try {
            BlocageDate temoin = bloquerEtDater(sonde, 300);

            assertThat(temoin.dates())
                    .as("un blocage de 300 ms donne un écart daté, et un seul")
                    .hasSize(1);
            assertThat(temoin.dates().getFirst().ms()).isGreaterThanOrEqualTo(300);
            // Une image au calme, avec un plancher de trois images à 60 par seconde : sous la charge d'un
            // build, une image saute entre le calme et le blocage. Une date décalée d'un écart entier,
            // ce que ce cas refuse, se trompe de 300 ms.
            double tolerance = Math.max(temoin.uneImageNs() / 1e6, 50);
            assertThat(temoin.avanceMs())
                    .as("l'écart commence à l'image d'avant le blocage")
                    .isBetween(0.0, tolerance);
            assertThat(temoin.retardMs())
                    .as("l'écart finit à l'image d'après le blocage")
                    .isBetween(0.0, tolerance);
        } finally {
            surLeFil(() -> {
                sonde.stop();
                return null;
            });
        }
    }

    @Test
    void la_sonde_releve_l_ecart_d_un_travail_une_fois_son_image_passee() throws Exception {
        SondeImages sonde = new SondeImages();
        surLeFil(() -> {
            sonde.start();
            return null;
        });
        try {
            attendreLesImages(sonde, 3);
            Gel aussitot = surLeFil(() -> {
                bloquerLeFil(300);
                return sonde.relever();
            });
            assertThat(aussitot.dates())
                    .as("relevé dans le passage du fil qui a bloqué, l'écart n'existe pas encore")
                    .isEmpty();

            attendreLesImages(sonde, aussitot.images() + IMAGES_DE_GARDE);

            assertThat(sonde.relever().dates())
                    .as("deux images plus tard, il est là")
                    .hasSize(1);
        } finally {
            surLeFil(() -> {
                sonde.stop();
                return null;
            });
        }
    }

    /// Un import daté comme le banc le date : le clic, puis ses cinq jalons.
    private static DatationDesEcarts importDate() {
        DatationDesEcarts datation = new DatationDesEcarts();
        datation.marquer("clic", CLIC);
        datation.marquer("plan établi", CLIC + 400 * MS);
        datation.marquer("premier fichier terminé", CLIC + 2_000 * MS);
        datation.marquer("dernier fichier terminé", CLIC + 12_000 * MS);
        datation.marquer("TERMINE", CLIC + 12_300 * MS);
        datation.marquer("compte rendu", CLIC + 12_500 * MS);
        return datation;
    }
}
