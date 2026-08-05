package fr.univ_amu.iut.saison.outils;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.util.Modules;
import fr.univ_amu.iut.commun.di.RacineInjecteur;
import fr.univ_amu.iut.commun.model.Commune;
import fr.univ_amu.iut.commun.model.Horloge;
import fr.univ_amu.iut.commun.model.HorlogeFigee;
import fr.univ_amu.iut.commun.model.Protocole;
import fr.univ_amu.iut.commun.model.StatutWorkflow;
import fr.univ_amu.iut.commun.model.Utilisateur;
import fr.univ_amu.iut.commun.model.Verdict;
import fr.univ_amu.iut.commun.model.dao.UtilisateurDao;
import fr.univ_amu.iut.commun.outils.ApercuFx;
import fr.univ_amu.iut.commun.outils.ModuleCaptureCommun;
import fr.univ_amu.iut.commun.persistence.MigrationSchema;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.passage.model.Campagne;
import fr.univ_amu.iut.passage.model.Enregistreur;
import fr.univ_amu.iut.passage.model.Passage;
import fr.univ_amu.iut.passage.model.dao.CampagneDao;
import fr.univ_amu.iut.passage.model.dao.EnregistreurDao;
import fr.univ_amu.iut.passage.model.dao.PassageDao;
import fr.univ_amu.iut.passage.model.dao.PassageOpportunisteDao;
import fr.univ_amu.iut.saison.view.SaisonController;
import fr.univ_amu.iut.sites.model.PointDEcoute;
import fr.univ_amu.iut.sites.model.Site;
import fr.univ_amu.iut.sites.model.dao.PointCommuneDao;
import fr.univ_amu.iut.sites.model.dao.PointDao;
import fr.univ_amu.iut.sites.model.dao.SiteDao;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;

/// Outil de capture/mesure, utilisable tel quel.
///
/// Capture l'écran **M-Saison** (solde de la saison) en PNG (`apercu-saison.png`) pour le comparer à la
/// maquette du brief : une ligne par point suivi, l'état des deux passages en pastilles (couleurs
/// reprises du modèle), et la colonne « reste à faire ».
///
/// On seede une base SQLite temporaire via les **DAO réels** (`sites` + `passage`, dépendance déjà
/// portée par la feature) : un utilisateur (qui devient l'utilisateur courant), deux sites
/// PointFixeStandard et quatre points aux états variés (déposé, prêt à déposer, inexploitable, second
/// passage manquant). L'**horloge est figée** au 20/07/2026 pour que la saison courante et les échéances
/// de fenêtre soient déterministes, quel que soit le jour où la galerie est régénérée.
///
/// Lancement headless : `.github/assets/capture-screenshots.sh` (Headless Platform JavaFX 26).
public final class CaptureSaison {

    private static final String ID_UTILISATEUR = "demo-enseignant";
    private static final String ENREGISTREUR = "1925492";
    private static final String FXML = "Saison.fxml";
    private static final LocalDate AUJOURDHUI = LocalDate.of(2026, 7, 20);

    private CaptureSaison() {}

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
        Path workspace = Files.createTempDirectory("vc-capture-saison");
        System.setProperty("vigiechiro.workspace", workspace.toString());
        Path sortie = Path.of(System.getProperty("capture.outDir", ".github/assets"));

        Injector injecteur = creerInjecteur();
        SourceDeDonnees source = injecteur.getInstance(SourceDeDonnees.class);
        new MigrationSchema(source).migrer();
        seeder(source);

        FXMLLoader loader = new FXMLLoader(SaisonController.class.getResource(FXML));
        loader.setControllerFactory(injecteur::getInstance);
        Parent vue = loader.load();
        Path fichier = sortie.resolve("apercu-saison.png");
        // 1180 depuis #3313 : « Nom du carré » (#3289) puis « Commune » ont ajouté 290 px, et la table
        // débordait à chaque fois - « Reste à faire », qui est la raison d être de l écran, se
        // faisait tronquer. Vérifié sur capture les deux fois. 1180 est une largeur que le dépôt
        // emploie déjà ; cet aperçu en était le plus étroit avant #3289.
        ApercuFx.enregistrerPng(new Scene(vue, 1180, 520), fichier);
        System.out.println("Apercu ecrit dans " + fichier.toAbsolutePath());
    }

    /// Injecteur de l'outil : la composition **complète** de l'application, surchargée par des exécuteurs
    /// synchrones et une horloge figée. La liste de modules à la main oubliait `CampagneModule` (#2610),
    /// et le sélecteur de campagne ne se rendait pas. `creerInjecteur` est exposée pour le garde-fou de
    /// câblage des captures.
    public static Injector creerInjecteur() {
        return Guice.createInjector(Modules.override(RacineInjecteur.modules())
                .with(ModuleCaptureCommun.executeursSynchrones(), new AbstractModule() {
                    @Override
                    protected void configure() {
                        bind(Horloge.class).toInstance(new HorlogeFigee(AUJOURDHUI));
                    }
                }));
    }

    /// Seede l'utilisateur courant, deux sites PointFixeStandard et quatre points aux états variés,
    /// pour montrer chaque cas de la colonne « reste à faire ».
    private static void seeder(SourceDeDonnees source) {
        new UtilisateurDao(source).insert(new Utilisateur(ID_UTILISATEUR, "Capitaine Chiro (demo)"));
        new EnregistreurDao(source).insert(new Enregistreur(ENREGISTREUR, "V1.01", null));
        SiteDao siteDao = new SiteDao(source);
        PointDao pointDao = new PointDao(source);
        PassageDao passageDao = new PassageDao(source);

        Site etang = siteDao.insert(new Site(
                null, "640380", "Étang de la Tuilière", Protocole.STANDARD, null, "2026-01-01", ID_UTILISATEUR));
        Site chenes = siteDao.insert(
                new Site(null, "640381", "Bois des Chênes", Protocole.STANDARD, null, "2026-01-01", ID_UTILISATEUR));

        // Deux campagnes (#2610) : le sélecteur de l'écran a de quoi proposer, et la capture montre le
        // contrôle plutôt qu'une barre où il serait retiré faute de campagne.
        CampagneDao campagneDao = new CampagneDao(source);
        campagneDao.insert(new Campagne(null, "Suivi ENS 2026", 2026, null));
        campagneDao.insert(new Campagne(null, "Thèse Samuel", 2025, null));

        Long a1 = pointDao.insert(new PointDEcoute(null, "A1", null, null, null, etang.id()))
                .id();
        Long b2 = pointDao.insert(new PointDEcoute(null, "B2", null, null, null, etang.id()))
                .id();
        Long c1 = pointDao.insert(new PointDEcoute(null, "C1", null, null, null, chenes.id()))
                .id();
        Long d1 = pointDao.insert(new PointDEcoute(null, "D1", null, null, null, chenes.id()))
                .id();

        // Les communes (#3313) : trois points sur quatre en ont une, D1 non. Une capture où TOUS en
        // auraient une ne montrerait pas la cellule vide, qui est l'état normal d'un point sans GPS -
        // c'est le défaut qu'on avait laissé passer sur la colonne « Commune » de Carte & passages, et
        // qu'il avait fallu corriger à la clôture du lot 3.
        PointCommuneDao communes = new PointCommuneDao(source);
        communes.definir(a1, new Commune("Ahetze", "64014"));
        communes.definir(b2, new Commune("Ahetze", "64014"));
        communes.definir(c1, new Commune("Bidart", "64125"));

        // A1 : passage 1 déposé, passage 2 prêt à déposer → « Téléverser la nuit du 21/08 ».
        passage(passageDao, 1, "2026-06-20", StatutWorkflow.DEPOSE, Verdict.OK, a1);
        passage(passageDao, 2, "2026-08-21", StatutWorkflow.PRET_A_DEPOSER, Verdict.OK, a1);
        // B2 : les deux passages déposés → rien.
        passage(passageDao, 1, "2026-06-21", StatutWorkflow.DEPOSE, Verdict.OK, b2);
        passage(passageDao, 2, "2026-08-20", StatutWorkflow.DEPOSE, Verdict.OK, b2);
        // C1 : passage 1 déposé, passage 2 absent → « Poser l'enregistreur avant le 30/09 ».
        passage(passageDao, 1, "2026-06-22", StatutWorkflow.DEPOSE, Verdict.OK, c1);
        // D1 : passage 1 inexploitable → « Refaire le 1er passage ».
        passage(passageDao, 1, "2026-06-23", StatutWorkflow.VERIFIE, Verdict.A_JETER, d1);
        // E3 : nuit OPPORTUNISTE (#2525), réalisée sur le carré d'un tiers. Elle est hors protocole :
        // pastille « Opportuniste », hors décompte, et aucun « reste à faire ». Sans elle, la capture
        // ne montrerait pas un état que la documentation décrit.
        Long e3 = pointDao.insert(new PointDEcoute(null, "E3", null, null, null, chenes.id()))
                .id();
        long idOpportuniste = passage(passageDao, 1, "2026-07-04", StatutWorkflow.DEPOSE, Verdict.OK, e3);
        new PassageOpportunisteDao(source).marquer(idOpportuniste);
    }

    /// Sème un passage et renvoie son identifiant (utile pour le marquer ensuite, cf. la nuit
    /// opportuniste ci-dessus).
    private static long passage(
            PassageDao dao, int numero, String date, StatutWorkflow statut, Verdict verdict, Long idPoint) {
        return dao.insert(new Passage(
                        null,
                        numero,
                        2026,
                        date,
                        "20:25:00",
                        "07:47:00",
                        null,
                        statut,
                        verdict,
                        null,
                        null,
                        null,
                        idPoint,
                        ENREGISTREUR,
                        null))
                .id();
    }
}
