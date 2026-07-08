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
import fr.univ_amu.iut.commun.view.OuvrirMultisite;
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
import fr.univ_amu.iut.validation.model.RevueEnLot;
import fr.univ_amu.iut.validation.model.ServiceValidation;
import fr.univ_amu.iut.validation.model.ValidationManuelle;
import fr.univ_amu.iut.validation.model.dao.ObservationDao;
import fr.univ_amu.iut.validation.model.dao.ResultatsIdentificationDao;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.TableView;

/// Socle **partagé** des outils de capture de la vue « Sons & validation » : injecteur partiel, seed d'une
/// base SQLite de démonstration (utilisateur, site/point, passage `DEPOSE`, session à trois séquences avec
/// de vrais WAV de démo, trois observations `is_reference`) et rendu hors-écran d'un état de la vue.
///
/// Chaque état à illustrer vit dans son **propre `main`** ([CaptureSonsValidation] pour l'aperçu général,
/// [CaptureSonsValidationFiltres] pour la barre de filtres active, [CaptureSonsValidationColonnes] pour les
/// colonnes de mesures). Un seul état par JVM : [ApercuFx#capturerApresPreparation] attend le chargement
/// asynchrone de l'`AudioView` via une boucle d'événements imbriquée ([AttenteAudio]), après quoi la
/// Headless Platform JavaFX 26 refuse un nouveau `Stage` — donc pas deux captures « audio » dans le même
/// processus. Ce socle factorise tout le reste.
final class GraineSonsValidation {

    static final String RACINE_DEMO = "/home/observateur/VigieChiro";
    static final String ID_UTILISATEUR = "demo-enseignant";
    static final String ENREGISTREUR = "1925492";
    static final String NUMERO_CARRE = "640380";
    static final String CODE_POINT = "A1";
    static final String NOM_SITE = "Étang de la Tuilière";

    private GraineSonsValidation() {}

    /// Prépare l'espace de travail, migre le schéma et seede la base de démo. Renvoie l'injecteur partiel
    /// prêt à charger la vue. À appeler sur le thread JavaFX (comme les `main` de capture).
    static Injector preparer() throws IOException {
        Path workspace = Files.createTempDirectory("vc-capture-sons-validation");
        System.setProperty("vigiechiro.workspace", workspace.toString());
        Injector injecteur = creerInjecteur();
        SourceDeDonnees source = injecteur.getInstance(SourceDeDonnees.class);
        new MigrationSchema(source).migrer();
        Graine graine = seeder(source, workspace);
        seederReferences(source, graine);
        return injecteur;
    }

    /// Dossier de sortie des PNG (`.github/assets` par défaut, surchargé par `-Dcapture.outDir`).
    static Path dossierSortie() {
        return Path.of(System.getProperty("capture.outDir", ".github/assets"));
    }

    /// Injecteur (partiel) partagé par les outils de capture de la vue audio. Exposé pour le garde-fou de
    /// câblage (test) via le `creerInjecteur()` de chaque `main`.
    static Injector creerInjecteur() {
        return Guice.createInjector(
                new CommunModule(),
                new PersistenceModule(),
                new PassageModule(),
                new ValidationModule(),
                new BibliothequeModule(),
                new ModuleCaptureNavigationAudio(),
                new AbstractModule() {
                    @Provides
                    AudioViewModel viewModel(
                            ServiceValidation validation,
                            ValidationManuelle validationManuelle,
                            RevueEnLot revueEnLot,
                            ServiceBibliotheque bibliotheque) {
                        return new AudioViewModel(validation, validationManuelle, revueEnLot, bibliotheque);
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
                        return (filtres, afficherCarte) -> {};
                    }

                    @Provides
                    OuvrirMultisite ouvrirMultisite() {
                        return numeroCarre -> {};
                    }
                });
    }

    /// Charge `SonsValidation.fxml`, ouvre la vue sur la source `References` et la rend hors-écran dans un
    /// PNG. La `preparation` (spécifique à chaque capture : activer un filtre, afficher des colonnes…)
    /// s'exécute **après** que la scène est montrée et layoutée, **avant** de sélectionner la première ligne
    /// (pour peupler le détail et les mesures) et d'attendre le chargement de l'`AudioView` (spectrogramme
    /// déterministe). La table et l'`AudioView` vivent dans un `SplitPane` : leur skin n'attache les nœuds
    /// qu'au layout, d'où cette préparation tardive.
    static void rendre(Injector injecteur, Path fichier, double largeur, Consumer<Parent> preparation) {
        FXMLLoader loader = new FXMLLoader(SonsValidationController.class.getResource("SonsValidation.fxml"));
        loader.setControllerFactory(injecteur::getInstance);
        Parent vue = charger(loader);
        SonsValidationController controleur = loader.getController();
        controleur.ouvrirSur(new SourceObservations.References(ID_UTILISATEUR));
        Scene scene = new Scene(vue, largeur, 720);

        ApercuFx.capturerApresPreparation(
                scene,
                () -> {
                    preparation.accept(vue); // l'appelant sélectionne la/les ligne(s) et applique ses réglages
                    if (vue.lookup("#audioView") instanceof AudioView audio) {
                        audio.setMinHeight(340); // place pour spectrogramme + sonogramme
                        audio.setPrefHeight(340);
                        AttenteAudio.attendreChargement(audio);
                    }
                },
                fichier);
        System.out.println("Apercu ecrit dans " + fichier.toAbsolutePath());
    }

    /// Sélectionne une ou plusieurs lignes de la table (par index) et **donne le focus** à la table, pour que
    /// les lignes retenues ressortent en surbrillance accentuée sur la capture. La sélection de la ligne
    /// « courante » déclenche aussi le chargement audio et le calcul des mesures.
    static void selectionner(Parent vue, int... indices) {
        if (vue.lookup("#tableObservations") instanceof TableView<?> table) {
            table.getSelectionModel().clearSelection();
            for (int index : indices) {
                if (index < table.getItems().size()) {
                    table.getSelectionModel().select(index);
                }
            }
            table.requestFocus();
        }
    }

    private static Parent charger(FXMLLoader loader) {
        try {
            return loader.load();
        } catch (IOException echec) {
            throw new UncheckedIOException("Chargement de SonsValidation.fxml impossible", echec);
        }
    }

    /// Seede un passage déposé avec sa session et trois séquences (`seqA/B/C_000.wav`). Écrit pour chaque
    /// séquence un **vrai WAV** de démonstration (cris FM, cf. [SonDemo]) sous le `workspace` temporaire,
    /// afin que l'`AudioView` affiche un spectrogramme réel sur la séquence écoutée.
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
                seq.get(0), 45, "Pippip", "Nyclei", "Cri social typique, capté en fin de nuit.", idResultats));
        observationDao.insert(reference(seq.get(1), 47, "Pippip", null, null, idResultats));
        observationDao.insert(
                reference(seq.get(2), 108, "Rhihip", "Rhihip", "Excellent rapport signal sur bruit.", idResultats));
    }

    private static Observation reference(
            long idSequence,
            int frequenceKHz,
            String taxonTadarida,
            String taxonObservateur,
            String commentaire,
            long idResultats) {
        return new Observation(
                null,
                idSequence,
                0.5,
                3.8,
                frequenceKHz,
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

    /// Insère en SQL brut un site et son point d'écoute (cibles de clé étrangère du passage), sans les DAO
    /// de la feature `sites` (que `audio` ne doit pas dépendre, cycle ArchUnit). Renvoie l'`id` du point.
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
