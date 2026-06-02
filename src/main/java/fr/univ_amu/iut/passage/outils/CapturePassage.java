// --solution-only--
package fr.univ_amu.iut.passage.outils;

import com.google.inject.Guice;
import com.google.inject.Injector;
import fr.univ_amu.iut.commun.di.CommunModule;
import fr.univ_amu.iut.commun.di.PersistenceModule;
import fr.univ_amu.iut.commun.model.Prefixe;
import fr.univ_amu.iut.commun.model.StatutWorkflow;
import fr.univ_amu.iut.commun.model.Utilisateur;
import fr.univ_amu.iut.commun.model.Verdict;
import fr.univ_amu.iut.commun.model.dao.UtilisateurDao;
import fr.univ_amu.iut.commun.outils.ApercuFx;
import fr.univ_amu.iut.commun.persistence.MigrationSchema;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.commun.viewmodel.ContexteSite;
import fr.univ_amu.iut.passage.di.PassageModule;
import fr.univ_amu.iut.passage.model.EnregistrementOriginal;
import fr.univ_amu.iut.passage.model.Enregistreur;
import fr.univ_amu.iut.passage.model.Passage;
import fr.univ_amu.iut.passage.model.SequenceDEcoute;
import fr.univ_amu.iut.passage.model.ServicePassage;
import fr.univ_amu.iut.passage.model.SessionDEnregistrement;
import fr.univ_amu.iut.passage.model.dao.EnregistrementOriginalDao;
import fr.univ_amu.iut.passage.model.dao.EnregistreurDao;
import fr.univ_amu.iut.passage.model.dao.PassageDao;
import fr.univ_amu.iut.passage.model.dao.SequenceDao;
import fr.univ_amu.iut.passage.model.dao.SessionDao;
import fr.univ_amu.iut.passage.view.NavigationPassage;
import fr.univ_amu.iut.passage.view.PassageController;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;

/// OUTIL ENSEIGNANT (hors version etudiante, retire en passe A2).
///
/// Capture l'écran pivot M-Passage en PNG pour le comparer à la maquette du brief, dans le « cas
/// standard » : passage vérifié, verdict OK, statistiques de nuit renseignées.
///
/// On seede une base SQLite temporaire (un utilisateur, un site/point, un passage `VERIFIE` non
/// déposé, une session de 60 séquences avec volumes). On fabrique le [ServicePassage] via Guice
/// (socle + passage), puis on charge `Passage.fxml` avec une `controllerFactory` qui injecte un
/// [PassageViewModel] connu et des contrats de navigation neutres (la capture ne navigue pas).
/// La vue est enfin rendue hors-écran par [ApercuFx].
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
    private static final Prefixe PREFIXE = new Prefixe(NUMERO_CARRE, 2026, 2, CODE_POINT);
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

        Injector injecteur = Guice.createInjector(new CommunModule(), new PersistenceModule(), new PassageModule());
        SourceDeDonnees source = injecteur.getInstance(SourceDeDonnees.class);
        new MigrationSchema(source).migrer();
        long idPassage = seeder(source, workspace);

        PassageViewModel passageVm = new PassageViewModel(injecteur.getInstance(ServicePassage.class));
        FXMLLoader loader = new FXMLLoader(PassageController.class.getResource("Passage.fxml"));
        loader.setControllerFactory(type -> type == PassageController.class
                ? new PassageController(
                        passageVm, idp -> {}, idp -> {}, idp -> {}, injecteur.getInstance(NavigationPassage.class))
                : injecteur.getInstance(type));
        Parent vue = loader.load();
        PassageController controleur = loader.getController();
        controleur.ouvrirSur(idPassage, new ContexteSite(NUMERO_CARRE, CODE_POINT, NOM_SITE));

        Path fichier = sortie.resolve("apercu-passage.png");
        ApercuFx.enregistrerPng(new Scene(vue, 1100, 540), fichier);
        System.out.println("Apercu ecrit dans " + fichier.toAbsolutePath());
    }

    /// Seede une nuit complète vérifiée (chemins sous le `workspace` temporaire) et renvoie
    /// l'identifiant du passage à afficher.
    private static long seeder(SourceDeDonnees source, Path workspace) {
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
                StatutWorkflow.VERIFIE,
                Verdict.OK,
                null,
                null,
                null,
                idPoint,
                ENREGISTREUR));
        SessionDEnregistrement session = sessionDao.insert(new SessionDEnregistrement(
                null,
                workspace.resolve(PREFIXE.nomDossierSession()).toString(),
                VOLUME_ORIGINAUX_OCTETS,
                VOLUME_SEQUENCES_OCTETS,
                passage.id()));

        LocalDateTime debut = LocalDateTime.of(2026, 6, 22, 20, 25, 0);
        DateTimeFormatter horodatage = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
        for (int i = 0; i < NB_SEQUENCES; i++) {
            String suffixe =
                    "PaRecPR" + ENREGISTREUR + "_" + debut.plusMinutes(12L * i).format(horodatage) + ".wav";
            String nomOriginal = PREFIXE.nommerOriginal(suffixe);
            EnregistrementOriginal original = originalDao.insert(new EnregistrementOriginal(
                    null,
                    nomOriginal,
                    workspace.resolve("bruts").resolve(nomOriginal).toString(),
                    5.0,
                    384000,
                    null,
                    session.id()));
            String nomSequence = PREFIXE.nommerSequence(nomOriginal, 0);
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
                ps.setDouble(2, 43.5298);
                ps.setDouble(3, 5.4474);
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
