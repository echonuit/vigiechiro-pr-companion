package fr.univ_amu.iut.importation.outils;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.util.Modules;
import fr.univ_amu.iut.commun.di.CommunModule;
import fr.univ_amu.iut.commun.di.PersistenceModule;
import fr.univ_amu.iut.commun.model.Horloge;
import fr.univ_amu.iut.commun.model.HorlogeFigee;
import fr.univ_amu.iut.commun.model.Protocole;
import fr.univ_amu.iut.commun.model.Utilisateur;
import fr.univ_amu.iut.commun.model.dao.UtilisateurDao;
import fr.univ_amu.iut.commun.outils.ApercuFx;
import fr.univ_amu.iut.commun.persistence.MigrationSchema;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.importation.di.ImportationModule;
import fr.univ_amu.iut.importation.model.Progression;
import fr.univ_amu.iut.importation.view.ImportationController;
import fr.univ_amu.iut.importation.viewmodel.ImportationViewModel;
import fr.univ_amu.iut.passage.di.PassageModule;
import fr.univ_amu.iut.sites.di.SitesModule;
import fr.univ_amu.iut.sites.model.ServiceSites;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
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
/// Capture l'assistant M-Import en PNG, pour le comparer a la maquette du brief. Pour montrer le
/// « cas standard » (sections inspection + rattachement remplies, et non l'ecran vide), on pilote
/// directement le [ImportationViewModel] :
///
/// 1. base SQLite temporaire seedee (un utilisateur, un site avec un point) + un dossier
///    d'echantillon (journal LogPR, releve climatique, deux WAV) ;
/// 2. injecteur Guice minimal (socle + sites + passage + importation) avec une [HorlogeFigee] pour
///    une annee de passage deterministe ;
/// 3. la vue est chargee avec une `controllerFactory` qui injecte un VM connu, qu'on pilote ensuite
///    (choix du dossier -> inspection -> selection site/point) avant le rendu hors-ecran par
///    [ApercuFx].
///
/// Lancement headless : `.github/assets/capture-screenshots.sh` (Headless Platform JavaFX 26).
public final class CaptureImport {

    private static final String ID_UTILISATEUR = "demo-enseignant";
    private static final LocalDate REFERENCE = LocalDate.of(2026, 9, 20);
    private static final String IMPORT_FXML = "/fr/univ_amu/iut/importation/view/Importation.fxml";

    private static final String LOG =
            "22/04/26 - 16:02:20 PR1925492 Demarrage Passive Recorder numero de serie 1925492, V1.01,"
                    + " CPU 600000000, T4.1\n"
                    + "22/04/26 - 16:02:21 PR1925492 Sonde temperature/hygrometrie presente, lecture toutes"
                    + " les 600s\n"
                    + "22/04/26 - 16:02:21 PR1925492 Parametres : Acquisi. 20:25-07:47, Fe384kHz, Bd. Freq."
                    + " 8-120kHz\n";

    private CaptureImport() {}

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
        Throwable probleme = erreur.get();
        if (probleme != null) {
            probleme.printStackTrace();
            System.exit(1);
        }
        System.exit(0);
    }

    private static void capturer() throws IOException {
        Path workspace = Files.createTempDirectory("vc-capture-import");
        System.setProperty("vigiechiro.workspace", workspace.toString());
        Path sortie = Path.of(System.getProperty("capture.outDir", ".github/assets"));

        Injector injecteur = creerInjecteur();
        injecteur.getInstance(MigrationSchema.class).migrer();
        seeder(injecteur);
        Path dossierSd = creerDossierEchantillon();

        // VM connu, injecte dans le controller via une controllerFactory dediee, puis piloté pour
        // remplir l'assistant comme le « cas standard » de la maquette.
        ImportationViewModel vm = injecteur.getInstance(ImportationViewModel.class);
        FXMLLoader loader = new FXMLLoader(CaptureImport.class.getResource(IMPORT_FXML));
        loader.setControllerFactory(type ->
                type == ImportationController.class ? new ImportationController(vm) : injecteur.getInstance(type));
        Parent vue = loader.load();
        Scene scene = new Scene(vue, 1100, 860);

        // État « décompression d'un .zip » (#146) : avant toute inspection, la barre de progression
        // déterminée « X / N fichiers » s'affiche avec un temps restant estimé et un bouton « Annuler »
        // (le formulaire est gelé). Le court délai laisse l'ETA s'établir (sinon « ~0 s » à t≈0).
        vm.marquerExtractionEnCours();
        dormir(2500);
        vm.appliquerProgression(
                new Progression("Décompression : 740 / 3692 · PaRecPR1925492_20260423_034512.wav", 0.20));
        rendre(scene, sortie.resolve("apercu-import-decompression.png"));

        // Poser la source ramène l'état à PRET (réinitialisation pour nouveau dossier) ; on inspecte et on
        // rattache pour le « cas standard » de la maquette.
        vm.inspection().dossierSourceProperty().set(dossierSd);
        vm.inspecter();
        if (!vm.rattachement().sites().isEmpty()) {
            vm.rattachement()
                    .siteSelectionneProperty()
                    .set(vm.rattachement().sites().get(0));
        }
        if (!vm.rattachement().points().isEmpty()) {
            vm.rattachement()
                    .pointSelectionneProperty()
                    .set(vm.rattachement().points().get(0));
        }

        rendre(scene, sortie.resolve("apercu-import-assistant.png"));

        // État « import en cours » (#33/#146) : barre de progression déterminée, temps restant estimé,
        // bouton « Annuler », formulaire gelé. Phase de copie en début d'import (l'ETA y est parlant).
        vm.marquerEnCours();
        dormir(2500);
        vm.appliquerProgression(new Progression("Copie 48/191 · PaRecPR1925492_20260422_205518.wav", 0.126));
        rendre(scene, sortie.resolve("apercu-import-en-cours.png"));

        // État « mélange » (#33) : dossier mêlant deux enregistreurs → avertissement à l'inspection
        // (non bloquant). Changer le dossier source réinitialise l'état, on ré-inspecte.
        vm.inspection().dossierSourceProperty().set(creerDossierMelange());
        vm.inspecter();
        rendre(scene, sortie.resolve("apercu-import-melange.png"));

        // État « incohérence » (#33) : le journal (série 1925492, nuit du 22/04) contredit les WAV
        // (série 1648011, nuit du 30/04) → bandeau rouge non bloquant (série ET date).
        vm.inspection().dossierSourceProperty().set(creerDossierIncoherence());
        vm.inspecter();
        rendre(scene, sortie.resolve("apercu-import-incoherence.png"));
    }

    /// Rend `scene` hors-écran en PNG et journalise (helper : évite la répétition du libellé de log,
    /// interdite par PMD `AvoidDuplicateLiterals` au-delà de 3 occurrences).
    private static void rendre(Scene scene, Path fichier) {
        ApercuFx.enregistrerPng(scene, fichier);
        System.out.println("Apercu ecrit dans " + fichier.toAbsolutePath());
    }

    /// Pause (outil de capture uniquement) : laisse s'écouler un peu de temps après le début d'une
    /// opération pour que l'estimation du temps restant (#146) soit représentative sur l'aperçu.
    private static void dormir(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /// Dossier d'exemple **mélangé** (chemin déterministe) : journal + relevé de la série 1925492 mais
    /// deux WAV de **deux enregistreurs** distincts → l'inspection lève l'avertissement « mélange ».
    private static Path creerDossierMelange() throws IOException {
        return creerDossier(
                "vigiechiro-sd-melange",
                "PaRecPR1925492_20260422_203922.wav",
                "PaRecPR1648011_20260422_204326.wav"); // 2e enregistreur
    }

    /// Dossier d'exemple **incohérent** (chemin déterministe) : journal et relevé de la série 1925492
    /// (nuit du 22/04) alors que les WAV portent la série 1648011 et la nuit du 30/04 → l'inspection
    /// lève l'avertissement « incohérence » (série ET date).
    private static Path creerDossierIncoherence() throws IOException {
        return creerDossier(
                "vigiechiro-sd-incoherence",
                "PaRecPR1648011_20260430_203922.wav",
                "PaRecPR1648011_20260430_204326.wav");
    }

    /// Fabrique un dossier d'échantillon **déterministe** (sous `java.io.tmpdir/<nom>`) : journal
    /// `LogPR` + relevé climatique de la série 1925492, plus deux WAV nommés `wavA`/`wavB`. Chemin
    /// fixe (et non un dossier temporaire aléatoire) car il est affiché dans le champ « Dossier
    /// source », donc une racine stable garde les PNG reproductibles. Réécrit à chaque appel
    /// (idempotent). Helper unique : factorise les libellés communs (PMD `AvoidDuplicateLiterals`).
    private static Path creerDossier(String nom, String wavA, String wavB) throws IOException {
        Path sd = Path.of(System.getProperty("java.io.tmpdir"), nom);
        Files.createDirectories(sd);
        Files.writeString(sd.resolve("LogPR1925492.txt"), LOG, StandardCharsets.UTF_8);
        Files.writeString(sd.resolve("PaRecPR1925492_THLog.csv"), "Date\tHour\n", StandardCharsets.UTF_8);
        Files.writeString(sd.resolve(wavA), "wav1");
        Files.writeString(sd.resolve(wavB), "wav2");
        return sd;
    }

    private static Injector creerInjecteur() {
        return Guice.createInjector(Modules.override(
                        new CommunModule(),
                        new PersistenceModule(),
                        new SitesModule(),
                        new PassageModule(),
                        new ImportationModule())
                .with(liaison -> liaison.bind(Horloge.class).toInstance(new HorlogeFigee(REFERENCE))));
    }

    private static void seeder(Injector injecteur) {
        SourceDeDonnees source = injecteur.getInstance(SourceDeDonnees.class);
        new UtilisateurDao(source).insert(new Utilisateur(ID_UTILISATEUR, "Capitaine Chiro (demo)"));
        ServiceSites service = injecteur.getInstance(ServiceSites.class);
        var site = service.creerSite(
                "640380", "Etang de la Tuiliere", Protocole.STANDARD, "Aix-en-Provence", ID_UTILISATEUR);
        service.ajouterPoint(site.id(), "A1", 43.5298, 5.4474, "Pres du grand chene");
    }

    /// Dossier d'exemple **standard** (chemin déterministe) : journal + relevé + deux WAV cohérents
    /// (même série 1925492, même nuit) → inspection sans avertissement.
    private static Path creerDossierEchantillon() throws IOException {
        return creerDossier(
                "vigiechiro-sd-demo", "PaRecPR1925492_20260422_203922.wav", "PaRecPR1925492_20260422_204326.wav");
    }
}
