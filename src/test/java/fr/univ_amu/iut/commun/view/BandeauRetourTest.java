package fr.univ_amu.iut.commun.view;

import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.commun.viewmodel.RetourOperation;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.kordamp.ikonli.javafx.FontIcon;
import org.testfx.framework.junit5.ApplicationExtension;

/// Contrat du [BandeauRetour], **composant partagé par douze écrans et modales** depuis l'EPIC #1870.
///
/// Il n'avait aucun test propre : sa correspondance sévérité → classe CSS, son repli hors de la mise en
/// page et le câblage de sa croix n'étaient vérifiés qu'**incidemment**, par ceux des écrans qui y
/// pensaient - six sur douze pour la sévérité, trois sur douze pour la croix. Une modification de
/// `installer` serait donc passée selon l'écran regardé.
///
/// [ApplicationExtension] initialise le toolkit JavaFX (construction de nœuds) ; aucune scène affichée.
@ExtendWith(ApplicationExtension.class)
class BandeauRetourTest {

    private HBox conteneur;
    private Label texte;
    private Button fermer;
    private ObjectProperty<RetourOperation> retour;

    private int fermetures;

    /// Les nœuds se construisent ici et non en champs : à l'instanciation de la classe de test, le
    /// toolkit JavaFX n'est pas encore démarré par [ApplicationExtension].
    @BeforeEach
    void monterLesNoeuds() {
        conteneur = new HBox();
        texte = new Label();
        fermer = new Button();
        retour = new SimpleObjectProperty<>(RetourOperation.AUCUN);
        fermetures = 0;
    }

    private void installer() {
        BandeauRetour.installer(conteneur, texte, fermer, retour, () -> fermetures++);
    }

    @Test
    @DisplayName("Sans retour, le bandeau est invisible ET retiré de la mise en page")
    void aucun_retour_bandeau_absent_et_non_manage() {
        installer();

        assertThat(conteneur.isVisible()).isFalse();
        assertThat(conteneur.isManaged())
                .as("un bandeau vide qui garderait sa place laisserait un trou dans l'écran")
                .isFalse();
        assertThat(texte.getText()).isEmpty();
    }

    @Test
    @DisplayName("Chaque sévérité pose sa classe CSS, et retire celle de la sévérité précédente")
    void chaque_severite_pose_sa_classe_et_retire_la_precedente() {
        installer();

        retour.set(RetourOperation.succes("fait"));
        assertThat(conteneur.getStyleClass()).contains("retour-succes");

        retour.set(RetourOperation.info("à savoir"));
        assertThat(conteneur.getStyleClass())
                .as("une sévérité qui s'ajoute sans que l'ancienne parte donnerait deux fonds superposés")
                .contains("retour-info")
                .doesNotContain("retour-succes");

        retour.set(RetourOperation.erreur("raté"));
        assertThat(conteneur.getStyleClass()).contains("retour-erreur").doesNotContain("retour-info");
    }

    @Test
    @DisplayName("Un retour présent rend le bandeau visible et affiche son texte")
    void retour_present_affiche_le_bandeau() {
        installer();

        retour.set(RetourOperation.succes("Archives générées."));

        assertThat(conteneur.isVisible()).isTrue();
        assertThat(conteneur.isManaged()).isTrue();
        assertThat(texte.getText()).contains("Archives générées.");
    }

    @Test
    @DisplayName("La croix déclenche l'effacement confié par l'écran")
    void la_croix_declenche_l_effacement() {
        installer();
        retour.set(RetourOperation.erreur("raté"));

        fermer.fire();

        assertThat(fermetures)
                .as("la croix n'efface pas elle-même : elle rend la main à l'écran, qui possède le canal")
                .isEqualTo(1);
    }

    @Test
    @DisplayName("Un retour posé AVANT l'installation est pris en compte, pas seulement les suivants")
    void retour_deja_pose_avant_installation() {
        // Le câblage colore sur écouteur ; sans une pose initiale, un écran ouvert sur un état déjà en
        // erreur afficherait un bandeau incolore jusqu'au retour suivant.
        retour.set(RetourOperation.erreur("Passage introuvable"));

        installer();

        assertThat(conteneur.isVisible()).isTrue();
        assertThat(conteneur.getStyleClass()).contains("retour-erreur");
    }
    /// L'icône installée par [BandeauRetour], retrouvée dans le conteneur (elle y est insérée en tête).
    private String icone() {
        return conteneur.getChildren().stream()
                .filter(FontIcon.class::isInstance)
                .map(noeud -> ((FontIcon) noeud).getIconLiteral())
                .findFirst()
                .orElseThrow();
    }

    @Test
    @DisplayName("chaque sévérité a sa propre forme, pas seulement sa propre couleur")
    void chaque_severite_a_sa_forme() {
        installer();

        // La promesse est écrite dans le composant : « le bandeau dit la même chose en couleur et en
        // forme, pour qui distingue mal les couleurs comme pour qui lit vite ». Elle n'était vérifiée
        // par aucun test - la table des icônes pouvait être modifiée ou dégarnie sans rien faire rougir.
        Map<RetourOperation, String> attendu = new LinkedHashMap<>();
        attendu.put(RetourOperation.succes("fait"), "fas-check-circle");
        attendu.put(RetourOperation.info("à savoir"), "fas-info-circle");
        attendu.put(RetourOperation.avertissement("attention"), "fas-exclamation-triangle");
        attendu.put(RetourOperation.erreur("raté"), "fas-times-circle");

        attendu.forEach((valeur, glyphe) -> {
            retour.set(valeur);
            assertThat(icone()).as("sévérité %s", valeur.severite()).isEqualTo(glyphe);
        });

        assertThat(Set.copyOf(attendu.values()))
                .as("deux sévérités qui partagent une forme ne se distinguent plus quand la couleur manque")
                .hasSize(attendu.size());
    }

    @Test
    @DisplayName("#2045 : l'avertissement a sa classe propre, entre l'information et l'erreur")
    void avertissement_a_sa_classe() {
        installer();

        retour.set(RetourOperation.avertissement("Cette nuit a déjà été importée."));

        assertThat(conteneur.getStyleClass()).contains("retour-avertissement");
        assertThat(conteneur.getStyleClass()).doesNotContain("retour-info", "retour-erreur");
    }
}
