package fr.univ_amu.iut.importation.view;

import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.commun.model.Severite;
import fr.univ_amu.iut.commun.view.Confirmateur;
import fr.univ_amu.iut.commun.viewmodel.CompteRendu;
import fr.univ_amu.iut.commun.viewmodel.CompteRendu.Constat;
import fr.univ_amu.iut.importation.model.ApercuEcrasement;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Tests de [ConfirmationsImport] : la formulation des confirmations d'import (sans boîte de dialogue,
/// via un [Confirmateur] capturant les messages présentés).
class ConfirmationsImportTest {

    /// Confirmateur qui accepte toujours et **mémorise** ce qu'on lui présente - la chaîne (1ʳᵉ
    /// confirmation d'écrasement) **et** le compte rendu structuré (la 2ᵉ, #2223), chacun par sa porte,
    /// sans laisser le second retomber sur l'aplatissement du port.
    private static final class ConfirmateurCapturant implements Confirmateur {
        private final List<String> messages = new ArrayList<>();
        private final List<CompteRendu> comptesRendus = new ArrayList<>();

        @Override
        public boolean confirmer(String message) {
            messages.add(message);
            return true;
        }

        @Override
        public boolean confirmer(CompteRendu compteRendu) {
            comptesRendus.add(compteRendu);
            return true;
        }
    }

    @Test
    @DisplayName("Écrasement : la 2ᵉ confirmation annonce le nombre de séquences, sans mention de validations si 0")
    void ecrasement_sans_validation_ne_mentionne_pas_les_validations() {
        ConfirmateurCapturant capture = new ConfirmateurCapturant();

        assertThat(new ConfirmationsImport(capture).confirmerEcrasement(new ApercuEcrasement(5, 0)))
                .isTrue();

        // 1ʳᵉ confirmation : une phrase (le principe). 2ᵉ : un compte rendu structuré (#2223).
        assertThat(capture.messages)
                .singleElement()
                .satisfies(m -> assertThat(m).contains("Écraser"));
        assertThat(capture.comptesRendus)
                .singleElement()
                .satisfies(rendu -> assertThat(rendu.constats()).singleElement().satisfies(constat -> {
                    assertThat(constat.fait()).contains("5 séquence(s)");
                    assertThat(constat.severite()).isEqualTo(Severite.ERREUR);
                    assertThat(constat.details())
                            .as("aucune validation menacée")
                            .isEmpty();
                }));
    }

    @Test
    @DisplayName("Écrasement : la 2ᵉ confirmation annonce aussi les validations perdues quand il y en a")
    void ecrasement_avec_validations_les_annonce() {
        ConfirmateurCapturant capture = new ConfirmateurCapturant();

        assertThat(new ConfirmationsImport(capture).confirmerEcrasement(new ApercuEcrasement(5, 2)))
                .isTrue();

        // #2223 : le nombre de séquences est le constat, les validations perdues un détail sous lui.
        assertThat(capture.comptesRendus).singleElement().satisfies(rendu -> {
            Constat perte = rendu.constats().getFirst();
            assertThat(perte.fait()).contains("5 séquence(s)");
            assertThat(perte.severite()).isEqualTo(Severite.ERREUR);
            assertThat(perte.details())
                    .singleElement()
                    .satisfies(d ->
                            assertThat(d.sujet()).contains("2 validation(s)").contains("définitivement perdue(s)"));
        });
    }
}
