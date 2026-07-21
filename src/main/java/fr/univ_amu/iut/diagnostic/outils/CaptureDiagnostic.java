package fr.univ_amu.iut.diagnostic.outils;

import com.google.inject.Guice;
import com.google.inject.Injector;
import fr.univ_amu.iut.commun.di.PersistenceModule;
import fr.univ_amu.iut.commun.model.JsonSimple;
import fr.univ_amu.iut.commun.model.Protocole;
import fr.univ_amu.iut.commun.model.StatutWorkflow;
import fr.univ_amu.iut.commun.model.Utilisateur;
import fr.univ_amu.iut.commun.model.Verdict;
import fr.univ_amu.iut.commun.model.dao.UtilisateurDao;
import fr.univ_amu.iut.commun.outils.ApercuFx;
import fr.univ_amu.iut.commun.outils.ModuleCaptureCommun;
import fr.univ_amu.iut.commun.persistence.MigrationSchema;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.commun.viewmodel.ContextePassage;
import fr.univ_amu.iut.commun.viewmodel.ContexteSite;
import fr.univ_amu.iut.diagnostic.di.DiagnosticModule;
import fr.univ_amu.iut.diagnostic.view.DiagnosticController;
import fr.univ_amu.iut.passage.di.PassageModule;
import fr.univ_amu.iut.passage.model.Enregistreur;
import fr.univ_amu.iut.passage.model.JournalDuCapteur;
import fr.univ_amu.iut.passage.model.Passage;
import fr.univ_amu.iut.passage.model.ReleveClimatique;
import fr.univ_amu.iut.passage.model.SessionDEnregistrement;
import fr.univ_amu.iut.passage.model.dao.EnregistreurDao;
import fr.univ_amu.iut.passage.model.dao.JournalDuCapteurDao;
import fr.univ_amu.iut.passage.model.dao.PassageDao;
import fr.univ_amu.iut.passage.model.dao.ReleveClimatiqueDao;
import fr.univ_amu.iut.passage.model.dao.SessionDao;
import fr.univ_amu.iut.sites.di.SitesModule;
import fr.univ_amu.iut.sites.model.PointDEcoute;
import fr.univ_amu.iut.sites.model.Site;
import fr.univ_amu.iut.sites.model.dao.PointDao;
import fr.univ_amu.iut.sites.model.dao.SiteDao;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;

/// Outil de capture/mesure, utilisable tel quel.
///
/// Capture l'écran M-Diagnostic en PNG pour le comparer à la maquette du brief, en **quatre états**
/// afin d'en montrer les particularités :
///
/// - `apercu-diagnostic.png` : **relevé présent**, nuit **cohérente** (horaires dans la fenêtre
///   nocturne, aucune alerte), courbe climatique (T°/hygrométrie de la nuit), anomalies (R19 : réveil
///   non programmé, batterie faible), évènements, cohérence horaire et GPS ;
/// - `apercu-diagnostic-sans-releve.png` : **relevé absent** (R20), l'absence de sonde est signalée,
///   le graphe est vide mais les anomalies du journal restent affichées ;
/// - `apercu-diagnostic-sans-gps.png` : **point sans coordonnées GPS**, le repère GPS passe à « non
///   renseigné » et l'encart cohérence horaires disparaît (calcul impossible sans coordonnées) ;
/// - `apercu-diagnostic-hors-nuit.png` : **enregistrement hors nuit** (démarrage avant le coucher et
///   arrêt après le lever du soleil), l'alerte de cohérence horaire s'affiche - couleur et icône
///   posées par `LibelleRetour` depuis la sévérité, un état migré par #2050 que rien ne montrait de
///   façon dédiée (#2222).
///
/// On seede une base SQLite temporaire via les **DAO réels** (`diagnostic → passage/sites`, autorisé)
/// : un point géolocalisé et un point sans GPS, trois passages déposés avec leur session et leur
/// journal du capteur ; les passages « avec relevé » et « sans GPS » reçoivent en plus un **THLog
/// synthétique** (écrit dans le workspace, relu par le service comme à l'import). Chaque vue est
/// chargée via une `controllerFactory` Guice (socle + sites + passage + diagnostic) et rendue
/// hors-écran par [ApercuFx].
///
/// **Déterminisme** : l'écran n'affiche que la série climatique, les anomalies et le GPS — aucun
/// chemin de fichier, donc aucune dépendance au dossier temporaire.
///
/// Lancement headless : `.github/assets/capture-screenshots.sh` (Headless Platform JavaFX 26).
public final class CaptureDiagnostic {

    private static final String ID_UTILISATEUR = "demo-enseignant";
    private static final String SERIE = "1925492";

    /// Horaires **nominaux** : l'enregistrement tient dans la fenêtre nocturne (démarrage après le
    /// coucher, arrêt avant le lever), donc **aucune** alerte de cohérence horaire. C'est l'état que
    /// montre la capture principale, désormais une nuit cohérente (#2222).
    private static final String DEBUT_NOMINAL = "22:00:00";

    private static final String FIN_NOMINALE = "06:00:00";

    /// Horaires **hors nuit** : démarrage avant le coucher du soleil et arrêt après le lever - une
    /// partie de l'enregistrement est diurne. Déclenche l'alerte « hors nuit » que la capture dédiée
    /// `apercu-diagnostic-hors-nuit.png` met en évidence (#2222).
    private static final String DEBUT_HORS_NUIT = "20:25:00";

    private static final String FIN_HORS_NUIT = "07:47:00";

    /// THLog synthétique : une nuit de juin, refroidissement de 19→11 °C et hygrométrie montante
    /// (entête comprise, séparateur tabulation, comme `PaRecPR<sn>_THLog.csv`).
    private static final List<String> THLOG = List.of(
            "Date\tHour\tTemperature\tHumidity",
            "22/06/2026\t20:30:00\t+19.2\t62",
            "22/06/2026\t21:30:00\t+17.8\t66",
            "22/06/2026\t22:30:00\t+16.1\t70",
            "22/06/2026\t23:30:00\t+14.9\t74",
            "23/06/2026\t00:30:00\t+13.8\t78",
            "23/06/2026\t01:30:00\t+13.0\t81",
            "23/06/2026\t02:30:00\t+12.3\t83",
            "23/06/2026\t03:30:00\t+11.7\t85",
            "23/06/2026\t04:30:00\t+11.2\t87",
            "23/06/2026\t05:30:00\t+11.9\t84");

    private CaptureDiagnostic() {}

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

    /// Identifiant volontairement absent de la base : le chargement échoue et l'écran rend son bandeau.
    private static final long PASSAGE_INEXISTANT = 999_999L;

    private static void capturer() throws IOException {
        Path workspace = Files.createTempDirectory("vc-capture-diagnostic");
        System.setProperty("vigiechiro.workspace", workspace.toString());
        Path sortie = Path.of(System.getProperty("capture.outDir", ".github/assets"));

        Injector injecteur = creerInjecteur();
        SourceDeDonnees source = injecteur.getInstance(SourceDeDonnees.class);
        new MigrationSchema(source).migrer();

        Graine graine = seeder(source, workspace);

        rendre(injecteur, graine.idAvecReleve(), sortie.resolve("apercu-diagnostic.png"));
        rendre(injecteur, graine.idSansReleve(), sortie.resolve("apercu-diagnostic-sans-releve.png"));
        rendre(injecteur, graine.idSansGps(), sortie.resolve("apercu-diagnostic-sans-gps.png"));
        // État migré (#2050) que rien ne montrait de façon dédiée (#2222) : un enregistrement hors nuit
        // fait apparaître l'alerte de cohérence horaire, rendue par LibelleRetour depuis la sévérité.
        rendre(injecteur, graine.idHorsNuit(), sortie.resolve("apercu-diagnostic-hors-nuit.png"));
        // Bandeau de retour (#1917) : jusqu'ici AUCUN aperçu ne montrait de bandeau sur AUCUN écran migré
        // - on ne vérifiait que « rien n'est déplacé » quand il est absent. Ouvrir sur un passage
        // inexistant produit le cas réel sans mock : le chargement échoue et l'écran le dit.
        rendre(injecteur, PASSAGE_INEXISTANT, sortie.resolve("apercu-diagnostic-retour.png"));
    }

    /// Injecteur (partiel) utilisé par cet outil de capture. Exposé pour le garde-fou de câblage
    /// (test).
    public static Injector creerInjecteur() {
        return Guice.createInjector(
                ModuleCaptureCommun.communSynchrone(),
                new PersistenceModule(),
                new SitesModule(),
                new PassageModule(),
                new DiagnosticModule());
    }

    /// Charge `Diagnostic.fxml`, l'ouvre sur le passage puis rend la scène hors-écran en PNG.
    private static void rendre(Injector injecteur, long idPassage, Path fichier) throws IOException {
        FXMLLoader loader = new FXMLLoader(DiagnosticController.class.getResource("Diagnostic.fxml"));
        loader.setControllerFactory(injecteur::getInstance);
        Parent vue = loader.load();
        DiagnosticController controleur = loader.getController();
        // Capture hors-chrome : le fil d'Ariane n'est pas rendu ; le contexte n'a donc pas à être réel.
        controleur.ouvrirSur(new ContextePassage(idPassage, 1, new ContexteSite("640380", "A1", null)));
        ApercuFx.enregistrerPng(new Scene(vue, 1000, 660), fichier);
        System.out.println("Apercu ecrit dans " + fichier.toAbsolutePath());
    }

    /// Seede un site, un point géolocalisé et un point sans GPS, et quatre passages déposés (chacun
    /// session + journal) : nominal avec relevé, sans relevé, sans GPS, et hors nuit avec relevé. Les
    /// passages « avec relevé », « sans GPS » et « hors nuit » reçoivent un THLog synthétique. Renvoie
    /// les quatre identifiants.
    private static Graine seeder(SourceDeDonnees source, Path workspace) throws IOException {
        new UtilisateurDao(source).insert(new Utilisateur(ID_UTILISATEUR, "Capitaine Chiro (demo)"));
        new EnregistreurDao(source).insert(new Enregistreur(SERIE, "V1.01", null));
        SiteDao siteDao = new SiteDao(source);
        PointDao pointDao = new PointDao(source);
        PassageDao passageDao = new PassageDao(source);
        SessionDao sessionDao = new SessionDao(source);
        JournalDuCapteurDao journalDao = new JournalDuCapteurDao(source);
        ReleveClimatiqueDao releveDao = new ReleveClimatiqueDao(source);

        // Même carré que le contexte ouvert plus haut (640380, dépt 64) : site, GPS et nom de session cohérents.
        Site site = siteDao.insert(new Site(
                null, "640380", "Étang de la Tuilière", Protocole.STANDARD, null, "2026-01-01", ID_UTILISATEUR));
        Long idPoint = pointDao.insert(new PointDEcoute(null, "A1", 43.4010, -1.5740, "lisière", site.id()))
                .id();
        // Point NON géolocalisé (#1673) : sert la 3e capture (GPS non renseigné → cohérence indisponible).
        Long idPointSansGps = pointDao.insert(new PointDEcoute(null, "A2", null, null, "clairière", site.id()))
                .id();

        long idAvecReleve = passageAvecJournal(
                passageDao, sessionDao, journalDao, idPoint, 2, "2026-06-22", DEBUT_NOMINAL, FIN_NOMINALE);
        Path thlog = workspace.resolve("PaRecPR" + SERIE + "_THLog.csv");
        Files.writeString(thlog, String.join("\n", THLOG) + "\n", StandardCharsets.UTF_8);
        rattacherReleve(releveDao, sessionDao, idAvecReleve, thlog);

        long idSansReleve = passageAvecJournal(
                passageDao, sessionDao, journalDao, idPoint, 1, "2026-06-08", DEBUT_NOMINAL, FIN_NOMINALE);

        // Relevé présent mais point sans GPS : la courbe s'affiche, le repère GPS passe à « non
        // renseigné » et l'encart cohérence horaires disparaît (calcul impossible sans coordonnées).
        long idSansGps = passageAvecJournal(
                passageDao, sessionDao, journalDao, idPointSansGps, 3, "2026-06-24", DEBUT_NOMINAL, FIN_NOMINALE);
        rattacherReleve(releveDao, sessionDao, idSansGps, thlog);

        // Passage dédié à l'alerte « hors nuit » (#2222) : géolocalisé (le calcul exige un GPS) et
        // relevé présent (écran complet), mais des horaires qui débordent la fenêtre nocturne.
        long idHorsNuit = passageAvecJournal(
                passageDao, sessionDao, journalDao, idPoint, 4, "2026-06-20", DEBUT_HORS_NUIT, FIN_HORS_NUIT);
        rattacherReleve(releveDao, sessionDao, idHorsNuit, thlog);

        return new Graine(idAvecReleve, idSansReleve, idSansGps, idHorsNuit);
    }

    /// Rattache le relevé climatique `thlog` à la session du passage `idPassage`.
    private static void rattacherReleve(
            ReleveClimatiqueDao releveDao, SessionDao sessionDao, long idPassage, Path thlog) {
        Long idSession = sessionDao.trouverParPassage(idPassage).orElseThrow().id();
        releveDao.insert(new ReleveClimatique(null, thlog.toString(), null, idSession));
    }

    /// Insère un passage déposé, sa session et un journal du capteur (mêmes anomalies/évènements pour
    /// les deux états) ; renvoie l'identifiant du passage.
    private static long passageAvecJournal(
            PassageDao passageDao,
            SessionDao sessionDao,
            JournalDuCapteurDao journalDao,
            Long idPoint,
            int numero,
            String date,
            String heureDebut,
            String heureFin) {
        Passage passage = passageDao.insert(new Passage(
                null,
                numero,
                2026,
                date,
                heureDebut,
                heureFin,
                null,
                StatutWorkflow.DEPOSE,
                Verdict.OK,
                null,
                "{\"tempDebut\":8.5}", // météo de début de nuit (#106) : montre la valeur sur l'aperçu
                null,
                idPoint,
                SERIE));
        Long idSession = sessionDao
                .insert(new SessionDEnregistrement(
                        null, "Car640380-2026-Pass" + numero + "-A1", null, null, passage.id()))
                .id();
        List<String> evenements = List.of("### Démarrage PR" + SERIE, "Arrêt programmé à 06:00:00");
        List<String> anomalies = List.of(
                "Réveil non programmé : 23/06/26 - 03:12:00 PR" + SERIE + " Wakeup",
                "Batterie faible (18%) : Batteries internes 18%");
        journalDao.insert(new JournalDuCapteur(
                null,
                "LogPR" + SERIE + ".txt",
                JsonSimple.tableau(evenements),
                JsonSimple.tableau(anomalies),
                idSession));
        return passage.id();
    }

    private record Graine(long idAvecReleve, long idSansReleve, long idSansGps, long idHorsNuit) {}
}
