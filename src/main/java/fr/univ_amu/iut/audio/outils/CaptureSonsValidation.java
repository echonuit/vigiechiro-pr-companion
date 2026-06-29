package fr.univ_amu.iut.audio.outils;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Provides;
import fr.nedjar.vigiechiro.audio.AudioView;
import fr.univ_amu.iut.audio.view.SonsValidationController;
import fr.univ_amu.iut.audio.viewmodel.AudioViewModel;
import fr.univ_amu.iut.bibliotheque.di.BibliothequeModule;
import fr.univ_amu.iut.bibliotheque.model.ServiceBibliotheque;
import fr.univ_amu.iut.commun.di.CommunModule;
import fr.univ_amu.iut.commun.di.PersistenceModule;
import fr.univ_amu.iut.commun.model.ModeValidation;
import fr.univ_amu.iut.commun.model.StatutWorkflow;
import fr.univ_amu.iut.commun.model.Utilisateur;
import fr.univ_amu.iut.commun.model.Verdict;
import fr.univ_amu.iut.commun.model.dao.UtilisateurDao;
import fr.univ_amu.iut.commun.outils.ApercuFx;
import fr.univ_amu.iut.commun.outils.AttenteAudio;
import fr.univ_amu.iut.commun.outils.ModuleCaptureNavigationAudio;
import fr.univ_amu.iut.commun.outils.SonDemo;
import fr.univ_amu.iut.commun.persistence.MigrationSchema;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.commun.view.OuvrirAnalyse;
import fr.univ_amu.iut.commun.view.OuvrirSite;
import fr.univ_amu.iut.commun.viewmodel.SourceObservations;
import fr.univ_amu.iut.passage.di.PassageModule;
import fr.univ_amu.iut.passage.model.EnregistrementOriginal;
import fr.univ_amu.iut.passage.model.Enregistreur;
import fr.univ_amu.iut.passage.model.Passage;
import fr.univ_amu.iut.passage.model.SequenceDEcoute;
import fr.univ_amu.iut.passage.model.SessionDEnregistrement;
import fr.univ_amu.iut.passage.model.dao.EnregistrementOriginalDao;
import fr.univ_amu.iut.passage.model.dao.EnregistreurDao;
import fr.univ_amu.iut.passage.model.dao.PassageDao;
import fr.univ_amu.iut.passage.model.dao.SequenceDao;
import fr.univ_amu.iut.passage.model.dao.SessionDao;
import fr.univ_amu.iut.validation.di.ValidationModule;
import fr.univ_amu.iut.validation.model.Observation;
import fr.univ_amu.iut.validation.model.ResultatsIdentification;
import fr.univ_amu.iut.validation.model.ServiceValidation;
import fr.univ_amu.iut.validation.model.dao.ObservationDao;
import fr.univ_amu.iut.validation.model.dao.ResultatsIdentificationDao;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.TableView;

/// Outil de capture/mesure, utilisable tel quel.
///
/// Capture la **vue audio unifiée** (« Sons & validation ») sur la source `References` en PNG
/// (`apercu-sons-validation.png`) : la table des sons de référence (disposition empilée, colonnes de
/// contexte passage/carré/point visibles car la source n'est pas un unique passage), la première ligne
/// sélectionnée pour peupler le panneau d'écoute pleine largeur, et l'`AudioView` affichant un
/// **spectrogramme** réel calculé depuis un WAV de démonstration (cris FM de synthèse, cf. [SonDemo]).
///
/// On seede une base SQLite temporaire (utilisateur, site/point en SQL brut, passage `DEPOSE`, session
/// avec trois séquences) puis un jeu de résultats et trois observations `is_reference` (taxons et
/// commentaires variés). La vue est chargée avec une `controllerFactory` Guice (socle + passage +
/// validation + bibliotheque, plus un module inline fournissant le [AudioViewModel]) et rendue
/// hors-écran par [ApercuFx]. La capture **attend** la fin du chargement asynchrone de l'`AudioView`
/// ([AttenteAudio]) pour un spectrogramme reproductible.
///
/// Le site et le point (cibles de clé étrangère) sont insérés en SQL brut, sans les DAO de la feature
/// `sites` : la feature `audio` ne doit pas en dépendre (cycle ArchUnit).
///
/// Lancement headless : `.github/assets/capture-screenshots.sh` (Headless Platform JavaFX 26).
public final class CaptureSonsValidation {

    private static final String RACINE_DEMO = "/home/observateur/VigieChiro";
    private static final String ID_UTILISATEUR = "demo-enseignant";
    private static final String ENREGISTREUR = "1925492";
    private static final String NUMERO_CARRE = "640380";
    private static final String CODE_POINT = "A1";
    private static final String NOM_SITE = "Étang de la Tuilière";

    private CaptureSonsValidation() {}

    public static void main(String[] args) throws InterruptedException {
        CountDownLatch fini = new CountDownLatch(1);
        AtomicReference<Throwable> erreur = new AtomicReference<>();
        Platform.startup(() -> {
            try {
                capturer();
            } catch (RuntimeException | IOException probleme) {
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

    private static void capturer() throws IOException {
        Path workspace = Files.createTempDirectory("vc-capture-sons-validation");
        System.setProperty("vigiechiro.workspace", workspace.toString());
        Path sortie = Path.of(System.getProperty("capture.outDir", ".github/assets"));

        Injector injecteur = Guice.createInjector(
                new CommunModule(),
                new PersistenceModule(),
                new PassageModule(),
                new ValidationModule(),
                new BibliothequeModule(),
                new ModuleCaptureNavigationAudio(),
                new AbstractModule() {
                    @Provides
                    AudioViewModel viewModel(ServiceValidation validation, ServiceBibliotheque bibliotheque) {
                        return new AudioViewModel(validation, bibliotheque);
                    }

                    // OuvrirSite requis par le controller pour son fil d'Ariane, mais SitesModule n'est
                    // pas inclus : no-op (la source References ne l'exerce pas). OuvrirPassage, lui, est
                    // déjà fourni par PassageModule (inclus) - ne pas le rebinder (BindingAlreadySet).
                    @Provides
                    OuvrirSite ouvrirSite() {
                        return new OuvrirSite() {
                            @Override
                            public void ouvrirListe() {}

                            @Override
                            public void ouvrirDetail(String numeroCarre) {}
                        };
                    }

                    @Provides
                    OuvrirAnalyse ouvrirAnalyse() {
                        return () -> {};
                    }
                });
        SourceDeDonnees source = injecteur.getInstance(SourceDeDonnees.class);
        new MigrationSchema(source).migrer();

        Graine graine = seeder(source, workspace);
        seederReferences(source, graine);

        rendre(injecteur, sortie.resolve("apercu-sons-validation.png"));
    }

    /// Charge `SonsValidation.fxml`, ouvre la vue sur la source `References` et rend la scène hors-écran.
    /// La table et l'`AudioView` vivent dans un `SplitPane` : leur skin n'attache les nœuds qu'au
    /// **layout**, donc la sélection de la première ligne (détail + écoute) et l'attente du chargement
    /// audio ([AttenteAudio], pour un spectrogramme déterministe) se font **dans** la préparation, après
    /// que la scène est montrée et layoutée par [ApercuFx#capturerApresPreparation].
    private static void rendre(Injector injecteur, Path fichier) throws IOException {
        FXMLLoader loader = new FXMLLoader(SonsValidationController.class.getResource("SonsValidation.fxml"));
        loader.setControllerFactory(injecteur::getInstance);
        Parent vue = loader.load();
        SonsValidationController controleur = loader.getController();
        controleur.ouvrirSur(new SourceObservations.References(ID_UTILISATEUR));
        Scene scene = new Scene(vue, 1100, 720);

        ApercuFx.capturerApresPreparation(
                scene,
                () -> {
                    if (vue.lookup("#tableObservations") instanceof TableView<?> table
                            && !table.getItems().isEmpty()) {
                        table.getSelectionModel().select(0); // déclenche le chargement audio
                    }
                    if (vue.lookup("#audioView") instanceof AudioView audio) {
                        audio.setMinHeight(340); // place pour spectrogramme + sonogramme
                        audio.setPrefHeight(340);
                        AttenteAudio.attendreChargement(audio);
                    }
                },
                fichier);
        System.out.println("Apercu ecrit dans " + fichier.toAbsolutePath());
    }

    /// Seede un passage déposé avec sa session et trois séquences (`seqA/B/C_000.wav`). Écrit pour
    /// chaque séquence un **vrai WAV** de démonstration (cris FM, cf. [SonDemo]) sous le `workspace`
    /// temporaire, afin que l'`AudioView` affiche un spectrogramme réel sur la séquence écoutée.
    private static Graine seeder(SourceDeDonnees source, Path workspace) throws IOException {
        new UtilisateurDao(source).insert(new Utilisateur(ID_UTILISATEUR, "Capitaine Chiro (demo)"));
        PassageDao passageDao = new PassageDao(source);
        SessionDao sessionDao = new SessionDao(source);
        EnregistrementOriginalDao originalDao = new EnregistrementOriginalDao(source);
        SequenceDao sequenceDao = new SequenceDao(source);
        new EnregistreurDao(source).insert(new Enregistreur(ENREGISTREUR, "V1.01", null));

        long idPoint = seederSiteEtPoint(source);
        Passage passage = passageDao.insert(new Passage(
                null,
                2,
                2026,
                "2026-06-22",
                "20:25:00",
                "07:47:00",
                null,
                StatutWorkflow.DEPOSE,
                Verdict.OK,
                null,
                null,
                null,
                idPoint,
                ENREGISTREUR));
        SessionDEnregistrement session = sessionDao.insert(
                new SessionDEnregistrement(null, RACINE_DEMO + "/Etang-Tuiliere-Pass2", null, null, passage.id()));

        List<Long> idSequences = new ArrayList<>();
        for (String base : List.of("seqA", "seqB", "seqC")) {
            EnregistrementOriginal original = originalDao.insert(new EnregistrementOriginal(
                    null, base + ".wav", RACINE_DEMO + "/bruts/" + base + ".wav", 5.0, 384000, null, session.id()));
            Path cheminTransforme = workspace.resolve("transformes").resolve(base + "_000.wav");
            SonDemo.ecrireCrisDemo(cheminTransforme); // vrai signal → spectrogramme réel dans AudioView
            SequenceDEcoute sequence = sequenceDao.insert(new SequenceDEcoute(
                    null,
                    base + "_000.wav",
                    original.id(),
                    0,
                    0.0,
                    5.0,
                    cheminTransforme.toString(),
                    false,
                    session.id()));
            idSequences.add(sequence.id());
        }
        return new Graine(passage.id(), idSequences);
    }

    /// Insère un jeu de résultats puis trois observations marquées `is_reference` (taxons retenus et
    /// commentaires variés). Le taxon retenu est l'observateur s'il est saisi, sinon Tadarida.
    private static void seederReferences(SourceDeDonnees source, Graine graine) {
        ResultatsIdentification resultats = new ResultatsIdentificationDao(source)
                .insert(new ResultatsIdentification(
                        null, RACINE_DEMO + "/nuit-observations.csv", "Brut", "2026-06-23", graine.idPassage()));
        ObservationDao observationDao = new ObservationDao(source);
        long idResultats = resultats.id();
        List<Long> seq = graine.idSequences();

        observationDao.insert(reference(
                seq.get(0), 27000, "Pippip", "Nyclei", "Cri social typique, capté en fin de nuit.", idResultats));
        observationDao.insert(reference(seq.get(1), 45000, "Pippip", null, null, idResultats));
        observationDao.insert(
                reference(seq.get(2), 23000, "Rhihip", "Rhihip", "Excellent rapport signal sur bruit.", idResultats));
    }

    private static Observation reference(
            long idSequence,
            int frequenceHz,
            String taxonTadarida,
            String taxonObservateur,
            String commentaire,
            long idResultats) {
        return new Observation(
                null,
                idSequence,
                0.5,
                3.8,
                frequenceHz,
                taxonTadarida,
                0.74,
                null,
                taxonObservateur,
                taxonObservateur == null ? null : 0.91,
                commentaire,
                true,
                ModeValidation.MANUEL,
                idResultats);
    }

    /// Insère en SQL brut un site et son point d'écoute (cibles de clé étrangère du passage), sans les
    /// DAO de la feature `sites` (que `audio` ne doit pas dépendre, cycle ArchUnit). Renvoie l'`id` du point.
    private static long seederSiteEtPoint(SourceDeDonnees source) {
        String insertSite = "INSERT INTO monitoring_site"
                + "(square_number, friendly_name, protocol, comment, created_at, user_id)"
                + " VALUES (?, ?, ?, ?, ?, ?)";
        String insertPoint =
                "INSERT INTO listening_point(code, gps_lat, gps_lon, description, site_id)" + " VALUES (?, ?, ?, ?, ?)";
        try (Connection cx = source.getConnection()) {
            long idSite;
            try (PreparedStatement ps = cx.prepareStatement(insertSite, Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, NUMERO_CARRE);
                ps.setString(2, NOM_SITE);
                ps.setString(3, "STANDARD");
                ps.setString(4, "Aix-en-Provence");
                ps.setString(5, "2026-01-01");
                ps.setString(6, ID_UTILISATEUR);
                ps.executeUpdate();
                idSite = cleGeneree(ps);
            }
            try (PreparedStatement ps = cx.prepareStatement(insertPoint, Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, CODE_POINT);
                ps.setDouble(2, 43.4010);
                ps.setDouble(3, -1.5740);
                ps.setString(4, "Près du grand chêne");
                ps.setLong(5, idSite);
                ps.executeUpdate();
                return cleGeneree(ps);
            }
        } catch (SQLException echec) {
            throw new IllegalStateException("Seed SQL du site/point impossible", echec);
        }
    }

    private static long cleGeneree(PreparedStatement ps) throws SQLException {
        try (ResultSet cles = ps.getGeneratedKeys()) {
            if (!cles.next()) {
                throw new IllegalStateException("Aucune clé générée renvoyée par l'INSERT");
            }
            return cles.getLong(1);
        }
    }

    /// Identifiants seedés réutilisés par les observations de référence.
    private record Graine(long idPassage, List<Long> idSequences) {}
}
