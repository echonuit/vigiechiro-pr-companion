package fr.univ_amu.iut.importation.view;

import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.commun.view.Confirmateur;
import fr.univ_amu.iut.importation.model.ApercuEcrasement;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Tests de [ConfirmationsImport] : la formulation des confirmations d'import (sans boîte de dialogue,
/// via un [Confirmateur] capturant les messages présentés).
class ConfirmationsImportTest {

    /// Confirmateur qui accepte toujours et **mémorise** les messages présentés, pour les inspecter.
    private static final class ConfirmateurCapturant implements Confirmateur {
        private final List<String> messages = new ArrayList<>();

        @Override
        public boolean confirmer(String message) {
            messages.add(message);
            return true;
        }
    }

    @Test
    @DisplayName("Écrasement : la 2ᵉ confirmation annonce le nombre de séquences, sans mention de validations si 0")
    void ecrasement_sans_validation_ne_mentionne_pas_les_validations() {
        ConfirmateurCapturant capture = new ConfirmateurCapturant();

        assertThat(new ConfirmationsImport(capture).confirmerEcrasement(new ApercuEcrasement(5, 0)))
                .isTrue();

        assertThat(capture.messages).hasSize(2);
        assertThat(capture.messages.get(1)).contains("5 séquence(s)").doesNotContain("validation");
    }

    @Test
    @DisplayName("Écrasement : la 2ᵉ confirmation annonce aussi les validations perdues quand il y en a")
    void ecrasement_avec_validations_les_annonce() {
        ConfirmateurCapturant capture = new ConfirmateurCapturant();

        assertThat(new ConfirmationsImport(capture).confirmerEcrasement(new ApercuEcrasement(5, 2)))
                .isTrue();

        assertThat(capture.messages.get(1))
                .contains("5 séquence(s)")
                .contains("2 validation(s)")
                .contains("définitivement perdue(s)");
    }
}
