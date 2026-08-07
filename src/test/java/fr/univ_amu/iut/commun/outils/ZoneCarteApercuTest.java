package fr.univ_amu.iut.commun.outils;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import javafx.scene.Group;
import javafx.scene.Scene;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.framework.junit5.ApplicationExtension;

/// Le rectangle de masque d'un aperçu se **dérive** de la scène rendue (#3439).
///
/// ## Ce que ces cas défendent
///
/// Ces rectangles étaient recopiés à la main dans `filtrer-bruit-cartes.sh`, et un rectangle recopié se
/// démode en silence. La liste relevée le 2026-08-06 était fausse **des deux côtés** :
///
/// | Aperçu | Déclaré à la main | Réel |
/// | --- | --- | --- |
/// | `sites-modale-point` | `18,331,464,457` | `25,363,535,601` |
/// | `multisite-edition` | `12,90,865,571` | `19,144,462,564` |
///
/// Le second masquait **55 % de surface qui n'est pas de la carte** - le tableau de données entier de
/// l'écran Multisite, colonnes et barre de recherche comprises. Une régression y aurait été invisible.
/// La liste était en outre incomplète : trois écrans d'import portaient une carte qu'elle ignorait.
@ExtendWith(ApplicationExtension.class)
class ZoneCarteApercuTest {

    /// La classe que `CarteSites` se pose, et par laquelle une carte se reconnaît.
    private static final String CARTE = "carte-sites";

    @Test
    @DisplayName("le rectangle épouse la carte, pas le conteneur qui l'entoure")
    void le_rectangle_epouse_la_carte() {
        Pane racine = new Pane();
        racine.setPrefSize(800, 600);
        Region carte = carteA(100, 200, 300, 240);
        racine.getChildren().add(carte);

        assertThat(rectangleDe(racine)).contains("100,200,400,440");
    }

    @Test
    @DisplayName("une scène sans carte n'a pas de rectangle : rien à masquer, tout se compare")
    void sans_carte_aucun_rectangle() {
        Pane racine = new Pane();
        racine.setPrefSize(800, 600);
        racine.getChildren().add(new Region());

        assertThat(rectangleDe(racine))
                .as("un rectangle sur un écran sans carte aveuglerait une zone de produit")
                .isEmpty();
    }

    @Test
    @DisplayName("deux cartes sur le même écran : le rectangle les couvre TOUTES")
    void deux_cartes_sont_couvertes_ensemble() {
        // La liste écrite à la main ne portait qu'un rectangle par aperçu : un second fond de carte
        // aurait donc continué à bruiter, sans que personne n'en soit averti.
        Pane racine = new Pane();
        racine.setPrefSize(800, 600);
        racine.getChildren().addAll(carteA(50, 50, 100, 100), carteA(300, 400, 120, 80));

        assertThat(rectangleDe(racine)).contains("50,50,420,480");
    }

    @Test
    @DisplayName("une carte qui déborde de la scène est ramenée dans l'image")
    void une_carte_qui_deborde_est_bornee() {
        // Sans ce bornage, le masque viserait des pixels qui n'existent pas dans le PNG, et
        // ImageMagick refuserait le rectangle - le filtre échouerait sur un aperçu parfaitement sain.
        Pane racine = new Pane();
        racine.setPrefSize(200, 150);
        racine.getChildren().add(carteA(120, 100, 400, 400));

        assertThat(rectangleDe(racine)).contains("120,100,200,150");
    }

    @Test
    @DisplayName("déposer sans rectangle EFFACE le fichier : un écran qui perd sa carte perd son masque")
    void deposer_sans_rectangle_efface_le_fichier(@TempDir Path bac) throws Exception {
        // Le cas qui compte le plus. Sans cet effacement, un écran dont on retire la carte garderait
        // son masque, et une régression dans cette zone cesserait d'être vue : le dispositif cassé se
        // présenterait sous la forme d'un succès.
        Path png = bac.resolve("apercu-truc.png");
        Path zone = bac.resolve("apercu-truc.png" + ZoneCarteApercu.SUFFIXE);
        Files.writeString(zone, "1,2,3,4");

        ZoneCarteApercu.deposer(null, png);

        assertThat(zone).doesNotExist();
    }

    @Test
    @DisplayName("déposer un rectangle l'écrit à côté du PNG, sur une seule ligne")
    void deposer_ecrit_a_cote_du_png(@TempDir Path bac) throws Exception {
        Path png = bac.resolve("apercu-truc.png");

        ZoneCarteApercu.deposer("10,20,30,40", png);

        assertThat(Files.readString(bac.resolve("apercu-truc.png" + ZoneCarteApercu.SUFFIXE)))
                .isEqualToIgnoringWhitespace("10,20,30,40");
    }

    /// Une fausse carte, posée où on veut : seule la classe de style compte pour la détection.
    private static Region carteA(double x, double y, double largeur, double hauteur) {
        Region carte = new Region();
        carte.getStyleClass().add(CARTE);
        carte.setPrefSize(largeur, hauteur);
        carte.resizeRelocate(x, y, largeur, hauteur);
        return carte;
    }

    /// Monte `racine` dans une scène, force une passe de mise en page, et mesure.
    private static java.util.Optional<String> rectangleDe(Pane racine) {
        Scene scene = new Scene(new Group(racine), racine.getPrefWidth(), racine.getPrefHeight());
        racine.applyCss();
        racine.layout();
        return ZoneCarteApercu.rectangleDe(scene);
    }
}
