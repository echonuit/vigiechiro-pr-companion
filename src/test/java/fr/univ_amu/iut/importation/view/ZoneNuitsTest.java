package fr.univ_amu.iut.importation.view;

import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.commun.model.Severite;
import fr.univ_amu.iut.commun.viewmodel.CompteRendu;
import fr.univ_amu.iut.commun.viewmodel.CompteRendu.Constat;
import fr.univ_amu.iut.commun.viewmodel.CompteRendu.Detail;
import fr.univ_amu.iut.importation.viewmodel.NuitVM;
import java.util.List;
import java.util.stream.IntStream;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;

/// La **couture** entre le blocage de numérotation multi-nuits (bien testé côté ViewModel par
/// `ImportationViewModelTest`) et son rendu à l'écran (#2097).
///
/// Le ViewModel pouvait produire un blocage parfait qui n'arrivait nulle part : `ZoneNuits` n'avait aucun
/// test, et le vert du dessous donnait le sentiment que le dessus l'était aussi. Ce test tient les trois
/// promesses de la zone : le compte rendu **atteint l'écran**, il **se retire** de la mise en page quand
/// il est vide, et `SANS_PLAFOND` **montre bien tous** les détails - le choix délibéré de cette zone,
/// puisqu'ils sont bornés par la table juste au-dessus.
@ExtendWith(ApplicationExtension.class)
class ZoneNuitsTest {

    /// Le blocage n'a pas à passer par le ViewModel : ce qu'on éprouve, c'est le rendu d'un `CompteRendu`
    /// **quelconque** dans la zone. `CompteRendu.de("", List.of())` est l'état vide, celui que le VM pose
    /// au repos.
    private static final CompteRendu VIDE = CompteRendu.de("", List.of());

    private VBox zone;
    private SimpleObjectProperty<CompteRendu> blocage;

    @BeforeEach
    void monterLaZone() {
        zone = new VBox();
        ObservableList<NuitVM> nuits = FXCollections.observableArrayList();
        blocage = new SimpleObjectProperty<>(VIDE);
        ZoneNuits.remplir(zone, nuits, blocage);
    }

    /// Le rendu occupe le second enfant de la zone : la table des nuits est le premier.
    private VBox rendu() {
        return (VBox) zone.getChildren().get(1);
    }

    private List<Label> lignes(String classe) {
        return rendu().getChildren().stream()
                .filter(Label.class::isInstance)
                .map(Label.class::cast)
                .filter(label -> label.getStyleClass().contains(classe))
                .toList();
    }

    @Test
    @DisplayName("Un blocage vide ne montre rien ET se retire de la mise en page")
    void blocage_vide_zone_absente() {
        assertThat(rendu().isVisible()).isFalse();
        assertThat(rendu().isManaged())
                .as("un cadre vide qui garderait sa place laisserait croire à un blocage muet")
                .isFalse();
    }

    @Test
    @DisplayName("Un blocage non vide atteint l'écran, fait et détails compris")
    void blocage_atteint_l_ecran() {
        blocage.set(CompteRendu.de(
                "Numérotation à corriger",
                List.of(new Constat(
                        "Des numéros de passage sont déjà pris.",
                        Severite.AVERTISSEMENT,
                        List.of(
                                Detail.de("nuit du 21/06 → n° 3 déjà utilisé"),
                                Detail.de("nuit du 22/06 → n° 5 déjà utilisé"))))));

        assertThat(rendu().isVisible()).isTrue();
        assertThat(rendu().isManaged()).isTrue();
        assertThat(lignes("compte-rendu-fait"))
                .as("le fait du constat doit arriver jusqu'au nœud affiché")
                .singleElement()
                .satisfies(label -> assertThat(label.getText()).contains("déjà pris"));
        assertThat(lignes("compte-rendu-detail"))
                .as("chaque numéro pris nomme sa nuit : c'est ce que #2050 a rendu possible")
                .hasSize(2);
    }

    @Test
    @DisplayName("SANS_PLAFOND montre tous les détails, sans « … et N autre(s) »")
    void sans_plafond_montre_tout() {
        // Plus de détails qu'aucun plafond usuel (VueCompteRendu en montre 3 ou 5 ailleurs) : si la zone
        // tronquait, un « … et N autre(s) » apparaîtrait. La table juste au-dessus borne déjà le nombre,
        // donc les résumer renverrait à une liste qu'on voit déjà (#2097).
        List<Detail> beaucoup = IntStream.rangeClosed(1, 8)
                .mapToObj(n -> Detail.de("nuit n°" + n + " → conflit de numéro"))
                .toList();
        blocage.set(CompteRendu.de(
                "Numérotation à corriger",
                List.of(new Constat("Des numéros de passage sont déjà pris.", Severite.AVERTISSEMENT, beaucoup))));

        assertThat(lignes("compte-rendu-detail")).hasSize(8);
        assertThat(lignes("compte-rendu-reste"))
                .as("SANS_PLAFOND : aucun résumé « … et N autre(s) » ne doit masquer une nuit")
                .isEmpty();
    }

    @Test
    @DisplayName("Le blocage levé, la zone se retire de nouveau")
    void blocage_leve_zone_se_retire() {
        blocage.set(CompteRendu.de(
                "Numérotation à corriger",
                List.of(Constat.de("Des numéros de passage sont déjà pris.", Severite.AVERTISSEMENT))));
        assertThat(rendu().isVisible()).isTrue();

        blocage.set(VIDE);
        assertThat(rendu().isVisible())
                .as("le conflit résolu, plus rien ne subsiste à l'écran")
                .isFalse();
        assertThat(rendu().isManaged()).isFalse();
    }
}
