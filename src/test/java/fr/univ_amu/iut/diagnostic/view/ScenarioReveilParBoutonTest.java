package fr.univ_amu.iut.diagnostic.view;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.Injector;
import fr.univ_amu.iut.commun.model.Protocole;
import fr.univ_amu.iut.commun.model.Utilisateur;
import fr.univ_amu.iut.commun.model.dao.UtilisateurDao;
import fr.univ_amu.iut.commun.persistence.MigrationSchema;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.commun.view.ExecuteurTache;
import fr.univ_amu.iut.commun.view.ExecuteurTacheAsynchrone;
import fr.univ_amu.iut.commun.view.Navigateur;
import fr.univ_amu.iut.importation.view.PreambuleImport;
import fr.univ_amu.iut.recette.Attente;
import fr.univ_amu.iut.recette.BancDeRecette;
import fr.univ_amu.iut.recette.CarteDeRecette;
import fr.univ_amu.iut.recette.CasDeRecette;
import fr.univ_amu.iut.recette.ExecuteurTacheRalenti;
import fr.univ_amu.iut.recette.GesteVisible;
import fr.univ_amu.iut.recette.Portee;
import fr.univ_amu.iut.recette.Respiration;
import fr.univ_amu.iut.recette.SansExceptionAvalee;
import fr.univ_amu.iut.recette.film.EnregistreurDeFilm;
import fr.univ_amu.iut.sites.model.ServiceSites;
import fr.univ_amu.iut.sites.model.Site;
import fr.univ_amu.iut.sites.view.NavigationSites;
import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.TimeoutException;
import javafx.scene.control.ListView;
import javafx.stage.Stage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/// `S2-76` : un appui sur une touche n'est pas une anomalie (#4981).
///
/// ## Le geste, tel que Samuel l'a vécu
///
/// Il est venu regarder l'écran de son enregistreur pendant la nuit. Le firmware sort alors de la
/// veille pour le laisser agir, et écrit `Wakeup by PINPUSH`. Au retour, le diagnostic lui reprochait
/// un « réveil non programmé » : son propre geste, porté à son débit, sur une nuit qu'il n'avait pas
/// abîmée.
///
/// ## Ce que ce clip doit montrer, et pourquoi l'absence se filme mal
///
/// Le remède **retire** une ligne. Un clip qui montrerait un écran sans elle ne prouverait rien : on
/// ne voit pas ce qui n'est pas là. Le banc affirme donc sur la liste des anomalies **et** sur celle
/// des évènements : le réveil par touche doit paraître au journal, qui relate, et ne pas paraître aux
/// anomalies, qui accusent. C'est la paire qui porte le sens, jamais l'absence seule.
@ExtendWith({ApplicationExtension.class, EnregistreurDeFilm.class, SansExceptionAvalee.class})
class ScenarioReveilParBoutonTest {

    private static final String ID_USER = "u-recette";

    private static final String CARRE = "640380";

    /// La carte de #5126 : son journal porte `Wakeup by PINPUSH... Cpt 2` au milieu de la nuit.
    private static final String FIXTURE = "sd-reveil-bouton";

    /// Ce qu'une anomalie de réveil dit, mot pour mot, dans `AnalyseurLogPR#collecterAnomalie`.
    private static final String ACCUSATION = "Réveil non programmé";

    private static final String APPUI = "PINPUSH";

    private static final int APPARITION_SECONDES = 30;

    private static final long PAUSE_PAR_FICHIER_MS = 900;

    private Path carteSd;

    private Injector injecteur;

    @Start
    void start(Stage stage) throws IOException {
        carteSd = CarteDeRecette.materialiser(FIXTURE);

        injecteur = BancDeRecette.surLeChrome()
                .taille(1180, 900)
                .executeur(BancDeRecette.Executeur.ASYNCHRONE)
                .remplacer(liaison -> liaison.bind(ExecuteurTache.class)
                        .toInstance(new ExecuteurTacheRalenti(new ExecuteurTacheAsynchrone(), PAUSE_PAR_FICHIER_MS)))
                .semer(this::poserLeCarreEtSonPoint)
                .ouvrir(inj -> inj.getInstance(NavigationSites.class).ouvrirDetail(CARRE))
                .montrer(stage);
    }

    private void poserLeCarreEtSonPoint(Injector inj) {
        SourceDeDonnees source = inj.getInstance(SourceDeDonnees.class);
        new MigrationSchema(source).migrer();
        new UtilisateurDao(source).insert(new Utilisateur(ID_USER, "Observateur"));
        ServiceSites service = inj.getInstance(ServiceSites.class);
        Site carre = service.creerSite(CARRE, "Étang de la Tuilière", Protocole.STANDARD, null, ID_USER);
        service.ajouterPoint(carre.id(), "A1", 43.42, 5.11, "Près du grand chêne");
    }

    @Test
    @CasDeRecette(value = "S2-76", portee = Portee.A_L_ECRAN)
    @DisplayName("S2-76 · un appui sur une touche paraît au journal, et jamais aux anomalies")
    void un_reveil_par_bouton_n_est_pas_une_anomalie(FxRobot robot) throws TimeoutException {
        PreambuleImport.importerUneNuitEtOuvrirSonPassage(robot, injecteur.getInstance(Navigateur.class), carteSd);

        Respiration.surLeMomentCle(robot);
        GesteVisible.cliquer(robot, "#boutonDiagnostic");
        WaitForAsyncUtils.waitForFxEvents();

        Attente.que(
                () -> robot.lookup("#listeAnomalies").tryQuery().isPresent(),
                "le diagnostic ne s'est pas ouvert depuis le passage : c'est par sa carte que"
                        + " l'observateur y arrive, et sans l'écran le cas n'a rien à lire",
                APPARITION_SECONDES * 1000L);

        // ─── Le réveil EST au journal : il a eu lieu, et l'écran le relate ───────────────────────
        // Cette moitié-là est celle qui rend l'autre lisible. Sans elle, une liste d'anomalies vide
        // prouverait aussi bien que le journal n'a pas été lu.
        assertThat(lignes(robot, "#listeEvenements"))
                .as("l'appui sur la touche doit PARAÎTRE au journal des évènements : il a eu lieu, et"
                        + " l'observateur doit pouvoir le retrouver. C'est le lieu des faits")
                .anyMatch(ligne -> ligne.contains(APPUI));

        // ─── Et il n'est PAS une anomalie : c'est le geste de l'observateur ──────────────────────
        assertThat(lignes(robot, "#listeAnomalies"))
                .as(
                        "« %s » accuse. Un appui sur une touche est VOULU - le firmware sort de la veille"
                                + " pour laisser l'observateur agir - et le porter à son débit lui reproche"
                                + " d'être venu regarder son enregistreur (#4981)",
                        ACCUSATION)
                .noneMatch(ligne -> ligne.contains(ACCUSATION));
    }

    /// Les lignes affichées par une liste de l'écran, telles qu'elles se lisent.
    private static java.util.List<String> lignes(FxRobot robot, String identifiant) {
        ListView<?> liste = robot.lookup(identifiant).queryAs(ListView.class);
        return liste.getItems().stream().map(String::valueOf).toList();
    }
}
