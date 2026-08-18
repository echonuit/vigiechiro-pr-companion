package fr.univ_amu.iut.commun.outils;

import fr.univ_amu.iut.commun.model.Severite;
import fr.univ_amu.iut.commun.view.PanneauCompteRendu;
import fr.univ_amu.iut.commun.viewmodel.CompteRenduChiffre;
import fr.univ_amu.iut.commun.viewmodel.CompteRenduChiffre.Action;
import fr.univ_amu.iut.commun.viewmodel.CompteRenduChiffre.Avertissement;
import fr.univ_amu.iut.commun.viewmodel.CompteRenduChiffre.Barre;
import fr.univ_amu.iut.commun.viewmodel.CompteRenduChiffre.Motif;
import fr.univ_amu.iut.commun.viewmodel.CompteRenduChiffre.Segment;
import fr.univ_amu.iut.commun.viewmodel.CompteRenduChiffre.Teinte;
import fr.univ_amu.iut.commun.viewmodel.CompteRenduChiffre.Ventilation;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Hyperlink;
import javafx.scene.layout.VBox;

/// Outil de capture/mesure, utilisable tel quel.
///
/// Rend le **compte rendu chiffré** ([PanneauCompteRendu], #2358) dans ses trois états, avec le composant
/// de production et ses feuilles de style réelles (ADR 0025 : une capture passe par le code de
/// production, jamais par un fac-similé) :
///
/// - `apercu-compte-rendu.png` : une opération **avec rejets et avertissement** - le cas de la maquette,
///   où les quatre blocs ont quelque chose à dire ;
/// - `apercu-compte-rendu-sans-rejet.png` : le cas **courant**, sans rejet ni avertissement. C'est celui
///   qui prouve que les blocs vides **disparaissent** au lieu d'afficher des cadres vides ;
/// - `apercu-compte-rendu-echec.png` : une opération **en échec**, où les proportions s'inversent et où
///   le pied propose la reprise plutôt qu'un acquittement ;
/// - `apercu-compte-rendu-motifs.png` : le premier cas, **détail des motifs ouvert** - chaque motif y
///   montre la liste de ses fichiers, ce que le seul résumé du pied ne fait pas.
///
/// Les chiffres sont ceux de la maquette M-CompteRendu (612 enregistrements : 583 importés, 21 déjà
/// présents, 8 rejetés ; 5,0 Go lus, 6,8 Go écrits), pour que la capture et la maquette se confrontent.
///
/// Lancement headless : `.github/assets/capture-screenshots.sh` (Headless Platform JavaFX 26).
public final class CaptureCompteRendu {

    /// Largeur de rendu : celle d'un panneau intégré sous l'écran d'import, forme d'insertion retenue.
    private static final int LARGEUR = 900;

    private CaptureCompteRendu() {}

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
        rendre(avecRejets(), sortie.resolve("apercu-compte-rendu.png"));
        rendre(sansRejet(), sortie.resolve("apercu-compte-rendu-sans-rejet.png"));
        rendre(enEchec(), sortie.resolve("apercu-compte-rendu-echec.png"));
        rendreMotifsOuverts(sortie.resolve("apercu-compte-rendu-motifs.png"));
    }

    /// Le même compte rendu, **détail des motifs ouvert** : chaque motif y montre la liste de ses fichiers.
    /// L'ouverture passe par le lien du pied, comme un clic d'utilisateur, et non par une méthode dédiée à
    /// la capture - une capture prouve ce que l'écran fait, pas ce qu'on lui fait faire.
    private static void rendreMotifsOuverts(Path fichier) {
        rendre(avecRejets(), fichier, panneau -> {
            Node lien = panneau.lookup(".cr-resume-motifs");
            if (lien instanceof Hyperlink resume) {
                resume.fire();
            }
        });
    }

    private static void rendre(CompteRenduChiffre modele, Path fichier) {
        rendre(modele, fichier, panneau -> {});
    }

    private static void rendre(CompteRenduChiffre modele, Path fichier, Consumer<PanneauCompteRendu> geste) {
        PanneauCompteRendu panneau = new PanneauCompteRendu();
        panneau.afficher(modele);
        geste.accept(panneau);
        // Marge autour du panneau : la capture montre le composant tel qu'il s'insère, pas collé au bord.
        VBox cadre = new VBox(panneau);
        cadre.setStyle("-fx-padding: 16; -fx-background-color: #f5f6f8;");
        Scene scene = new Scene(cadre, LARGEUR, -1);
        // Les feuilles du socle arrivent par `Habillage`, que `ApercuFx` appelle (#3992). Les poser
        // ici serait une copie de plus du même geste - l'ADR 3374 en a déjà retiré trois.
        ApercuFx.enregistrerPng(scene, fichier);
        System.out.println("Apercu ecrit dans " + fichier.toAbsolutePath());
    }

    /// Le cas de la maquette : 583 importés sur 612, deux motifs de rejet, un avertissement encore vrai.
    private static CompteRenduChiffre avecRejets() {
        return new CompteRenduChiffre(
                "Import terminé - nuit du 22/06/2026, carré 640380 · A1",
                "583 / 612 importés",
                Severite.AVERTISSEMENT,
                volumes(),
                new Ventilation(
                        "Devenir des 612 enregistrements",
                        612,
                        List.of(
                                new Segment("Importés", 583, "583", Teinte.RETENU),
                                new Segment("Déjà présents", 21, "21", Teinte.ECARTE),
                                new Segment("Rejetés", 8, "8", Teinte.REFUSE))),
                // Libellés au pluriel : le résumé les compose avec un effectif (« 6 fichiers déjà
                // expansés »), et « 6 fichier déjà expansé » se lisait mal - vu à la capture.
                List.of(
                        new Motif(
                                "fichiers déjà expansés",
                                List.of("a.wav", "b.wav", "c.wav", "d.wav", "e.wav", "f.wav")),
                        new Motif("en-têtes WAV illisibles", List.of("g.wav", "h.wav"))),
                List.of(Avertissement.de(
                        "Relevé climatique absent : le diagnostic de la nuit sera partiel, le dépôt reste possible.")),
                List.of(
                        new Action("Ouvrir le passage", true, () -> {}),
                        new Action("Vérifier l'enregistrement", false, () -> {})));
    }

    /// Le cas **courant** : tout est passé. Les blocs « motifs » et « avertissements » disparaissent.
    private static CompteRenduChiffre sansRejet() {
        return new CompteRenduChiffre(
                "Import terminé - nuit du 20/06/2026, carré 640380 · A1",
                // « 584 importés », et non « 584 / 584 » : la traduction refuse d'afficher un écart qui
                // n'existe pas, parce qu'un « 584 / 584 » fait chercher la différence. La capture disait
                // pourtant l'inverse - une donnée de démonstration qui contredisait la règle du produit,
                // vue à la revue visuelle de la clôture.
                "584 importés",
                Severite.SUCCES,
                volumes(),
                new Ventilation(
                        "Devenir des 584 enregistrements",
                        584,
                        List.of(new Segment("Importés", 584, "584", Teinte.RETENU))),
                List.of(),
                List.of(),
                List.of(new Action("Ouvrir le passage", true, () -> {})));
    }

    /// Une opération **en échec** : les proportions s'inversent, et la reprise remplace l'acquittement.
    private static CompteRenduChiffre enEchec() {
        return new CompteRenduChiffre(
                "Téléversement interrompu - 9 archives sur 14",
                "5 en échec",
                Severite.ERREUR,
                List.of(),
                new Ventilation(
                        "Devenir des 14 archives",
                        14,
                        List.of(
                                new Segment("Déposées", 9, "9 archives", Teinte.RETENU),
                                new Segment("En échec", 5, "5 archives", Teinte.REFUSE))),
                // Aucun motif ici : « connexion interrompue » redirait ce que la légende annonce déjà
                // (« En échec · 5 archives »), et un compte rendu ne se répète pas - vu à la capture.
                List.of(),
                List.of(Avertissement.de(
                        "Connexion interrompue. Aucune archive perdue : les 9 déjà déposées ne seront pas renvoyées.")),
                List.of(new Action("Retenter les échecs", true, () -> {}), new Action("Plus tard", false, () -> {})));
    }

    /// Les deux barres de volume de la maquette, à échelle commune : 5,0 Go lus, 6,8 Go écrits (dont 5,0
    /// de bruts conservés). Exprimées en mégaoctets, l'unité lisible étant portée à côté.
    private static List<Barre> volumes() {
        return List.of(
                Barre.unique("Lu sur la carte", new Segment("lu", 5_000, "5,0 Go", Teinte.REFERENCE)),
                new Barre(
                        "Écrit sur le disque",
                        List.of(
                                new Segment("bruts", 5_000, "5,0 Go", Teinte.PRINCIPALE),
                                new Segment("séquences", 1_800, "1,8 Go", Teinte.SECONDAIRE))));
    }
}
