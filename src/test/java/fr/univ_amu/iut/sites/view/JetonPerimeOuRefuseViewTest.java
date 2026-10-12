package fr.univ_amu.iut.sites.view;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.inject.AbstractModule;
import com.google.inject.Injector;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import fr.univ_amu.iut.commun.api.ClientSurReseauSimule;
import fr.univ_amu.iut.commun.api.ClientVigieChiro;
import fr.univ_amu.iut.commun.api.FournisseurToken;
import fr.univ_amu.iut.commun.model.Horloge;
import fr.univ_amu.iut.commun.model.LienVigieChiro;
import fr.univ_amu.iut.commun.model.Protocole;
import fr.univ_amu.iut.commun.model.Utilisateur;
import fr.univ_amu.iut.commun.model.dao.LienVigieChiroDao;
import fr.univ_amu.iut.commun.model.dao.UtilisateurDao;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.commun.view.InfobulleDeBlocage;
import fr.univ_amu.iut.connexion.viewmodel.RefletDuJeton;
import fr.univ_amu.iut.recette.Attente;
import fr.univ_amu.iut.recette.BancDeRecette;
import fr.univ_amu.iut.recette.GesteVisible;
import fr.univ_amu.iut.recette.SansExceptionAvalee;
import fr.univ_amu.iut.sites.model.ServiceSites;
import fr.univ_amu.iut.sites.model.Site;
import java.io.ByteArrayInputStream;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/// Un jeton **périmé** ou **refusé** ferme les gestes de plateforme dès le premier geste qui le
/// découvre, et le message nomme la reconnexion (#6151).
///
/// La mesure qui a ouvert l'issue montrait l'inverse sur les trois écrans du port `EtatConnexion` : le
/// reflet restait à « connecté » jusqu'à une reconnexion, le bouton restait actif, et un `401` refaisait
/// la même requête refusée à chaque clic.
///
/// Ce banc ne simule que le **réseau** : le client et le transport sont les vrais, branchés sur la
/// source de jeton du banc, et l'horloge est pilotée. La péremption se voit donc au transport, qui
/// n'émet plus rien, et le `401` efface le jeton par le chemin de production. Toutes les lectures
/// d'état passent par le fil JavaFX.
@ExtendWith({ApplicationExtension.class, SansExceptionAvalee.class})
class JetonPerimeOuRefuseViewTest {

    private static final String ID_USER = "u-test";
    private static final String CARRE = "640380";
    private static final String SE_CONNECTER = "Se connecter à Vigie-Chiro…";
    private static final String GESTE = "Reconnectez-vous depuis le menu principal";
    private static final long DELAI = 10_000;
    private static final LocalDate JOUR_0 = LocalDate.of(2026, 10, 1);

    /// L'horloge de l'application, que le cas avance au lieu d'attendre.
    static final class HorlogeMobile implements Horloge {
        private final AtomicReference<LocalDate> jour = new AtomicReference<>(JOUR_0);

        void avancerDe(int jours) {
            jour.set(JOUR_0.plusDays(jours));
        }

        @Override
        public LocalDate aujourdhui() {
            return jour.get();
        }

        @Override
        public LocalDateTime maintenant() {
            return jour.get().atTime(12, 0);
        }
    }

    private enum Serveur {
        ACCEPTE,
        REFUSE_401
    }

    private final HorlogeMobile horloge = new HorlogeMobile();
    private final AtomicReference<Serveur> serveur = new AtomicReference<>(Serveur.ACCEPTE);

    /// Chaque requête qui a VRAIMENT atteint le réseau simulé.
    private final List<String> requetes = new CopyOnWriteArrayList<>();

    private Injector injector;
    private Site etang;

    @Start
    void start(Stage stage) throws Exception {
        HttpClient http = mock(HttpClient.class);
        when(http.send(any(), any())).thenAnswer(appel -> repondre(appel.getArgument(0)));
        injector = BancDeRecette.surLeChrome()
                .taille(1180, 900)
                .executeur(BancDeRecette.Executeur.SYNCHRONE)
                .connecte(ID_USER, "chiro", "observateur")
                .remplacer(new AbstractModule() {
                    @Override
                    protected void configure() {
                        bind(Horloge.class).toInstance(horloge);
                    }

                    @Provides
                    @Singleton
                    ClientVigieChiro client(FournisseurToken jetons) {
                        return ClientSurReseauSimule.sur(http, jetons);
                    }
                })
                .semer(this::semer)
                .ouvrir(inj -> inj.getInstance(NavigationSites.class).ouvrirAccueil())
                .montrer(stage);
    }

    private void semer(Injector inj) {
        SourceDeDonnees source = inj.getInstance(SourceDeDonnees.class);
        new UtilisateurDao(source).insert(new Utilisateur(ID_USER, "Testeur"));
        ServiceSites service = inj.getInstance(ServiceSites.class);
        etang = service.creerSite(CARRE, "Étang de la Tuilière", Protocole.STANDARD, null, ID_USER);
        service.ajouterPoint(etang.id(), "A1", 43.5, 5.4, "Chêne");
        service.creerSite("752204", "ZAC Nord", Protocole.STANDARD, null, ID_USER);
        // Le carré est relié : c'est la condition pour que la fiche offre de publier un point.
        new LienVigieChiroDao(source)
                .upsert(new LienVigieChiro(LienVigieChiro.ENTITE_SITE, String.valueOf(etang.id()), "vc-1", false));
    }

    @AfterEach
    void nettoyer() {
        System.clearProperty("vigiechiro.workspace");
    }

    // ---------------------------------------------------------------- Mes sites

    @Test
    @DisplayName("#6151 : jeton périmé, « Récupérer depuis Vigie-Chiro » dit de se reconnecter, puis se ferme")
    void peremption_le_premier_geste_ferme_la_recuperation(FxRobot robot) {
        assertThat(boutonFerme(robot, "#btnSyncVigieChiro"))
                .as("connecté : le geste est ouvert")
                .isFalse();

        horloge.avancerDe(15);
        GesteVisible.cliquer(robot, "#btnSyncVigieChiro");

        assertThat(texte(robot, "#lblSynchro"))
                .as("le message nomme la cause et le geste qui répare")
                .contains("jeton absent ou expiré")
                .contains(GESTE);
        assertThat(requetes).as("sans jeton, rien ne part").isEmpty();
        assertThat(reflet()).as("le reflet a suivi le jeton").isFalse();
        assertThat(boutonFerme(robot, "#btnSyncVigieChiro"))
                .as("le geste se ferme")
                .isTrue();
        assertThat(InfobulleDeBlocage.texteDe(robot.lookup("#enveloppeSync").query()))
                .as("et son motif dit où se reconnecter")
                .contains(SE_CONNECTER);
    }

    @Test
    @DisplayName("#6151 : jeton périmé, rouvrir la modale de connexion ferme le geste sans autre clic")
    void peremption_rouvrir_la_modale_ferme_la_recuperation(FxRobot robot) {
        horloge.avancerDe(15);

        GesteVisible.cliquer(robot, "#menuOutils");
        WaitForAsyncUtils.waitForFxEvents();
        GesteVisible.cliquer(robot, SE_CONNECTER);
        WaitForAsyncUtils.waitForFxEvents();
        assertThat(texte(robot, "#labelIdentite")).isEqualTo("Non connecté");
        robot.type(KeyCode.ESCAPE);
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(reflet()).as("la modale relit l'état à son ouverture").isFalse();
        assertThat(boutonFerme(robot, "#btnSyncVigieChiro")).isTrue();
    }

    @Test
    @DisplayName("#6151 : témoin, au dernier jour valide la récupération part et le geste reste ouvert")
    void dernier_jour_valide_rien_ne_change(FxRobot robot) {
        horloge.avancerDe(14);
        GesteVisible.cliquer(robot, "#btnSyncVigieChiro");

        assertThat(texte(robot, "#lblSynchro")).isEqualTo("2 sites synchronisés depuis Vigie-Chiro.");
        assertThat(requetes).as("le jeton du quatorzième jour part encore").isNotEmpty();
        assertThat(jetonEnregistre()).isTrue();
        assertThat(reflet()).isTrue();
        assertThat(boutonFerme(robot, "#btnSyncVigieChiro")).isFalse();
    }

    @Test
    @DisplayName("#6151 : jeton refusé (401), le jeton est oublié, le geste se ferme et ne se rejoue pas")
    void refus_401_ferme_la_recuperation_et_ne_rejoue_pas(FxRobot robot) {
        serveur.set(Serveur.REFUSE_401);

        GesteVisible.cliquer(robot, "#btnSyncVigieChiro");
        int apresLePremier = requetes.size();

        assertThat(apresLePremier)
                .as("le premier clic part : rien ne pouvait prévoir le refus")
                .isPositive();
        assertThat(texte(robot, "#lblSynchro"))
                .contains("jeton refusé par Vigie-Chiro")
                .contains(GESTE);
        assertThat(jetonEnregistre())
                .as("un 401 de l'API efface le jeton enregistré")
                .isFalse();
        assertThat(reflet()).isFalse();
        assertThat(boutonFerme(robot, "#btnSyncVigieChiro")).isTrue();

        GesteVisible.cliquer(robot, "#btnSyncVigieChiro");
        assertThat(requetes)
                .as("le second clic ne refait pas la requête refusée")
                .hasSize(apresLePremier);
    }

    // ---------------------------------------------------------------- modale de carré

    @Test
    @DisplayName("#6151 : jeton périmé, « Vérifier sur Vigie-Chiro » dit de se reconnecter, puis se ferme")
    void peremption_la_verification_du_carre_se_ferme(FxRobot robot) {
        ouvrirLaCreationEtSaisirLeCarre(robot);
        horloge.avancerDe(15);

        GesteVisible.cliquer(robot, "#btnVerifierCarre");

        assertThat(texte(robot, "#messageCarreExistant"))
                .contains("PAS été vérifié")
                .contains(GESTE)
                .doesNotContain("injoignable");
        assertThat(requetes).isEmpty();
        assertThat(boutonFerme(robot, "#btnVerifierCarre")).isTrue();
    }

    @Test
    @DisplayName("#6151 : jeton refusé (401), la vérification du carré se ferme et ne se rejoue pas")
    void refus_401_la_verification_du_carre_se_ferme(FxRobot robot) {
        serveur.set(Serveur.REFUSE_401);
        ouvrirLaCreationEtSaisirLeCarre(robot);

        GesteVisible.cliquer(robot, "#btnVerifierCarre");

        assertThat(requetes).hasSize(1);
        assertThat(texte(robot, "#messageCarreExistant")).contains(GESTE).doesNotContain("injoignable");
        assertThat(jetonEnregistre()).isFalse();
        assertThat(boutonFerme(robot, "#btnVerifierCarre")).isTrue();
        GesteVisible.cliquer(robot, "#btnVerifierCarre");
        assertThat(requetes).as("le second clic ne refait pas la requête").hasSize(1);
    }

    @Test
    @DisplayName("#6151 : témoin, jeton valide et plateforme qui répond, la vérification rend son verdict")
    void temoin_la_verification_du_carre_rend_son_verdict(FxRobot robot) {
        ouvrirLaCreationEtSaisirLeCarre(robot);
        horloge.avancerDe(14);

        GesteVisible.cliquer(robot, "#btnVerifierCarre");

        assertThat(texte(robot, "#messageCarreExistant")).contains("n'existe pas encore sur Vigie-Chiro");
        assertThat(boutonFerme(robot, "#btnVerifierCarre")).isFalse();
    }

    // ---------------------------------------------------------------- fiche du site

    @Test
    @DisplayName("#6151 : jeton refusé (401), « Publier sur Vigie-Chiro » dit de se reconnecter, puis se ferme")
    void refus_401_la_publication_d_un_point_se_ferme(FxRobot robot) {
        serveur.set(Serveur.REFUSE_401);
        ouvrirLaFiche(robot);
        assertThat(lienFerme(robot))
                .as("connecté, carré relié, point placé : le geste est ouvert")
                .isFalse();

        GesteVisible.cliquer(robot, lienPublier(robot));
        int apresLePremier = requetes.size();

        assertThat(apresLePremier).isPositive();
        assertThat(texte(robot, "#lblRetour")).contains(GESTE);
        assertThat(jetonEnregistre()).isFalse();
        assertThat(reflet()).isFalse();
        assertThat(lienFerme(robot)).as("la carte reconstruite ferme le geste").isTrue();
    }

    @Test
    @DisplayName("#6151 : jeton périmé, la publication d'un point dit de se reconnecter, puis se ferme")
    void peremption_la_publication_d_un_point_se_ferme(FxRobot robot) {
        ouvrirLaFiche(robot);
        horloge.avancerDe(15);

        GesteVisible.cliquer(robot, lienPublier(robot));

        assertThat(requetes).isEmpty();
        // La fiche refuse avant le réseau, sur son propre motif de gris : il nomme le geste et l'endroit.
        assertThat(texte(robot, "#lblRetour")).contains("Connectez-vous").contains(SE_CONNECTER);
        assertThat(reflet()).isFalse();
        assertThat(lienFerme(robot)).isTrue();
    }

    // ---------------------------------------------------------------- aides

    private void ouvrirLaCreationEtSaisirLeCarre(FxRobot robot) {
        Node nouveau = Attente.surLeFil(
                () -> robot.lookup("+ Nouveau site").queryAll().stream()
                        .filter(Node::isVisible)
                        .findFirst()
                        .orElseThrow(),
                "trouver « + Nouveau site »",
                DELAI);
        GesteVisible.cliquer(robot, nouveau);
        WaitForAsyncUtils.waitForFxEvents();
        GesteVisible.remplacerLeTexte(robot, "#champCarre", CARRE);
        WaitForAsyncUtils.waitForFxEvents();
        assertThat(boutonFerme(robot, "#btnVerifierCarre"))
                .as("connecté, six chiffres : ouvert")
                .isFalse();
    }

    private void ouvrirLaFiche(FxRobot robot) {
        robot.interact(() -> injector.getInstance(NavigationSites.class).ouvrirDetail(etang));
        WaitForAsyncUtils.waitForFxEvents();
    }

    private Hyperlink lienPublier(FxRobot robot) {
        return Attente.surLeFil(
                () -> robot.lookup(".hyperlink").queryAllAs(Hyperlink.class).stream()
                        .filter(lien -> "Publier sur Vigie-Chiro".equals(lien.getText()))
                        .findFirst()
                        .orElseThrow(() -> new AssertionError("aucun lien « Publier sur Vigie-Chiro »")),
                "trouver le lien de publication",
                DELAI);
    }

    private boolean lienFerme(FxRobot robot) {
        Hyperlink lien = lienPublier(robot);
        return Attente.surLeFil(lien::isDisable, "lire le lien de publication", DELAI);
    }

    private boolean boutonFerme(FxRobot robot, String selecteur) {
        return Attente.surLeFil(
                () -> robot.lookup(selecteur).queryAs(Button.class).isDisabled(), "lire " + selecteur, DELAI);
    }

    private String texte(FxRobot robot, String selecteur) {
        return Attente.surLeFil(
                () -> robot.lookup(selecteur).queryAs(Label.class).getText(), "lire " + selecteur, DELAI);
    }

    private boolean reflet() {
        return Attente.surLeFil(
                () -> injector.getInstance(RefletDuJeton.class)
                        .connecteProperty()
                        .get(),
                "lire le reflet",
                DELAI);
    }

    /// Le jeton est-il encore ENREGISTRÉ ? Lu au fichier, sans l'horloge : « effacé » et « périmé » ne
    /// se confondent pas, et c'est l'effacement que le `401` doit produire.
    private static boolean jetonEnregistre() {
        return Files.exists(Path.of(System.getProperty("vigiechiro.workspace"), "connexion.json"));
    }

    private HttpResponse<Object> repondre(HttpRequest requete) {
        String chemin = requete.uri().getPath();
        requetes.add(requete.method() + " " + chemin);
        if (serveur.get() == Serveur.REFUSE_401) {
            return reponse(401, "{\"_status\":\"ERR\",\"_error\":{\"code\":401}}");
        }
        if (chemin.endsWith("/moi")) {
            return reponse(200, "{\"_id\":\"" + ID_USER + "\",\"pseudo\":\"chiro\",\"role\":\"observateur\"}");
        }
        String requeteBrute = requete.uri().getQuery();
        if (chemin.endsWith("/moi/participations") && requeteBrute != null && requeteBrute.contains("page=1")) {
            return reponse(
                    200,
                    "{\"_meta\":{\"total\":2},\"_items\":[" + participation("p1", "vc-1", CARRE) + ","
                            + participation("p2", "vc-2", "752204") + "]}");
        }
        return reponse(200, "{\"_meta\":{\"total\":0},\"_items\":[]}");
    }

    private static String participation(String id, String site, String carre) {
        return "{\"_id\":\"" + id + "\",\"site\":{\"_id\":\"" + site + "\",\"titre\":\"Vigiechiro - Point Fixe-" + carre
                + "\",\"observateur\":\"" + ID_USER + "\"}}";
    }

    @SuppressWarnings("unchecked")
    private static HttpResponse<Object> reponse(int statut, String corps) {
        byte[] octets = corps.getBytes(StandardCharsets.UTF_8);
        HttpResponse<Object> reponse = mock(HttpResponse.class);
        when(reponse.statusCode()).thenReturn(statut);
        when(reponse.body()).thenAnswer(appel -> new ByteArrayInputStream(octets));
        when(reponse.headers()).thenReturn(HttpHeaders.of(Map.of(), (nom, valeur) -> true));
        return reponse;
    }
}
