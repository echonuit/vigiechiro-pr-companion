package fr.univ_amu.iut.importation.outils;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.util.Modules;
import fr.univ_amu.iut.commun.di.RacineInjecteur;
import fr.univ_amu.iut.commun.model.Horloge;
import fr.univ_amu.iut.commun.model.HorlogeFigee;
import fr.univ_amu.iut.commun.model.Progression;
import fr.univ_amu.iut.commun.model.Protocole;
import fr.univ_amu.iut.commun.model.StatutWorkflow;
import fr.univ_amu.iut.commun.model.Utilisateur;
import fr.univ_amu.iut.commun.model.Verdict;
import fr.univ_amu.iut.commun.model.dao.UtilisateurDao;
import fr.univ_amu.iut.commun.outils.ApercuFx;
import fr.univ_amu.iut.commun.outils.AttenteTuiles;
import fr.univ_amu.iut.commun.outils.ModuleCaptureCommun;
import fr.univ_amu.iut.commun.persistence.MigrationSchema;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.commun.view.ExecuteurTache;
import fr.univ_amu.iut.importation.model.LigneRapport;
import fr.univ_amu.iut.importation.model.RapportImport;
import fr.univ_amu.iut.importation.model.ResultatImport;
import fr.univ_amu.iut.importation.model.StatutImportFichier;
import fr.univ_amu.iut.importation.model.VolumesImport;
import fr.univ_amu.iut.importation.view.ImportationController;
import fr.univ_amu.iut.importation.viewmodel.ImportationViewModel;
import fr.univ_amu.iut.importation.viewmodel.PreferenceConservation;
import fr.univ_amu.iut.passage.model.Campagne;
import fr.univ_amu.iut.passage.model.Enregistreur;
import fr.univ_amu.iut.passage.model.Passage;
import fr.univ_amu.iut.passage.model.SessionDEnregistrement;
import fr.univ_amu.iut.passage.model.dao.CampagneDao;
import fr.univ_amu.iut.passage.model.dao.EnregistreurDao;
import fr.univ_amu.iut.passage.model.dao.PassageDao;
import fr.univ_amu.iut.sites.model.ServiceSites;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Group;
import javafx.scene.Parent;
import javafx.scene.Scene;

/// Outil de capture/mesure, utilisable tel quel.
///
/// Capture l'assistant M-Import en PNG, pour le comparer à la maquette du brief. Pour montrer le
/// « cas standard » (sections inspection + rattachement remplies, et non l'écran vide), on pilote
/// directement le [ImportationViewModel] :
///
/// 1. base SQLite temporaire semée (un utilisateur, un site avec un point) + un dossier
///    d'échantillon (journal LogPR, relevé climatique, deux WAV) ;
/// 2. injecteur Guice minimal (socle + sites + passage + importation) avec une [HorlogeFigee] pour
///    une année de passage déterministe ;
/// 3. la vue est chargée avec une `controllerFactory` qui injecte un VM connu, qu'on pilote ensuite
///    (choix du dossier -> inspection -> sélection site/point) avant le rendu hors-écran par
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

    /// Série de l'enregistreur de démonstration (celui du journal `LOG`), partagée par le seed et les
    /// scénarios pour rester cohérente d'un état à l'autre.
    private static final String SERIE = "1925492";

    /// Les deux WAV standard d'une nuit de démonstration (série 1925492, nuit du 22/04), réutilisés par
    /// plusieurs dossiers d'échantillon.
    private static final String WAV_NUIT_A = "PaRecPR1925492_20260422_203922.wav";
    private static final String WAV_NUIT_B = "PaRecPR1925492_20260422_204326.wav";

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
        loader.setControllerFactory(type -> type == ImportationController.class
                ? new ImportationController(
                        vm,
                        injecteur.getInstance(PreferenceConservation.class),
                        injecteur.getInstance(ExecuteurTache.class),
                        injecteur.getInstance(fr.univ_amu.iut.importation.view.FabriqueActionImportTransformes.class),
                        injecteur.getInstance(fr.univ_amu.iut.commun.view.OuvrirPassage.class),
                        // Sans chrome : le port de révélation (#1486) reste muet, ce qui est exactement
                        // son contrat hors `MainView`. Une capture ne défile pas.
                        new fr.univ_amu.iut.commun.view.DefilementChrome())
                : injecteur.getInstance(type));
        Parent vue = loader.load();
        // Seule la LARGEUR compte ici : chaque rendu se fait ensuite à la hauteur de son propre contenu
        // (cf. [#rendreAjuste]). La hauteur donnée n'est que celle de la mise en page de départ.
        Scene scene = new Scene(vue, 1100, 900);

        // État « décompression d'un .zip » (#146) : avant toute inspection, la barre de progression
        // déterminée « X / N fichiers » s'affiche avec un temps restant estimé et un bouton « Annuler »
        // (le formulaire est gelé). L'écoulé est POSÉ (#3483) : lu à l'horloge, il ferait entrer la
        // vitesse de la machine dans le PNG. 2,5 s à 20 % → « ~10 s restant », partout.
        vm.marquerExtractionEnCours();
        vm.progression()
                .appliquer(
                        new Progression("Décompression : 740 / 3692 · PaRecPR1925492_20260423_034512.wav", 0.20),
                        Duration.ofMillis(2500));
        rendre(scene, sortie.resolve("apercu-import-decompression.png"));

        // État « gros fichier en cours » (#2733) : sur une entrée de plusieurs Go, le compteur
        // « X / N fichiers » et la barre ne bougent pas pendant des minutes. C'est le VOLUME écrit qui
        // dit que la décompression avance - et c'est ce que cette capture doit montrer.
        //
        // Même fraction que ci-dessus, un moment plus tard : l'écoulé posé est donc PLUS GRAND, et
        // l'estimation grandit avec lui (3,5 s à 20 % → « ~14 s restant »). C'est exactement ce que
        // raconte l'état documenté - le temps passe, le compteur non.
        vm.progression()
                .appliquer(
                        new Progression(
                                "Décompression : 740 / 3692 · PaRecPR1925492_20260423_034512.wav · 128 Mo", 0.20),
                        Duration.ofMillis(3500));
        rendre(scene, sortie.resolve("apercu-import-decompression-volume.png"));

        // Refus d'une archive (#2732) : ces messages sont rendus par le code de production
        // (ImportationViewModel.signalerSourceIllisible → RetourOperation), pas recopiés ici.
        vm.signalerSourceIllisible(new fr.univ_amu.iut.commun.model.RegleMetierException(
                "Espace disque insuffisant pour décompresser : besoin d'environ 12,4 Go, seulement 3,1 Go"
                        + " disponibles. Libérez de l'espace, ou décompressez l'archive vous-même."));
        rendre(scene, sortie.resolve("apercu-import-archive-espace-disque.png"));

        vm.signalerSourceIllisible(new fr.univ_amu.iut.commun.model.RegleMetierException(
                "Archive zip interrompue : elle a écrit 12,4 Go alors qu'elle en annonçait 1 Mo. Une archive"
                        + " qui ment sur sa taille n'est pas une carte SD."));
        rendre(scene, sortie.resolve("apercu-import-archive-menteuse.png"));

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

        // La carte de rattachement est peuplée (site/point sélectionnés) : on attend les tuiles OSM.
        rendreAvecCarte(scene, sortie.resolve("apercu-import-assistant.png"));

        // État « import en cours » (#33/#146) : barre de progression déterminée, temps restant estimé,
        // bouton « Annuler », formulaire gelé. Phase de copie en début d'import (l'ETA y est parlant).
        // Écoulé posé, comme plus haut : 2,5 s à 12,6 % → « ~17 s restant ».
        vm.marquerEnCours();
        vm.progression()
                .appliquer(
                        new Progression("Copie 48/191 · PaRecPR1925492_20260422_205518.wav", 0.126),
                        Duration.ofMillis(2500));
        rendre(scene, sortie.resolve("apercu-import-en-cours.png"));

        capturerCartesInhabituelles(vm, scene, sortie);

        capturerAvertissementsRattachement(vm, scene, sortie);

        // État « import terminé AVEC rapport » (#155) : import résilient, la liste des fichiers rejetés
        // (illisible, format invalide) et leur raison s'affiche sous le message de succès.
        vm.inspection().dossierSourceProperty().set(dossierSd);
        vm.inspecter();
        RapportImport rapport = new RapportImport(List.of(
                new LigneRapport(
                        "Car640380-2026-Pass1-A1-PaRecPR1925492_20260422_203922.wav",
                        StatutImportFichier.IMPORTE,
                        "3 séquence(s)"),
                new LigneRapport(
                        "PaRecPR1648011_20260422_210000.wav",
                        StatutImportFichier.REJETE,
                        "Original illisible (en-tête WAV invalide)"),
                new LigneRapport(
                        "PaRecPR1925492_20260422_211500.wav",
                        StatutImportFichier.REJETE,
                        "Fréquence source 44100 Hz non divisible par 10"),
                new LigneRapport("notes-terrain.txt", StatutImportFichier.IGNORE, "fichier non pertinent")));
        // Agrégat et volumes renseignés (#2358) : sans eux, la bande de compte rendu n'affiche ni le titre
        // de la nuit, ni les barres de volume, ni l'action suivante - la capture montrerait alors un
        // compte rendu amputé, que l'application ne produit jamais.
        vm.marquerTermine(new ResultatImport(
                passageImporte(),
                new SessionDEnregistrement(1L, "/ws/Car640380-2026-Pass1-A1", 0L, 0L, 1L),
                SERIE,
                1,
                3,
                List.of(),
                rapport,
                new VolumesImport(5_100_273_664L, 5_100_273_664L, 1_932_735_283L)));
        rendre(scene, sortie.resolve("apercu-import-rejets.png"));
    }

    /// Le passage créé par l'import capturé : ce que le compte rendu nomme en titre et ce que son action
    /// suivante ouvre.
    private static Passage passageImporte() {
        return new Passage(
                1L,
                1,
                2026,
                "2026-04-22",
                "20:39",
                "07:12",
                null,
                StatutWorkflow.TRANSFORME,
                null,
                null,
                null,
                null,
                1L,
                SERIE,
                null);
    }

    /// État « avertissements de rattachement » (#108/#111) : deux encarts AMBRE non bloquants, restylés du
    /// rouge `.insp-*` vers la famille `.encart` par #2072, et que rien ne montrait (#2097).
    ///
    /// Le dossier inspecte proprement (deux WAV normaux → une nuit détectée) mais contient AUSSI un fichier
    /// déjà préfixé pour un AUTRE point (B2) : d'où la discordance de préfixe (#111), sans perturber la
    /// détection de nuit. Le rattachement A1 sélectionné, on re-choisit le n°1 - déjà pris par le passage
    /// seedé - d'où le doublon de passage (#108). Les deux encarts co-existent dans un même rendu.
    /// Les quatre cartes qui **sortent du cas nominal**, chacune inspectée puis rendue : deux
    /// enregistreurs mêlés, un journal en désaccord, des bruts déjà préfixés, et plusieurs nuits.
    ///
    /// Extraite de [#capturer()] parce que le quatrième état l'a fait dépasser le plafond NCSS du
    /// profil qualité (#4141). La coupure suit une frontière réelle : ces quatre-là partagent le même
    /// geste - changer le dossier source, ré-inspecter, rendre - alors que ce qui les précède monte
    /// l'assistant et ce qui les suit joue un import terminé.
    private static void capturerCartesInhabituelles(ImportationViewModel vm, Scene scene, Path sortie)
            throws IOException {
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

        // État « déjà préfixés » (#4141) : les bruts portent le préfixe `Car…` qu'un import précédent a
        // posé → « État du nommage : fichiers déjà préfixés (seront copiés et transformés) ».
        //
        // ⚠️ Ce qui ne rejoue pas est le RENOMMAGE, et non la transformation. La phrase des bruts dit
        // « copiés, renommés et transformés » ; celle-ci retire le seul mot « renommés ». #4055 écrivait
        // « la transformation ne rejoue pas » : l'écran dit le contraire, et c'est lui qui fait foi.
        //
        // ⚠️ Le préfixe est celui d'un AUTRE carré que le rattachement choisi, comme dans la fixture de
        // recette `sd-prefixee` (Car130711-…-Z1). C'est le cas réel : on récupère une carte déjà traitée
        // ailleurs. L'import est alors BLOQUÉ, et l'aperçu montre les deux à la fois - l'état de nommage,
        // et le refus qui nomme le préfixe attendu.
        vm.inspection().dossierSourceProperty().set(creerDossierPrefixe());
        vm.inspecter();
        rendre(scene, sortie.resolve("apercu-import-prefixe.png"));

        // État « plusieurs nuits » (#4144) : une carte laissée tourner plusieurs nuits (3 dates) → la table
        // des nuits s'affiche, une ligne par nuit (inclure, date, fichiers, état, n° de passage proposé,
        // auto-numéroté). Le rattachement (site/point) reste sélectionné, donc les n° 1/2/3 apparaissent.
        vm.inspection().dossierSourceProperty().set(creerDossierMultiNuits());
        vm.inspecter();
        rendre(scene, sortie.resolve("apercu-import-multi-nuits.png"));
    }

    private static void capturerAvertissementsRattachement(ImportationViewModel vm, Scene scene, Path sortie)
            throws IOException {
        vm.inspection().dossierSourceProperty().set(creerDossierAvertissements());
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
        vm.rattachement().numeroPassageProperty().set(1);
        rendreAvecCarte(scene, sortie.resolve("apercu-import-rattachement-avertissements.png"));
    }

    /// Rend le contenu **à sa hauteur naturelle**, quel que soit l'état de l'assistant à cet instant.
    ///
    /// Les six états capturés ici n'ont pas la même hauteur : la zone de progression, les bandeaux
    /// (mélange, incohérence), la table des nuits et le compte rendu de rejets apparaissent puis
    /// disparaissent. Une scène de hauteur fixe convient donc à **un** état et comprime les autres - et
    /// elle les comprimait, de deux façons. La consigne de la section « espace disque » se rabattait sur
    /// une ligne terminée par une ellipse (#2049) ; et la liste des rejets tombait franchement hors cadre,
    /// au point qu'un gabarit « pied de formulaire » de 1290 px avait été ajouté pour cette seule capture.
    /// Mesurer le contenu répond aux deux d'un coup, et se passe de constante à ré-ajuster.
    ///
    /// On reparente la racine le temps du cliché, puis on la rend à la scène d'origine : un nœud
    /// n'appartient qu'à une scène, et les rendus suivants réutilisent celle-ci.
    private static void rendreAjuste(Scene scene, Path fichier, BiConsumer<Scene, Path> capture) {
        Parent racine = scene.getRoot();
        // Mesure sur la racine encore attachée : sans passe CSS, les libellés n'ont pas leurs métriques de
        // police et la hauteur demandée est fausse.
        racine.applyCss();
        racine.layout();
        double hauteur = racine.prefHeight(scene.getWidth());
        scene.setRoot(new Group());
        Scene ajustee = new Scene(racine, scene.getWidth(), hauteur);
        ajustee.getStylesheets().setAll(scene.getStylesheets());
        capture.accept(ajustee, fichier);
        ajustee.setRoot(new Group());
        scene.setRoot(racine);
        System.out.println("Apercu ecrit dans " + fichier.toAbsolutePath());
    }

    /// Rend `scene` hors-écran en PNG, à la hauteur de son contenu.
    private static void rendre(Scene scene, Path fichier) {
        rendreAjuste(scene, fichier, ApercuFx::enregistrerPng);
    }

    /// Comme [#rendre], mais **après attente des tuiles OSM** : la carte de rattachement (composant
    /// [CarteSites][fr.univ_amu.iut.commun.view.carte.CarteSites]) charge son fond de carte de façon
    /// asynchrone ; sans cette attente, le PNG la fige avant l'arrivée des tuiles (carte vide). Même
    /// mécanisme que les autres captures à carte (CaptureMultisite).
    private static void rendreAvecCarte(Scene scene, Path fichier) {
        rendreAjuste(
                scene,
                fichier,
                (ajustee, cible) -> ApercuFx.capturerApresPreparation(ajustee, AttenteTuiles::attendre, cible));
    }

    /// Dossier d'exemple **mélangé** (chemin déterministe) : journal + relevé de la série 1925492 mais
    /// deux WAV de **deux enregistreurs** distincts → l'inspection lève l'avertissement « mélange ».
    private static Path creerDossierMelange() throws IOException {
        return creerDossier(
                "vigiechiro-sd-melange", WAV_NUIT_A, "PaRecPR1648011_20260422_204326.wav"); // 2e enregistreur
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

    /// Journal daté de la **première nuit** (03/07) de l'échantillon multi-nuits : sa date tombe dans les
    /// nuits des fichiers (03/04/05-07), pour que l'inspection ne lève **pas** l'avertissement de
    /// non-correspondance journal/enregistrements (le cas normal d'une carte laissée tourner plusieurs
    /// nuits, où le journal couvre bien ces nuits).
    private static final String LOG_MULTI =
            "03/07/26 - 20:25:00 PR1925492 Demarrage Passive Recorder numero de serie 1925492, V1.01,"
                    + " CPU 600000000, T4.1\n"
                    + "03/07/26 - 20:25:01 PR1925492 Sonde temperature/hygrometrie presente, lecture toutes"
                    + " les 600s\n"
                    + "03/07/26 - 20:25:01 PR1925492 Parametres : Acquisi. 20:25-07:47, Fe384kHz, Bd. Freq."
                    + " 8-120kHz\n";

    /// Fabrique un dossier d'échantillon **déterministe** (sous `java.io.tmpdir/<nom>`) : journal
    /// `LogPR` (contenu `log`) + relevé climatique de la série 1925492, plus les WAV nommés. Chemin fixe
    /// (et non un dossier temporaire aléatoire) car il est affiché dans le champ « Dossier source », donc
    /// une racine stable garde les PNG reproductibles. Réécrit à chaque appel (idempotent). Factorise les
    /// libellés communs (PMD `AvoidDuplicateLiterals`).
    /// Délègue à [DossierDeFixture], qui **vide** le dossier avant d'écrire - son chemin est
    /// déterministe, et les restes d'une exécution précédente feraient montrer à la capture la SOMME
    /// des deux (#4044).
    private static Path creerDossierAvecWav(String nom, String log, List<String> wavs) throws IOException {
        return DossierDeFixture.preparer(nom, log, wavs);
    }

    /// Variante à **deux** WAV (une seule nuit), pour les échantillons standard / mélange / incohérence.
    private static Path creerDossier(String nom, String wavA, String wavB) throws IOException {
        return creerDossierAvecWav(nom, LOG, List.of(wavA, wavB));
    }

    /// Dossier pour la capture des **avertissements de rattachement** (#2097) : une nuit standard (deux
    /// WAV normaux, donc détection de nuit propre) **plus** un fichier déjà préfixé pour un autre point
    /// (`Car640380-2026-Pass2-B2-…`), qui ne correspond pas au rattachement A1 choisi → avertissement de
    /// discordance de préfixe (#111). Le journal reste celui de la série 1925492, nuit du 22/04.
    private static Path creerDossierAvertissements() throws IOException {
        return creerDossierAvecWav(
                "vigiechiro-sd-avertissements",
                LOG,
                List.of(WAV_NUIT_A, WAV_NUIT_B, "Car640380-2026-Pass2-B2-PaRecPR1925492_20260422_205000.wav"));
    }

    /// Dossier d'exemple **multi-nuits** (chemin déterministe) : trois soirées distinctes (2 WAV chacune)
    /// du même enregistreur, avec un journal daté de la première nuit → l'inspection détecte 3 nuits et
    /// affiche la table des nuits, sans avertissement de non-correspondance.
    /// Dossier d'exemple **déjà préfixé** (chemin déterministe) : les trois bruts portent le préfixe
    /// `Car130711-2026-Pass1-Z1-…` qu'un import précédent a posé → l'inspection bascule l'état de nommage
    /// sur `PREFIXE`, et l'écran l'écrit.
    ///
    /// Les noms reprennent ceux de la fixture de recette `sd-prefixee`, pour que l'aperçu et la carte
    /// que la recette monte montrent **la même chose**.
    private static Path creerDossierPrefixe() throws IOException {
        return creerDossierAvecWav(
                "vigiechiro-sd-prefixee",
                LOG,
                List.of(
                        "Car130711-2026-Pass1-Z1-PaRecPR1925492_20260422_203922.wav",
                        "Car130711-2026-Pass1-Z1-PaRecPR1925492_20260422_210515.wav",
                        "Car130711-2026-Pass1-Z1-PaRecPR1925492_20260422_223045.wav"));
    }

    private static Path creerDossierMultiNuits() throws IOException {
        List<String> wavs = new ArrayList<>();
        for (String jour : List.of("20260703", "20260704", "20260705")) {
            wavs.add("PaRecPR1925492_" + jour + "_203922.wav");
            wavs.add("PaRecPR1925492_" + jour + "_204326.wav");
        }
        return creerDossierAvecWav("vigiechiro-sd-multi-nuits", LOG_MULTI, wavs);
    }

    public static Injector creerInjecteur() {
        // Composition **complète**, celle de l'application : les features s'ajoutent d'elles-mêmes.
        // C'est ce que #333 prescrivait, et ce que l'énumération à la main a fait rater quatre fois -
        // dont ici, où l'absence de `CampagneModule` a photographié un assistant sans sa ligne Campagne.
        return Guice.createInjector(Modules.override(RacineInjecteur.modules())
                .with(
                        ModuleCaptureCommun.executeursSynchrones(),
                        liaison -> liaison.bind(Horloge.class).toInstance(new HorlogeFigee(REFERENCE))));
    }

    private static void seeder(Injector injecteur) {
        SourceDeDonnees source = injecteur.getInstance(SourceDeDonnees.class);
        new UtilisateurDao(source).insert(new Utilisateur(ID_UTILISATEUR, "Capitaine Chiro (demo)"));
        ServiceSites service = injecteur.getInstance(ServiceSites.class);
        var site = service.creerSite("640380", "Étang de la Tuilière", Protocole.STANDARD, "Ahetze", ID_UTILISATEUR);
        var point = service.ajouterPoint(site.id(), "A1", 43.4010, -1.5740, "Près du grand chêne");
        // Un passage n°1 DÉJÀ en base pour ce point (#108) : re-choisir le n°1 dans l'assistant fait
        // apparaître l'avertissement « Le passage n° 1 existe déjà pour ce point en… ». Restylé en
        // ambre par #2072 et jamais capturé (#2097), c'est ce que montre
        // apercu-import-rattachement-avertissements. Le passage
        // référence son enregistreur (contrainte de clé étrangère), qu'on sème d'abord.
        new EnregistreurDao(source).insert(new Enregistreur(SERIE, "V1.01", null));
        // Deux campagnes (#2631) : la liste déroulante montre un choix, et le passage déjà en base en
        // porte une, ce qui rend la proposition observable sur la capture.
        CampagneDao campagnes = new CampagneDao(source);
        Campagne campagne = campagnes.insert(new Campagne(null, "Suivi ENS 2026", 2026, null));
        campagnes.insert(new Campagne(null, "Thèse Samuel", 2025, null));
        new PassageDao(source)
                .insert(new Passage(
                        null,
                        1,
                        2026,
                        "2026-04-22",
                        "20:25:00",
                        "07:47:00",
                        null,
                        StatutWorkflow.TRANSFORME,
                        Verdict.OK,
                        null,
                        null,
                        null,
                        point.id(),
                        SERIE,
                        // Rattaché à une campagne (#2631) : c'est ce passage-là que l'assistant regarde
                        // pour proposer la même campagne à la nuit suivante sur ce point.
                        campagne.id()));
    }

    /// Dossier d'exemple **standard** (chemin déterministe) : journal + relevé + deux WAV cohérents
    /// (même série 1925492, même nuit) → inspection sans avertissement.
    private static Path creerDossierEchantillon() throws IOException {
        return creerDossier("vigiechiro-sd-demo", WAV_NUIT_A, WAV_NUIT_B);
    }
}
