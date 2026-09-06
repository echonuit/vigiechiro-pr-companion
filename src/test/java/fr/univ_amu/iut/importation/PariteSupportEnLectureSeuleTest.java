package fr.univ_amu.iut.importation;

import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.cli.commande.Importer;
import fr.univ_amu.iut.importation.viewmodel.AvertissementsInspection;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Les deux surfaces disent-elles le support en lecture seule, et disent-elles la même chose ?
///
/// Chacune écrit dans sa langue - l'écran un constat et trois détails, le terminal une ligne - et ce
/// banc les confronte, comme `PariteCoherenceHoraireTest` le fait pour la complétude.
///
/// Il lit le **contenu** et non la seule présence : c'est ce qui manquait quand un libellé a pu
/// affirmer ce que seul le journal attestait (#5352).
///
/// Le pourquoi est dans #4991 et #5361.
class PariteSupportEnLectureSeuleTest {

    @Test
    @DisplayName("#5361 : les deux surfaces nomment l'ÉTAT mesuré")
    void les_deux_nomment_l_etat() {
        assertThat(Importer.supportEnLectureSeuleLisible())
                .as("le terminal nomme ce qui a été mesuré sur le volume")
                .contains("lecture seule");
        assertThat(AvertissementsInspection.supportEnLectureSeuleLisible())
                .as("l'écran nomme le même état")
                .contains("lecture seule");
    }

    @Test
    @DisplayName("#5361 : les deux surfaces nomment l'ENJEU, qui est la moitié qu'on abrège")
    void les_deux_nomment_l_enjeu() {
        // Dire « la carte est en lecture seule » sans dire ce qu'il en coûte laisse l'observateur
        // repartir avec elle. C'est le seul constat d'inspection dont le coût est à venir.
        assertThat(Importer.supportEnLectureSeuleLisible())
                .as("le terminal dit ce qui arrivera si rien n'est fait")
                .contains("prochaine nuit");
        assertThat(AvertissementsInspection.enjeuDuSupportEnLectureSeule())
                .as("l'écran dit le même enjeu")
                .contains("prochaine nuit");
    }

    @Test
    @DisplayName("#5361 : les deux surfaces disent le GESTE, et non seulement le constat")
    void les_deux_disent_le_geste() {
        assertThat(Importer.supportEnLectureSeuleLisible())
                .as("un avertissement sans geste laisse l'observateur sans recours")
                .contains("verrou");
    }

    @Test
    @DisplayName("#5361 : aucune des deux ne pronostique la mort de la carte")
    void aucune_ne_pronostique() {
        // Le passage en lecture seule est le mode de fin de vie ordinaire d'une carte, mais ce peut
        // aussi être un verrou mécanique poussé sans y penser. Les deux surfaces disent ce qui a été
        // MESURÉ ; l'observateur est le seul à pouvoir regarder la carte.
        for (String pronostic : List.of("morte", "hors service", "fin de vie", "défectueuse", "usée")) {
            assertThat(Importer.supportEnLectureSeuleLisible()).doesNotContain(pronostic);
            assertThat(AvertissementsInspection.supportEnLectureSeuleLisible()).doesNotContain(pronostic);
            assertThat(AvertissementsInspection.enjeuDuSupportEnLectureSeule()).doesNotContain(pronostic);
        }
    }
}
