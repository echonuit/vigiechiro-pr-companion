package fr.univ_amu.iut.bibliotheque.outils;

import com.google.inject.Guice;
import com.google.inject.Injector;
import fr.univ_amu.iut.bibliotheque.di.BibliothequeModule;
import fr.univ_amu.iut.bibliotheque.view.BibliothequeController;
import fr.univ_amu.iut.commun.di.CommunModule;
import fr.univ_amu.iut.commun.di.PersistenceModule;
import fr.univ_amu.iut.commun.model.ModeValidation;
import fr.univ_amu.iut.commun.model.StatutWorkflow;
import fr.univ_amu.iut.commun.model.Utilisateur;
import fr.univ_amu.iut.commun.model.Verdict;
import fr.univ_amu.iut.commun.model.dao.UtilisateurDao;
import fr.univ_amu.iut.commun.outils.ApercuFx;
import fr.univ_amu.iut.commun.persistence.MigrationSchema;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
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
/// Capture l'écran M-Bibliotheque en PNG pour le comparer à la maquette du brief, en **deux états**
/// afin d'en montrer les particularités :
///
/// - `apercu-bibliotheque-vide.png` : **état vide**, aucune observation marquée référence — résumé
///   d'invite et bouton d'export désactivé ;
/// - `apercu-bibliotheque-sons.png` : **état peuplé**, trois sons de référence triés par taxon, la
///   première ligne sélectionnée (panneau de détail + écoute via `AudioView`) et l'export actif.
///
/// On seede une base SQLite temporaire (utilisateur, site/point en SQL brut, passage `DEPOSE`,
/// session avec trois séquences). La première capture est rendue **avant** tout marquage référence ;
/// on insère ensuite un jeu de résultats et trois observations `is_reference`, puis on rend la
/// seconde. Chaque vue est chargée avec une `controllerFactory` Guice (socle + passage + validation
/// + bibliotheque) et rendue hors-écran par [ApercuFx].
///
/// **Déterminisme des PNG** : les chemins de séquences (liés à `AudioView`) utilisent une racine
/// fixe [#RACINE_DEMO], jamais le dossier temporaire aléatoire — sinon chaque exécution salirait les
/// PNG versionnés (cf. la même précaution dans `CaptureLot`).
///
/// Le site et le point (cibles de clé étrangère) sont insérés en SQL brut, sans les DAO de la
/// feature `sites` : `bibliotheque` ne doit pas en dépendre (cycle ArchUnit `features_sans_cycle`).
///
/// Lancement headless : `.github/assets/capture-screenshots.sh` (Headless Platform JavaFX 26).
public final class CaptureBibliotheque {

    private static final String RACINE_DEMO = "/home/observateur/VigieChiro";
    private static final String ID_UTILISATEUR = "demo-enseignant";
    private static final String ENREGISTREUR = "1925492";
    private static final String NUMERO_CARRE = "640380";
    private static final String CODE_POINT = "A1";
    private static final String NOM_SITE = "Étang de la Tuilière";

    private CaptureBibliotheque() {}

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
        Path workspace = Files.createTempDirectory("vc-capture-bibliotheque");
        System.setProperty("vigiechiro.workspace", workspace.toString());
        Path sortie = Path.of(System.getProperty("capture.outDir", ".github/assets"));

        Injector injecteur = Guice.createInjector(
                new CommunModule(),
                new PersistenceModule(),
                new PassageModule(),
                new ValidationModule(),
                new BibliothequeModule());
        SourceDeDonnees source = injecteur.getInstance(SourceDeDonnees.class);
        new MigrationSchema(source).migrer();

        Graine graine = seeder(source);

        // 1) État vide : aucune observation n'est encore marquée référence → résumé d'invite.
        rendre(injecteur, -1, sortie.resolve("apercu-bibliotheque-vide.png"));

        // Insère un jeu de résultats + trois observations de référence (taxons et commentaires variés).
        seederReferences(source, graine);

        // 2) État peuplé : table triée par taxon, première ligne sélectionnée → détail + écoute.
        rendre(injecteur, 0, sortie.resolve("apercu-bibliotheque-sons.png"));
    }

    /// Charge `Bibliotheque.fxml` (le controller auto-charge la table en `initialize()`), sélectionne
    /// éventuellement une ligne (`>= 0`) pour peupler le détail et l'écoute, puis rend la scène
    /// hors-écran en PNG.
    private static void rendre(Injector injecteur, int ligneSelectionnee, Path fichier) throws IOException {
        FXMLLoader loader = new FXMLLoader(BibliothequeController.class.getResource("Bibliotheque.fxml"));
        loader.setControllerFactory(injecteur::getInstance);
        Parent vue = loader.load();

        if (ligneSelectionnee >= 0
                && vue.lookup("#tableEntrees") instanceof TableView<?> table
                && table.getItems().size() > ligneSelectionnee) {
            table.getSelectionModel().select(ligneSelectionnee);
        }

        ApercuFx.enregistrerPng(new Scene(vue, 1000, 620), fichier);
        System.out.println("Apercu ecrit dans " + fichier.toAbsolutePath());
    }

    /// Seede un passage déposé avec sa session et trois séquences (`seqA/B/C_000.wav`) aux chemins
    /// déterministes. Renvoie l'identifiant du passage et celui des séquences (cibles des
    /// observations de référence).
    private static Graine seeder(SourceDeDonnees source) {
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
            SequenceDEcoute sequence = sequenceDao.insert(new SequenceDEcoute(
                    null,
                    base + "_000.wav",
                    original.id(),
                    0,
                    0.0,
                    5.0,
                    RACINE_DEMO + "/transformes/" + base + "_000.wav",
                    false,
                    session.id()));
            idSequences.add(sequence.id());
        }
        return new Graine(passage.id(), idSequences);
    }

    /// Insère un jeu de résultats pour le passage puis trois observations marquées `is_reference`
    /// (taxons retenus et commentaires variés pour montrer le détail). Le taxon retenu est
    /// l'observateur s'il est saisi, sinon Tadarida ; la table est triée alphabétiquement.
    private static void seederReferences(SourceDeDonnees source, Graine graine) {
        ResultatsIdentification resultats = new ResultatsIdentificationDao(source)
                .insert(new ResultatsIdentification(
                        null, RACINE_DEMO + "/nuit-observations.csv", "Brut", "2026-06-23", graine.idPassage()));
        ObservationDao observationDao = new ObservationDao(source);
        long idResultats = resultats.id();
        List<Long> seq = graine.idSequences();

        // seqA → Nyclei (corrigée), avec commentaire ; tri alphabétique : 1re ligne de la table.
        observationDao.insert(reference(
                seq.get(0), 27000, "Pippip", "Nyclei", "Cri social typique, capté en fin de nuit.", idResultats));
        // seqB → Pippip (proposition Tadarida retenue), sans commentaire.
        observationDao.insert(reference(seq.get(1), 45000, "Pippip", null, null, idResultats));
        // seqC → Rhihip (corrigée), avec commentaire.
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

    /// Insère en SQL brut un site (`monitoring_site`) et son point d'écoute (`listening_point`),
    /// cibles de clé étrangère du passage, et renvoie l'`id` du point. Volontairement sans les DAO de
    /// la feature `sites` : `bibliotheque` ne doit pas en dépendre (cycle ArchUnit).
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

    private record Graine(long idPassage, List<Long> idSequences) {}
}
