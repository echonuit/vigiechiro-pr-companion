package fr.univ_amu.iut.importation.outils;

import fr.univ_amu.iut.commun.model.StatutWorkflow;
import fr.univ_amu.iut.commun.model.Verdict;
import fr.univ_amu.iut.commun.outils.ApercuFx;
import fr.univ_amu.iut.commun.view.PanneauCompteRendu;
import fr.univ_amu.iut.commun.viewmodel.CompteRenduChiffre;
import fr.univ_amu.iut.commun.viewmodel.CompteRenduChiffre.Action;
import fr.univ_amu.iut.importation.model.LigneRapport;
import fr.univ_amu.iut.importation.model.RapportImport;
import fr.univ_amu.iut.importation.model.ResultatImport;
import fr.univ_amu.iut.importation.model.ResultatImportMultiNuits;
import fr.univ_amu.iut.importation.model.StatutImportFichier;
import fr.univ_amu.iut.importation.model.VolumesImport;
import fr.univ_amu.iut.importation.viewmodel.CompteRenduChiffreImport;
import fr.univ_amu.iut.importation.viewmodel.CompteRenduChiffreImport.ContexteApresImport;
import fr.univ_amu.iut.passage.model.Passage;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.layout.VBox;

/// Aperçu de l'**annonce de participation** en fin d'import (#3473).
///
/// ## Pourquoi cet aperçu manquait
///
/// C'est la mention qui dit à l'utilisateur qu'une écriture est partie sur un serveur distant, et
/// depuis #3473 **ce qu'il lui reste à y faire**. Aucun aperçu ne la montrait, dans aucune de ses deux
/// formes : `CaptureCompteRendu` rend la bande du socle sur des mentions génériques, et les aperçus de
/// l'écran d'import s'arrêtent avant la fin.
///
/// ⚠️ #3473 a **presque doublé** la longueur de cette mention. C'est exactement le contenu où la revue
/// visuelle trouve ses défauts, et la bande vit dans des largeurs très différentes (ADR 2358). Une
/// mesure faite à la clôture dit qu'elle s'enroule correctement de 900 à 480 px ; cet aperçu est ce qui
/// le **montre**, et ce que le garde anti-troncature contrôlera à chaque build.
///
/// ## Les deux formes, parce qu'elles ne se valent pas
///
/// - `apercu-import-participation.png` : **une** nuit, une participation. Le cas courant.
/// - `apercu-import-participations-multi-nuits.png` : **plusieurs** nuits, donc plusieurs
///   participations. C'est la forme la plus longue, elle ne sort que d'une carte laissée plusieurs
///   jours sur le terrain, et c'est celle qu'on oublie de regarder.
///
/// Le texte n'est **pas** recopié ici : il est composé par [CompteRenduChiffreImport], le code de
/// production (ADR 0025). Une capture qui reconstruit son contenu finit par montrer un produit qui
/// n'existe plus - c'est le mode de panne de #1468.
///
/// Lancement headless : `.github/assets/capture-screenshots.sh` (Headless Platform JavaFX 26).
public final class CaptureCompteRenduParticipation {

    /// Largeur d'un panneau intégré sous l'écran d'import, forme d'insertion retenue par #2358.
    private static final double LARGEUR = 900;

    private CaptureCompteRenduParticipation() {}

    public static void main(String[] args) throws InterruptedException {
        CountDownLatch fini = new CountDownLatch(1);
        AtomicReference<Throwable> erreur = new AtomicReference<>();
        Platform.startup(() -> {
            try {
                capturer();
            } catch (RuntimeException probleme) {
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

    private static void capturer() {
        Path sortie = Path.of(System.getProperty("capture.outDir", ".github/assets"));
        rendre(uneNuit(), sortie.resolve("apercu-import-participation.png"));
        rendre(troisNuits(), sortie.resolve("apercu-import-participations-multi-nuits.png"));
    }

    /// Une nuit, sa participation créée : la forme singulière de la mention.
    private static CompteRenduChiffre uneNuit() {
        return CompteRenduChiffreImport.de(nuit("2026-04-22", 612, 0), actions(), ContexteApresImport.AUCUN);
    }

    /// Trois nuits publiées : la forme plurielle, la plus longue des deux.
    private static CompteRenduChiffre troisNuits() {
        return CompteRenduChiffreImport.de(
                new ResultatImportMultiNuits(
                        List.of(nuit("2026-04-22", 612, 0), nuit("2026-04-23", 488, 3), nuit("2026-04-24", 401, 0))),
                actions(),
                ContexteApresImport.AUCUN);
    }

    /// Un compte rendu ne se termine pas sur « Fermer » (ADR 2358) : le pied mène au passage créé.
    private static List<Action> actions() {
        return List.of(new Action("Ouvrir le passage", true, () -> {}));
    }

    /// Une nuit importée **avec sa participation créée**, telle que le service la rend (#1488).
    private static ResultatImport nuit(String date, int importes, int rejetes) {
        return new ResultatImport(
                passage(date),
                null,
                "1925492",
                importes + rejetes,
                importes,
                List.of(),
                new RapportImport(rapport(importes, rejetes)),
                new VolumesImport(5_000_000_000L, 0, 6_800_000_000L),
                true);
    }

    private static List<LigneRapport> rapport(int importes, int rejetes) {
        return java.util.stream.Stream.concat(
                        java.util.stream.IntStream.range(0, importes)
                                .mapToObj(i ->
                                        new LigneRapport("Car640380-" + i + ".wav", StatutImportFichier.IMPORTE, null)),
                        java.util.stream.IntStream.range(0, rejetes)
                                .mapToObj(i -> new LigneRapport(
                                        "Car640380-rejet-" + i + ".wav",
                                        StatutImportFichier.REJETE,
                                        "en-tête WAV illisible")))
                .toList();
    }

    private static Passage passage(String date) {
        return new Passage(
                1L,
                1,
                2026,
                date,
                "21:15",
                "06:40",
                null,
                StatutWorkflow.IMPORTE,
                Verdict.A_VERIFIER,
                null,
                null,
                null,
                1L,
                "1925492",
                null);
    }

    private static void rendre(CompteRenduChiffre modele, Path fichier) {
        PanneauCompteRendu panneau = new PanneauCompteRendu();
        panneau.afficher(modele);
        // Marge autour du panneau : la capture montre le composant tel qu'il s'insère, pas collé au bord.
        VBox cadre = new VBox(panneau);
        cadre.setStyle("-fx-padding: 16; -fx-background-color: #f5f6f8;");
        Scene scene = new Scene(cadre, LARGEUR, -1);
        for (String feuille : List.of("palette.css", "base.css", "design.css")) {
            var url = PanneauCompteRendu.class.getResource(feuille);
            if (url != null) {
                scene.getStylesheets().add(url.toExternalForm());
            }
        }
        ApercuFx.enregistrerPng(scene, fichier);
        System.out.println("Apercu ecrit dans " + fichier.toAbsolutePath());
    }
}
