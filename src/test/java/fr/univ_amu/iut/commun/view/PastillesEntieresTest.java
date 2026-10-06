package fr.univ_amu.iut.commun.view;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import fr.univ_amu.iut.commun.outils.FenetreAjustable;
import fr.univ_amu.iut.recette.Attente;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;

/// Ce que [PastillesEntieres] doit voir pour que les tests d'écran qui s'y fient jugent quelque
/// chose (#6101) : une pastille coupée, et une colonne qui n'en dessine aucune.
@ExtendWith(ApplicationExtension.class)
class PastillesEntieresTest {

    private static final String LIBELLE = "complétude inconnue";

    private TableView<String> table;
    private TableColumn<String, String> pastille;
    private TableColumn<String, String> texteNu;

    @Start
    void demarrer(Stage fenetre) {
        pastille = new TableColumn<>("État");
        pastille.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue()));
        pastille.setCellFactory(c -> ColonneBadge.cellule(ligne -> "badge-neutre"));
        pastille.setPrefWidth(220);
        texteNu = new TableColumn<>("Texte");
        texteNu.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue()));
        table = new TableView<>(FXCollections.observableArrayList(LIBELLE));
        table.getColumns().add(pastille);
        table.getColumns().add(texteNu);
        FenetreAjustable.poserHabillee(fenetre, new StackPane(table), 480, 200);
        FenetreAjustable.afficher(fenetre);
    }

    @Test
    @DisplayName("une pastille qui a la place est rendue entière, et son libellé est rendu à l'appelant")
    void une_pastille_qui_a_la_place_est_entiere() {
        assertThat(PastillesEntieres.libellesEntiers(table, pastille, "ce test"))
                .containsExactly(LIBELLE);
    }

    @Test
    @DisplayName("une colonne trop étroite est refusée, alors que la cellule porte toujours le libellé entier")
    void une_colonne_trop_etroite_est_refusee() {
        Attente.surLeFil(() -> pastille.setPrefWidth(90), "rétrécir la colonne", 5_000L);

        assertThat(PastillesEntieres.lire(table, pastille)).singleElement().satisfies(lue -> {
            assertThat(lue.recu()).isEqualTo(LIBELLE);
            assertThat(lue.dessine()).isNotEqualTo(LIBELLE);
            assertThat(lue.entiere()).isFalse();
        });
        assertThatThrownBy(() -> PastillesEntieres.libellesEntiers(table, pastille, "ce test"))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("coupe un libellé");
    }

    @Test
    @DisplayName("une colonne sans pastille ne rend rien : c'est à l'appelant de comparer à ce qu'il a semé")
    void une_colonne_sans_pastille_ne_rend_rien() {
        assertThat(PastillesEntieres.libellesEntiers(table, texteNu, "ce test")).isEmpty();
    }
}
