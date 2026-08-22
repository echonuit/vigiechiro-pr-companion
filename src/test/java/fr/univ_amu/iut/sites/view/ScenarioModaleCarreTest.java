package fr.univ_amu.iut.sites.view;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.google.inject.util.Modules;
import fr.univ_amu.iut.App;
import fr.univ_amu.iut.commun.api.ClientVigieChiro;
import fr.univ_amu.iut.commun.api.ProfilVigieChiro;
import fr.univ_amu.iut.commun.api.ReponseApi;
import fr.univ_amu.iut.commun.api.SiteVigieChiro;
import fr.univ_amu.iut.commun.di.RacineInjecteur;
import fr.univ_amu.iut.commun.model.Protocole;
import fr.univ_amu.iut.commun.model.Utilisateur;
import fr.univ_amu.iut.commun.model.dao.UtilisateurDao;
import fr.univ_amu.iut.commun.persistence.MigrationSchema;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.commun.view.ExecuteurTache;
import fr.univ_amu.iut.commun.view.ExecuteurTacheAsynchrone;
import fr.univ_amu.iut.commun.view.InfobulleDeBlocage;
import fr.univ_amu.iut.connexion.model.StockageConnexion;
import fr.univ_amu.iut.connexion.viewmodel.RefletDuJeton;
import fr.univ_amu.iut.recette.CadreVisible;
import fr.univ_amu.iut.recette.CasDeRecette;
import fr.univ_amu.iut.recette.FenetreDuBanc;
import fr.univ_amu.iut.recette.Portee;
import fr.univ_amu.iut.recette.Respiration;
import fr.univ_amu.iut.recette.Seance;
import fr.univ_amu.iut.sites.model.ImportSiteDistant;
import fr.univ_amu.iut.sites.model.RapatriementCarre;
import fr.univ_amu.iut.sites.model.ServiceSites;
import fr.univ_amu.iut.sites.model.Site;
import fr.univ_amu.iut.sites.model.SouhaitDeclaration;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/// Les six cas de la **déclaration d'un carré**, joués sur la fenêtre réelle : `S1-30` à `S1-34` et
/// `S1-36` (#4166).
///
/// ## Ce que la revue a dit de leurs clips
///
/// > Il faudrait voir la fenêtre principale avec la modale pour comprendre ce qui se passe.
///
/// `ModaleSiteVerifierCarreViewTest` monte `ModaleSite.fxml` **seule** : le clip montrait une modale
/// flottant sur du noir. « La fenêtre se ferme, la fiche du carré s'ouvre » n'avait aucun écran
/// d'arrivée à montrer, et « aucun carré ajouté » aucun endroit où se voir.
///
/// Cette classe-ci part de « Mes sites », ouvre la déclaration **par le bouton**, et laisse la fenêtre
/// derrière. Les assertions de l'autre classe restent où elles sont : elles gardent le câblage, ce sont
/// les **cas** qui déménagent vers le scénario qui les montre.
///
/// ## Une seule frontière truquée
///
/// [ClientVigieChiro] répond à la place de la plateforme, et le rapatriement pose le carré sans réseau.
/// Tout le reste - le chrome, la navigation, la modale, les gardes de saisie - est le chemin de
/// production.
///
/// ## L'exécuteur asynchrone, celui de la production
///
/// Même raison qu'en [ScenarioPerceptifRecuperationCarreTest] : en synchrone, la vérification se ferait
/// sur le fil JavaFX, aucune image ne serait rendue pendant ce temps, et l'attente que ces cas font
/// juger n'existerait sur aucune trame.
@ExtendWith(ApplicationExtension.class)
class ScenarioModaleCarreTest {

    private static final String ID_USER = "u-modale-carre";

    /// Le carré **déjà déclaré** sur la plateforme : celui de `S1-31`, `S1-34` et `S1-36`.
    private static final String CARRE_PRIS = "640380";

    /// Le carré **libre**, celui que la planche de `S1-30` nomme.
    private static final String CARRE_LIBRE = "999999";

    /// Ce que « la vérification prend du temps » veut dire : de quoi rendre quelques images à la cadence
    /// du banc, donc de quoi voir le bouton attendre puis l'encart paraître.
    private static final long VERIFICATION_MS = 700;

    private final ClientVigieChiro client = mock(ClientVigieChiro.class);

    private Injector injector;

    @Start
    void start(Stage stage) throws Exception {
        Path workspace = Files.createTempDirectory("vc-scenario-modale-carre");
        System.setProperty("vigiechiro.workspace", workspace.toString());

        injector =
                Guice.createInjector(Modules.override(RacineInjecteur.modules()).with(new AbstractModule() {
                    @Override
                    protected void configure() {
                        bind(ExecuteurTache.class)
                                .to(ExecuteurTacheAsynchrone.class)
                                .in(Singleton.class);
                        bind(ClientVigieChiro.class).toInstance(client);
                    }

                    // ⚠️ QUALIFIÉ : `RechercheCarreExistantModule` relie `Optional<RapatriementCarre>` à
                    // ce nom, et un `@Provides` nu serait ignoré - le clic partirait alors au
                    // rapatriement RÉEL, qui expire sans rien dire de plus.
                    @Provides
                    @Singleton
                    @Named("vigiechiro-carre-existant")
                    RapatriementCarre rapatriement(ImportSiteDistant importSiteDistant) {
                        return new RapatriementCarre(client, importSiteDistant) {
                            @Override
                            public Resultat rapatrier(SouhaitDeclaration souhait) {
                                return poserLeCarreEtSesPoints();
                            }
                        };
                    }
                }));

        // ⚠️ Une connexion RÉELLE : depuis #4210, « Vérifier sur Vigie-Chiro » est fermé sans jeton.
        // Ces scénarios jouent un utilisateur connecté ; ils doivent l'être pour de bon, au lieu de
        // s'appuyer sur un bouton qui ne demandait rien à personne.
        seConnecter();

        SourceDeDonnees source = injector.getInstance(SourceDeDonnees.class);
        new MigrationSchema(source).migrer();
        new UtilisateurDao(source).insert(new Utilisateur(ID_USER, "Observateur"));

        FXMLLoader loader = new FXMLLoader(App.class.getResource("commun/view/MainView.fxml"));
        loader.setControllerFactory(injector::getInstance);
        FenetreDuBanc.poser(stage, loader.load(), 1180, 900);
        injector.getInstance(NavigationSites.class).ouvrirAccueil();
        FenetreDuBanc.afficher(stage);
    }

    /// Enregistre une connexion, comme la modale de connexion le ferait.
    private void seConnecter() {
        injector.getInstance(StockageConnexion.class)
                .enregistrer("jeton-de-recette", new ProfilVigieChiro(ID_USER, "chiro", "observateur"));
    }

    /// Efface la connexion : le cas hors connexion se joue sur le produit, pas sur un drapeau.
    ///
    /// ⚠️ Et le reflet est PUBLIÉ, comme la modale de connexion le fait après chaque `rafraichir()`
    /// (#4205). Écrire dans le stockage sans le dire ne réveille personne : `RefletDuJeton` garde un
    /// reflet, il ne surveille pas le fichier. Sans cette ligne, ce cas verrait le geste encore ouvert
    /// et croirait tenir un défaut du produit.
    private void seDeconnecter() {
        injector.getInstance(StockageConnexion.class).effacer();
        injector.getInstance(RefletDuJeton.class).relire();
        WaitForAsyncUtils.waitForFxEvents();
    }

    @AfterEach
    void nettoyerWorkspace() {
        System.clearProperty("vigiechiro.workspace");
    }

    @Test
    @CasDeRecette(value = "S1-30", portee = Portee.A_L_ECRAN)
    @DisplayName("S1-30 · un carré libre : l'encart vert dit qu'on peut le déclarer ici")
    void le_carre_libre_s_annonce_libre(FxRobot robot) throws TimeoutException {
        laPlateformeNeConnaitPas(CARRE_LIBRE);
        ouvrirLaDeclaration(robot);
        saisir(robot, CARRE_LIBRE);

        verifier(robot);

        assertThat(encart(robot).getText()).contains("n'existe pas encore");
        assertThat(encart(robot).getStyleClass()).contains("encart-succes");
    }

    @Test
    @CasDeRecette(value = "S1-31", portee = Portee.A_L_ECRAN)
    @DisplayName("S1-31 · un carré déjà déclaré : l'avertissement nomme le site et dit quoi faire")
    void le_carre_deja_declare_nomme_le_site(FxRobot robot) throws TimeoutException {
        laPlateformeConnait(CARRE_PRIS);
        ouvrirLaDeclaration(robot);
        saisir(robot, CARRE_PRIS);

        verifier(robot);

        assertThat(encart(robot).getText())
                .contains("Vigiechiro - Point Fixe-" + CARRE_PRIS)
                .contains("rattaché");
        assertThat(encart(robot).getStyleClass()).contains("encart-avertissement");
    }

    @Test
    @CasDeRecette(value = "S1-32", portee = Portee.A_L_ECRAN)
    @DisplayName("S1-32 · corriger un chiffre efface le verdict, qui portait sur l'ancien numéro")
    void corriger_un_chiffre_efface_le_verdict(FxRobot robot) throws TimeoutException {
        laPlateformeConnait(CARRE_PRIS);
        ouvrirLaDeclaration(robot);
        saisir(robot, CARRE_PRIS);
        verifier(robot);
        assertThat(encart(robot).isVisible()).isTrue();

        corrigerLeDernierChiffre(robot, "1");

        assertThat(encart(robot).isVisible())
                .as("un verdict sous un autre numéro serait pire que pas de vérification")
                .isFalse();
        assertThat(champCarre(robot).getText()).isEqualTo("640381");
    }

    @Test
    @CasDeRecette(value = "S1-33", portee = Portee.A_L_ECRAN)
    @DisplayName("S1-33 · hors connexion : « Vérifier » est fermé et dit pourquoi, déclarer reste possible")
    void hors_connexion_verifier_est_ferme_mais_declarer_reste_possible(FxRobot robot) throws TimeoutException {
        seDeconnecter();
        ouvrirLaDeclaration(robot);
        saisir(robot, CARRE_LIBRE);

        Button verifier = robot.lookup("#btnVerifierCarre").queryAs(Button.class);
        CadreVisible.amener(verifier, robot);
        assertThat(verifier.isDisabled())
                .as("sans jeton, la vérification ne peut rien demander : le geste est fermé (#4210)")
                .isTrue();
        // ⚠️ Le motif se FAIT PARAÎTRE : lu par programme, il ne serait pas à l'image, et « il dit ce
        // qui manque » est justement ce que ce cas donne à juger.
        assertThat(InfobulleDeBlocage.montrerEtLire(
                        robot.lookup("#enveloppeVerifierCarre").query(), robot))
                .as("et il dit ce qui manque, avec le geste qui répare")
                .contains("pas connecté")
                .contains("Se connecter à Vigie-Chiro");
        Respiration.surLeMomentCle(robot);
        Respiration.leTempsDeLire(robot);

        // ⚠️ Le pendant du cas, et le plus important : c'est la VÉRIFICATION qui se ferme, jamais la
        // déclaration. Travailler hors ligne reste normal ; fermer les deux ferait de la plateforme une
        // condition pour saisir chez soi.
        assertThat(robot.lookup("#boutonValider").queryAs(Button.class).isDisabled())
                .as("déclarer un carré hors connexion reste possible")
                .isFalse();
        Respiration.leTempsDeLire(robot);
    }

    @Test
    @CasDeRecette(value = "S1-33", portee = Portee.A_L_ECRAN)
    @DisplayName("S1-33 · connecté mais plateforme injoignable : l'encart dit qu'on n'a PAS vérifié")
    void plateforme_injoignable_l_encart_ne_nie_pas_le_carre(FxRobot robot) throws TimeoutException {
        // Connecté, donc le geste est offert : ce que ce cas juge est ce qu'on lit APRÈS le clic, quand
        // la demande est partie et n'a pas abouti. Le jeton ne garantit pas que la plateforme réponde.
        when(client.chercherCarre(CARRE_LIBRE)).thenAnswer(appel -> {
            attendreCommeLeReseau();
            return ReponseApi.injoignable("bouchon de recette");
        });
        ouvrirLaDeclaration(robot);
        saisir(robot, CARRE_LIBRE);

        verifier(robot);

        assertThat(encart(robot).getText()).contains("PAS été vérifié").doesNotContain("n'existe pas encore");
        // Le geste reste offert : la plateforme peut répondre au prochain essai, et rien n'oblige à
        // fermer puis rouvrir la fenêtre pour réessayer.
        assertThat(robot.lookup("#btnVerifierCarre").queryAs(Button.class).isDisabled())
                .as("connecté, on peut réessayer sans quitter l'écran")
                .isFalse();
        Respiration.leTempsDeLire(robot);
    }

    @Test
    @CasDeRecette(value = "S1-34", portee = Portee.A_L_ECRAN)
    @DisplayName("S1-34 · récupérer : la modale se ferme, et « Mes sites » montre le carré et le dit")
    void recuperer_ferme_la_modale_et_rend_compte(FxRobot robot) throws TimeoutException {
        laPlateformeConnait(CARRE_PRIS);
        ouvrirLaDeclaration(robot);
        saisir(robot, CARRE_PRIS);
        verifier(robot);

        WaitForAsyncUtils.waitFor(
                10,
                TimeUnit.SECONDS,
                () -> robot.lookup("#btnRecupererCarre").tryQuery().isPresent());
        Respiration.avantLeGeste(robot);
        robot.clickOn("#btnRecupererCarre");

        // ⚠️ Ce que le cas existe pour montrer, et ce n'est PLUS ce que sa planche disait : depuis #4099
        // le geste se termine là où il a commencé. La modale s'efface, « Mes sites » reste, le carré
        // paraît dans la liste et le bandeau dit ce qui vient d'être créé. La planche promettait la
        // fiche du carré ; c'est ce clip qui a montré qu'elle avait un chantier de retard (#4180).
        WaitForAsyncUtils.waitFor(
                15,
                TimeUnit.SECONDS,
                () -> robot.lookup("#lblRetour")
                        .tryQueryAs(Label.class)
                        .filter(libelle -> libelle.getText().contains(CARRE_PRIS))
                        .isPresent());
        // ⚠️ Et on attend la CARTE, pas seulement le bandeau. Les deux arrivent par des chemins
        // différents - le bandeau est posé par le compte rendu, la liste se reconstruit après le signal
        // de mutation - et la liste arrive en dernier. Le test affirmait sur elle sans l'avoir attendue :
        // vert cent fois en local, rouge dans la suite complète, là où la machine est chargée. Une
        // assertion sur un état qu'on n'a pas attendu ne mesure que la vitesse du runner.
        WaitForAsyncUtils.waitFor(
                15,
                TimeUnit.SECONDS,
                () -> !robot.lookup(".carte-site").queryAll().isEmpty());
        Respiration.surLeMomentCle(robot);

        assertThat(robot.lookup("#champCarre").tryQuery())
                .as("la modale s'est refermée : son champ n'est plus dans la scène")
                .isEmpty();
        assertThat(robot.lookup("#lblRetour").queryAs(Label.class).getText())
                .as("le bandeau nomme le carré et compte ses points")
                .contains(CARRE_PRIS);
        assertThat(robot.lookup(".carte-site").queryAll())
                .as("la liste montre le carré récupéré, sans qu'on ait navigué")
                .isNotEmpty();
    }

    @Test
    @CasDeRecette(value = "S1-36", portee = Portee.A_L_ECRAN)
    @DisplayName("S1-36 · « Créer » se ferme avec son motif, et corriger un chiffre le rouvre")
    void creer_reste_ferme_puis_se_rouvre(FxRobot robot) throws TimeoutException {
        laPlateformeConnait(CARRE_PRIS);
        ouvrirLaDeclaration(robot);
        saisir(robot, CARRE_PRIS);
        verifier(robot);

        Button creer = robot.lookup("#boutonValider").queryAs(Button.class);
        assertThat(creer.isDisabled())
                .as("déclarer ici referait le doublon local qui a produit le dépôt manqué")
                .isTrue();
        assertThat(InfobulleDeBlocage.texteDe(robot.lookup("#enveloppeValider").queryAs(StackPane.class)))
                .contains("existe déjà")
                .doesNotContain("6 chiffres");
        Respiration.surLeMomentCle(robot);

        // ⚠️ La seconde moitié du cas, que le clip ne jouait pas : « corriger un chiffre du numéro le
        // rouvre ». Sans elle, ce cas ne fait pas tout ce que sa planche annonce (#4182).
        corrigerLeDernierChiffre(robot, "1");

        assertThat(creer.isDisabled())
                .as("le numéro n'est plus celui que la plateforme connaît : le geste se rouvre")
                .isFalse();
    }

    @Test
    @CasDeRecette(value = "S1-13", portee = Portee.A_L_ECRAN)
    @DisplayName("S1-13 · « Créer » s'ouvre au sixième chiffre, et le carré paraît dans « Mes sites »")
    void creer_s_ouvre_au_sixieme_chiffre_et_ajoute_le_carre(FxRobot robot) throws TimeoutException {
        laPlateformeNeConnaitPas(CARRE_LIBRE);
        long avant = robot.lookup(".carte-site").queryAll().size();
        ouvrirLaDeclaration(robot);

        Button creer = robot.lookup("#boutonValider").queryAs(Button.class);
        assertThat(creer.isDisabled()).as("formulaire vierge : rien à créer").isTrue();
        Respiration.avantLeGeste(robot);

        // Les chiffres se tapent un par un : c'est une validation EN DIRECT que ce cas fait juger, et
        // un champ qui se remplit d'un coup ne la montre pas.
        robot.clickOn(champCarre(robot)).write("9999");
        WaitForAsyncUtils.waitForFxEvents();
        assertThat(creer.isDisabled())
                .as("quatre chiffres : toujours pas un carré")
                .isTrue();
        Respiration.leTempsDeLire(robot);

        robot.write("99");
        WaitForAsyncUtils.waitForFxEvents();
        assertThat(creer.isDisabled()).as("six chiffres : le geste s'ouvre").isFalse();
        Respiration.surLeMomentCle(robot);

        // ⚠️ Et l'écran d'ARRIVÉE, sans quoi on ne voit pas ce que la modale a changé (ADR 4188).
        robot.clickOn(creer);
        WaitForAsyncUtils.waitFor(
                10,
                TimeUnit.SECONDS,
                () -> robot.lookup(".carte-site").queryAll().size() > avant);
        Respiration.surLeMomentCle(robot);

        assertThat(titresDesCartes(robot))
                .as("la liste montre le carré que la modale vient de créer")
                .anyMatch(titre -> titre.contains(CARRE_LIBRE));
    }

    @Test
    @CasDeRecette(value = "S1-25", portee = Portee.A_L_ECRAN)
    @DisplayName("S1-25 · « Annuler » ne crée rien : la liste est la même après qu'avant")
    void annuler_ne_change_rien_a_la_liste(FxRobot robot) throws TimeoutException {
        List<String> avant = titresDesCartes(robot);
        // ⚠️ La liste AVANT. Ce cas fait juger une ABSENCE de changement : sans point de comparaison à
        // l'image, il n'y a rien à comparer, et le clip précédent montrait une modale sur du noir
        // (#4176, ADR 4188).
        Respiration.leTempsDeLire(robot);

        ouvrirLaDeclaration(robot);
        robot.clickOn(champCarre(robot)).write(CARRE_LIBRE);
        WaitForAsyncUtils.waitForFxEvents();
        Respiration.avantLeGeste(robot);

        robot.clickOn("Annuler");
        WaitForAsyncUtils.waitFor(
                10,
                TimeUnit.SECONDS,
                () -> robot.lookup("#champCarre").tryQuery().isEmpty());
        Respiration.surLeMomentCle(robot);

        assertThat(titresDesCartes(robot))
                .as("la liste est exactement celle d'avant : rien n'a été créé")
                .isEqualTo(avant);
    }

    // ----------------------------------------------------------------------------------------

    /// Ouvre la déclaration **par le bouton de l'écran**, et attend que la modale soit là.
    private void ouvrirLaDeclaration(FxRobot robot) throws TimeoutException {
        Respiration.avantLeGeste(robot);
        robot.clickOn("+ Nouveau site");
        WaitForAsyncUtils.waitFor(
                10,
                TimeUnit.SECONDS,
                () -> robot.lookup("#champCarre").tryQuery().isPresent());
        Respiration.apresLeGeste(robot);
    }

    /// Tape le numéro, chiffre à chiffre, dans le champ qu'on vient de cliquer.
    private void saisir(FxRobot robot, String carre) {
        robot.clickOn(champCarre(robot)).write(carre);
        WaitForAsyncUtils.waitForFxEvents();
    }

    /// Clique « Vérifier », et attend le verdict que l'exécuteur asynchrone rendra.
    private void verifier(FxRobot robot) throws TimeoutException {
        Respiration.avantLeGeste(robot);
        robot.clickOn("#btnVerifierCarre");
        WaitForAsyncUtils.waitFor(10, TimeUnit.SECONDS, () -> encart(robot).isVisible());
        CadreVisible.amener(encart(robot), robot);
        Respiration.surLeMomentCle(robot);
    }

    /// Remplace le dernier chiffre : fin de ligne, retour arrière, un chiffre.
    ///
    /// ⚠️ C'est le geste que la planche décrit - « changer un chiffre du carré ». Vider le champ et
    /// retaper montrerait le verdict disparaître au VIDAGE, ce qui n'est pas le cas jugé.
    private void corrigerLeDernierChiffre(FxRobot robot, String chiffre) {
        robot.clickOn(champCarre(robot));
        robot.push(KeyCode.END);
        robot.push(KeyCode.BACK_SPACE);
        robot.write(chiffre);
        WaitForAsyncUtils.waitForFxEvents();
        Respiration.surLeMomentCle(robot);
    }

    /// Les intitulés des cartes de « Mes sites », dans leur ordre d'affichage.
    private static List<String> titresDesCartes(FxRobot robot) {
        return robot.lookup(".carte-titre").queryAll().stream()
                .filter(Label.class::isInstance)
                .map(noeud -> ((Label) noeud).getText())
                .toList();
    }

    private static TextField champCarre(FxRobot robot) {
        return robot.lookup("#champCarre").queryAs(TextField.class);
    }

    private static Label encart(FxRobot robot) {
        return robot.lookup("#messageCarreExistant").queryAs(Label.class);
    }

    private void laPlateformeConnait(String carre) {
        when(client.chercherCarre(carre)).thenAnswer(appel -> {
            attendreCommeLeReseau();
            return ReponseApi.succes(List.of(new SiteVigieChiro("6a49", "Vigiechiro - Point Fixe-" + carre, true)));
        });
    }

    private void laPlateformeNeConnaitPas(String carre) {
        when(client.chercherCarre(carre)).thenAnswer(appel -> {
            attendreCommeLeReseau();
            return ReponseApi.succes(List.of());
        });
    }

    /// Le temps qu'un appel réseau prend, **seulement en séance filmée** : hors tournage, la suite ne
    /// paie pas la lisibilité des films.
    private static void attendreCommeLeReseau() throws InterruptedException {
        if (Seance.filmee()) {
            Thread.sleep(VERIFICATION_MS);
        }
    }

    /// Écrit ce que la vraie récupération écrirait : le carré, puis ses points d'écoute.
    private RapatriementCarre.Resultat.Rapatrie poserLeCarreEtSesPoints() {
        ServiceSites service = injector.getInstance(ServiceSites.class);
        Site carre = service.creerSite(CARRE_PRIS, "Étang de la Tuilière", Protocole.STANDARD, null, ID_USER);
        service.ajouterPoint(carre.id(), "A1", 43.42, 5.11, "Près du grand chêne");
        service.ajouterPoint(carre.id(), "B2", 43.43, 5.12, "Lisière de roselière");
        return new RapatriementCarre.Resultat.Rapatrie(
                carre, service.listerPoints(carre.id()).size());
    }
}
