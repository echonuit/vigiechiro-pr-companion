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

/// `S2-75` : sans journal, le second encart **ne dit rien** (#5093, #5071).
///
/// ## Filmer un silence
///
/// L'encart se tait, et c'est le bon comportement : le journal ne couvre pas la nuit, donc il n'y a ni
/// interruption à signaler ni fin normale à attester. Son silence n'est **pas** une preuve que la nuit
/// fut entière - le journal est circulaire (R19).
///
/// Un clip qui montrerait un écran sans cet encart ne prouverait rien : on ne voit pas ce qui n'est
/// pas là. Le banc affirme donc **deux** choses ensemble - l'encart se tait, ET le reste du diagnostic
/// est bien là - faute de quoi l'absence pourrait aussi bien venir d'un écran qui ne s'est pas ouvert.
///
/// C'est la troisième face de la même règle : #5093 pour la nuit tronquée qui parle, #5071 pour la
/// nuit inconnue qui se tait, et ici pour l'écran qui n'invente rien.
@ExtendWith({ApplicationExtension.class, EnregistreurDeFilm.class, SansExceptionAvalee.class})
class ScenarioDiagnosticSansJournalTest {

    private static final String ID_USER = "u-recette";

    private static final String CARRE = "640380";

    /// La carte sans aucun journal : il n'y a donc rien à établir sur la fin de la nuit.
    private static final String FIXTURE = "sd-sans-journal";

    /// Ce que l'encart dirait s'il avait quelque chose à dire.
    private static final String INTERRUPTION = "interrompue";

    private static final String FIN_NORMALE = "fin de nuit normale";

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
    @CasDeRecette(value = "S2-75", portee = Portee.A_L_ECRAN)
    @DisplayName("S2-75 · sans journal, le second encart se tait, et le reste du diagnostic est là")
    void sans_journal_le_second_encart_se_tait(FxRobot robot) throws TimeoutException {
        PreambuleImport.importerUneNuitEtOuvrirSonPassage(robot, injecteur.getInstance(Navigateur.class), carteSd);

        Respiration.surLeMomentCle(robot);
        GesteVisible.cliquer(robot, "#boutonDiagnostic");
        WaitForAsyncUtils.waitForFxEvents();

        Attente.que(
                () -> robot.lookup("#lblNuitInterrompue").tryQuery().isPresent(),
                "le diagnostic ne s'est pas ouvert depuis le passage : sans l'écran, un encart muet ne"
                        + " prouve rien",
                APPARITION_SECONDES * 1000L);

        Labeled encart = robot.lookup("#lblNuitInterrompue").queryAs(Labeled.class);

        // ─── L'encart SE TAIT ────────────────────────────────────────────────────────────────────
        String dit = encart.getText() == null ? "" : encart.getText();
        assertThat(dit)
                .as("le journal ne couvre pas cette nuit : il n'y a ni interruption à signaler ni fin"
                        + " normale à attester. Affirmer l'un ou l'autre ferait passer une absence de"
                        + " preuve pour une preuve, ce que #4990 a précisément retiré du produit")
                .doesNotContain(INTERRUPTION)
                .doesNotContain(FIN_NORMALE);

        // ─── ET le reste du diagnostic est bien là ───────────────────────────────────────────────
        // Sans cette moitié, le silence de l'encart pourrait venir d'un écran qui ne s'est pas ouvert.
        assertThat(robot.lookup("#grapheClimat").tryQuery())
                .as("l'écran est bien celui du diagnostic, et il est peuplé : le silence de l'encart"
                        + " est donc un choix du produit, pas une page vide")
                .isPresent();
    }
}
