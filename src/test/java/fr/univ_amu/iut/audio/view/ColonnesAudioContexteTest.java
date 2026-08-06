package fr.univ_amu.iut.audio.view;

import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.validation.model.LigneObservationAudio;
import java.time.LocalDateTime;
import javafx.scene.control.TableColumn;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;

/// Les colonnes de **contexte** de « Sons & validation » et leur seule règle : elles disparaissent
/// quand la source cible un **unique passage**, où elles porteraient la même valeur sur toutes les
/// lignes.
///
/// La règle existait depuis #1194 sans qu'aucun test ne la tienne : elle vit dans un `setVisible` de
/// quatre lignes, qu'une inattention aurait effacé sans rien faire rougir. #3164, en ajoutant la
/// commune au même groupe, a rendu ce trou visible - c'est le moment de le combler plutôt que d'y
/// verser une cinquième colonne.
@ExtendWith(ApplicationExtension.class)
class ColonnesAudioContexteTest {

    private static ColonnesAudio.Colonnes colonnes() {
        return new ColonnesAudio.Colonnes(
                colonne(),
                colonne(),
                colonne(),
                colonne(),
                colonne(),
                colonne(),
                colonne(),
                colonne(),
                colonne(),
                colonne(),
                colonne(),
                colonne(),
                colonne(),
                colonne(),
                new TableColumn<LigneObservationAudio, LocalDateTime>(),
                colonne(),
                colonne(),
                colonne(),
                colonne(),
                colonne(),
                colonne(),
                colonne());
    }

    private static TableColumn<LigneObservationAudio, String> colonne() {
        return new TableColumn<>();
    }

    @Test
    @DisplayName("#3164 : sur un passage unique, le contexte disparaît - commune comprise")
    void le_contexte_disparait_sur_un_passage_unique() {
        ColonnesAudio.Colonnes col = colonnes();

        ColonnesAudio.adapterAuContexte(col, true);

        assertThat(col.passage().isVisible()).isFalse();
        assertThat(col.carre().isVisible()).isFalse();
        assertThat(col.point().isVisible()).isFalse();
        assertThat(col.date().isVisible()).isFalse();
        assertThat(col.commune().isVisible())
                .as("toutes les lignes d'un passage partagent la commune de son point")
                .isFalse();
    }

    @Test
    @DisplayName("#3164 : dès que la source couvre plusieurs passages, le contexte revient")
    void le_contexte_revient_sur_plusieurs_passages() {
        // Le pendant, sans lequel le test précédent passerait avec des colonnes masquées en permanence.
        ColonnesAudio.Colonnes col = colonnes();
        ColonnesAudio.adapterAuContexte(col, true);

        ColonnesAudio.adapterAuContexte(col, false);

        assertThat(col.passage().isVisible()).isTrue();
        assertThat(col.carre().isVisible()).isTrue();
        assertThat(col.point().isVisible()).isTrue();
        assertThat(col.date().isVisible()).isTrue();
        assertThat(col.commune().isVisible())
                .as("c'est là qu'elle sert : dire de quelle commune vient chaque ligne")
                .isTrue();
    }

    @Test
    @DisplayName("Les colonnes qui ne sont pas du contexte ne bougent pas")
    void les_autres_colonnes_ne_bougent_pas() {
        // Le garde qui empêche « masquer le contexte » de devenir « masquer la table ».
        ColonnesAudio.Colonnes col = colonnes();

        ColonnesAudio.adapterAuContexte(col, true);

        assertThat(col.tadarida().isVisible()).isTrue();
        assertThat(col.fichier().isVisible()).isTrue();
        assertThat(col.statut().isVisible()).isTrue();
    }

    @Test
    @DisplayName("#3300 : le nom du carré a sa colonne, masquée au départ mais offerte au sélecteur")
    void nom_du_carre_disponible_mais_masque() {
        // La recherche libre de cet écran retient une ligne sur ce nom (`CriteresAudio.correspond`), et
        // seule la puce « Lieu » le montrait. La colonne existe donc, mais MASQUÉE : cette table en
        // compte 22, et le sélecteur (#919) est fait pour ça.
        ColonnesAudio.Colonnes col = colonnes();
        ColonnesAudio.configurer(col, ligne -> false, (id, texte) -> {});

        assertThat(col.nomSite().getCellValueFactory())
                .as("câblée : sans cela, la colonne cochée resterait vide")
                .isNotNull();
        assertThat(ColonnesAudio.pourLeSelecteur(col, new TableColumn<>(), new TableColumn<>()))
                .as("offerte au sélecteur, sinon elle serait inatteignable")
                .anySatisfy(colonne -> assertThat(colonne.libelle()).isEqualTo("Nom du carré"));
    }
}
