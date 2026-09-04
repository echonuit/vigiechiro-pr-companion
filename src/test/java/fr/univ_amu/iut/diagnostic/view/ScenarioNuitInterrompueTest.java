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
import javafx.scene.control.Labeled;
import javafx.stage.Stage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/// `S2-74` : une nuit qui s'est arrêtée avant son terme le dit (#5093).
///
/// ## Ce que le second encart ajoute au premier
///
/// Le diagnostic porte deux axes distincts sous la cohérence horaire. Le premier dit si
/// l'enregistrement **couvre la fenêtre exigée** par le protocole ; celui-ci dit si la nuit **s'est
/// interrompue**. Une nuit peut porter les deux, et les confondre reviendrait à taire l'un des deux
/// faits.
///
/// ## Pourquoi une carte à soi
///
/// `sd-nominale` referme toujours son journal : sur elle, cet encart ne peut rien dire. La carte de
/// #5126 s'arrête après le réveil, sans mise en veille - ce que laisse une carte pleine, une batterie
/// vide ou un arrêt subi - et c'est le seul état où le second encart a quelque chose à annoncer.
///
/// Le banc affirme sur la **phrase**, pas sur la visibilité du bloc : un encart présent et muet
/// passerait un contrôle de visibilité sans rien apprendre à personne.
@ExtendWith({ApplicationExtension.class, EnregistreurDeFilm.class, SansExceptionAvalee.class})
class ScenarioNuitInterrompueTest {

    private static final String ID_USER = "u-recette";

    private static final String CARRE = "640380";

    /// La carte de #5126 : son journal s'arrête après le réveil, sans mise en veille.
    private static final String FIXTURE = "sd-nuit-interrompue";

    /// Ce que l'encart dit, mot pour mot, dans `DiagnosticViewModel#libelleCompletude`.
    private static final String INTERRUPTION = "interrompue avant son terme";

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
    @CasDeRecette(value = "S2-74", portee = Portee.A_L_ECRAN)
    @DisplayName("S2-74 · le second encart dit que la nuit s'est interrompue avant son terme")
    void une_nuit_interrompue_le_dit(FxRobot robot) throws TimeoutException {
        PreambuleImport.importerUneNuitEtOuvrirSonPassage(robot, injecteur.getInstance(Navigateur.class), carteSd);

        Respiration.surLeMomentCle(robot);
        GesteVisible.cliquer(robot, "#boutonDiagnostic");
        WaitForAsyncUtils.waitForFxEvents();

        Attente.que(
                () -> robot.lookup("#lblNuitInterrompue").tryQuery().isPresent(),
                "le diagnostic ne s'est pas ouvert depuis le passage : c'est par sa carte que"
                        + " l'observateur y arrive, et sans l'écran le cas n'a rien à lire",
                APPARITION_SECONDES * 1000L);
        // L'écran de diagnostic vient de s'ouvrir, et c'est LUI que le cas donne à lire. Sans arrêt,
        // il paraît et le clip s'arrête : retour de la revue du 2026-09-04, « la fenêtre diagnostique
        // apparaît trop vite et on n'a pas le temps de lire ». Quatre scénarios l'ouvraient, aucun ne
        // le tenait.
        Respiration.leTempsDeLire(robot);

        Labeled encart = robot.lookup("#lblNuitInterrompue").queryAs(Labeled.class);

        assertThat(encart.isVisible())
                .as("le second encart doit être MONTRÉ : le journal de cette carte s'arrête avant la"
                        + " mise en veille, et c'est précisément le cas qu'il existe pour dire")
                .isTrue();

        assertThat(encart.getText())
                .as("l'encart doit DIRE l'interruption, et non se contenter d'exister. C'est cette"
                        + " phrase-là qui manquait : une nuit s'arrêtait en son milieu sans que rien ne"
                        + " le signale (#5093)")
                .contains(INTERRUPTION);
    }
}
