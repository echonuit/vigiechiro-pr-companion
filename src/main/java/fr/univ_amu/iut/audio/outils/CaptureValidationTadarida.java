package fr.univ_amu.iut.audio.outils;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import fr.nedjar.vigiechiro.audio.AudioView;
import fr.univ_amu.iut.audio.view.SonsValidationController;
import fr.univ_amu.iut.audio.viewmodel.AudioViewModel;
import fr.univ_amu.iut.audio.viewmodel.ImportVigieChiroViewModel;
import fr.univ_amu.iut.bibliotheque.di.BibliothequeModule;
import fr.univ_amu.iut.bibliotheque.model.ServiceBibliotheque;
import fr.univ_amu.iut.commun.di.PersistenceModule;
import fr.univ_amu.iut.commun.model.StatutWorkflow;
import fr.univ_amu.iut.commun.model.Utilisateur;
import fr.univ_amu.iut.commun.model.Verdict;
import fr.univ_amu.iut.commun.model.dao.UtilisateurDao;
import fr.univ_amu.iut.commun.outils.ApercuFx;
import fr.univ_amu.iut.commun.outils.AttenteAudio;
import fr.univ_amu.iut.commun.outils.ModuleCaptureCommun;
import fr.univ_amu.iut.commun.outils.ModuleCaptureNavigationAudio;
import fr.univ_amu.iut.commun.outils.SonDemo;
import fr.univ_amu.iut.commun.persistence.MigrationSchema;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.commun.view.OuvrirAnalyse;
import fr.univ_amu.iut.commun.view.OuvrirMultisite;
import fr.univ_amu.iut.commun.view.OuvrirSite;
import fr.univ_amu.iut.commun.viewmodel.ContextePassage;
import fr.univ_amu.iut.commun.viewmodel.ContexteSite;
import fr.univ_amu.iut.commun.viewmodel.SourceObservations;
import fr.univ_amu.iut.passage.di.PassageModule;
import fr.univ_amu.iut.passage.model.EnregistrementOriginal;
import fr.univ_amu.iut.passage.model.Enregistreur;
import fr.univ_amu.iut.passage.model.Passage;
import fr.univ_amu.iut.passage.model.SequenceDEcoute;
import fr.univ_amu.iut.passage.model.ServiceDisponibiliteAudio;
import fr.univ_amu.iut.passage.model.SessionDEnregistrement;
import fr.univ_amu.iut.passage.model.dao.EnregistrementOriginalDao;
import fr.univ_amu.iut.passage.model.dao.EnregistreurDao;
import fr.univ_amu.iut.passage.model.dao.PassageDao;
import fr.univ_amu.iut.passage.model.dao.SequenceDao;
import fr.univ_amu.iut.passage.model.dao.SessionDao;
import fr.univ_amu.iut.validation.di.ValidationModule;
import fr.univ_amu.iut.validation.model.MarquageDouteux;
import fr.univ_amu.iut.validation.model.PlageNuitPassage;
import fr.univ_amu.iut.validation.model.RevueEnLot;
import fr.univ_amu.iut.validation.model.SaisieCertitude;
import fr.univ_amu.iut.validation.model.ServiceValidation;
import fr.univ_amu.iut.validation.model.ValidationManuelle;
import fr.univ_amu.iut.validation.model.dao.ProjectionsAudioDao;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.TableView;

/// Outil de capture/mesure, utilisable tel quel.
///
/// Capture la **vue audio unifiée « Sons & validation »** d'un passage (source `ParPassage`) en
/// PNG (`apercu-validation-tadarida.png`) : l'écran tel qu'il apparaît **juste après l'import d'un CSV
/// Tadarida** sur un passage. La table liste les observations « À revoir » (colonnes de contexte masquées
/// car la source est un unique passage), le panneau d'écoute affiche un **spectrogramme** réel (cris FM de
/// synthèse, cf. [SonDemo]), et le **bandeau de retour** récapitule l'import tolérant (« Import réussi :
/// N · M ignorée(s) (audio absent) · K taxon(s) hors référentiel »).
///
/// On seede une base SQLite temporaire (utilisateur, site/point en SQL brut, passage `DEPOSE`, session
/// avec des séquences), on écrit un petit CSV Tadarida référençant ces séquences (avec un taxon hors
/// référentiel et une ligne sans audio, pour exercer la tolérance), puis on **ouvre la vue sur le passage
/// et on importe réellement le CSV** via un [AudioViewModel] partagé (singleton de capture). La vue est
/// chargée par une `controllerFactory` Guice (socle + passage + validation + bibliotheque) et rendue
/// hors-écran par [ApercuFx], après attente du chargement audio ([AttenteAudio]) pour un spectrogramme
/// reproductible.
///
/// Le site et le point (cibles de clé étrangère) sont insérés en SQL brut, sans les DAO de la feature
/// `sites` : la feature `audio` ne doit pas en dépendre (cycle ArchUnit).
///
/// Lancement headless : `.github/assets/capture-screenshots.sh` (Headless Platform JavaFX 26).
public final class CaptureValidationTadarida {

    private static final String RACINE_DEMO = "/home/observateur/VigieChiro";
    private static final String ID_UTILISATEUR = "demo-enseignant";
    private static final String ENREGISTREUR = "1925492";
    private static final String NUMERO_CARRE = "640380";
    private static final String CODE_POINT = "Z1";
    private static final String NOM_SITE = "Étang de la Tuilière";

    /// Séquences seedées (avec un vrai WAV de démo) : la base de la table après import.
    private static final List<String> SEQUENCES = List.of("seqA", "seqB", "seqC", "seqD", "seqE", "seqF");

    private CaptureValidationTadarida() {}

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
        Path workspace = Files.createTempDirectory("vc-capture-validation-tadarida");
        System.setProperty("vigiechiro.workspace", workspace.toString());
        Path sortie = Path.of(System.getProperty("capture.outDir", ".github/assets"));

        Injector injecteur = creerInjecteur();
        SourceDeDonnees source = injecteur.getInstance(SourceDeDonnees.class);
        new MigrationSchema(source).migrer();

        long idPassage = seeder(source, workspace);
        Path csv = ecrireCsvTadarida(workspace);

        rendre(injecteur, idPassage, csv, sortie.resolve("apercu-validation-tadarida.png"));
    }

    /// Injecteur (partiel) utilisé par cet outil de capture. Exposé pour le garde-fou de câblage
    /// (test).
    public static Injector creerInjecteur() {
        return Guice.createInjector(
                ModuleCaptureCommun.communSynchrone(),
                new PersistenceModule(),
                new PassageModule(),
                new ValidationModule(),
                new BibliothequeModule(),
                new ModuleCaptureNavigationAudio(),
                new AbstractModule() {
                    /// Singleton de capture : le controller et le code de capture partagent le MÊME
                    /// ViewModel, pour pouvoir déclencher l'import après l'ouverture.
                    @Provides
                    @Singleton
                    AudioViewModel viewModel(
                            ServiceValidation validation,
                            ProjectionsAudioDao projectionsAudio,
                            PlageNuitPassage plageNuitPassage,
                            ValidationManuelle validationManuelle,
                            MarquageDouteux marquageDouteux,
                            SaisieCertitude saisieCertitude,
                            RevueEnLot revueEnLot,
                            ServiceBibliotheque bibliotheque,
                            ServiceDisponibiliteAudio disponibilite) {
                        return new AudioViewModel(
                                validation,
                                projectionsAudio,
                                plageNuitPassage,
                                validationManuelle,
                                marquageDouteux,
                                saisieCertitude,
                                revueEnLot,
                                bibliotheque,
                                disponibilite,
                                Files::exists);
                    }

                    // Import VigieChiro indisponible en capture (aucune connexion) : VM à dépôt vide.
                    @Provides
                    @Singleton
                    ImportVigieChiroViewModel importVigieChiro() {
                        return new ImportVigieChiroViewModel(Optional.empty());
                    }

                    @Provides
                    fr.univ_amu.iut.audio.viewmodel.PublicationCorrectionsViewModel publicationCorrections() {
                        return new fr.univ_amu.iut.audio.viewmodel.PublicationCorrectionsViewModel(Optional.empty());
                    }

                    // OuvrirSite requis par le fil d'Ariane du controller (SitesModule non inclus) : no-op.
                    // OuvrirPassage est déjà fourni par PassageModule (inclus) - ne pas le rebinder.
                    @Provides
                    OuvrirSite ouvrirSite() {
                        return new OuvrirSite() {
                            @Override
                            public void ouvrirListe() {}

                            @Override
                            public void ouvrirDetail(String numeroCarre) {}
                        };
                    }

                    // OuvrirAnalyse (#1087, feature `analyse` désactivable) : le controller l'injecte en
                    // Optional ; ce module n'inclut pas AnalyseModule (ni son OptionalBinder vide via
                    // AudioModule, non plus inclus), donc on fournit directement l'Optional peuplé (no-op).
                    @Provides
                    Optional<OuvrirAnalyse> ouvrirAnalyse() {
                        return Optional.of((filtres, afficherCarte) -> {});
                    }

                    @Provides
                    OuvrirMultisite ouvrirMultisite() {
                        return numeroCarre -> {};
                    }
                });
    }

    /// Charge `SonsValidation.fxml`, ouvre la vue sur le **passage** puis **importe** le CSV Tadarida sur
    /// le ViewModel partagé (table peuplée « À revoir » + bandeau de retour). La sélection de la première
    /// ligne (détail + écoute) et l'attente du chargement audio se font dans la préparation, après que la
    /// scène est montrée et layoutée (le `SplitPane` n'attache la table et l'`AudioView` qu'au layout).
    private static void rendre(Injector injecteur, long idPassage, Path csv, Path fichier) throws IOException {
        FXMLLoader loader = new FXMLLoader(SonsValidationController.class.getResource("SonsValidation.fxml"));
        loader.setControllerFactory(injecteur::getInstance);
        Parent vue = loader.load();
        SonsValidationController controleur = loader.getController();

        ContextePassage contexte =
                new ContextePassage(idPassage, 2, new ContexteSite(NUMERO_CARRE, CODE_POINT, NOM_SITE));
        controleur.ouvrirSur(new SourceObservations.ParPassage(contexte));
        // Import réel du CSV (tolérant) : peuple la table et arme le bandeau de retour de succès.
        injecteur.getInstance(AudioViewModel.class).importer(csv, false);

        Scene scene = new Scene(vue, 1100, 720);
        ApercuFx.capturerApresPreparation(
                scene,
                () -> {
                    if (vue.lookup("#tableObservations") instanceof TableView<?> table
                            && !table.getItems().isEmpty()) {
                        table.getSelectionModel().select(0); // déclenche le chargement audio
                    }
                    if (vue.lookup("#audioView") instanceof AudioView audio) {
                        audio.setMinHeight(300);
                        audio.setPrefHeight(300);
                        AttenteAudio.attendreChargement(audio);
                    }
                },
                fichier);
        System.out.println("Apercu ecrit dans " + fichier.toAbsolutePath());
    }

    /// Seede un passage déposé avec sa session et six séquences (`seqA…seqF_000.wav`), chacune avec un
    /// **vrai WAV** de démonstration (cris FM, cf. [SonDemo]) sous le `workspace` temporaire. Renvoie
    /// l'`id` du passage.
    private static long seeder(SourceDeDonnees source, Path workspace) throws IOException {
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

        for (String base : SEQUENCES) {
            EnregistrementOriginal original = originalDao.insert(new EnregistrementOriginal(
                    null, base + ".wav", RACINE_DEMO + "/bruts/" + base + ".wav", 5.0, 384000, null, session.id()));
            Path cheminTransforme = workspace.resolve("transformes").resolve(base + "_000.wav");
            SonDemo.ecrireCrisDemo(cheminTransforme); // vrai signal → spectrogramme réel dans AudioView
            sequenceDao.insert(new SequenceDEcoute(
                    null,
                    base + "_000.wav",
                    original.id(),
                    0,
                    0.0,
                    5.0,
                    cheminTransforme.toString(),
                    false,
                    session.id()));
        }
        return passage.id();
    }

    /// Écrit un petit CSV Tadarida (Brut) référençant les séquences seedées, avec des taxons variés (dont
    /// un **hors référentiel** : `Testes`) et une **ligne sans audio** (`perdu_000`) pour exercer la
    /// tolérance et alimenter le bandeau (importées / ignorées / hors référentiel).
    private static Path ecrireCsvTadarida(Path workspace) {
        String contenu = guillemets(
                        "nom du fichier",
                        "temps_debut",
                        "temps_fin",
                        "frequence_mediane",
                        "tadarida_taxon",
                        "tadarida_probabilite",
                        "tadarida_taxon_autre",
                        "observateur_taxon",
                        "observateur_probabilite",
                        "validateur_taxon",
                        "validateur_probabilite")
                + guillemets("seqA_000", "0.4", "2.6", "18000", "noise", "0.88", "", "", "", "", "")
                + guillemets("seqB_000", "0.2", "3.1", "45", "Pippip", "0.81", "", "", "", "", "")
                + guillemets("seqC_000", "0.5", "3.8", "23000", "Rhihip", "0.76", "", "", "", "", "")
                + guillemets("seqD_000", "0.3", "2.9", "27000", "Nyclei", "0.69", "", "", "", "", "")
                + guillemets("seqE_000", "0.1", "2.2", "8000", "piaf", "0.72", "", "", "", "", "")
                + guillemets("seqF_000", "0.6", "4.0", "35000", "Testes", "0.58", "", "", "", "", "")
                + guillemets("perdu_000", "0.2", "2.0", "40000", "Rusnit", "0.61", "", "", "", "", "");
        Path fichier = workspace.resolve("Car640380-2026-Pass2-Z1-observations.csv");
        try {
            Files.writeString(fichier, contenu, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return fichier;
    }

    private static String guillemets(String... champs) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < champs.length; i++) {
            if (i > 0) {
                sb.append(';');
            }
            sb.append('"').append(champs[i]).append('"');
        }
        return sb.append('\n').toString();
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
}
