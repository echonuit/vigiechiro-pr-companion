package fr.univ_amu.iut.lot.outils;

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
import fr.univ_amu.iut.commun.viewmodel.ContextePassage;
import fr.univ_amu.iut.commun.viewmodel.ContexteSite;
import fr.univ_amu.iut.lot.di.LotModule;
import fr.univ_amu.iut.lot.model.ServiceLot;
import fr.univ_amu.iut.lot.view.LotController;
import fr.univ_amu.iut.passage.di.PassageModule;
import fr.univ_amu.iut.passage.model.EnregistrementOriginal;
import fr.univ_amu.iut.passage.model.Enregistreur;
import fr.univ_amu.iut.passage.model.JournalDuCapteur;
import fr.univ_amu.iut.passage.model.Passage;
import fr.univ_amu.iut.passage.model.SequenceDEcoute;
import fr.univ_amu.iut.passage.model.SessionDEnregistrement;
import fr.univ_amu.iut.passage.model.dao.EnregistrementOriginalDao;
import fr.univ_amu.iut.passage.model.dao.EnregistreurDao;
import fr.univ_amu.iut.passage.model.dao.JournalDuCapteurDao;
import fr.univ_amu.iut.passage.model.dao.PassageDao;
import fr.univ_amu.iut.passage.model.dao.SequenceDao;
import fr.univ_amu.iut.passage.model.dao.SessionDao;
import fr.univ_amu.iut.sites.di.SitesModule;
import fr.univ_amu.iut.sites.model.PointDEcoute;
import fr.univ_amu.iut.sites.model.Site;
import fr.univ_amu.iut.sites.model.dao.PointDao;
import fr.univ_amu.iut.sites.model.dao.SiteDao;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;

/// Outil de capture/mesure, utilisable tel quel.
///
/// Capture l'écran M-Lot en PNG pour le comparer à la maquette du brief, en **trois états** afin
/// d'en montrer les particularités :
///
/// - `apercu-lot-preparer.png` : passage **Vérifié** cohérent — récap (séquences · volume) + dossier
///   à téléverser, bouton « Préparer le lot » actif ;
/// - `apercu-lot-deposer.png` : passage **Prêt à déposer** (après préparation) — bouton « Marquer
///   déposé » actif ;
/// - `apercu-lot-alertes.png` : passage **Vérifié incohérent** (séquences/journal manquants) — la
///   zone d'alertes de cohérence (R14) apparaît et « Préparer le lot » est désactivé.
///
/// On seede une base SQLite temporaire (deux passages sur un même point : un cohérent, un
/// incohérent), puis on charge l'écran via une `controllerFactory` Guice (socle + sites + passage +
/// lot) et on le rend hors-écran par [ApercuFx]. La feature `lot` dépendant déjà de `sites`
/// (vérification de cohérence), le seed utilise directement les DAO `sites`.
///
/// Lancement headless : `.github/assets/capture-screenshots.sh` (Headless Platform JavaFX 26).
public final class CaptureLot {

    private static final String ID_UTILISATEUR = "demo-enseignant";
    private static final String SERIE = "1925492";
    private static final String NUMERO_CARRE = "040962";
    private static final String CODE_POINT = "A1";
    private static final String NOM_SITE = "Étang de la Tuilière";
    private static final Prefixe PREFIXE = new Prefixe(NUMERO_CARRE, 2026, 1, CODE_POINT);
    private static final Prefixe PREFIXE_INCOHERENT = new Prefixe(NUMERO_CARRE, 2026, 2, CODE_POINT);
    private static final String NOM_ORIGINAL = PREFIXE.nommerOriginal("PaRecPR" + SERIE + "_20260620_213000.wav");
    private static final long VOLUME_SEQUENCES_OCTETS = 180L * 1024 * 1024; // 180 Mo

    /// Racine d'affichage **déterministe** du dossier de session (R22) montrée à l'écran. On
    /// n'utilise pas le `@TempDir` aléatoire : son suffixe se retrouverait dans les PNG commités et
    /// salirait les assets à chaque régénération (le contenu réel n'est de toute façon pas lu).
    private static final String RACINE_DEMO = "/home/observateur/VigieChiro";

    private CaptureLot() {}

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
        Path workspace = Files.createTempDirectory("vc-capture-lot");
        System.setProperty("vigiechiro.workspace", workspace.toString());
        Path sortie = Path.of(System.getProperty("capture.outDir", ".github/assets"));

        Injector injecteur = Guice.createInjector(
                new CommunModule(), new PersistenceModule(), new SitesModule(), new PassageModule(), new LotModule());
        SourceDeDonnees source = injecteur.getInstance(SourceDeDonnees.class);
        new MigrationSchema(source).migrer();
        ServiceLot service = injecteur.getInstance(ServiceLot.class);

        long idPoint = seederSiteEtPoint(source);
        long idCoherent = seederPassageCoherent(source, idPoint);
        long idIncoherent = seederPassageIncoherent(source, idPoint);

        // 1) Vérifié cohérent : « Préparer le lot » actif.
        rendre(injecteur, idCoherent, sortie.resolve("apercu-lot-preparer.png"));
        // 2) Après préparation : Prêt à déposer, « Marquer déposé » actif.
        service.preparerLot(idCoherent);
        rendre(injecteur, idCoherent, sortie.resolve("apercu-lot-deposer.png"));
        // 3) Vérifié incohérent : zone d'alertes (R14), « Préparer » désactivé.
        rendre(injecteur, idIncoherent, sortie.resolve("apercu-lot-alertes.png"));
    }

    /// Charge `Lot.fxml`, l'ouvre sur le passage puis rend la scène hors-écran en PNG.
    private static void rendre(Injector injecteur, long idPassage, Path fichier) throws IOException {
        FXMLLoader loader = new FXMLLoader(LotController.class.getResource("Lot.fxml"));
        loader.setControllerFactory(injecteur::getInstance);
        Parent vue = loader.load();
        LotController controleur = loader.getController();
        // Capture hors-chrome : le fil d'Ariane n'est pas rendu ; le contexte n'a donc pas à être réel.
        controleur.ouvrirSur(new ContextePassage(idPassage, 1, new ContexteSite(NUMERO_CARRE, CODE_POINT, null)));
        ApercuFx.enregistrerPng(new Scene(vue, 980, 600), fichier);
        System.out.println("Apercu ecrit dans " + fichier.toAbsolutePath());
    }

    private static long seederSiteEtPoint(SourceDeDonnees source) {
        new UtilisateurDao(source).insert(new Utilisateur(ID_UTILISATEUR, "Capitaine Chiro (demo)"));
        Site site = new SiteDao(source)
                .insert(new Site(
                        null,
                        NUMERO_CARRE,
                        NOM_SITE,
                        Protocole.STANDARD,
                        "Aix-en-Provence",
                        "2026-01-01",
                        ID_UTILISATEUR));
        return new PointDao(source)
                .insert(new PointDEcoute(null, CODE_POINT, 43.5298, 5.4474, "Près du grand chêne", site.id()))
                .id();
    }

    /// Passage Vérifié entièrement cohérent (6 séquences préfixées + journal) prêt à être préparé.
    private static long seederPassageCoherent(SourceDeDonnees source, long idPoint) {
        new EnregistreurDao(source).insert(new Enregistreur(SERIE, "V1.01", null));
        Passage passage = insererPassage(source, idPoint, 1, Verdict.OK);
        SessionDEnregistrement session = new SessionDao(source)
                .insert(new SessionDEnregistrement(
                        null,
                        RACINE_DEMO + "/" + PREFIXE.nomDossierSession(),
                        5L * 1024 * 1024 * 1024,
                        VOLUME_SEQUENCES_OCTETS,
                        passage.id()));
        long idOriginal = new EnregistrementOriginalDao(source)
                .insert(new EnregistrementOriginal(
                        null, NOM_ORIGINAL, "bruts/" + NOM_ORIGINAL, 12.0, 384000, null, session.id()))
                .id();
        SequenceDao sequenceDao = new SequenceDao(source);
        for (int i = 0; i < 6; i++) {
            String nom = PREFIXE.nommerSequence(NOM_ORIGINAL, i);
            sequenceDao.insert(new SequenceDEcoute(
                    null, nom, idOriginal, i, i * 5.0, 5.0, "transformes/" + nom, true, session.id()));
        }
        new JournalDuCapteurDao(source)
                .insert(new JournalDuCapteur(null, "LogPR" + SERIE + ".txt", null, null, session.id()));
        return passage.id();
    }

    /// Passage Vérifié incohérent : une session sans séquences ni journal → alertes bloquantes (R14).
    private static long seederPassageIncoherent(SourceDeDonnees source, long idPoint) {
        Passage passage = insererPassage(source, idPoint, 2, Verdict.OK);
        new SessionDao(source)
                .insert(new SessionDEnregistrement(
                        null, RACINE_DEMO + "/" + PREFIXE_INCOHERENT.nomDossierSession(), null, null, passage.id()));
        return passage.id();
    }

    private static Passage insererPassage(SourceDeDonnees source, long idPoint, int numero, Verdict verdict) {
        return new PassageDao(source)
                .insert(new Passage(
                        null,
                        numero,
                        2026,
                        "2026-06-20",
                        "21:30:00",
                        "05:15:00",
                        null,
                        StatutWorkflow.VERIFIE,
                        verdict,
                        null,
                        null,
                        null,
                        idPoint,
                        SERIE));
    }
}
