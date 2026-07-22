package fr.univ_amu.iut.analyse.outils;

import com.google.inject.Guice;
import com.google.inject.Injector;
import fr.univ_amu.iut.analyse.di.AnalyseModule;
import fr.univ_amu.iut.analyse.view.AnalyseController;
import fr.univ_amu.iut.analyse.viewmodel.Regroupement;
import fr.univ_amu.iut.commun.di.PersistenceModule;
import fr.univ_amu.iut.commun.model.ModeValidation;
import fr.univ_amu.iut.commun.model.Utilisateur;
import fr.univ_amu.iut.commun.model.dao.UtilisateurDao;
import fr.univ_amu.iut.commun.outils.ApercuFx;
import fr.univ_amu.iut.commun.outils.AttenteTuiles;
import fr.univ_amu.iut.commun.outils.ModuleCaptureCommun;
import fr.univ_amu.iut.commun.outils.ModuleCaptureNavigationAudio;
import fr.univ_amu.iut.commun.persistence.MigrationSchema;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.commun.view.GestionnaireColonnes;
import fr.univ_amu.iut.passage.di.PassageModule;
import fr.univ_amu.iut.sites.di.SitesModule;
import fr.univ_amu.iut.validation.di.ValidationModule;
import fr.univ_amu.iut.validation.model.EspeceAgregee;
import fr.univ_amu.iut.validation.model.Observation;
import fr.univ_amu.iut.validation.model.dao.ObservationDao;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

/// Outil de capture/mesure, utilisable tel quel.
///
/// Capture l'écran transverse **« Espèces & observations »** (feature `analyse`) en PNG, en deux états :
/// inventaire **par espèce** (par défaut) et **par carré** (richesse spécifique). Seede une base SQLite
/// temporaire avec un site, un passage et quelques observations (statuts variés) pour peupler la table.
///
/// Lancement headless : `.github/assets/capture-screenshots.sh` (Headless Platform JavaFX 26).
public final class CaptureAnalyse {

    private static final String ID_UTILISATEUR = "demo-analyse";

    /// Codes de taxons (semés par `V02`) utilisés dans les observations de démonstration.
    private static final String PIPISTRELLE = "Pippip";

    private static final String NOCTULE = "Nyclei";

    private static final String MOLOSSE = "Tadten";

    /// Ressource FXML de l'écran, chargée par chaque rendu (une scène neuve par PNG).
    private static final String FXML_ANALYSE = "Analyse.fxml";

    /// `fx:id` de la table maître (inventaire par espèce), atteinte après affichage.
    private static final String ID_TABLE_ESPECES = "#tableEspeces";

    private CaptureAnalyse() {}

    public static void main(String[] args) throws InterruptedException {
        CountDownLatch fini = new CountDownLatch(1);
        AtomicReference<Throwable> erreur = new AtomicReference<>();
        Platform.startup(() -> {
            try {
                capturer();
            } catch (RuntimeException | IOException | SQLException probleme) {
                erreur.set(probleme);
            } finally {
                fini.countDown();
            }
        });
        fini.await();
        Platform.exit();
        if (erreur.get() != null) {
            erreur.get().printStackTrace();
            System.exit(1);
        }
        System.exit(0);
    }

    private static void capturer() throws IOException, SQLException {
        Path workspace = Files.createTempDirectory("vc-capture-analyse");
        System.setProperty("vigiechiro.workspace", workspace.toString());
        Path sortie = Path.of(System.getProperty("capture.outDir", ".github/assets"));

        Injector injecteur = creerInjecteur();
        SourceDeDonnees source = injecteur.getInstance(SourceDeDonnees.class);
        new MigrationSchema(source).migrer();
        seeder(injecteur, source);

        rendre(injecteur, null, sortie.resolve("apercu-analyse.png"));
        rendre(injecteur, Regroupement.PAR_CARRE, sortie.resolve("apercu-analyse-carre.png"));
        rendreCarte(injecteur, sortie.resolve("apercu-analyse-carte.png"));
        rendreColonnes(injecteur, sortie.resolve("apercu-analyse-colonnes.png"));

        System.out.println("Apercus ecrits dans " + sortie.toAbsolutePath());
    }

    /// Injecteur (partiel) utilisé par cet outil de capture. Exposé pour le garde-fou de câblage
    /// (test).
    public static Injector creerInjecteur() {
        return Guice.createInjector(
                ModuleCaptureCommun.communSynchrone(),
                new PersistenceModule(),
                new SitesModule(),
                new PassageModule(),
                new ValidationModule(),
                new AnalyseModule(),
                new ModuleCaptureNavigationAudio());
    }

    /// Charge `Analyse.fxml`, applique éventuellement un regroupement (`Par carré`), puis rend l'écran. En
    /// mode Par espèce (regroupement nul), sélectionne la première espèce pour peupler le panneau détail.
    ///
    /// La sélection se fait **après l'affichage** (via [ApercuFx#capturerApresPreparation]) : le `SplitPane`
    /// n'attache ses enfants au graphe de scène qu'une fois son skin appliqué, donc `lookup(ID_TABLE_ESPECES)`
    /// renverrait `null` avant la première mise en page.
    private static void rendre(Injector injecteur, Regroupement regroupement, Path fichier) throws IOException {
        FXMLLoader loader = new FXMLLoader(AnalyseController.class.getResource(FXML_ANALYSE));
        loader.setControllerFactory(injecteur::getInstance);
        Parent vue = loader.load();
        Scene scene = new Scene(vue, 1080, 640);
        ApercuFx.capturerApresPreparation(scene, () -> preparer(vue, regroupement), fichier);
    }

    /// Rend l'écran par espèce avec le **panneau de sélection des colonnes ouvert** (`apercu-analyse-colonnes`,
    /// EPIC #914). Le panneau est un `VBox` (issu de [GestionnaireColonnes#construirePanneau], rendu sans
    /// `Popup` que le `snapshot` de scène ne capturerait pas) posé en surimpression en haut à droite, là où
    /// s'ancre le ☰ « outils ». On joint palette + design **à la scène** : le panneau, frère de la vue, n'hérite
    /// pas des feuilles chargées par le FXML sur la vue.
    private static void rendreColonnes(Injector injecteur, Path fichier) throws IOException {
        FXMLLoader loader = new FXMLLoader(AnalyseController.class.getResource(FXML_ANALYSE));
        loader.setControllerFactory(injecteur::getInstance);
        Parent vue = loader.load();
        StackPane pile = new StackPane(vue);
        Scene scene = new Scene(pile, 1080, 640);
        scene.getStylesheets()
                .addAll(
                        GestionnaireColonnes.class.getResource("palette.css").toExternalForm(),
                        GestionnaireColonnes.class.getResource("design.css").toExternalForm());
        ApercuFx.capturerApresPreparation(
                scene,
                () -> {
                    if (vue.lookup(ID_TABLE_ESPECES) instanceof TableView<?> table) {
                        table.getSelectionModel().select(0);
                        VBox panneau = GestionnaireColonnes.construirePanneau(
                                table, GestionnaireColonnes.colonnesParDefaut(table));
                        panneau.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
                        StackPane.setAlignment(panneau, Pos.TOP_RIGHT);
                        StackPane.setMargin(panneau, new Insets(70, 24, 0, 0));
                        pile.getChildren().add(panneau);
                    }
                },
                fichier);
    }

    /// Rend l'écran en mode **carte de répartition**. La bascule « 🗺️ Carte » est activée **avant**
    /// l'affichage de la scène (le bouton est directement atteignable, hors `SplitPane`), pour que la
    /// `MapView` soit **visible et dimensionnée dès le premier affichage** : son moteur de tuiles ne
    /// démarre sinon pas (carte hidden → fond vide). Comme `multisite`, on laisse ensuite les tuiles OSM
    /// se peindre avant la capture (choroplèthe de richesse + légende, par-dessus le fond).
    private static void rendreCarte(Injector injecteur, Path fichier) throws IOException {
        FXMLLoader loader = new FXMLLoader(AnalyseController.class.getResource(FXML_ANALYSE));
        loader.setControllerFactory(injecteur::getInstance);
        Parent vue = loader.load();
        if (vue.lookup("#boutonCarte") instanceof Button bascule) {
            bascule.fire();
        }
        Scene scene = new Scene(vue, 1080, 640);
        ApercuFx.capturerApresPreparation(
                scene,
                () -> {
                    // Recherche sur l'espèce (la barre reflète ainsi la sélection), puis sélectionne la ligne
                    // restante APRÈS l'affichage (la table est alors attachée au graphe) : peuple le panneau
                    // détail (bas) et bascule la carte en **répartition** (carrés de l'espèce mis en avant).
                    if (vue.lookup("#champRecherche") instanceof TextField champ) {
                        champ.setText("Pipistrelle");
                    }
                    if (vue.lookup(ID_TABLE_ESPECES) instanceof TableView<?> table
                            && !table.getItems().isEmpty()) {
                        table.getSelectionModel().select(0);
                    }
                    AttenteTuiles.attendre();
                },
                fichier);
    }

    /// Préparation post-affichage : applique le regroupement Par carré, ou sélectionne la première espèce
    /// (mode Par espèce) pour peupler le panneau détail des observations.
    private static void preparer(Parent vue, Regroupement regroupement) {
        if (regroupement != null && vue.lookup("#choixRegroupement") instanceof ComboBox<?> combo) {
            @SuppressWarnings("unchecked")
            ComboBox<Regroupement> choix = (ComboBox<Regroupement>) combo;
            choix.getSelectionModel().select(regroupement);
        } else if (regroupement == null && vue.lookup(ID_TABLE_ESPECES) instanceof TableView<?> table) {
            @SuppressWarnings("unchecked")
            TableView<EspeceAgregee> especes = (TableView<EspeceAgregee>) table;
            especes.getSelectionModel().select(0);
        }
    }

    /// Seede l'utilisateur courant et **trois carrés voisins** (du carroyage officiel, dept 64) de
    /// **richesse décroissante** (3 / 2 / 1 espèces), à statuts variés, pour illustrer la **choroplèthe**
    /// (dégradé de vert du plus riche au moins riche) tout en gardant un inventaire réaliste (validée /
    /// non touchée / corrigée). Chaque carré a un point, un passage et sa séquence.
    private static void seeder(Injector injecteur, SourceDeDonnees source) throws SQLException {
        new UtilisateurDao(source).insert(new Utilisateur(ID_UTILISATEUR, "Capitaine Chiro (demo)"));
        long[] riche;
        long[] moyen;
        long[] pauvre;
        try (Connection cx = source.getConnection()) {
            executer(cx, "INSERT INTO recorder(serial_number) VALUES ('SN-1')");
            riche = semerCarre(cx, "640380", "Étang de la Tuilière", "A1");
            moyen = semerCarre(cx, "640250", "Prairie de l'Adour", "B1");
            pauvre = semerCarre(cx, "640315", "Lisière du Bois", "C1");
        }
        ObservationDao observations = injecteur.getInstance(ObservationDao.class);
        // Carré riche : 3 espèces (Pipistrelle validée, Noctule non touchée, Molosse corrigé).
        observations.insererTout(java.util.List.of(
                validee(PIPISTRELLE, riche[0], riche[1]),
                nonTouchee(NOCTULE, riche[0], riche[1]),
                corrigee(NOCTULE, MOLOSSE, riche[0], riche[1])));
        // Carré moyen : 2 espèces.
        observations.insererTout(
                java.util.List.of(validee(PIPISTRELLE, moyen[0], moyen[1]), nonTouchee(NOCTULE, moyen[0], moyen[1])));
        // Carré pauvre : 1 espèce.
        observations.insererTout(java.util.List.of(validee(PIPISTRELLE, pauvre[0], pauvre[1])));
    }

    /// Seede la chaîne d'un carré (site/point/passage/séquence/résultats) et renvoie
    /// `{idSequence, idResultats}` pour y rattacher des observations.
    private static long[] semerCarre(Connection cx, String carre, String site, String pointCode) throws SQLException {
        long idSite = insererCle(
                cx,
                "INSERT INTO monitoring_site(square_number, friendly_name, protocol, created_at, user_id)"
                        + " VALUES (?, ?, 'Point fixe standard', '2026-05-01', ?)",
                carre,
                site,
                ID_UTILISATEUR);
        long idPoint = insererCle(cx, "INSERT INTO listening_point(code, site_id) VALUES (?, ?)", pointCode, idSite);
        long idPassage = insererCle(
                cx,
                "INSERT INTO passage(passage_number, year, recording_date, start_time, end_time,"
                        + " workflow_status, point_id, recorder_id)"
                        + " VALUES (1, 2026, '2026-06-22', '21:00', '05:00', 'Vérifié', ?, 'SN-1')",
                idPoint);
        String racine = "/ws/" + carre;
        long idSession =
                insererCle(cx, "INSERT INTO recording_session(root_path, passage_id) VALUES (?, ?)", racine, idPassage);
        long idOriginal = insererCle(
                cx,
                "INSERT INTO original_recording(file_name, file_path, session_id) VALUES ('a.wav', ?, ?)",
                racine + "/a.wav",
                idSession);
        long idSequence = insererCle(
                cx,
                "INSERT INTO listening_sequence(file_name, original_recording_id, file_path, session_id)"
                        + " VALUES ('a_000.wav', ?, ?, ?)",
                idOriginal,
                racine + "/a_000.wav",
                idSession);
        long idResultats = insererCle(
                cx,
                "INSERT INTO identification_results(file_path, detected_format, imported_at, passage_id)"
                        + " VALUES (?, 'Vu', '2026-06-23', ?)",
                racine + "/obs.csv",
                idPassage);
        return new long[] {idSequence, idResultats};
    }

    private static Observation validee(String code, long idSequence, long idResultats) {
        return new Observation(
                null,
                idSequence,
                0.5,
                3.0,
                45,
                code,
                0.9,
                null,
                code,
                0.95,
                null,
                false,
                ModeValidation.MANUEL,
                idResultats,
                false,
                null,
                null,
                null,
                null,
                null);
    }

    private static Observation nonTouchee(String codeTadarida, long idSequence, long idResultats) {
        return new Observation(
                null,
                idSequence,
                null,
                null,
                null,
                codeTadarida,
                0.7,
                null,
                null,
                null,
                null,
                false,
                ModeValidation.NON_VALIDE,
                idResultats,
                false,
                null,
                null,
                null,
                null,
                null);
    }

    private static Observation corrigee(
            String codeTadarida, String codeObservateur, long idSequence, long idResultats) {
        return new Observation(
                null,
                idSequence,
                null,
                null,
                null,
                codeTadarida,
                0.6,
                null,
                codeObservateur,
                0.8,
                null,
                false,
                ModeValidation.MANUEL,
                idResultats,
                false,
                null,
                null,
                null,
                null,
                null);
    }

    private static long insererCle(Connection cx, String sql, Object... params) throws SQLException {
        try (PreparedStatement ps = cx.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            for (int i = 0; i < params.length; i++) {
                ps.setObject(i + 1, params[i]);
            }
            ps.executeUpdate();
            try (ResultSet cles = ps.getGeneratedKeys()) {
                cles.next();
                return cles.getLong(1);
            }
        }
    }

    private static void executer(Connection cx, String sql) throws SQLException {
        try (Statement st = cx.createStatement()) {
            st.execute(sql);
        }
    }
}
