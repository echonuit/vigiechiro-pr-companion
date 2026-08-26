package fr.univ_amu.iut.commun.view;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.stage.Stage;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;

/// Ce que l'utilisateur reçoit quand il clique « Exporter l'image… » (#3404).
///
/// Cette capacité est **documentée** (`docs/ecrans/activite.md`, `docs/ecrans/diagnostic.md`), citée
/// comme « ce qu'on joint à un signalement d'anomalie », et n'avait **aucun test** : la seule mention
/// de [ExportGraphe] dans `src/test` était un commentaire.
///
/// Ces tests ne rétablissent **pas** un refus. Le contrôle de lisibilité ne suit pas dans
/// [RenduPng], et c'est un arbitrage tenu ([ADR 2746]) : une troncature surviendrait dans une scène
/// transitoire hors écran que l'utilisateur ne voit ni ne peut corriger, et faire échouer son export
/// là-dessus le laisserait sans recours. Ce qui manquait n'était pas un garde, c'était de **regarder
/// une fois** ce que cet export produit.
@ExtendWith(ApplicationExtension.class)
class ExportGrapheTest {

    /// Le contexte que la documentation utilisateur promet sous le graphe.
    private static final List<String> LEGENDE =
            List.of("Carré 640380 · point A1 · passage n°2 (2026)", "Tranches de 30 min · filtre : chiroptères");

    @Start
    void start(Stage stage) {
        // Aucune vue à monter : `ExportGraphe` construit sa propre scène. Le stage n'est là que pour
        // que la boîte à outils JavaFX soit démarrée.
        stage.show();
    }

    private static XYChart.Series<Number, Number> serie(String nom, int contacts) {
        XYChart.Series<Number, Number> serie = new XYChart.Series<>();
        serie.setName(nom);
        for (int heure = 21; heure <= 23; heure++) {
            serie.getData().add(new XYChart.Data<>(heure, contacts + heure - 21));
        }
        return serie;
    }

    private static void graduerHeures(NumberAxis axe) {
        axe.setAutoRanging(false);
        axe.setLowerBound(20);
        axe.setUpperBound(24);
        axe.setTickUnit(1);
    }

    @Test
    @DisplayName("l'export écrit un PNG lisible, aux dimensions de la scène d'export")
    void ecrit_un_png_aux_bonnes_dimensions(FxRobot robot, @TempDir Path dossier) throws IOException {
        Path fichier = dossier.resolve("courbe.png");

        robot.interact(() -> ExportGraphe.ecrire(
                () -> List.of(serie("Pipistrelle commune", 3)),
                ExportGrapheTest::graduerHeures,
                "Contacts",
                new double[] {21.5, 6.0},
                LEGENDE,
                fichier));

        assertThat(fichier).exists();
        BufferedImage image = ImageIO.read(fichier.toFile());
        assertThat(image)
                .as("un fichier écrit qu'ImageIO ne sait pas relire n'est pas un PNG")
                .isNotNull();
        assertThat(image.getWidth()).isEqualTo(1100);
        assertThat(image.getHeight()).isEqualTo(640);
    }

    @Test
    @DisplayName("les lignes de contexte changent l'image produite, et la bande basse porte de l'encre")
    void les_lignes_de_contexte_changent_l_image(FxRobot robot, @TempDir Path dossier) throws IOException {
        // La documentation promet que l'image « porte son contexte, inscrit SOUS le graphe » : sans
        // ces mentions, une courbe collée dans un compte rendu ne dit plus de quelle nuit elle parle.
        //
        // On ne lit pas le texte - ce serait de l'OCR - et une première version de ce test comparait
        // l'encre de la bande basse avec et sans légende, en attendant « plus avec ». Mesuré : 168
        // pixels avec, 281 sans. Le graphe porte `Vgrow.ALWAYS` : sans lignes de légende il s'étend
        // jusqu'en bas, et la bande contient alors les GRADUATIONS de l'axe, plus encrées que deux
        // lignes de texte. La sonde comparait « axe » à « légende », pas « texte » à « rien ».
        //
        // Ce qui se prouve sans OCR est plus modeste, et c'est ce que le test affirme : les lignes de
        // contexte CHANGENT l'image, et la bande basse n'est pas vide.
        Path avec = dossier.resolve("avec-contexte.png");
        Path sans = dossier.resolve("sans-contexte.png");

        robot.interact(() -> {
            ExportGraphe.ecrire(
                    () -> List.of(serie("Pipistrelle commune", 3)),
                    ExportGrapheTest::graduerHeures,
                    "Contacts",
                    null,
                    LEGENDE,
                    avec);
            ExportGraphe.ecrire(
                    () -> List.of(serie("Pipistrelle commune", 3)),
                    ExportGrapheTest::graduerHeures,
                    "Contacts",
                    null,
                    List.of(),
                    sans);
        });

        long encreAvec = pixelsSombresDansLaBandeBasse(avec);
        long encreSans = pixelsSombresDansLaBandeBasse(sans);

        assertThat(encreAvec)
                .as("la bande basse d'une image légendée ne doit pas être vide")
                .isGreaterThan(0);
        assertThat(encreSans)
                .as("sans légende, la bande basse porte les graduations de l'axe : elle n'est pas vide non plus")
                .isGreaterThan(0);

        long differents = pixelsDifferents(avec, sans);
        assertThat(differents)
                .as("passer des lignes de contexte doit changer l'image : %d pixels diffèrent", differents)
                .isGreaterThan(1000);
    }

    /// Nombre de pixels qui diffèrent entre deux images de mêmes dimensions.
    private static long pixelsDifferents(Path gauche, Path droite) throws IOException {
        BufferedImage a = ImageIO.read(gauche.toFile());
        BufferedImage b = ImageIO.read(droite.toFile());
        assertThat(a.getWidth()).isEqualTo(b.getWidth());
        assertThat(a.getHeight()).isEqualTo(b.getHeight());
        long differents = 0;
        for (int y = 0; y < a.getHeight(); y++) {
            for (int x = 0; x < a.getWidth(); x++) {
                if (a.getRGB(x, y) != b.getRGB(x, y)) {
                    differents++;
                }
            }
        }
        return differents;
    }

    @Test
    @DisplayName("un passage sans aucun contact s'exporte quand même, sans exception")
    void un_passage_sans_contact_s_exporte(FxRobot robot, @TempDir Path dossier) {
        // Chemin non nominal : une nuit sans détection est un résultat valide, pas une erreur. L'export
        // doit rendre une image vide de courbe, pas une pile.
        Path fichier = dossier.resolve("vide.png");

        assertThatCode(() -> robot.interact(() -> ExportGraphe.ecrire(
                        List::of, ExportGrapheTest::graduerHeures, "Contacts", null, LEGENDE, fichier)))
                .doesNotThrowAnyException();

        assertThat(fichier).exists();
        assertThat(fichier).satisfies(f -> assertThat(Files.size(f)).isPositive());
    }

    /// Compte les pixels sombres du dernier dixième de l'image, là où les lignes de légende sont posées.
    private static long pixelsSombresDansLaBandeBasse(Path fichier) throws IOException {
        BufferedImage image = ImageIO.read(fichier.toFile());
        int depuis = image.getHeight() * 9 / 10;
        long sombres = 0;
        for (int y = depuis; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int rgb = image.getRGB(x, y);
                int luminance = (((rgb >> 16) & 0xFF) + ((rgb >> 8) & 0xFF) + (rgb & 0xFF)) / 3;
                if (luminance < 128) {
                    sombres++;
                }
            }
        }
        return sombres;
    }
}
