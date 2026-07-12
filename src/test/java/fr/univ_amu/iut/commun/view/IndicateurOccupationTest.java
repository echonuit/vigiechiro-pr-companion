package fr.univ_amu.iut.commun.view;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;

/// Overlay d'occupation ([IndicateurOccupation], #1014) sur exécuteur **synchrone** (déterministe) :
/// l'overlay se superpose au contenu, `enCours` et sa visibilité suivent le cycle du traitement, le
/// libellé est affiché, et le résultat/l'erreur est routé au bon callback. [ApplicationExtension]
/// initialise le toolkit (construction des nœuds), aucune scène affichée.
@ExtendWith(ApplicationExtension.class)
class IndicateurOccupationTest {

    private final ExecuteurTache synchrone = new ExecuteurTacheSynchrone();

    @Test
    @DisplayName("l'overlay est ajouté par-dessus le contenu de l'hôte, masqué au repos")
    void overlay_ajoute_et_masque_au_repos() {
        Label contenu = new Label("écran");
        StackPane hote = new StackPane(contenu);

        IndicateurOccupation indicateur = new IndicateurOccupation(hote, synchrone);

        assertThat(hote.getChildren()).hasSize(2);
        assertThat(hote.getChildren().getLast())
                .as("overlay au-dessus du contenu")
                .isNotSameAs(contenu);
        assertThat(indicateur.enCoursProperty().get()).isFalse();
        assertThat(hote.getChildren().getLast().isVisible())
                .as("overlay masqué au repos")
                .isFalse();
    }

    @Test
    @DisplayName("occuper : overlay visible + libellé posé PENDANT le travail, masqué après, succès routé")
    void occuper_montre_puis_masque_et_route_le_succes() {
        StackPane hote = new StackPane(new Label("écran"));
        IndicateurOccupation indicateur = new IndicateurOccupation(hote, synchrone);
        AtomicBoolean visiblePendant = new AtomicBoolean();
        AtomicBoolean enCoursPendant = new AtomicBoolean();
        List<String> succes = new ArrayList<>();

        indicateur.occuper(
                "Import en cours…",
                () -> {
                    // Le travail synchrone s'exécute pendant que l'overlay est affiché.
                    visiblePendant.set(hote.getChildren().getLast().isVisible());
                    enCoursPendant.set(indicateur.enCoursProperty().get());
                    return "resultat";
                },
                succes::add,
                erreur -> {});

        assertThat(visiblePendant).as("overlay visible pendant le travail").isTrue();
        assertThat(enCoursPendant).as("enCours vrai pendant le travail").isTrue();
        assertThat(indicateur.libelleProperty().get()).isEqualTo("Import en cours…");
        assertThat(succes).containsExactly("resultat");
        assertThat(indicateur.enCoursProperty().get())
                .as("enCours faux après le travail")
                .isFalse();
        assertThat(hote.getChildren().getLast().isVisible())
                .as("overlay masqué après le travail")
                .isFalse();
    }

    @Test
    @DisplayName("occuper : une erreur du travail est routée à `echec`, l'overlay est masqué")
    void occuper_route_l_erreur_et_masque() {
        StackPane hote = new StackPane(new Label("écran"));
        IndicateurOccupation indicateur = new IndicateurOccupation(hote, synchrone);
        List<Throwable> echecs = new ArrayList<>();
        RuntimeException panne = new IllegalStateException("dossier illisible");

        indicateur.occuper(
                "Analyse…",
                () -> {
                    throw panne;
                },
                valeur -> {},
                echecs::add);

        assertThat(echecs).containsExactly(panne);
        assertThat(indicateur.enCoursProperty().get()).isFalse();
        assertThat(hote.getChildren().getLast().isVisible()).isFalse();
    }

    @Test
    @DisplayName("enrober : construit l'hôte StackPane autour du contenu et expose sa racine")
    void enrober_construit_l_hote() {
        Label contenu = new Label("écran");

        IndicateurOccupation indicateur = IndicateurOccupation.enrober(contenu, synchrone);

        assertThat(indicateur.racine().getChildren()).contains(contenu);
        assertThat(indicateur.racine().getChildren()).hasSize(2); // contenu + overlay
    }
}
