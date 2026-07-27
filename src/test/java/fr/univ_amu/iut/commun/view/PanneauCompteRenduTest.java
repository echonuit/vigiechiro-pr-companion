package fr.univ_amu.iut.commun.view;

import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.commun.model.Severite;
import fr.univ_amu.iut.commun.viewmodel.CompteRenduChiffre;
import fr.univ_amu.iut.commun.viewmodel.CompteRenduChiffre.Action;
import fr.univ_amu.iut.commun.viewmodel.CompteRenduChiffre.Barre;
import fr.univ_amu.iut.commun.viewmodel.CompteRenduChiffre.Motif;
import fr.univ_amu.iut.commun.viewmodel.CompteRenduChiffre.Segment;
import fr.univ_amu.iut.commun.viewmodel.CompteRenduChiffre.Teinte;
import fr.univ_amu.iut.commun.viewmodel.CompteRenduChiffre.Ventilation;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import org.assertj.core.data.Offset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;

/// Contrat du [PanneauCompteRendu] (#2358) : les largeurs sortent des quantités, et ce qui n'a rien à
/// dire disparaît.
///
/// Les largeurs sont **mesurées après mise en page** plutôt que déduites du code : c'est la seule façon
/// de prouver la règle que la maquette d'origine avait violée (deux échelles dans un même bloc, segments
/// peints l'un sur l'autre). Un test qui relirait la formule ne dirait rien de ce qui est dessiné.
///
/// [ApplicationExtension] initialise le toolkit ; aucune scène n'est affichée.
@ExtendWith(ApplicationExtension.class)
class PanneauCompteRenduTest {

    private static final double LARGEUR = 800;

    /// Les chiffres de la maquette : 612 enregistrements, 583 importés, 21 déjà présents, 8 rejetés.
    private static Ventilation devenirDeLaMaquette() {
        return new Ventilation(
                "Devenir des 612 enregistrements",
                612,
                List.of(
                        new Segment("Importés", 583, "583 fichiers", Teinte.RETENU),
                        new Segment("Déjà présents", 21, "21 fichiers", Teinte.ECARTE),
                        new Segment("Rejetés", 8, "8 fichiers", Teinte.REFUSE)));
    }

    /// Rend le panneau dans une scène de largeur connue, mise en page, pour pouvoir mesurer.
    private static PanneauCompteRendu miseEnPage(CompteRenduChiffre rendu) {
        PanneauCompteRendu panneau = new PanneauCompteRendu();
        panneau.afficher(rendu);
        Scene scene = new Scene(panneau, LARGEUR, 400);
        scene.getRoot().applyCss();
        scene.getRoot().layout();
        return panneau;
    }

    private static CompteRenduChiffre importAvecRejets() {
        return new CompteRenduChiffre(
                "Import terminé - nuit du 22/06/2026",
                "583 / 612 importés",
                Severite.AVERTISSEMENT,
                List.of(
                        Barre.unique("Lu sur la carte", new Segment("lu", 5_000, "5,0 Go", Teinte.REFERENCE)),
                        new Barre(
                                "Écrit sur le disque",
                                List.of(
                                        new Segment("bruts", 5_000, "5,0 Go", Teinte.PRINCIPALE),
                                        new Segment("séquences", 1_800, "1,8 Go", Teinte.SECONDAIRE)))),
                devenirDeLaMaquette(),
                List.of(new Motif("en-tête WAV illisible", List.of("g.wav", "h.wav"))),
                List.of("Relevé climatique absent : le diagnostic sera partiel."),
                List.of(new Action("Ouvrir le passage", true, () -> {})));
    }

    /// Les segments d'une barre, dans l'ordre : les `Region` porteuses de la classe `cr-seg`.
    private static List<Region> segments(HBox barre) {
        return barre.getChildren().stream()
                .filter(Region.class::isInstance)
                .map(Region.class::cast)
                .filter(noeud -> noeud.getStyleClass().contains("cr-seg"))
                .toList();
    }

    private static HBox barre(PanneauCompteRendu panneau, int rang) {
        List<HBox> barres = panneau.lookupAll(".cr-barre").stream()
                .filter(HBox.class::isInstance)
                .map(HBox.class::cast)
                .toList();
        return barres.get(rang);
    }

    @Test
    @DisplayName("règle 1 : la largeur de chaque segment est la fraction de sa quantité, mesurée à l'écran")
    void largeurs_proportionnelles_aux_quantites() {
        PanneauCompteRendu panneau = miseEnPage(importAvecRejets());

        HBox ventilation = barre(panneau, 0);
        List<Region> parts = segments(ventilation);
        double utile = ventilation.getWidth();
        assertThat(utile).as("la barre occupe une largeur exploitable").isGreaterThan(400);

        assertThat(parts).hasSize(3);
        assertThat(parts.get(0).getWidth()).isCloseTo(utile * 583 / 612, Offset.offset(1.0));
        assertThat(parts.get(1).getWidth()).isCloseTo(utile * 21 / 612, Offset.offset(1.0));
        assertThat(parts.get(2).getWidth()).isCloseTo(utile * 8 / 612, Offset.offset(1.0));

        // Exhaustivité à l'écran : les segments se suivent et remplissent la barre, aucun n'est peint
        // par-dessus un autre (le défaut exact de la maquette d'origine).
        double somme = parts.stream().mapToDouble(Region::getWidth).sum();
        assertThat(somme)
                .as("la somme des segments fait la largeur de la barre")
                .isCloseTo(utile, Offset.offset(1.5));
    }

    @Test
    @DisplayName("règle 1 : les deux barres de volume partagent leur échelle, « lu » ne remplit donc pas tout")
    void volumes_a_echelle_commune() {
        PanneauCompteRendu panneau = miseEnPage(importAvecRejets());

        // barre(0) est la ventilation ; les volumes suivent, dans l'ordre lu puis écrit.
        HBox lu = barre(panneau, 1);
        HBox ecrit = barre(panneau, 2);
        double largeurLu = segments(lu).get(0).getWidth();
        List<Region> partsEcrit = segments(ecrit);
        double largeurEcrit = partsEcrit.stream().mapToDouble(Region::getWidth).sum();

        assertThat(largeurEcrit)
                .as("« écrit » (6,8 Go) est la plus grande, elle porte l'échelle")
                .isGreaterThan(largeurLu);
        // Même px par Go de part et d'autre : 5,0 / 6,8 de la largeur d'« écrit ».
        assertThat(largeurLu / 5.0).isCloseTo(largeurEcrit / 6.8, Offset.offset(0.5));
        assertThat(partsEcrit.get(1).getWidth() / 1.8).isCloseTo(largeurLu / 5.0, Offset.offset(0.5));
    }

    @Test
    @DisplayName("un segment minuscule reste visible, et sa valeur exacte est en légende")
    void petit_segment_reste_visible() {
        Ventilation presqueTout = new Ventilation(
                "Devenir",
                10_000,
                List.of(
                        new Segment("Importés", 9_999, "9 999 fichiers", Teinte.RETENU),
                        new Segment("Rejetés", 1, "1 fichier", Teinte.REFUSE)));
        PanneauCompteRendu panneau = miseEnPage(new CompteRenduChiffre(
                "Import terminé",
                "9 999 / 10 000",
                Severite.SUCCES,
                List.of(),
                presqueTout,
                List.of(),
                List.of(),
                List.of()));

        List<Region> parts = segments(barre(panneau, 0));
        assertThat(parts.get(1).getWidth())
                .as("1 sur 10 000 ferait 0,08 px : le plancher le garde visible")
                .isGreaterThanOrEqualTo(3);
        assertThat(textes(panneau))
                .as("et sa valeur exacte figure en légende, jamais arrondie en silence")
                .anyMatch(texte -> texte.contains("1 fichier"));
    }

    @Test
    @DisplayName("les pourcentages de la légende sont au dixième, comme la maquette")
    void legende_au_dixieme() {
        PanneauCompteRendu panneau = miseEnPage(importAvecRejets());

        assertThat(textes(panneau))
                .anyMatch(t -> t.contains("Importés") && t.contains("95,3 %"))
                .anyMatch(t -> t.contains("Déjà présents") && t.contains("3,4 %"))
                .anyMatch(t -> t.contains("Rejetés") && t.contains("1,3 %"));
    }

    @Test
    @DisplayName("ce qui n'a rien à dire disparaît : ni bloc vide, ni trou dans la bande")
    void blocs_vides_disparaissent() {
        CompteRenduChiffre rienASignaler = new CompteRenduChiffre(
                "Import terminé - nuit du 20/06/2026",
                "584 / 584 importés",
                Severite.SUCCES,
                List.of(),
                new Ventilation("Devenir", 584, List.of(new Segment("Importés", 584, "584 fichiers", Teinte.RETENU))),
                List.of(),
                List.of(),
                List.of(new Action("Ouvrir le passage", true, () -> {})));

        PanneauCompteRendu panneau = miseEnPage(rienASignaler);

        // Aucun avertissement, aucun motif : ces blocs sont masqués ET démanagés (pas de place réservée).
        assertThat(panneau.lookupAll(".cr-avertissement")).isEmpty();
        assertThat(panneau.lookupAll(".cr-resume-motifs").stream().allMatch(n -> !n.isManaged()))
                .as("le résumé des motifs ne réserve pas de place quand il n'y a aucun rejet")
                .isTrue();
        // Les volumes non fournis ne laissent pas de barre vide : seule la ventilation est là.
        assertThat(panneau.lookupAll(".cr-barre").stream().filter(javafx.scene.Node::isManaged))
                .hasSize(1);
    }

    @Test
    @DisplayName("règle 3 : le pied porte l'action suivante, et le clic la déclenche")
    void action_suivante_est_cablee() {
        AtomicBoolean declenchee = new AtomicBoolean(false);
        CompteRenduChiffre rendu = new CompteRenduChiffre(
                "Téléversement interrompu",
                "5 en échec",
                Severite.ERREUR,
                List.of(),
                new Ventilation(
                        "Devenir",
                        14,
                        List.of(
                                new Segment("Déposées", 9, "9 archives", Teinte.RETENU),
                                new Segment("En échec", 5, "5 archives", Teinte.REFUSE))),
                List.of(),
                List.of(),
                List.of(
                        new Action("Retenter les échecs", true, () -> declenchee.set(true)),
                        new Action("Plus tard", false, () -> {})));

        PanneauCompteRendu panneau = miseEnPage(rendu);
        List<Button> boutons = panneau.lookupAll(".button").stream()
                .filter(Button.class::isInstance)
                .map(Button.class::cast)
                .toList();

        assertThat(boutons).extracting(Button::getText).containsExactly("Retenter les échecs", "Plus tard");
        assertThat(boutons.get(0).getStyleClass())
                .as("l'action suivante est mise en avant")
                .contains("bouton-primaire");
        boutons.get(0).fire();
        assertThat(declenchee)
                .as("le geste du modèle est bien celui que le bouton déclenche")
                .isTrue();
    }

    @Test
    @DisplayName("la pastille porte un libellé chiffré et la classe de sa sévérité")
    void pastille_chiffree_et_teintee() {
        PanneauCompteRendu panneau = miseEnPage(importAvecRejets());

        Label pastille = (Label) panneau.lookup(".cr-badge");
        assertThat(pastille.getText()).isEqualTo("583 / 612 importés");
        assertThat(pastille.getStyleClass()).contains("cr-badge-avertissement");
    }

    @Test
    @DisplayName("le résumé des motifs dit de quoi il s'agit sans dérouler la liste des fichiers")
    void resume_des_motifs_en_pied() {
        PanneauCompteRendu panneau = miseEnPage(importAvecRejets());

        assertThat(textes(panneau)).anyMatch(t -> t.equals("2 en-tête WAV illisible"));
        assertThat(textes(panneau))
                .as("les noms de fichiers restent hors de la bande")
                .noneMatch(t -> t.contains("g.wav"));
    }

    private static List<String> textes(PanneauCompteRendu panneau) {
        return panneau.lookupAll(".label").stream()
                .filter(Label.class::isInstance)
                .map(noeud -> ((Label) noeud).getText())
                .filter(texte -> texte != null)
                .toList();
    }
}
