package fr.univ_amu.iut.commun.di;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Le diagnostic d'une liaison manquante, éprouvé sur un injecteur volontairement incomplet (#4767).
class DiagnosticGuiceTest {

    interface PieceNonLiee {}

    static class ControleurFictif {
        @Inject
        ControleurFictif(PieceNonLiee manquante) {}
    }

    static class ControleurSansDependance {}

    private static Injector injecteurVide() {
        return Guice.createInjector(new AbstractModule() {});
    }

    @Test
    @DisplayName("une liaison manquante est NOMMÉE, au lieu de sortir en version de fichier de classe")
    void une_liaison_manquante_est_nommee() {
        assertThatThrownBy(() -> DiagnosticGuice.pour(injecteurVide()).call(ControleurFictif.class))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("ControleurFictif")
                .hasMessageContaining("PieceNonLiee")
                .as("le nom de la pièce manquante est la seule chose qui fasse avancer le lecteur");
    }

    @Test
    @DisplayName("le message ne dit RIEN d'une version de fichier de classe")
    void le_message_n_envoie_pas_sur_une_fausse_piste() {
        assertThatThrownBy(() -> DiagnosticGuice.pour(injecteurVide()).call(ControleurFictif.class))
                .hasMessageNotContaining("class file major version");
    }

    @Test
    @DisplayName("une construction qui réussit passe inchangée")
    void une_construction_qui_reussit_passe() {
        assertThat(DiagnosticGuice.pour(injecteurVide()).call(ControleurSansDependance.class))
                .isInstanceOf(ControleurSansDependance.class);
    }
}
