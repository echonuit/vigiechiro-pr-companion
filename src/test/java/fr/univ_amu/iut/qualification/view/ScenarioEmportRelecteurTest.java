package fr.univ_amu.iut.qualification.view;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.Injector;
import fr.univ_amu.iut.commun.model.MethodeSelection;
import fr.univ_amu.iut.commun.model.Protocole;
import fr.univ_amu.iut.commun.model.Utilisateur;
import fr.univ_amu.iut.commun.model.dao.UtilisateurDao;
import fr.univ_amu.iut.commun.persistence.MigrationSchema;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.commun.view.ExecuteurTache;
import fr.univ_amu.iut.commun.view.ExecuteurTacheAsynchrone;
import fr.univ_amu.iut.commun.view.FiltreFichier;
import fr.univ_amu.iut.commun.view.Navigateur;
import fr.univ_amu.iut.commun.view.SelecteurFichier;
import fr.univ_amu.iut.importation.view.PreambuleImport;
import fr.univ_amu.iut.passage.model.ManifestePaquet;
import fr.univ_amu.iut.passage.model.OuvertureDePaquet;
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
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import javafx.stage.Stage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/// Le parcours d'emport vu du **poste relecteur** (#4728, S3-50 à S3-52).
///
/// Seconde des deux familles. Le relecteur reçoit une nuit qu'il n'a pas enregistrée, la juge, et
/// renvoie son avis **signé de lui**. Son identité est relevée **à l'ouverture** du paquet, pas au
/// moment du jugement : le banc arrive donc connecté, ce que le cas S3-51 constate.
///
/// **Ce que ce clip ne peut pas montrer** : le voyage du fichier. Le paquet arrive, et d'où il vient
/// est l'affaire de l'autre famille. C'est une limite du dispositif, pas un manque du tournage.
@ExtendWith({ApplicationExtension.class, EnregistreurDeFilm.class, SansExceptionAvalee.class})
class ScenarioEmportRelecteurTest {

    private static final String ID_USER = "u-recette";

    private static final String CARRE = "640380";

    private static final String FIXTURE = "sd-nominale";

    private static final int APPARITION_SECONDES = 30;

    private static final long PAUSE_PAR_FICHIER_MS = 900;

    /// Le relecteur, tel que la plateforme le connaît. C'est ce pseudo qui signera ses verdicts.
    private static final String PSEUDO = "chiro-pierre";

    private Path carteSd;

    private Path echanges;

    private Injector injecteur;

    private final java.util.List<String> comptesRendus = new java.util.ArrayList<>();

    private final java.util.List<String> questions = new java.util.ArrayList<>();

    @Start
    void start(Stage stage) throws IOException {
        carteSd = CarteDeRecette.materialiser(FIXTURE);
        echanges = Files.createTempDirectory("relecture-recette");

        injecteur = BancDeRecette.surLeChrome()
                .taille(1180, 900)
                .executeur(BancDeRecette.Executeur.ASYNCHRONE)
                .connecte("507f1f77bcf86cd799439011", PSEUDO, "Observateur")
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
            value = {"S3-50", "S3-51"},
            portee = Portee.A_L_ECRAN)
    @DisplayName("S3-50 et S3-51 - ouvrir un paquet recu : la selection devient celle de l expediteur, figee")
    void relire_une_nuit_confiee(FxRobot robot) throws TimeoutException, IOException {
        ouvrirLaVerification(robot);
        QualificationController controleur = controleurDeLEcran();
        controleur.confirmateur().definir(message -> {
            questions.add(message);
            return true;
        });
        controleur.notificateur().definir((niveau, entete, message) -> comptesRendus.add(entete + " | " + message));

        // Le paquet arrive **par la fixture**, pas par le menu : ce clip filme le rôle du relecteur, et
        // le voyage du fichier n'est filmable par aucune des deux familles. Le composer ici par le
        // service produit le paquet que le produit produit, sans filmer le geste de l'autre rôle.
        Path paquet = echanges.resolve("nuit-confiee.zip");
        Long idPassage = passageCourant();
        injecteur.getInstance(ServiceEmport.class).composer(idPassage, paquet);
        definirSelecteur(robot, selecteur(paquet));

        SelectionDao selections = injecteur.getInstance(SelectionDao.class);
        GesteVisible.choisir(robot, controleur.menuDeLaSelection(), "Ouvrir un paquet reçu…");
        Respiration.surLeMomentCle(robot);

        assertThat(questions)
                .as("S3-50 : le remplacement de la sélection locale se confirme, il n'est pas tacite")
                .anySatisfy(question -> assertThat(question).contains("verdicts posés ici seront perdus"));
        assertThat(selections.findByPassage(idPassage).orElseThrow().methode())
                .as("S3-50 : la sélection reçue est figée, elle n'a pas été tirée ici")
                .isEqualTo(MethodeSelection.RECUE_D_UN_PAQUET);
        assertThat(comptesRendus)
                .as("S3-51 : le compte rendu dit qui signera les verdicts")
                .anySatisfy(compte -> assertThat(compte).contains(PSEUDO));
    }

    @Test
    @CasDeRecette(value = "S3-52", portee = Portee.A_L_ECRAN)
    @DisplayName("S3-52 - renvoyer son avis : un manifeste signe, sans les sequences de l'expediteur")
    void renvoyer_son_avis(FxRobot robot) throws TimeoutException, IOException {
        ouvrirLaVerification(robot);
        QualificationController controleur = controleurDeLEcran();
        controleur.confirmateur().definir(message -> true);
        controleur.notificateur().definir((niveau, entete, message) -> comptesRendus.add(entete + " | " + message));

        Path retour = echanges.resolve("mon-avis.zip");
        definirSelecteur(robot, selecteur(retour));
        GesteVisible.choisir(robot, controleur.menuDeLaSelection(), "Renvoyer mon avis…");
        Respiration.surLeMomentCle(robot);

        assertThat(Files.exists(retour)).as("S3-52 : l'avis part").isTrue();
        ManifestePaquet manifeste = ManifestePaquet.depuis(OuvertureDePaquet.lireManifeste(retour));
        assertThat(manifeste.pseudoJugeur())
                .as("S3-52 : l'identite relevee a l'ouverture est celle qui signe")
                .isEqualTo(PSEUDO);
        assertThat(manifeste.sequences())
                .as("S3-52 : l'avis porte les verdicts, pas les sequences que l'expediteur possede deja")
                .isNotEmpty();
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
                "l'écran de vérification ne s'est pas ouvert : sans lui, aucun des trois cas n'a de quoi se lire");
    }

    private Long passageCourant() {
        return injecteur
                .getInstance(fr.univ_amu.iut.passage.model.dao.PassageDao.class)
                .findAll()
                .getFirst()
                .id();
    }

    private QualificationController controleurDeLEcran() {
        Object courant =
                injecteur.getInstance(Navigateur.class).historique().getLast().controleur();
        if (!(courant instanceof QualificationController qualification)) {
            throw new IllegalStateException("L'écran affiché n'est pas la vérification mais "
                    + (courant == null ? "rien" : courant.getClass().getSimpleName()));
        }
        return qualification;
    }

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

    private static SelecteurFichier selecteur(Path chemin) {
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

    @SuppressWarnings("unused")
    private List<String> inutilise() {
        return List.of();
    }
}
