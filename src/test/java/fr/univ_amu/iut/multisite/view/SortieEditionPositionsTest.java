package fr.univ_amu.iut.multisite.view;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import fr.univ_amu.iut.commun.view.carte.CarteSites;
import fr.univ_amu.iut.multisite.viewmodel.MultisiteViewModel;
import fr.univ_amu.iut.multisite.viewmodel.PositionsEnAttente;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javafx.scene.control.Button;
import javafx.scene.control.ToggleButton;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;

/// Le **geste de sortie du mode édition** de la carte, avec des positions déplacées non enregistrées
/// (#1431).
///
/// Ce dialogue proposait trois boutons - « Enregistrer », « Abandonner », « Annuler » - et se terminait
/// par un `showAndWait`. **Aucune** de ses issues n'était vérifiée. Or l'une d'elles **jette le travail
/// de l'utilisateur** : des points qu'il a déplacés un à un sur la carte.
///
/// Le point de conception que ce test épingle :
///
/// > **« Annuler » n'est pas une troisième décision.** C'est le refus de décider - on reste en édition,
/// > rien n'est enregistré, rien n'est perdu. Il se modélise donc comme un `Optional.empty()`, exactement
/// > comme un sélecteur de fichier qu'on ferme.
///
/// C'est ce qui a permis de porter ce dialogue par le contrat générique `DemandeurDeChoix`, sans inventer
/// un contrat « à trois issues » qui n'aurait décrit qu'un seul écran.
@ExtendWith(ApplicationExtension.class)
class SortieEditionPositionsTest {

    private PositionsEnAttente enAttente;
    private CarteSites carte;
    private ToggleButton toggle;
    private EditionPositionsCarte edition;

    /// Ce que le double de choix répondra : vide = l'utilisateur **renonce**.
    private Optional<SortieEdition> choix = Optional.empty();

    /// Options réellement proposées à l'utilisateur.
    private final List<String> proposees = new ArrayList<>();

    @BeforeEach
    void preparer() {
        MultisiteViewModel viewModel = mock(MultisiteViewModel.class);
        enAttente = mock(PositionsEnAttente.class);
        when(viewModel.positionsEnAttente()).thenReturn(enAttente);
        // Des points ont été déplacés : c'est ce qui déclenche la question au moment de sortir.
        when(enAttente.aDesEnAttente()).thenReturn(true);

        carte = mock(CarteSites.class);
        toggle = new ToggleButton();
        edition = new EditionPositionsCarte(carte, viewModel, toggle, new Button());
        edition.demandeur().definir((entete, question, options, libelle) -> {
            options.forEach(option -> proposees.add(libelle.apply(option)));
            return choix;
        });
    }

    /// L'utilisateur quitte le mode édition (il déselectionne le toggle).
    private void quitterLEdition() {
        toggle.setSelected(false);
        edition.basculer();
    }

    @Test
    @DisplayName("#1431 : « Enregistrer » persiste les déplacements et ferme l'édition")
    void enregistrer_persiste_et_ferme() {
        choix = Optional.of(SortieEdition.ENREGISTRER);

        quitterLEdition();

        assertThat(proposees)
                .as("deux décisions, une par bouton - « Annuler » n'en est pas une")
                .containsExactly("Enregistrer", "Abandonner");
        verify(enAttente).enregistrer();
        verify(enAttente, never()).annuler();
        verify(carte).setEditionActive(false);
    }

    @Test
    @DisplayName("#1431 : « Abandonner » JETTE les déplacements et ferme l'édition")
    void abandonner_jette_et_ferme() {
        choix = Optional.of(SortieEdition.ABANDONNER);

        quitterLEdition();

        // C'est la branche destructrice : elle jette un travail que l'utilisateur a fait à la main, point
        // par point. Elle n'était vérifiée nulle part.
        verify(enAttente).annuler();
        verify(enAttente, never()).enregistrer();
        verify(carte).setEditionActive(false);
    }

    @Test
    @DisplayName("#1431 : renoncer (« Annuler ») ne décide RIEN : on reste en édition, le travail est intact")
    void renoncer_ne_decide_rien() {
        choix = Optional.empty();

        quitterLEdition();

        // Renoncer et abandonner ferment tous deux le dialogue ; un seul détruit le travail. Les confondre
        // serait le pire défaut possible ici.
        verify(enAttente, never()).enregistrer();
        verify(enAttente, never()).annuler();
        verify(carte, never()).setEditionActive(false);
        assertThat(toggle.isSelected())
                .as("on n'a pas quitté l'édition : le toggle doit le montrer")
                .isTrue();
    }
}
