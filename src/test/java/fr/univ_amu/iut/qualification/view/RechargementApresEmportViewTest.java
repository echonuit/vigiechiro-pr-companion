package fr.univ_amu.iut.qualification.view;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.Injector;
import fr.univ_amu.iut.commun.model.Protocole;
import fr.univ_amu.iut.commun.model.Utilisateur;
import fr.univ_amu.iut.commun.model.VerdictFichier;
import fr.univ_amu.iut.commun.model.dao.UtilisateurDao;
import fr.univ_amu.iut.commun.persistence.MigrationSchema;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.commun.view.FiltreFichier;
import fr.univ_amu.iut.commun.view.Navigateur;
import fr.univ_amu.iut.commun.view.SelecteurFichier;
import fr.univ_amu.iut.importation.view.PreambuleImport;
import fr.univ_amu.iut.passage.model.dao.PassageDao;
import fr.univ_amu.iut.qualification.model.SequenceSelectionnee;
import fr.univ_amu.iut.qualification.model.ServiceEmport;
import fr.univ_amu.iut.qualification.model.dao.SelectionDao;
import fr.univ_amu.iut.recette.Attente;
import fr.univ_amu.iut.recette.BancDeRecette;
import fr.univ_amu.iut.recette.CarteDeRecette;
import fr.univ_amu.iut.recette.GesteVisible;
import fr.univ_amu.iut.recette.SansExceptionAvalee;
import fr.univ_amu.iut.sites.model.ServiceSites;
import fr.univ_amu.iut.sites.model.Site;
import fr.univ_amu.iut.sites.view.NavigationSites;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeoutException;
import java.util.function.Predicate;
import javafx.scene.control.Labeled;
import javafx.stage.Stage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/// Après un geste d'emport qui change la base, l'écran de vérification montre le nouvel état.
///
/// Les deux scénarios filmés de l'emport constatent le compte rendu et la base, jamais la table.
/// Cette classe lit la table, **sans quitter l'écran**, et n'est pas filmée : elle ne cite aucun cas
/// de recette et ne pèse sur aucun clip.
@ExtendWith({ApplicationExtension.class, SansExceptionAvalee.class})
class RechargementApresEmportViewTest {

    private static final String ID_USER = "u-recette";
    private static final String CARRE = "640380";
    private static final long DELAI_MS = 8_000L;

    private Injector injecteur;
    private Path carteSd;
    private final List<String> comptesRendus = new ArrayList<>();

    @TempDir
    private Path echanges;

    @Start
    void start(Stage stage) throws IOException {
        carteSd = CarteDeRecette.materialiser("sd-nominale");
        injecteur = BancDeRecette.surLeChrome()
                .taille(1180, 900)
                .executeur(BancDeRecette.Executeur.ASYNCHRONE)
                .connecte("507f1f77bcf86cd799439011", "chiro-pierre", "Observateur")
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
    @DisplayName("Un avis repris paraît dans la colonne « Avis relecteur » sans quitter l'écran")
    void un_avis_repris_parait_dans_la_colonne_sans_quitter_l_ecran(FxRobot robot)
            throws TimeoutException, IOException {
        QualificationController controleur = ouvrirLaVerification(robot);
        Path avis = unAvisSignePar("claire");
        assertThat(texteAffiche(robot, "claire"))
                .as("avant le geste, la table ne nomme aucun relecteur")
                .isFalse();

        controleur.gestesEmport().selecteur().definir(selecteur(avis));
        GesteVisible.choisir(robot, controleur.menuDeLaSelection(), "Reprendre un avis reçu…");

        attendreLeCompteRendu("claire");
        Attente.queSurLeFil(
                () -> texteAffiche(robot, "Inexploitable · claire"),
                "l'avis de « claire » paraît dans la colonne « Avis relecteur » de la table affichée :"
                        + " la base le porte, et l'écran montre encore l'état d'avant le geste",
                DELAI_MS);
    }

    @Test
    @DisplayName("Un paquet ouvert remplace la sélection affichée sans quitter l'écran")
    void un_paquet_ouvert_remplace_la_selection_affichee_sans_quitter_l_ecran(FxRobot robot)
            throws TimeoutException, IOException {
        QualificationController controleur = ouvrirLaVerification(robot);
        Path paquet = unPaquetDontUneSequenceEstJugeeBonne();
        assertThat(badgeAffiche(robot, VerdictFichier.BON))
                .as("avant le geste, aucune séquence affichée n'est jugée")
                .isFalse();

        controleur.gestesEmport().selecteur().definir(selecteur(paquet));
        GesteVisible.choisir(robot, controleur.menuDeLaSelection(), "Ouvrir un paquet reçu…");

        attendreLeCompteRendu("à relire");
        Attente.queSurLeFil(
                () -> badgeAffiche(robot, VerdictFichier.BON),
                "le verdict « Bon » de l'expéditeur paraît dans la table affichée : la sélection reçue est"
                        + " en base, et l'écran montre encore celle d'avant le geste",
                DELAI_MS);
    }

    // --- montage -----------------------------------------------------------

    private QualificationController ouvrirLaVerification(FxRobot robot) throws TimeoutException {
        Navigateur navigateur = injecteur.getInstance(Navigateur.class);
        PreambuleImport.importerUneNuitEtOuvrirSonPassage(robot, navigateur, carteSd);
        GesteVisible.cliquer(robot, "#boutonVerifier");
        WaitForAsyncUtils.waitForFxEvents();
        Attente.queSurLeFil(
                () -> robot.lookup("#tableSequences").tryQuery().isPresent()
                        && !robot.lookup("#tableSequences")
                                .queryTableView()
                                .getItems()
                                .isEmpty(),
                "l'écran de vérification s'ouvre avec sa sélection : sans elle, il n'y a rien à relire",
                30_000L);
        QualificationController controleur =
                (QualificationController) navigateur.historique().getLast().controleur();
        controleur.confirmateur().definir(message -> true);
        controleur.notificateur().definir((niveau, entete, message) -> comptesRendus.add(entete + " | " + message));
        return controleur;
    }

    // Le geste a rendu compte : l'action est finie, et ce qui manque ensuite à l'écran n'est pas un retard.
    private void attendreLeCompteRendu(String fragment) {
        Attente.queSurLeFil(
                () -> comptesRendus.stream().anyMatch(compte -> compte.contains(fragment)),
                () -> "le geste rend un compte rendu qui contient « " + fragment + " » ; reçus : " + comptesRendus,
                DELAI_MS);
    }

    private static boolean texteAffiche(FxRobot robot, String fragment) {
        return dansLaTable(
                robot, libelle -> libelle.getText() != null && libelle.getText().contains(fragment));
    }

    private static boolean badgeAffiche(FxRobot robot, VerdictFichier verdict) {
        return dansLaTable(
                robot,
                libelle -> libelle.getStyleClass().contains("badge-verdict")
                        && verdict.libelle().equals(libelle.getText()));
    }

    // Ce que la table REND : ses libellés affichés, et non les lignes de son modèle.
    private static boolean dansLaTable(FxRobot robot, Predicate<Labeled> reconnu) {
        return robot.lookup("#tableSequences").queryTableView().lookupAll(".label").stream()
                .anyMatch(noeud -> noeud instanceof Labeled libelle && reconnu.test(libelle));
    }

    private Long passageCourant() {
        return injecteur.getInstance(PassageDao.class).findAll().getFirst().id();
    }

    // Un avis tel qu'un relecteur le renverrait. Les verdicts qu'il porte sont posés puis retirés de
    // la base locale : après ce montage, l'écran et la base disent la même chose, et seul le geste
    // les fera diverger.
    private Path unAvisSignePar(String pseudo) throws IOException {
        Long idPassage = passageCourant();
        SelectionDao selections = injecteur.getInstance(SelectionDao.class);
        Long idSelection = selections.findByPassage(idPassage).orElseThrow().id();
        List<SequenceSelectionnee> lignes = selections.listerSequences(idSelection);
        lignes.forEach(
                ligne -> selections.marquerVerdict(idSelection, ligne.idSequence(), VerdictFichier.INEXPLOITABLE));
        Path avis = echanges.resolve("avis-" + pseudo + ".zip");
        injecteur.getInstance(ServiceEmport.class).renvoyerAvis(idPassage, avis, pseudo);
        lignes.forEach(ligne -> selections.marquerVerdict(idSelection, ligne.idSequence(), VerdictFichier.NON_JUGE));
        return avis;
    }

    // Le paquet d'un expéditeur qui a jugé une séquence « Bon ». Même retour à l'état d'avant.
    private Path unPaquetDontUneSequenceEstJugeeBonne() throws IOException {
        Long idPassage = passageCourant();
        SelectionDao selections = injecteur.getInstance(SelectionDao.class);
        Long idSelection = selections.findByPassage(idPassage).orElseThrow().id();
        Long idSequence = selections.listerSequences(idSelection).getFirst().idSequence();
        selections.marquerVerdict(idSelection, idSequence, VerdictFichier.BON);
        Path paquet = echanges.resolve("nuit-confiee.zip");
        injecteur.getInstance(ServiceEmport.class).composer(idPassage, paquet);
        selections.marquerVerdict(idSelection, idSequence, VerdictFichier.NON_JUGE);
        return paquet;
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
}
