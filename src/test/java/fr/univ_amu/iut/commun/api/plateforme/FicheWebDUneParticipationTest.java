package fr.univ_amu.iut.commun.api.plateforme;

import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.recette.Attente;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.testcontainers.selenium.BrowserWebDriverContainer;
import org.testcontainers.utility.DockerImageName;

/// Un navigateur ouvre la fiche web d'une participation, connecté comme l'observatrice qui la possède
/// (#6061, sous-chantier #5645).
///
/// C'est sur cette fiche que les lots suivants reliront ce que Companion écrit. Le navigateur rejoint le
/// réseau de la plateforme et ouvre le front à l'adresse que l'API sert (ADR 6060). Il se connecte en
/// rangeant dans le stockage du navigateur le jeton que l'état de départ frappe pour l'observatrice, là
/// où le front le lit, et non la session « John Doe » de `DEV_FAKE_AUTH`, qui ne possède rien.
///
/// Les valeurs lues viennent de `etat-de-depart.json` : en changer une fait rougir ce banc. Le navigateur
/// ne joint rien hors de la plateforme : sans cela, une lenteur de cdnjs le faisait rougir sans défaut.
@Tag("plateforme-de-test")
class FicheWebDUneParticipationTest {

    private static final long DELAI_DE_LA_FICHE_MS = 30_000L;

    private static BrowserWebDriverContainer navigateur;

    private static RemoteWebDriver pilote;

    @BeforeAll
    static void ouvrirLeNavigateur() {
        PlateformeDeTest.Acces acces = PlateformeDeTest.acces();
        String image = PlateformeDeTest.epingles(PlateformeDeTest.dossierDeLaPlateforme())
                .getProperty("navigateur.image");
        navigateur = new BrowserWebDriverContainer(
                        DockerImageName.parse(image).asCompatibleSubstituteFor("selenium/standalone-chrome"))
                .withNetwork(PlateformeDeTest.reseau())
                .withRecordingMode(BrowserWebDriverContainer.VncRecordingMode.SKIP, null);
        navigateur.start();
        // Tout hôte autre que la plateforme échoue aussitôt : la fiche ne doit rien demander à Internet.
        // Ce que le front chargeait de cdnjs est servi par l'image (#6061) ; Google Maps et Analytics,
        // dont la fiche se passe, tombent en erreur sans la retenir.
        ChromeOptions options = new ChromeOptions()
                .addArguments("--host-resolver-rules=MAP * ~NOTFOUND, EXCLUDE " + PlateformeDeTest.ALIAS_DE_L_API);
        pilote = new RemoteWebDriver(navigateur.getSeleniumAddress(), options);
        // Le jeton se range là où le front le lit, avant que la fiche ne démarre (#6183). Par `?token=`,
        // le front le rangeait puis rechargeait la page, mais une requête partie avant sans jeton pouvait
        // recevoir son 401 après coup : le front effaçait alors le jeton, et la fiche disait « Non trouvé ».
        String front = PlateformeDeTest.urlDuFrontDansLeReseau();
        pilote.get(front);
        pilote.executeScript(
                "window.localStorage.setItem('auth-session-token', arguments[0]);", acces.jeton("observatrice"));
        pilote.get(front + "#/participations/" + acces.id("participations:nuit-traitee"));
        pilote.navigate().refresh();
    }

    @AfterAll
    static void fermerLeNavigateur() {
        if (pilote != null) {
            pilote.quit();
        }
        if (navigateur != null) {
            navigateur.stop();
        }
    }

    @Test
    @DisplayName("#6061 : la fiche de la nuit traitée s'ouvre, et dit le point et l'observatrice que l'état déclare")
    void la_fiche_dit_ce_que_l_etat_de_depart_declare() {
        Attente.que(
                () -> texteDeLaPage().contains("effectuée par"),
                () -> "la fiche de la participation n'a pas paru. La page disait : " + texteDeLaPage(),
                DELAI_DE_LA_FICHE_MS);

        assertThat(texteDeLaPage())
                .as("la fiche doit nommer l'observatrice et le point que `etat-de-depart.json` déclare")
                .contains("effectuée par Observatrice")
                .contains("Point Z1");
    }

    @Test
    @DisplayName("#6061 : la session est celle de l'observatrice, et la charte ne s'interpose pas")
    void la_session_est_celle_de_l_observatrice() {
        Attente.que(
                () -> texteDeLaPage().contains("effectuée par"),
                () -> "la fiche de la participation n'a pas paru. La page disait : " + texteDeLaPage(),
                DELAI_DE_LA_FICHE_MS);

        assertThat(pilote.findElements(By.cssSelector("#charteModal.in")))
                .as("la charte s'ouvre tant que l'utilisatrice ne l'a pas acceptée : l'état de départ la"
                        + " déclare acceptée pour l'observatrice, sans quoi elle couvrirait la fiche")
                .isEmpty();
        assertThat(texteDe("nav"))
                .as("la barre de navigation nomme l'utilisatrice connectée")
                .contains("Observatrice");
    }

    private static String texteDeLaPage() {
        return texteDe("body");
    }

    /// Le texte visible du premier élément que le sélecteur désigne, lu en une seule commande (#6183).
    ///
    /// Trouver l'élément puis lire son texte en deux commandes laissait au front le temps de le
    /// remplacer entre les deux : le second appel sortait alors en `StaleElementReferenceException`,
    /// et le job rougissait sans défaut. Une lecture par le script ne garde aucune référence.
    private static String texteDe(String selecteur) {
        Object texte = pilote.executeScript(
                "const e = document.querySelector(arguments[0]); return e === null ? '' : e.innerText;", selecteur);
        return String.valueOf(texte);
    }
}
