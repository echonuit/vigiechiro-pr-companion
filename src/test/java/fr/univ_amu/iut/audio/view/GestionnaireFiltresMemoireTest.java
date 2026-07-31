package fr.univ_amu.iut.audio.view;

import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.commun.view.DescripteurCritere;
import fr.univ_amu.iut.commun.view.DescripteurFiltre;
import fr.univ_amu.iut.commun.view.GestionnaireFiltres;
import fr.univ_amu.iut.commun.viewmodel.Filtres;
import fr.univ_amu.iut.validation.model.LigneObservationAudio;
import fr.univ_amu.iut.validation.model.StatutObservation;
import java.util.List;
import javafx.collections.FXCollections;
import javafx.collections.transformation.FilteredList;
import javafx.scene.control.MenuButton;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;

/// Mémoire de session des **filtres** (#484) : l'état capturé (recherche texte + puces actives avec leurs
/// valeurs) doit être restitué fidèlement. On vérifie le tour complet **restaurer → capturer** : capturer
/// après restauration doit rendre exactement l'état cible (curseur de proba, plage horaire, puce booléenne).
@ExtendWith(ApplicationExtension.class)
class GestionnaireFiltresMemoireTest {

    /// Une observation localisée sur le carré 640380, point Z1 : de quoi peupler la puce « Lieu »
    /// d'une entrée qualifiée (« 640380 · Z1 »), la forme livrée par #2995.
    private static final LigneObservationAudio LIGNE_Z1 =
            ligneLieu(1L, "PIPPIP", "Aix-en-Provence", "640380", "Z1", "Site");

    private GestionnaireFiltres<LigneObservationAudio> gestion;

    @Start
    void start(javafx.stage.Stage stage) {
        FilteredList<LigneObservationAudio> affichees = new FilteredList<>(FXCollections.observableArrayList());
        Filtres<LigneObservationAudio> filtres = new Filtres<>(affichees, () -> {});
        gestion = new GestionnaireFiltres<>(
                new TextField(),
                new MenuButton(),
                new HBox(),
                filtres,
                List.of(
                        CriteresAudio.statut(),
                        CriteresAudio.lieu(() -> List.of(LIGNE_Z1)),
                        CriteresAudio.probabilite(),
                        CriteresAudio.heure(),
                        CriteresAudio.references()),
                CriteresAudio.rechercheTexte());
    }

    @Test
    @DisplayName("#3071 : une puce à cocher survit à la mémoire de session")
    void puce_a_cocher_survit_a_la_memoire(FxRobot robot) {
        // Le geste qui déclenche le défaut est banal : poser un filtre de lieu, aller écouter un son,
        // revenir. La puce est ré-affichée - elle a donc l'air posée - mais elle ne retient plus rien,
        // et « rien de coché n'écarte rien » : l'écran montre plus que ce qu'il annonce.
        robot.interact(() -> gestion.restaurer(
                new DescripteurFiltre("", List.of(new DescripteurCritere("lieu", List.of("640380 · Z1"))))));

        DescripteurFiltre memoire = gestion.decrire();
        robot.interact(() -> gestion.restaurer(memoire));

        assertThat(gestion.decrire().criteres())
                .as("après un aller-retour en mémoire de session, la valeur cochée doit être encore là")
                .containsExactly(new DescripteurCritere("lieu", List.of("640380 · Z1")));
    }

    @Test
    @DisplayName("Restaurer un état de filtres puis le décrire redonne exactement le même état")
    void restaurer_puis_decrire_est_fidele(FxRobot robot) {
        // Curseur proba à 0,8 ; plage horaire de 3 h à 9 h ; puce Références (booléenne). Les valeurs
        // sont désormais celles qu'on VOIT, non des indices de contrôles (#3071).
        DescripteurFiltre cible = new DescripteurFiltre(
                "bruant",
                List.of(
                        new DescripteurCritere("proba", List.of("0.8")),
                        new DescripteurCritere("heure", List.of("3", "9")),
                        new DescripteurCritere("references", List.of())));

        DescripteurFiltre[] capture = new DescripteurFiltre[1];
        robot.interact(() -> {
            gestion.restaurer(cible);
            capture[0] = gestion.decrire();
        });

        assertThat(capture[0]).isEqualTo(cible);
    }

    private static LigneObservationAudio ligneLieu(
            long id, String taxon, String commune, String carre, String point, String site) {
        return new LigneObservationAudio(
                id,
                10 + id,
                7L,
                1,
                "2026-06-20",
                carre,
                point,
                site,
                taxon,
                0.9,
                null,
                null,
                StatutObservation.VALIDEE,
                false,
                null,
                45,
                null,
                taxon,
                null,
                "Chiroptères",
                "f" + id + ".wav",
                0.2,
                0.4,
                null,
                false,
                null,
                null,
                null,
                null,
                0,
                commune);
    }
}
