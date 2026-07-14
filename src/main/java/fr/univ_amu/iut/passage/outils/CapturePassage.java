package fr.univ_amu.iut.passage.outils;

import com.google.inject.Guice;
import com.google.inject.Injector;
import fr.univ_amu.iut.commun.di.PersistenceModule;
import fr.univ_amu.iut.commun.model.PortailVigieChiro;
import fr.univ_amu.iut.commun.model.Prefixe;
import fr.univ_amu.iut.commun.model.StatutWorkflow;
import fr.univ_amu.iut.commun.model.Utilisateur;
import fr.univ_amu.iut.commun.model.Verdict;
import fr.univ_amu.iut.commun.model.dao.UtilisateurDao;
import fr.univ_amu.iut.commun.outils.ApercuFx;
import fr.univ_amu.iut.commun.outils.ModuleCaptureCommun;
import fr.univ_amu.iut.commun.persistence.MigrationSchema;
import fr.univ_amu.iut.commun.persistence.ServicePurgeOriginaux;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.commun.view.ExecuteurTache;
import fr.univ_amu.iut.commun.view.OuvrirDiagnostic;
import fr.univ_amu.iut.commun.view.OuvrirLot;
import fr.univ_amu.iut.commun.view.OuvrirVerification;
import fr.univ_amu.iut.commun.viewmodel.ContexteSite;
import fr.univ_amu.iut.passage.di.PassageModule;
import fr.univ_amu.iut.passage.model.EnregistrementOriginal;
import fr.univ_amu.iut.passage.model.Enregistreur;
import fr.univ_amu.iut.passage.model.Passage;
import fr.univ_amu.iut.passage.model.SequenceDEcoute;
import fr.univ_amu.iut.passage.model.ServiceArchivagePassage;
import fr.univ_amu.iut.passage.model.ServicePassage;
import fr.univ_amu.iut.passage.model.ServiceReactivationPassage;
import fr.univ_amu.iut.passage.model.SessionDEnregistrement;
import fr.univ_amu.iut.passage.model.dao.EnregistrementOriginalDao;
import fr.univ_amu.iut.passage.model.dao.EnregistreurDao;
import fr.univ_amu.iut.passage.model.dao.PassageDao;
import fr.univ_amu.iut.passage.model.dao.SequenceDao;
import fr.univ_amu.iut.passage.model.dao.SessionDao;
import fr.univ_amu.iut.passage.view.AppuisPassage;
import fr.univ_amu.iut.passage.view.NavigationPassage;
import fr.univ_amu.iut.passage.view.PassageController;
import fr.univ_amu.iut.passage.view.RattachementModaleController;
import fr.univ_amu.iut.passage.viewmodel.PassageViewModel;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;

/// Outil de capture/mesure, utilisable tel quel.
///
/// Capture l'écran pivot M-Passage en PNG pour le comparer à la maquette du brief, en **trois
/// états** afin d'en montrer les particularités :
///
/// - `apercu-passage.png` : passage **vérifié** — « Préparer le dépôt » actif, validation Tadarida
///   verrouillée (le passage n'est pas encore déposé) ;
/// - `apercu-passage-depose.png` : passage **déposé** — stepper au bout, « Préparer le dépôt »
///   désactivé, validation Tadarida déverrouillée ;
/// - `apercu-passage-rattachement.png` : la **modale « Modifier le rattachement »** (année + n° de
///   passage).
///
/// On seede une base SQLite temporaire (un utilisateur, un site/point, deux passages vérifié/déposé
/// avec leur session de 60 séquences). On fabrique le [ServicePassage] via Guice (socle + passage),
/// puis on charge `Passage.fxml` / `RattachementModale.fxml` avec une `controllerFactory` qui injecte
/// un [PassageViewModel] connu et des contrats de navigation neutres (la capture ne navigue pas).
/// Les vues sont rendues hors-écran par [ApercuFx].
///
/// Le site et le point (cibles de clé étrangère) sont insérés en SQL brut, sans les DAO de la
/// feature `sites` : `passage` ne doit pas en dépendre (cycle ArchUnit `features_sans_cycle`, et
/// `sites` dépend déjà de `passage`).
///
/// Lancement headless : `.github/assets/capture-screenshots.sh` (Headless Platform JavaFX 26).
public final class CapturePassage {

    private static final String ID_UTILISATEUR = "demo-enseignant";
    private static final String ENREGISTREUR = "1925492";
    private static final String NUMERO_CARRE = "640380";
    private static final String CODE_POINT = "A1";
    private static final String NOM_SITE = "Étang de la Tuilière";
    private static final int NB_SEQUENCES = 60;
    private static final long VOLUME_ORIGINAUX_OCTETS = 5L * 1024 * 1024 * 1024; // 5 Go
    private static final long VOLUME_SEQUENCES_OCTETS = 180L * 1024 * 1024; // 180 Mo

    private CapturePassage() {}

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
        Path workspace = Files.createTempDirectory("vc-capture-passage");
        System.setProperty("vigiechiro.workspace", workspace.toString());
        Path sortie = Path.of(System.getProperty("capture.outDir", ".github/assets"));

        Injector injecteur = creerInjecteur();
        SourceDeDonnees source = injecteur.getInstance(SourceDeDonnees.class);
        new MigrationSchema(source).migrer();

        new UtilisateurDao(source).insert(new Utilisateur(ID_UTILISATEUR, "Capitaine Chiro (demo)"));
        new EnregistreurDao(source).insert(new Enregistreur(ENREGISTREUR, "V1.01", null));
        long idPoint = seederSiteEtPoint(source);
        long idVerifie = seederPassage(source, workspace, idPoint, StatutWorkflow.VERIFIE, 2);
        long idDepose = seederPassage(source, workspace, idPoint, StatutWorkflow.DEPOSE, 1);

        // Pivot : deux statuts pour montrer l'évolution des actions disponibles (préparer le dépôt
        // quand vérifié ; validation déverrouillée une fois déposé).
        rendrePivot(injecteur, idVerifie, sortie.resolve("apercu-passage.png"));
        rendrePivot(injecteur, idDepose, sortie.resolve("apercu-passage-depose.png"));
        // Modale « Modifier le rattachement » (année + n° de passage) ouverte sur le passage vérifié.
        rendreRattachement(injecteur, idVerifie, sortie.resolve("apercu-passage-rattachement.png"));
    }

    /// Injecteur (partiel) utilisé par cet outil de capture. Exposé pour le garde-fou de câblage
    /// (test).
    public static Injector creerInjecteur() {
        return Guice.createInjector(
                ModuleCaptureCommun.communSynchrone(), new PersistenceModule(), new PassageModule());
    }

    /// Charge `Passage.fxml` sur `idPassage` (ViewModel connu + contrats de navigation neutres) et
    /// rend le pivot hors-écran.
    private static void rendrePivot(Injector injecteur, long idPassage, Path fichier) throws IOException {
        PassageViewModel passageVm = new PassageViewModel(
                injecteur.getInstance(ServicePassage.class),
                injecteur.getInstance(ServicePurgeOriginaux.class),
                injecteur.getInstance(ServiceArchivagePassage.class),
                injecteur.getInstance(ServiceReactivationPassage.class),
                // La capture tourne hors connexion : « Importer les observations » (#1350) s'y montre
                // donc grisé, avec le tooltip qui dit pourquoi. C'est l'aperçu fidèle de cet état.
                Optional.empty());
        FXMLLoader loader = new FXMLLoader(PassageController.class.getResource("Passage.fxml"));
        loader.setControllerFactory(type -> type == PassageController.class
                ? new PassageController(
                        passageVm,
                        Optional.<OuvrirVerification>of(idp -> {}),
                        Optional.<OuvrirDiagnostic>of(idp -> {}),
                        idp -> {},
                        Optional.<OuvrirLot>of(idp -> {}),
                        injecteur.getInstance(NavigationPassage.class),
                        ouvrirSiteNeutre(),
                        numeroCarre -> {},
                        idp -> 0,
                        // Appuis socle (#1213) : l'exécuteur vient de l'injecteur de capture, donc
                        // SYNCHRONE (garde-fou #1278) - le snapshot part une fois l'écran chargé.
                        new AppuisPassage(
                                injecteur.getInstance(ExecuteurTache.class),
                                injecteur.getInstance(PortailVigieChiro.class),
                                url -> {}))
                : injecteur.getInstance(type));
        Parent vue = loader.load();
        PassageController controleur = loader.getController();
        controleur.ouvrirSur(idPassage, new ContexteSite(NUMERO_CARRE, CODE_POINT, NOM_SITE));
        ApercuFx.enregistrerPng(new Scene(vue, 1100, 620), fichier);
        System.out.println("Apercu ecrit dans " + fichier.toAbsolutePath());
    }

    /// Contrat [fr.univ_amu.iut.commun.view.OuvrirSite] neutre (no-op) pour la capture : la navigation
    /// vers le site (segment « Carré N » du fil d'Ariane) est portée par le chrome, hors de cet aperçu
    /// hors-écran. Évite aussi de faire dépendre l'outil de la feature `sites` (cycle ArchUnit).
    private static fr.univ_amu.iut.commun.view.OuvrirSite ouvrirSiteNeutre() {
        return new fr.univ_amu.iut.commun.view.OuvrirSite() {
            @Override
            public void ouvrirListe() {}

            @Override
            public void ouvrirDetail(String numeroCarre) {}
        };
    }

    /// Charge `RattachementModale.fxml` (controller injecté par Guice), la démarre sur le passage et
    /// rend la modale hors-écran.
    private static void rendreRattachement(Injector injecteur, long idPassage, Path fichier) throws IOException {
        FXMLLoader loader = new FXMLLoader(NavigationPassage.class.getResource("RattachementModale.fxml"));
        loader.setControllerFactory(injecteur::getInstance);
        Parent vue = loader.load();
        RattachementModaleController controleur = loader.getController();
        controleur.demarrer(idPassage, NUMERO_CARRE, CODE_POINT, () -> {});
        ApercuFx.enregistrerPng(new Scene(vue), fichier);
        System.out.println("Apercu ecrit dans " + fichier.toAbsolutePath());
    }

    /// Seede une nuit complète (chemins sous le `workspace` temporaire) avec le `statut` et le
    /// `numero` de passage donnés, et renvoie l'identifiant du passage. Le site/point (FK) est seedé
    /// à part et partagé.
    private static long seederPassage(
            SourceDeDonnees source, Path workspace, long idPoint, StatutWorkflow statut, int numero) {
        PassageDao passageDao = new PassageDao(source);
        SessionDao sessionDao = new SessionDao(source);
        EnregistrementOriginalDao originalDao = new EnregistrementOriginalDao(source);
        SequenceDao sequenceDao = new SequenceDao(source);

        Prefixe prefixe = new Prefixe(NUMERO_CARRE, 2026, numero, CODE_POINT);
        Passage passage = passageDao.insert(new Passage(
                null,
                numero,
                2026,
                "2026-06-22",
                "20:25:00",
                "07:47:00",
                null,
                statut,
                Verdict.OK,
                null,
                "{\"tempDebut\":8.5}", // météo de début de nuit (#106) : montre la valeur sur l'aperçu
                null,
                idPoint,
                ENREGISTREUR));
        SessionDEnregistrement session = sessionDao.insert(new SessionDEnregistrement(
                null,
                workspace.resolve(prefixe.nomDossierSession()).toString(),
                VOLUME_ORIGINAUX_OCTETS,
                VOLUME_SEQUENCES_OCTETS,
                passage.id()));

        LocalDateTime debut = LocalDateTime.of(2026, 6, 22, 20, 25, 0);
        DateTimeFormatter horodatage = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
        for (int i = 0; i < NB_SEQUENCES; i++) {
            String suffixe =
                    "PaRecPR" + ENREGISTREUR + "_" + debut.plusMinutes(12L * i).format(horodatage) + ".wav";
            String nomOriginal = prefixe.nommerOriginal(suffixe);
            EnregistrementOriginal original = originalDao.insert(new EnregistrementOriginal(
                    null,
                    nomOriginal,
                    workspace.resolve("bruts").resolve(nomOriginal).toString(),
                    5.0,
                    384000,
                    null,
                    session.id()));
            String nomSequence = prefixe.nommerSequence(nomOriginal, 0);
            sequenceDao.insert(new SequenceDEcoute(
                    null,
                    nomSequence,
                    original.id(),
                    0,
                    0.0,
                    5.0,
                    workspace.resolve("transformes").resolve(nomSequence).toString(),
                    false,
                    session.id()));
        }
        return passage.id();
    }

    /// Insère en SQL brut un site (`monitoring_site`) et son point d'écoute (`listening_point`),
    /// cibles de clé étrangère du passage, et renvoie l'`id` du point. Volontairement sans les DAO de
    /// la feature `sites` : `passage` ne doit pas en dépendre (cycle ArchUnit). Le point n'est qu'une
    /// FK ici — M-Passage affiche le libellé du site via [ContexteSite], sans jointure.
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
