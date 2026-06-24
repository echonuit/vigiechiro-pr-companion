package fr.univ_amu.iut.qualification.outils;

import com.google.inject.Guice;
import com.google.inject.Injector;
import fr.univ_amu.iut.commun.di.CommunModule;
import fr.univ_amu.iut.commun.di.PersistenceModule;
import fr.univ_amu.iut.commun.model.Prefixe;
import fr.univ_amu.iut.commun.model.Protocole;
import fr.univ_amu.iut.commun.model.StatutWorkflow;
import fr.univ_amu.iut.commun.model.Utilisateur;
import fr.univ_amu.iut.commun.model.Verdict;
import fr.univ_amu.iut.commun.model.dao.UtilisateurDao;
import fr.univ_amu.iut.commun.outils.ApercuFx;
import fr.univ_amu.iut.commun.persistence.MigrationSchema;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.commun.view.OuvrirSite;
import fr.univ_amu.iut.commun.viewmodel.ContextePassage;
import fr.univ_amu.iut.commun.viewmodel.ContexteSite;
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
import fr.univ_amu.iut.qualification.di.QualificationModule;
import fr.univ_amu.iut.qualification.view.QualificationController;
import fr.univ_amu.iut.qualification.viewmodel.QualificationViewModel;
import fr.univ_amu.iut.qualification.viewmodel.SelectionEcouteViewModel;
import fr.univ_amu.iut.sites.di.SitesModule;
import fr.univ_amu.iut.sites.model.PointDEcoute;
import fr.univ_amu.iut.sites.model.Site;
import fr.univ_amu.iut.sites.model.dao.PointDao;
import fr.univ_amu.iut.sites.model.dao.SiteDao;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;

/// Outil de capture/mesure, utilisable tel quel.
///
/// Capture l'écran M-Qualification en PNG, pour le comparer à la maquette du brief. Pour montrer le
/// « cas standard » de la maquette (sélection de 30 séquences, 12 déjà écoutées, verdict OK
/// pré-sélectionné mais non enregistré), on seede une nuit complète puis on pilote les deux
/// ViewModel :
///
/// 1. base SQLite temporaire seedée : un utilisateur, un site/point, un passage `TRANSFORME` et une
///    session de **60 enregistrements** horodatés sur toute la nuit (20:25 → ~08:13) et nommés
///    selon le préfixe R6 — de quoi obtenir un pré-check **tout au vert** (≥ 50 fichiers, noms
///    conformes, couverture complète) ;
/// 2. injecteur Guice (socle + sites + passage + qualification) ;
/// 3. la vue est chargée avec une `controllerFactory` qui injecte deux VM connus, qu'on pilote
///    ensuite (ouverture, sélection de 30, marquage de 12 écoutées, verdict OK) avant le rendu
///    hors-écran par [ApercuFx].
///
/// Lancement headless : `.github/assets/capture-screenshots.sh` (Headless Platform JavaFX 26).
public final class CaptureQualification {

    private static final String ID_UTILISATEUR = "demo-enseignant";
    private static final String ENREGISTREUR = "1925492";
    private static final String QUALIF_FXML = "/fr/univ_amu/iut/qualification/view/Qualification.fxml";
    private static final String NUMERO_CARRE = "640380";
    private static final String CODE_POINT = "A1";
    private static final Prefixe PREFIXE = new Prefixe(NUMERO_CARRE, 2026, 2, CODE_POINT);
    private static final int NB_ENREGISTREMENTS = 60;
    private static final int TAILLE_SELECTION = 30;
    private static final int NB_ECOUTEES = 12;

    private CaptureQualification() {}

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
        Path workspace = Files.createTempDirectory("vc-capture-qualif");
        System.setProperty("vigiechiro.workspace", workspace.toString());
        Path sortie = Path.of(System.getProperty("capture.outDir", ".github/assets"));

        Injector injecteur = Guice.createInjector(
                new CommunModule(),
                new PersistenceModule(),
                new SitesModule(),
                new PassageModule(),
                new QualificationModule());
        SourceDeDonnees source = injecteur.getInstance(SourceDeDonnees.class);
        new MigrationSchema(source).migrer();
        long idPassage = seeder(source, workspace);

        QualificationViewModel verdictVm = injecteur.getInstance(QualificationViewModel.class);
        SelectionEcouteViewModel selectionVm = injecteur.getInstance(SelectionEcouteViewModel.class);
        FXMLLoader loader = new FXMLLoader(CaptureQualification.class.getResource(QUALIF_FXML));
        loader.setControllerFactory(type -> type == QualificationController.class
                ? new QualificationController(
                        verdictVm, selectionVm, (id, contexte) -> {}, injecteur.getInstance(OuvrirSite.class))
                : injecteur.getInstance(type));
        Parent vue = loader.load();
        QualificationController controleur = loader.getController();

        Scene scene = new Scene(vue, 1240, 900);
        // Capture hors-chrome : le fil d'Ariane n'est pas rendu ; le contexte n'a donc pas à être réel.
        controleur.ouvrirSur(new ContextePassage(idPassage, 1, new ContexteSite(NUMERO_CARRE, CODE_POINT, null)));
        selectionVm.tailleProperty().set(TAILLE_SELECTION);
        selectionVm.regenerer();

        // État initial : sélection générée, aucune séquence écoutée, aucun verdict posé.
        Path initial = sortie.resolve("apercu-qualification-initial.png");
        ApercuFx.enregistrerPng(scene, initial);
        System.out.println("Apercu ecrit dans " + initial.toAbsolutePath());

        // État avancé : quelques séquences écoutées (progression) et verdict OK posé.
        for (int i = 0; i < NB_ECOUTEES && i < selectionVm.lignes().size(); i++) {
            selectionVm.selectionner(selectionVm.lignes().get(i));
            selectionVm.marquerCouranteEcoutee();
        }
        if (selectionVm.lignes().size() > NB_ECOUTEES) {
            selectionVm.selectionner(selectionVm.lignes().get(NB_ECOUTEES)); // séquence courante à écouter
        }
        verdictVm.choisirVerdict(Verdict.OK);

        Path fichier = sortie.resolve("apercu-qualification.png");
        ApercuFx.enregistrerPng(scene, fichier);
        System.out.println("Apercu ecrit dans " + fichier.toAbsolutePath());
    }

    /// Seede une nuit complète (chemins sous le `workspace` temporaire) et renvoie l'identifiant du
    /// passage à vérifier.
    private static long seeder(SourceDeDonnees source, Path workspace) {
        new UtilisateurDao(source).insert(new Utilisateur(ID_UTILISATEUR, "Capitaine Chiro (demo)"));
        SiteDao siteDao = new SiteDao(source);
        PointDao pointDao = new PointDao(source);
        PassageDao passageDao = new PassageDao(source);
        SessionDao sessionDao = new SessionDao(source);
        EnregistrementOriginalDao originalDao = new EnregistrementOriginalDao(source);
        SequenceDao sequenceDao = new SequenceDao(source);
        new EnregistreurDao(source).insert(new Enregistreur(ENREGISTREUR, "V1.01", null));

        Site site = siteDao.insert(new Site(
                null,
                NUMERO_CARRE,
                "Étang de la Tuilière",
                Protocole.STANDARD,
                "Aix-en-Provence",
                "2026-01-01",
                ID_UTILISATEUR));
        PointDEcoute point =
                pointDao.insert(new PointDEcoute(null, "A1", 43.5298, 5.4474, "Près du grand chêne", site.id()));
        Passage passage = passageDao.insert(new Passage(
                null,
                2,
                2026,
                "2026-06-22",
                "20:25:00",
                "07:47:00",
                null,
                StatutWorkflow.TRANSFORME,
                null,
                null,
                null,
                null,
                point.id(),
                ENREGISTREUR));
        SessionDEnregistrement session = sessionDao.insert(new SessionDEnregistrement(
                null, workspace.resolve(PREFIXE.nomDossierSession()).toString(), null, null, passage.id()));

        LocalDateTime debut = LocalDateTime.of(2026, 6, 22, 20, 25, 0);
        DateTimeFormatter horodatage = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
        for (int i = 0; i < NB_ENREGISTREMENTS; i++) {
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
}
