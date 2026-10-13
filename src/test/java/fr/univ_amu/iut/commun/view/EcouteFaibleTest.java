package fr.univ_amu.iut.commun.view;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.scene.Group;
import javafx.scene.Node;
import org.junit.jupiter.api.Test;

/// Une écoute faible suit sa source tant que son porteur vit, et la source ne retient pas le porteur
/// (#6184). Les deux moitiés se tiennent : un écouteur faible que rien ne tient tombe au premier
/// ramasse-miettes, et un écouteur fort tient son écran jusqu'à la fermeture de l'application.
class EcouteFaibleTest {

    @Test
    void l_ecoute_suit_la_source_tant_que_son_porteur_vit() {
        StringProperty source = new SimpleStringProperty("avant");
        Node porteur = new Group();
        List<String> recues = new ArrayList<>();
        EcouteFaible.poser(porteur, source, (observable, ancienne, nouvelle) -> recues.add(nouvelle));

        ramasser();
        source.set("après");

        assertThat(recues)
                .as("le porteur vit encore : son écoute ne doit pas être tombée au ramasse-miettes")
                .containsExactly("après");
        assertThat(porteur.getProperties()).isNotEmpty();
    }

    @Test
    void la_source_ne_retient_pas_le_porteur() {
        StringProperty source = new SimpleStringProperty("avant");
        WeakReference<Node> porteur = porteurQuiEcoute(source);

        ramasser();

        assertThat(porteur.get())
                .as("la source vit toujours, et ne doit pas tenir celui qui l'écoutait")
                .isNull();
        source.set("après");
    }

    /// Dans sa propre méthode, pour qu'aucune variable locale du test ne tienne le porteur. L'écouteur
    /// le capture, comme celui d'un vrai contrôle : c'est par là qu'une écoute forte le retiendrait.
    private static WeakReference<Node> porteurQuiEcoute(StringProperty source) {
        Node porteur = new Group();
        EcouteFaible.poser(porteur, source, (observable, ancienne, nouvelle) -> porteur.setId(nouvelle));
        return new WeakReference<>(porteur);
    }

    private static void ramasser() {
        WeakReference<Object> temoin = new WeakReference<>(new Object());
        for (int essai = 0; essai < 50 && temoin.get() != null; essai++) {
            System.gc();
        }
        assertThat(temoin.get())
                .as("le ramasse-miettes n'a pas tourné : ce test ne prouverait rien")
                .isNull();
        System.gc();
    }
}
