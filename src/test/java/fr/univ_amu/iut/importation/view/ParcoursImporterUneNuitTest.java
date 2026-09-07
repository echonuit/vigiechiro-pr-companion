package fr.univ_amu.iut.importation.view;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.AbstractModule;
import com.google.inject.Injector;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import fr.univ_amu.iut.commun.model.Protocole;
import fr.univ_amu.iut.commun.model.ReglageDesignation;
import fr.univ_amu.iut.commun.model.Reglages;
import fr.univ_amu.iut.commun.model.Utilisateur;
import fr.univ_amu.iut.commun.model.dao.UtilisateurDao;
import fr.univ_amu.iut.commun.persistence.MigrationSchema;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.commun.view.ContenuDesignation;
import fr.univ_amu.iut.commun.view.ExecuteurTache;
import fr.univ_amu.iut.commun.view.ExecuteurTacheAsynchrone;
import fr.univ_amu.iut.recette.Attente;
import fr.univ_amu.iut.recette.BancDeRecette;
import fr.univ_amu.iut.recette.CarteDeRecette;
import fr.univ_amu.iut.recette.ExecuteurTacheRalenti;
import fr.univ_amu.iut.recette.GesteVisible;
import fr.univ_amu.iut.recette.Respiration;
import fr.univ_amu.iut.recette.SansExceptionAvalee;
import fr.univ_amu.iut.recette.film.EnregistreurDeFilm;
import fr.univ_amu.iut.sites.model.ServiceSites;
import fr.univ_amu.iut.sites.model.Site;
import fr.univ_amu.iut.sites.view.NavigationSites;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/// Le parcours de DOCUMENTATION « importer une nuit », joué **sans qu'aucune étape ne se dérobe**.
///
/// `ScenarioImportNominalTest` est un cas de RECETTE : il substitue son porteur de désignation
/// (`selecteur().definir(...)`), et le film qu'il produit **saute** l'étape la plus déroutante pour un
/// débutant. Poser le réglage n'y suffisait pas - un scénario qui substitue n'ouvre jamais le
/// dialogue. Ce parcours cesse de substituer, et pilote le vrai dialogue au robot.
///
/// Il ne cite **aucun cas de recette** : il ne garde pas une exigence, il documente un geste. Le banc
/// le filme par `-Drecette.film.tout`.
///
/// Ce que ce clip a tranché pour le chantier #5282, et ce que porter les huit parcours coûte, vivent
/// dans l'[ADR 5282](../../../../../../../dev-docs/decisions/5282-les-huit-parcours-filmes-passent-au-banc-java.md).
@ExtendWith({ApplicationExtension.class, EnregistreurDeFilm.class, SansExceptionAvalee.class})
class ParcoursImporterUneNuitTest {

    private static final String ID_USER = "u-recette";
    private static final String CARRE = "640380";
    private static final String FIXTURE = "sd-nominale";

    /// Freiné, comme le scénario de recette : sur des fixtures générées l'import dure des
    /// millisecondes, et un film qui ne montre rien ne documente rien.
    private static final long PAUSE_PAR_FICHIER_MS = 900;

    private static final int APPARITION_SECONDES = 30;

    /// Le nom sous lequel la carte paraît dans le dialogue, et le dossier court qui la contient.
    ///
    /// `CarteDeRecette.materialiser` rend un chemin temporaire de la forme
    /// `/tmp/vc-carte-sd-nominale857838373229970727`. Filmé, ce chemin se tape caractère par
    /// caractère pendant six secondes et ne ressemble à rien de ce qu'un observateur ferait. La carte
    /// est donc recopiée sous un nom qui évoque ce qu'elle est.
    private static final String NOM_DE_LA_CARTE = "VIGIECHIRO";

    private Path dossierDesCartes;
    private Path carteSd;

    @Start
    void start(Stage stage) throws IOException {
        dossierDesCartes = Path.of(System.getProperty("java.io.tmpdir"), "vigiechiro-parcours");
        carteSd = recopierSousUnNomLisible(CarteDeRecette.materialiser(FIXTURE));

        BancDeRecette.surLeChrome()
                .taille(1180, 900)
                .executeur(BancDeRecette.Executeur.ASYNCHRONE)
                .remplacer(new AbstractModule() {
                    @Provides
                    @Singleton
                    ExecuteurTache executeurFreine() {
                        return new ExecuteurTacheRalenti(new ExecuteurTacheAsynchrone(), PAUSE_PAR_FICHIER_MS);
                    }
                })
                .semer(this::poserLaBaseEtArmerLeDialogue)
                .ouvrir(inj -> inj.getInstance(NavigationSites.class).ouvrirDetail(CARRE))
                .montrer(stage);
    }

    /// Le dossier court est un reliquat que l'[ADR 4859] compte, et la CI l'a refusé : la suite
    /// laissait `/tmp/vigiechiro-parcours` derrière elle. Il se rend, comme tout ce qu'on emprunte.
    @AfterEach
    void rendreLeDossierDesCartes() throws IOException {
        effacer(dossierDesCartes);
    }

    /// La carte, recopiée dans un dossier court et sous un nom qui se lit.
    ///
    /// Le clip est l'artefact de ce lot : un chemin qu'on ne peut pas lire à l'écran documente moins
    /// bien qu'une capture. La copie coûte six fichiers de fixture.
    private Path recopierSousUnNomLisible(Path source) throws IOException {
        Path cible = dossierDesCartes.resolve(NOM_DE_LA_CARTE);
        effacer(dossierDesCartes);
        try (Stream<Path> arbre = Files.walk(source)) {
            for (Path p : arbre.toList()) {
                Path destination = cible.resolve(source.relativize(p).toString());
                if (Files.isDirectory(p)) {
                    Files.createDirectories(destination);
                } else {
                    Files.createDirectories(destination.getParent());
                    Files.copy(p, destination);
                }
            }
        }
        return cible;
    }

    private static void effacer(Path racine) throws IOException {
        if (!Files.exists(racine)) {
            return;
        }
        try (Stream<Path> arbre = Files.walk(racine)) {
            for (Path p : arbre.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(p);
            }
        }
    }

    /// La base de départ **et** le réglage, en un seul semis.
    ///
    /// `BancDeRecette.semer` REMPLACE le semis précédent au lieu de l'ajouter : deux appels laissent
    /// le second seul, et l'écran monte alors sans son carré. Le défaut ne se lit pas à l'appel, il se
    /// voit trois gestes plus loin, sur un `#boutonImporterNuit` introuvable.
    private void poserLaBaseEtArmerLeDialogue(Injector inj) {
        SourceDeDonnees source = inj.getInstance(SourceDeDonnees.class);
        new MigrationSchema(source).migrer();
        new UtilisateurDao(source).insert(new Utilisateur(ID_USER, "Observateur"));
        ServiceSites service = inj.getInstance(ServiceSites.class);
        Site carre = service.creerSite(CARRE, "Étang de la Tuilière", Protocole.STANDARD, null, ID_USER);
        service.ajouterPoint(carre.id(), "A1", 43.42, 5.11, "Près du grand chêne");

        // Le réglage, posé comme un utilisateur le poserait. C'est ce qui distingue ce parcours d'une
        // mise en scène : il ne force pas un dispositif dans le code, il écrit la préférence que
        // l'écran des réglages écrirait. Le film montre une configuration qui EXISTE.
        inj.getInstance(Reglages.class).ecrireBooleen(ReglageDesignation.CLE, true);
    }

    @Test
    @DisplayName("importer une nuit, du clic sur « Parcourir » jusqu'au chemin dans l'écran")
    void importer_une_nuit_sans_etape_derobee(FxRobot robot) {
        Respiration.avantLeGeste(robot);
        GesteVisible.cliquer(robot, "#boutonImporterNuit");
        WaitForAsyncUtils.waitForFxEvents();

        // Le champ reste en lecture seule : c'est le dialogue qui désigne, pas la frappe.
        assertThat(robot.lookup("#champDossier").queryAs(TextField.class).isEditable())
                .isFalse();

        // ─── Le geste que le clip de recette saute ───────────────────────────────────────────────
        Respiration.surLeMomentCle(robot);
        GesteVisible.cliquer(robot, "#boutonParcourir");

        // Le dialogue est une FENÊTRE de l'application : le banc la filme, et le robot la pilote.
        // C'est tout l'apport du chantier, et il se constate ici.
        Attente.queSurLeFil(
                this::dialogueOuvert,
                "le dialogue de désignation n'a jamais paru : le réglage est-il bien posé ?",
                APPARITION_SECONDES * 1000L);

        // Le geste tel qu'un observateur le ferait : on va au dossier qui contient les cartes, PUIS
        // on désigne la carte en cliquant dessus. Taper le chemin complet marcherait aussi, et ne
        // montrerait pas que la liste sert à naviguer.
        Respiration.surLeMomentCle(robot);
        GesteVisible.remplacerLeTexte(robot, "#" + ContenuDesignation.ID_CHEMIN, dossierDesCartes.toString());
        robot.push(KeyCode.ENTER);

        Attente.queSurLeFil(
                () -> robot.lookup(NOM_DE_LA_CARTE + "/").tryQuery().isPresent(),
                "la liste n'a pas suivi le chemin saisi : " + NOM_DE_LA_CARTE + " devrait y paraître",
                APPARITION_SECONDES * 1000L);

        Respiration.surLeMomentCle(robot);
        GesteVisible.cliquer(robot, NOM_DE_LA_CARTE + "/");

        Respiration.surLeMomentCle(robot);
        GesteVisible.cliquer(robot, "#" + ContenuDesignation.ID_VALIDER);

        Attente.queSurLeFil(
                () -> !dialogueOuvert(),
                "le dialogue ne s'est pas refermé après le choix",
                APPARITION_SECONDES * 1000L);

        // ─── Et le chemin ARRIVE dans l'écran, ce qui ferme le geste ─────────────────────────────
        // SUR LE FIL (ADR 5278) : `texte` fait un `lookup` puis un `getText`, donc lit le graphe. Le
        // garde ne le voit pas - la lecture est cachée dans l'aide - et 36 autres sites du dépôt sont
        // dans ce cas, consignés en #5353.
        Attente.queSurLeFil(
                () -> !texte(robot, "#labelOriginaux").isBlank(),
                "l'inspection n'a jamais rendu son compte d'originaux",
                APPARITION_SECONDES * 1000L);

        assertThat(robot.lookup("#champDossier").queryAs(TextField.class).getText())
                .as("le dossier désigné dans le dialogue doit être celui que l'écran affiche")
                .contains(NOM_DE_LA_CARTE);

        Respiration.apresLeGeste(robot);
    }

    /// Le dialogue est-il ouvert ? Lu SUR LE FIL par l'appelant (ADR 5278).
    private boolean dialogueOuvert() {
        return Window.getWindows().stream()
                .filter(Window::isShowing)
                .anyMatch(f -> f.getScene() != null && f.getScene().lookup("#" + ContenuDesignation.ID_CHEMIN) != null);
    }

    private static String texte(FxRobot robot, String selecteur) {
        Label label = robot.lookup(selecteur).queryAs(Label.class);
        return label == null || label.getText() == null ? "" : label.getText();
    }
}
