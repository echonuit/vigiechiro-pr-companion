package fr.univ_amu.iut.qualification.view;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.Injector;
import fr.univ_amu.iut.commun.model.Protocole;
import fr.univ_amu.iut.commun.model.Utilisateur;
import fr.univ_amu.iut.commun.model.VerdictFichier;
import fr.univ_amu.iut.commun.model.dao.UtilisateurDao;
import fr.univ_amu.iut.commun.persistence.MigrationSchema;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.commun.view.ExecuteurTache;
import fr.univ_amu.iut.commun.view.ExecuteurTacheAsynchrone;
import fr.univ_amu.iut.commun.view.FiltreFichier;
import fr.univ_amu.iut.commun.view.Navigateur;
import fr.univ_amu.iut.commun.view.SelecteurFichier;
import fr.univ_amu.iut.importation.view.PreambuleImport;
import fr.univ_amu.iut.qualification.model.ServiceEmport;
import fr.univ_amu.iut.qualification.model.dao.SelectionDao;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import javafx.scene.control.TableView;
import javafx.stage.Stage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/// Le parcours d'emport vu du **poste expéditeur** (#4728, S3-45 à S3-49).
///
/// Première des deux familles de clips. Un clip filme une application ; le parcours en traverse deux,
/// et le découper par **rôle** est ce qui rend chacun tournable. Ce qu'aucune famille ne peut montrer
/// est le voyage du fichier : il s'y réduit à un paquet qui part.
///
/// **Deux contrôles négatifs sont filmés**, et c'est ce qui donne son sens au reste : annuler la
/// désignation, et refuser l'annonce de volume. Un clip qui ne montre que le chemin heureux laisse
/// croire qu'il n'y a rien à refuser.
@ExtendWith({ApplicationExtension.class, EnregistreurDeFilm.class, SansExceptionAvalee.class})
class ScenarioEmportExpediteurTest {

    private static final String ID_USER = "u-recette";

    private static final String CARRE = "640380";

    private static final String FIXTURE = "sd-nominale";

    private static final int APPARITION_SECONDES = 30;

    private static final long PAUSE_PAR_FICHIER_MS = 900;

    private Path carteSd;

    private Path dossierDEmport;

    private Injector injecteur;

    @Start
    void start(Stage stage) throws IOException {
        carteSd = CarteDeRecette.materialiser(FIXTURE);
        dossierDEmport = Files.createTempDirectory("emport-recette");

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
    @CasDeRecette(
            value = {"S3-45", "S3-46", "S3-47"},
            portee = Portee.A_L_ECRAN)
    @DisplayName("S3-45 à S3-47 · emporter une nuit : le volume s'annonce, et deux refus n'écrivent rien")
    void emporter_une_nuit_et_ses_deux_refus(FxRobot robot) throws TimeoutException {
        ouvrirLaVerification(robot);
        Path destination = dossierDEmport.resolve("nuit.zip");

        // S3-46 d'abord : annuler la désignation. Le geste doit s'arrêter AVANT de peser, donc avant
        // que la moindre question soit posée - c'est ce que le clip doit montrer, et l'ordre compte :
        // le filmer en dernier laisserait un paquet déjà écrit sur le disque.
        List<String> demandes = new ArrayList<>();
        QualificationController controleur = controleurDeLEcran();
        controleur.confirmateur().definir(message -> {
            demandes.add(message);
            return false;
        });
        controleur.notificateur().definir((niveau, entete, message) -> {});
        definirSelecteur(robot, selecteurQuiAnnule());

        GesteVisible.choisir(robot, controleur.menuDeLaSelection(), "Emporter cette nuit…");
        Respiration.surLeMomentCle(robot);

        assertThat(demandes)
                .as("S3-46 : annuler la désignation arrête le geste avant même de peser")
                .isEmpty();
        assertThat(Files.exists(destination)).isFalse();

        // S3-47 : le volume s'annonce, et on le refuse.
        definirSelecteur(robot, selecteurQuiRepond(destination));
        GesteVisible.choisir(robot, controleur.menuDeLaSelection(), "Emporter cette nuit…");
        Respiration.surLeMomentCle(robot);

        assertThat(demandes)
                .as("S3-45 : le volume s'annonce AVANT d'écrire, sinon on confirme à l'aveugle")
                .singleElement()
                .satisfies(
                        annonce -> assertThat(annonce).contains("séquence(s)").contains("audio"));
        assertThat(Files.exists(destination))
                .as("S3-47 : un volume refusé ne laisse aucun fichier")
                .isFalse();

        // S3-45 : la même annonce, acceptée cette fois.
        controleur.confirmateur().definir(message -> true);
        GesteVisible.choisir(robot, controleur.menuDeLaSelection(), "Emporter cette nuit…");
        Respiration.surLeMomentCle(robot);

        assertThat(Files.exists(destination))
                .as("S3-45 : confirmé, le paquet est écrit")
                .isTrue();
    }

    @Test
    @CasDeRecette(
            value = {"S3-48", "S3-49"},
            portee = Portee.A_L_ECRAN)
    @DisplayName("S3-48 et S3-49 - reprendre un avis : il se range a cote, et un second se confirme")
    void reprendre_un_avis_et_le_second_qui_se_confirme(FxRobot robot) throws TimeoutException, IOException {
        ouvrirLaVerification(robot);
        QualificationController controleur = controleurDeLEcran();
        List<String> questions = new ArrayList<>();
        controleur.confirmateur().definir(message -> {
            questions.add(message);
            return true;
        });
        List<String> comptes = new ArrayList<>();
        controleur.notificateur().definir((niveau, entete, message) -> comptes.add(entete + " | " + message));

        // Deux avis, écrits par le service : le voyage du fichier n'est filmable par aucune famille.
        Path premier = avisSignePar("claire");
        Path second = avisSignePar("martin");

        // S3-48 : le premier avis se range à côté du nôtre.
        definirSelecteur(robot, selecteurQuiRepond(premier));
        GesteVisible.choisir(robot, controleur.menuDeLaSelection(), "Reprendre un avis reçu…");
        Respiration.surLeMomentCle(robot);

        assertThat(comptes)
                .as("S3-48 : le compte rendu nomme qui a jugé, et combien de verdicts sont rangés")
                .anySatisfy(compte -> assertThat(compte).contains("claire"));

        // S3-49 : le second nomme le relecteur présent avant de remplacer.
        definirSelecteur(robot, selecteurQuiRepond(second));
        GesteVisible.choisir(robot, controleur.menuDeLaSelection(), "Reprendre un avis reçu…");
        Respiration.surLeMomentCle(robot);

        assertThat(questions)
                .as("S3-49 : un second avis nomme celui qui serait remplace, et ce qui serait perdu")
                .anySatisfy(question -> assertThat(question).contains("claire").contains("verdict"));
    }

    /// Un avis tel qu'un relecteur le renverrait, ecrit par le service : le geste de l'autre role est
    /// filme par l'autre famille, pas ici.
    private Path avisSignePar(String pseudo) throws IOException {
        ServiceEmport service = injecteur.getInstance(ServiceEmport.class);
        Long idPassage = injecteur
                .getInstance(fr.univ_amu.iut.passage.model.dao.PassageDao.class)
                .findAll()
                .getFirst()
                .id();
        // Le relecteur a JUGÉ, sinon son avis serait vide et il n'y aurait rien à remplacer : c'est ce
        // que le premier tir de S3-49 a montré, la question n'étant jamais posée.
        SelectionDao selections = injecteur.getInstance(SelectionDao.class);
        Long idSelection = selections.findByPassage(idPassage).orElseThrow().id();
        selections
                .listerSequences(idSelection)
                .forEach(ligne ->
                        selections.marquerVerdict(idSelection, ligne.idSequence(), VerdictFichier.INEXPLOITABLE));

        Path avis = dossierDEmport.resolve("avis-" + pseudo + ".zip");
        service.renvoyerAvis(idPassage, avis, pseudo);
        return avis;
    }

    // --- montage -----------------------------------------------------------

    private void ouvrirLaVerification(FxRobot robot) throws TimeoutException {
        PreambuleImport.importerUneNuitEtOuvrirSonPassage(robot, injecteur.getInstance(Navigateur.class), carteSd);
        Respiration.surLeMomentCle(robot);
        GesteVisible.cliquer(robot, "#boutonVerifier");
        WaitForAsyncUtils.waitForFxEvents();
        attendre(
                APPARITION_SECONDES,
                () -> robot.lookup("#tableSequences").tryQuery().isPresent(),
                "l'écran de vérification ne s'est pas ouvert : sans lui, aucun des cas n'a de quoi se lire");
    }

    private QualificationController controleurDeLEcran() {
        Object courant =
                injecteur.getInstance(Navigateur.class).historique().getLast().controleur();
        if (!(courant instanceof QualificationController qualification)) {
            throw new IllegalStateException("L'écran affiché n'est pas la vérification mais "
                    + (courant == null ? "rien" : courant.getClass().getSimpleName())
                    + " : le clic sur « Vérifier » n'a pas mené où la session le dit.");
        }
        return qualification;
    }

    /// Le sélecteur est substitué **sur l'action**, pas sur le contrôleur : un sélecteur natif fige un
    /// test headless, et c'est la raison d'être du porteur injectable (#3197).
    private void definirSelecteur(FxRobot robot, SelecteurFichier selecteur) {
        controleurDeLEcran().gestesEmport().selecteur().definir(selecteur);
    }

    private static void attendre(int secondes, java.util.concurrent.Callable<Boolean> condition, String motif)
            throws TimeoutException {
        try {
            WaitForAsyncUtils.waitFor(secondes, TimeUnit.SECONDS, condition);
        } catch (TimeoutException delai) {
            throw new TimeoutException(motif);
        }
    }

    private static SelecteurFichier selecteurQuiRepond(Path chemin) {
        return new SelecteurFichier() {
            @Override
            public Optional<Path> choisirDossier(String titre, Optional<Path> initial) {
                return Optional.of(chemin);
            }

            @Override
            public Optional<Path> choisirFichier(String titre, Optional<Path> initial, FiltreFichier filtre) {
                return Optional.of(chemin);
            }

            @Override
            public Optional<Path> enregistrerFichier(String titre, String nom, FiltreFichier filtre) {
                return Optional.of(chemin);
            }
        };
    }

    private static SelecteurFichier selecteurQuiAnnule() {
        return new SelecteurFichier() {
            @Override
            public Optional<Path> choisirDossier(String titre, Optional<Path> initial) {
                return Optional.empty();
            }

            @Override
            public Optional<Path> choisirFichier(String titre, Optional<Path> initial, FiltreFichier filtre) {
                return Optional.empty();
            }

            @Override
            public Optional<Path> enregistrerFichier(String titre, String nom, FiltreFichier filtre) {
                return Optional.empty();
            }
        };
    }

    @SuppressWarnings("unused")
    private TableView<?> table(FxRobot robot) {
        return robot.lookup("#tableSequences").queryAs(TableView.class);
    }
}
