package fr.univ_amu.iut.commun.viewmodel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Descripteur de source de la vue audio unifiée : capacités propres à chaque variante et défenses du
/// constructeur (le lot de passages doit être immuable, les identifiants non nuls).
class SourceObservationsTest {

    private static final ContextePassage PASSAGE =
            new ContextePassage(7L, 1, new ContexteSite("640380", "A1", "Mon site"));

    @Test
    @DisplayName("seule ParPassage permet le workflow Tadarida (import CSV / export _Vu)")
    void capacite_workflow_tadarida() {
        assertThat(new SourceObservations.ParPassage(PASSAGE).permetWorkflowTadarida())
                .isTrue();
        assertThat(new SourceObservations.ParPassages(List.of(7L), "lot").permetWorkflowTadarida())
                .isFalse();
        assertThat(new SourceObservations.ParEspece("u-1", "Pippip", null, "Pipistrelle").permetWorkflowTadarida())
                .isFalse();
        assertThat(new SourceObservations.References("u-1").permetWorkflowTadarida())
                .isFalse();
    }

    @Test
    @DisplayName("seule References permet l'export de la bibliothèque")
    void capacite_export_bibliotheque() {
        assertThat(new SourceObservations.References("u-1").permetExportBibliotheque())
                .isTrue();
        assertThat(new SourceObservations.ParPassage(PASSAGE).permetExportBibliotheque())
                .isFalse();
    }

    @Test
    @DisplayName("ParPassages copie la liste fournie (immuable, isolée des mutations de l'appelant)")
    void lot_passages_immuable() {
        List<Long> mutable = new ArrayList<>(List.of(7L, 8L));
        SourceObservations.ParPassages source = new SourceObservations.ParPassages(mutable, "sélection (2 passages)");

        mutable.add(9L);

        assertThat(source.idPassages()).containsExactly(7L, 8L);
        assertThat(source.idPassages()).isUnmodifiable();
    }

    @Test
    @DisplayName("les identifiants obligatoires sont refusés s'ils sont nuls")
    void identifiants_obligatoires() {
        assertThatThrownBy(() -> new SourceObservations.ParPassage(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new SourceObservations.References(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new SourceObservations.ParEspece(null, "Pippip", null, "Pipistrelle"))
                .isInstanceOf(NullPointerException.class);
    }
}
