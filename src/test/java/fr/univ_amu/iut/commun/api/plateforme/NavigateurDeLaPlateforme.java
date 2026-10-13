package fr.univ_amu.iut.commun.api.plateforme;

import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.testcontainers.selenium.BrowserWebDriverContainer;
import org.testcontainers.utility.DockerImageName;

/// Le navigateur qui lit le front web de la plateforme de test, connecté comme un de ses utilisateurs
/// (#6061, mis en commun par #6062).
///
/// Il rejoint le réseau de la plateforme et ouvre le front à l'adresse que l'API sert (ADR 6060). Il ne
/// joint rien d'autre : ce que le front chargeait de cdnjs est servi par l'image, et une lenteur
/// d'Internet ne fait pas rougir un banc.
///
/// Deux courses avec le front ont fait rougir le job `plateforme-de-test` sans défaut (#6183), et cette
/// classe les tient à un seul endroit :
///
/// - **la connexion**. Le front efface le jeton quand une requête partie sans lui reçoit son 401. Le
///   jeton se range donc depuis une page de la même origine qui ne lance aucun script, avant que le front
///   ne tourne ;
/// - **la lecture**. Le front remplace ses éléments en se dessinant. Un texte ou une valeur se lit donc en
///   une seule commande, sans garder de référence à un élément d'une commande à l'autre.
final class NavigateurDeLaPlateforme implements AutoCloseable {

    /// Le délai laissé au front pour dessiner une page après ses requêtes.
    static final long DELAI_D_UNE_PAGE_MS = 30_000L;

    private final BrowserWebDriverContainer conteneur;

    private final RemoteWebDriver pilote;

    private NavigateurDeLaPlateforme(BrowserWebDriverContainer conteneur, RemoteWebDriver pilote) {
        this.conteneur = conteneur;
        this.pilote = pilote;
    }

    /// Démarre un navigateur dans le réseau de la plateforme, connecté sous le jeton de l'utilisateur
    /// que l'état de départ déclare sous cette clé.
    ///
    /// La session « John Doe » que `DEV_FAKE_AUTH` ouvrirait ne possède rien : c'est le jeton frappé
    /// pour l'utilisateur déclaré qui ouvre ses participations.
    static NavigateurDeLaPlateforme connecteComme(String utilisateur) {
        String jeton = PlateformeDeTest.acces().jeton(utilisateur);
        String image = PlateformeDeTest.epingles(PlateformeDeTest.dossierDeLaPlateforme())
                .getProperty("navigateur.image");
        BrowserWebDriverContainer conteneur = new BrowserWebDriverContainer(
                        DockerImageName.parse(image).asCompatibleSubstituteFor("selenium/standalone-chrome"))
                .withNetwork(PlateformeDeTest.reseau())
                .withRecordingMode(BrowserWebDriverContainer.VncRecordingMode.SKIP, null);
        conteneur.start();
        // Tout hôte autre que la plateforme échoue aussitôt : le front ne doit rien demander à Internet.
        // Google Maps et Analytics, dont ses pages se passent, tombent en erreur sans les retenir.
        ChromeOptions options = new ChromeOptions()
                .addArguments("--host-resolver-rules=MAP * ~NOTFOUND, EXCLUDE " + PlateformeDeTest.ALIAS_DE_L_API);
        RemoteWebDriver pilote = new RemoteWebDriver(conteneur.getSeleniumAddress(), options);
        pilote.get(PlateformeDeTest.urlDuFrontDansLeReseau() + "favicon.ico");
        pilote.executeScript("window.localStorage.setItem('auth-session-token', arguments[0]);", jeton);
        return new NavigateurDeLaPlateforme(conteneur, pilote);
    }

    /// Ouvre une route du front, donnée sans son `#/` : `participations/<id>`.
    ///
    /// La page est rechargée en entier : le banc lit alors ce que la plateforme porte à cet instant,
    /// sans dépendre de ce que le front garde en mémoire d'une route à l'autre.
    void ouvrir(String route) {
        pilote.get(PlateformeDeTest.urlDuFrontDansLeReseau() + "#/" + route);
        pilote.navigate().refresh();
    }

    /// Le texte visible du premier élément que le sélecteur désigne, ou une chaîne vide s'il n'y en a pas.
    String texteDe(String selecteur) {
        Object texte = pilote.executeScript(
                "const e = document.querySelector(arguments[0]); return e === null ? '' : e.innerText;", selecteur);
        return String.valueOf(texte);
    }

    /// La valeur du champ de formulaire qui porte cet identifiant, ou une chaîne vide s'il n'existe pas.
    ///
    /// Par identifiant et non par sélecteur : ceux du front portent un point
    /// (`configuration.detecteur_enregistreur_numero_serie`), qu'un sélecteur lirait comme une classe.
    String valeurDuChamp(String identifiant) {
        Object valeur = pilote.executeScript(
                "const e = document.getElementById(arguments[0]); return e === null ? '' : e.value;", identifiant);
        return String.valueOf(valeur);
    }

    /// Le nombre d'éléments que le sélecteur désigne.
    long nombreDe(String selecteur) {
        Object nombre = pilote.executeScript("return document.querySelectorAll(arguments[0]).length;", selecteur);
        return ((Number) nombre).longValue();
    }

    @Override
    public void close() {
        pilote.quit();
        conteneur.stop();
    }
}
