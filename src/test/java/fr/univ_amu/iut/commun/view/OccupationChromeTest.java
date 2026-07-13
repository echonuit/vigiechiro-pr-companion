package fr.univ_amu.iut.commun.view;

import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.commun.viewmodel.NavigationViewModel;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;

/// Voile d'occupation du **chrome** ([OccupationChrome], #1215) sur exécuteur synchrone : le voile
/// couvre la fenêtre pendant le travail, l'**opération critique** (#906) est posée le temps du
/// travail puis effacée (succès comme échec), et le résultat/l'erreur est routé au bon callback -
/// y compris quand le chrome n'est pas installé (contexte partiel : pas de voile, mêmes garanties).
@ExtendWith(ApplicationExtension.class)
class OccupationChromeTest {

    private final NavigationViewModel navigation = new NavigationViewModel();
    private final OccupationChrome occupation = new OccupationChrome(new ExecuteurTacheSynchrone(), navigation);

    @Test
    @DisplayName("#1215 : installé, occuper voile le chrome et pose l'opération critique le temps du travail")
    void occuper_voile_et_pose_l_operation_critique() {
        StackPane hote = new StackPane(new Label("chrome"));
        occupation.installer(hote);
        AtomicReference<String> operationPendant = new AtomicReference<>();
        AtomicReference<Boolean> voilePendant = new AtomicReference<>();
        List<String> resultats = new ArrayList<>();

        occupation.occuper(
                "Sauvegarde de la base…",
                "la sauvegarde de la base",
                () -> {
                    // Le travail synchrone s'exécute pendant que le voile est affiché.
                    operationPendant.set(navigation.operationCritique());
                    voilePendant.set(hote.getChildren().getLast().isVisible());
                    return "ok";
                },
                resultats::add,
                erreur -> {});

        assertThat(operationPendant.get())
                .as("fermer l'app pendant la copie doit avertir (#906)")
                .isEqualTo("la sauvegarde de la base");
        assertThat(voilePendant.get()).as("voile visible pendant le travail").isTrue();
        assertThat(resultats).containsExactly("ok");
        assertThat(navigation.operationCritique())
                .as("opération critique effacée à la fin")
                .isEmpty();
        assertThat(hote.getChildren().getLast().isVisible())
                .as("voile masqué une fois le travail fini")
                .isFalse();
    }

    @Test
    @DisplayName("#1215 : un échec est routé vers le callback d'erreur et l'opération critique est effacée")
    void echec_route_et_operation_critique_effacee() {
        occupation.installer(new StackPane(new Label("chrome")));
        List<Throwable> erreurs = new ArrayList<>();

        occupation.occuper(
                "Purge des originaux…",
                "la purge des originaux",
                () -> {
                    throw new IllegalStateException("disque plein");
                },
                resultat -> {},
                erreurs::add);

        assertThat(erreurs).hasSize(1);
        assertThat(erreurs.get(0)).hasMessage("disque plein");
        assertThat(navigation.operationCritique())
                .as("jamais d'opération critique orpheline après un échec")
                .isEmpty();
    }

    @Test
    @DisplayName("#1215 : sans chrome installé (contexte partiel), occuper exécute sans voile, mêmes garanties")
    void sans_chrome_execute_sans_voile() {
        List<String> resultats = new ArrayList<>();

        occupation.occuper("Sauvegarde…", "la sauvegarde", () -> "ok", resultats::add, erreur -> {});

        assertThat(resultats).containsExactly("ok");
        assertThat(navigation.operationCritique()).isEmpty();
    }
}
