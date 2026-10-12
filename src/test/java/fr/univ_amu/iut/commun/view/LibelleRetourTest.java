package fr.univ_amu.iut.commun.view;

import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.commun.model.Severite;
import fr.univ_amu.iut.commun.viewmodel.RetourOperation;
import fr.univ_amu.iut.recette.SansEcritureHorsDuFil;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.control.Label;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.kordamp.ikonli.javafx.FontIcon;
import org.testfx.framework.junit5.ApplicationExtension;

/// Contrat du [LibelleRetour], jumeau **inline** du [BandeauRetour] (#2050).
///
/// Écrit avec le composant, et non après : `BandeauRetour` a vécu sans test propre jusqu'à #1870, et sa
/// table d'icônes jusqu'à #2045 - on pouvait la vider sans rien faire rougir.
@ExtendWith({ApplicationExtension.class, SansEcritureHorsDuFil.class})
class LibelleRetourTest {

    private Label libelle;
    private ObjectProperty<RetourOperation> retour;

    @BeforeEach
    void monterLesNoeuds() {
        libelle = new Label();
        retour = new SimpleObjectProperty<>(RetourOperation.AUCUN);
        LibelleRetour.installer(libelle, retour);
    }

    @Test
    @DisplayName("Sans retour, le libellé est invisible ET retiré de la mise en page")
    void aucun_retour_libelle_absent() {
        assertThat(libelle.isVisible()).isFalse();
        assertThat(libelle.isManaged())
                .as("un encadré vide qui garderait sa place trouerait le formulaire")
                .isFalse();
        assertThat(libelle.getText()).isEmpty();
    }

    @Test
    @DisplayName("Le texte et la visibilité suivent la propriété")
    void texte_et_visibilite_suivent() {
        retour.set(RetourOperation.avertissement("Le passage n° 3 existe déjà pour ce point en 2026."));

        assertThat(libelle.getText()).isEqualTo("Le passage n° 3 existe déjà pour ce point en 2026.");
        assertThat(libelle.isVisible()).isTrue();
        assertThat(libelle.isManaged()).isTrue();

        retour.set(RetourOperation.AUCUN);
        assertThat(libelle.isVisible())
                .as("le retour levé, le libellé s'efface")
                .isFalse();
    }

    @Test
    @DisplayName("Chaque sévérité a sa classe, et une seule à la fois")
    void une_classe_par_severite() {
        Map<RetourOperation, String> attendu = new LinkedHashMap<>();
        attendu.put(RetourOperation.succes("fait"), "encart-succes");
        attendu.put(RetourOperation.info("à savoir"), "encart-info");
        attendu.put(RetourOperation.avertissement("attention"), "encart-avertissement");
        attendu.put(RetourOperation.erreur("raté"), "encart-erreur");

        attendu.forEach((valeur, classe) -> {
            retour.set(valeur);
            assertThat(libelle.getStyleClass())
                    .as("sévérité %s", valeur.severite())
                    .containsExactlyInAnyOrder(LibelleRetour.CLASSE_BASE, classe);
        });

        assertThat(Set.copyOf(attendu.values()))
                .as("deux sévérités qui partageraient une classe seraient indiscernables")
                .hasSize(attendu.size());
    }

    @Test
    @DisplayName("L'icône suit la sévérité, pas seulement la couleur")
    void l_icone_suit_la_severite() {
        // La couleur seule ne distingue rien pour qui ne la perçoit pas : la forme doit changer aussi
        // (ADR 0035). Sans cette assertion, on pouvait vider `setGraphic` sans rien faire rougir - c'est
        // exactement le trou que la Javadoc de cette classe reproche à BandeauRetour d'avoir eu (#2050).
        for (Severite severite : Severite.values()) {
            retour.set(new RetourOperation("message", severite));
            assertThat(libelle.getGraphic())
                    .as("sévérité %s : une icône est posée", severite)
                    .isInstanceOf(FontIcon.class);
            assertThat(((FontIcon) libelle.getGraphic()).getIconLiteral())
                    .as("sévérité %s : le glyphe est celui de la table partagée", severite)
                    .isEqualTo(IconesSeverite.glyphe(severite));
        }
    }

    @Test
    @DisplayName("Changer de sévérité ne laisse pas la précédente derrière soi")
    void la_classe_precedente_ne_s_accumule_pas() {
        retour.set(RetourOperation.erreur("raté"));
        retour.set(RetourOperation.avertissement("attention"));

        // `setAll` et non `add` : sans cela le libellé porterait les deux classes, et la couleur
        // dépendrait de l'ordre de la feuille de style plutôt que de la valeur.
        assertThat(libelle.getStyleClass()).doesNotContain("encart-erreur");
    }
}
