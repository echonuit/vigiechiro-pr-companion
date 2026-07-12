package fr.univ_amu.iut.commun.view;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import javafx.fxml.FXMLLoader;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Garde-fou du chargement FXML : [ChargeurFxml] doit renvoyer un loader localisé quand la ressource
/// existe, et lever un message **actionnable** (plutôt que le « Location is not set » opaque de JavaFX)
/// quand `target/classes` est obsolète. `new FXMLLoader(url)` n'initialise pas le toolkit : test unitaire
/// pur, sans TestFX.
class ChargeurFxmlTest {

    @Test
    @DisplayName("FXML présent : loader avec localisation renseignée")
    void fxml_present() {
        FXMLLoader loader = ChargeurFxml.chargeur(ChargeurFxml.class, "MainView.fxml");

        assertThat(loader.getLocation()).isNotNull();
    }

    @Test
    @DisplayName("FXML absent : IllegalStateException actionnable citant le fichier et la reconstruction")
    void fxml_absent() {
        assertThatIllegalStateException()
                .isThrownBy(() -> ChargeurFxml.chargeur(ChargeurFxml.class, "Inexistant.fxml"))
                .withMessageContaining("Inexistant.fxml")
                .withMessageContaining("clean");
    }

    @Test
    @DisplayName("Arguments nuls refusés")
    void arguments_nuls() {
        assertThatNullPointerException().isThrownBy(() -> ChargeurFxml.chargeur(null, "X.fxml"));
        assertThatNullPointerException().isThrownBy(() -> ChargeurFxml.chargeur(ChargeurFxml.class, null));
    }
}
