package fr.univ_amu.iut.cli;

import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.commun.persistence.MigrationSchema;
import fr.univ_amu.iut.fixture.SortieCapturee;
import java.io.PrintStream;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/// Le contrôle du carré en ligne de commande (#4671), comblé à la passe 2 de la clôture de #4671.
///
/// L'écran contrôle depuis #733 dès qu'un point reçoit ses coordonnées ; `ajouter-point` et
/// `modifier-point` posaient les mêmes coordonnées sans rien dire. Le contrôle est ici **hors ligne** -
/// le carroyage embarqué reproduit la plateforme au centimètre (ADR 4577) - donc ces deux commandes
/// restent locales, sans jeton ni réseau.
class CliControleDuCarreTest {

    /// Position à 374,9 m du centre de `040110`, mesurée le 2026-08-27 : loin de tout bord.
    private static final double LAT_DANS_040110 = 44.44674980384396;

    private static final double LON_DANS_040110 = 6.298116860416506;

    @TempDir
    Path workspaceDir;

    private Cli cli;

    private final SortieCapturee capture = new SortieCapturee();
    private final PrintStream sortie = capture.sortie();
    private final PrintStream erreur = capture.erreur();

    @BeforeEach
    void preparer() {
        System.setProperty("vigiechiro.workspace", workspaceDir.toString());
        var injecteur = Cli.injecteurApplicatif();
        cli = new Cli(injecteur);
        injecteur.getInstance(MigrationSchema.class).migrer();
    }

    @AfterEach
    void nettoyer() {
        System.clearProperty("vigiechiro.workspace");
    }

    @Test
    @DisplayName("ajouter-point : une position hors du carré déclaré le DIT, sur stderr, sans changer le code")
    void position_hors_du_carre_declare_avertit() {
        String site = creerSite("130711");

        int code = cli.executer(
                new String[] {
                    "ajouter-point",
                    "--site",
                    site,
                    "--code",
                    "A1",
                    "--lat",
                    String.valueOf(LAT_DANS_040110),
                    "--lon",
                    String.valueOf(LON_DANS_040110)
                },
                sortie,
                erreur);

        assertThat(code)
                .as("le contrôle est un confort, jamais une condition : il ne refuse pas")
                .isZero();
        assertThat(capture.texteErreur())
                .as("le site déclare 130711, le point tombe dans 040110")
                .contains("040110")
                .contains("130711");
        assertThat(capture.texte())
                .as("l'identifiant du point reste SEUL sur stdout : POINT=$(vigiechiro ajouter-point ...)"
                        + " casserait sinon")
                .doesNotContain("040110");
    }

    @Test
    @DisplayName("ajouter-point : une position DANS le carré déclaré ne dit rien - une concordance est du bruit")
    void position_concordante_se_tait() {
        String site = creerSite("040110");

        int code = cli.executer(
                new String[] {
                    "ajouter-point",
                    "--site",
                    site,
                    "--code",
                    "A1",
                    "--lat",
                    String.valueOf(LAT_DANS_040110),
                    "--lon",
                    String.valueOf(LON_DANS_040110)
                },
                sortie,
                erreur);

        assertThat(code).isZero();
        assertThat(capture.texteErreur())
                .as("le dire à chaque appel ferait du bruit que personne ne lirait plus")
                .isEmpty();
    }

    /// L'écran signale depuis #5688 un point du même site à 40 m ou moins de la position saisie. La
    /// commande crée le même point par le même service et n'en disait rien (#5837, ADR 0014).
    @Test
    @DisplayName("#5837 : ajouter-point à moins de 40 m d'un point du site le dit, en le nommant, sans changer le code")
    void un_point_voisin_se_dit() {
        String site = creerSite("040110");
        ajouterPoint(site, "A1", LAT_DANS_040110, LON_DANS_040110);
        capture.vider();

        // 0,0002 degré de latitude : environ 22 m.
        int code = ajouterPoint(site, "Z1", LAT_DANS_040110 + 0.0002, LON_DANS_040110);

        assertThat(code).as("un voisinage se signale, il n'interdit rien").isZero();
        assertThat(capture.texteErreur()).contains("A1", "22 m");
        assertThat(capture.texte().strip())
                .as("l'identifiant du point reste seul sur stdout")
                .matches("\\d+");
    }

    @Test
    @DisplayName("#5837 : au-delà de 40 m, ajouter-point ne dit rien d'un voisin")
    void un_point_eloigne_ne_se_dit_pas() {
        String site = creerSite("040110");
        ajouterPoint(site, "A1", LAT_DANS_040110, LON_DANS_040110);
        capture.vider();

        // 0,001 degré de latitude : environ 111 m.
        int code = ajouterPoint(site, "Z1", LAT_DANS_040110 + 0.001, LON_DANS_040110);

        assertThat(code).isZero();
        assertThat(capture.texteErreur()).isEmpty();
    }

    private int ajouterPoint(String site, String codePoint, double latitude, double longitude) {
        return cli.executer(
                new String[] {
                    "ajouter-point",
                    "--site",
                    site,
                    "--code",
                    codePoint,
                    "--lat",
                    String.valueOf(latitude),
                    "--lon",
                    String.valueOf(longitude)
                },
                sortie,
                erreur);
    }

    @Test
    @DisplayName("ajouter-point sans coordonnées : rien à confronter, donc rien à dire")
    void point_sans_coordonnees_se_tait() {
        String site = creerSite("130711");

        int code = cli.executer(new String[] {"ajouter-point", "--site", site, "--code", "A1"}, sortie, erreur);

        assertThat(code).isZero();
        assertThat(capture.texteErreur())
                .as("un point sans position est normal : ce n'est pas une anomalie à signaler")
                .isEmpty();
    }

    @Test
    @DisplayName("modifier-point : déplacer un point hors du carré déclaré le dit aussi")
    void modifier_point_avertit_aussi() {
        String site = creerSite("130711");
        cli.executer(new String[] {"ajouter-point", "--site", site, "--code", "A1"}, sortie, erreur);
        String point = capture.texte().strip().lines().reduce((a, b) -> b).orElseThrow();
        capture.vider();

        int code = cli.executer(
                new String[] {
                    "modifier-point",
                    "--point",
                    point,
                    "--site",
                    site,
                    "--code",
                    "A1",
                    "--lat",
                    String.valueOf(LAT_DANS_040110),
                    "--lon",
                    String.valueOf(LON_DANS_040110)
                },
                sortie,
                erreur);

        assertThat(code).isZero();
        assertThat(capture.texteErreur())
                .as("le défaut se pose aussi bien en déplaçant qu'en créant : les deux portes, pas une")
                .contains("040110");
    }

    /// Crée un site déclarant `numeroCarre` et rend son identifiant.
    private String creerSite(String numeroCarre) {
        cli.executer(new String[] {"creer-site", "--carre", numeroCarre, "--sans-verification"}, sortie, erreur);
        String identifiant = capture.texte().strip().lines().reduce((a, b) -> b).orElseThrow();
        capture.vider();
        return identifiant;
    }
}
