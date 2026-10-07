package fr.univ_amu.iut.commun.outils;

import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.commun.view.ColonneAbregeable;
import fr.univ_amu.iut.commun.view.ColonneBadge;
import fr.univ_amu.iut.recette.Attente;
import java.util.List;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.stage.Stage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;

/// Le garde des captures devant une **cellule de table** dont le texte est coupé (#5113).
///
/// Il ne la voyait pas. Son critère de largeur compare ce que le contrôle demande à ce qu'il a reçu,
/// et une cellule de table demande la largeur de sa colonne : l'écart vaut zéro quel que soit le
/// texte. La colonne « État » de la table des nuits est partie ainsi dans une capture, « complétude
/// inconnue » coupé, sans que rien ne refuse (#5111).
///
/// Chaque cas lit lui-même ce que la cellule **dessine**, sans passer par le garde, avant de lui
/// demander son verdict : un cas qui conclurait « le garde se tait » sur une cellule qui n'est pas
/// coupée, ou qui n'existe pas, n'aurait rien éprouvé.
@ExtendWith(ApplicationExtension.class)
class LisibiliteCaptureCelluleDeTableTest {

    /// Le libellé réel de #5111, dans la largeur réelle où il était coupé.
    private static final String LIBELLE = "complétude inconnue";

    private static final double ETROITE = 130;
    private static final double LARGE = 260;
    private static final String COURT = "ok";
    private static final long DELAI_MS = 5_000L;

    private TableView<String> table;
    private TableColumn<String, String> colonne;

    /// Une cellule telle que le banc la lit : ce qu'elle a reçu, ce qu'elle dessine, et si elle est
    /// montrée, c'est-à-dire si ni elle ni aucun de ses parents n'est masqué.
    private record Cellule(String recu, String dessine, boolean montree) {

        boolean coupee() {
            return !recu.equals(dessine);
        }
    }

    /// Ce que le garde a jeté, ou `null`, à côté de ce que la table dessinait à ce moment-là.
    private record Jugement(Throwable refus, List<Cellule> cellules, long cellulesSansTexte) {

        List<Cellule> montrees() {
            return cellules.stream().filter(Cellule::montree).toList();
        }

        List<Cellule> masquees() {
            return cellules.stream().filter(cellule -> !cellule.montree()).toList();
        }
    }

    @Start
    void demarrer(Stage fenetre) {
        colonne = new TableColumn<>("État");
        colonne.setCellValueFactory(ligne -> new ReadOnlyStringWrapper(ligne.getValue()));
        colonne.setCellFactory(c -> ColonneBadge.cellule(ligne -> "badge-neutre"));
        colonne.setPrefWidth(ETROITE);
        table = new TableView<>(FXCollections.observableArrayList(LIBELLE));
        table.getColumns().add(colonne);
        FenetreAjustable.poserHabillee(fenetre, new VBox(table), 480, 400);
        FenetreAjustable.afficher(fenetre);
    }

    private void surLeFil(Runnable geste, String ceQuOnFait) {
        Attente.surLeFil(geste, ceQuOnFait, DELAI_MS);
    }

    private Jugement juger() {
        return Attente.surLeFil(
                () -> {
                    Parent racine = table.getScene().getRoot();
                    racine.applyCss();
                    racine.layout();
                    List<TableCell<?, ?>> toutes = table.lookupAll(".table-cell").stream()
                            .filter(TableCell.class::isInstance)
                            .<TableCell<?, ?>>map(noeud -> (TableCell<?, ?>) noeud)
                            .toList();
                    List<Cellule> lues = toutes.stream()
                            .filter(LisibiliteCaptureCelluleDeTableTest::porteUnTexte)
                            .map(LisibiliteCaptureCelluleDeTableTest::lire)
                            .toList();
                    Throwable refus = null;
                    try {
                        LisibiliteCapture.refuserToutTexteIllisible(table.getScene());
                    } catch (IllegalStateException probleme) {
                        refus = probleme;
                    }
                    return new Jugement(refus, lues, toutes.size() - lues.size());
                },
                "juger la table",
                DELAI_MS);
    }

    private static boolean porteUnTexte(TableCell<?, ?> cellule) {
        return cellule.getText() != null && !cellule.getText().isBlank();
    }

    private static Cellule lire(TableCell<?, ?> cellule) {
        Text rendu = (Text) cellule.lookup(".text");
        return new Cellule(cellule.getText(), rendu.getText(), estMontree(cellule));
    }

    private static boolean estMontree(Node noeud) {
        for (Node courant = noeud; courant != null; courant = courant.getParent()) {
            if (!courant.isVisible()) {
                return false;
            }
        }
        return true;
    }

    @Test
    @DisplayName("#5113 : une cellule dont le texte est coupé fait refuser la capture")
    void une_cellule_coupee_est_refusee() {
        Jugement rendu = juger();

        assertThat(rendu.montrees())
                .as("la cellule est bien coupée à l'écran : sinon le cas éprouverait autre chose")
                .singleElement()
                .satisfies(cellule -> {
                    assertThat(cellule.recu()).isEqualTo(LIBELLE);
                    assertThat(cellule.dessine()).isNotEqualTo(LIBELLE);
                });
        assertThat(rendu.refus())
                .as(
                        "la cellule dessine « %s » pour « %s » dans %.0f px, et le garde n'a rien vu",
                        rendu.montrees().getFirst().dessine(), LIBELLE, ETROITE)
                .isInstanceOf(IllegalStateException.class);
        assertThat(rendu.refus().getMessage())
                .as("le refus nomme le libellé, ce qui en reste, et la colonne à élargir")
                .contains(LIBELLE)
                .contains(rendu.montrees().getFirst().dessine())
                .contains("État")
                .contains("130 px");
    }

    @Test
    @DisplayName("#5113 : une cellule qui a la place passe, le garde ne crie pas sur une table saine")
    void une_cellule_entiere_passe() {
        surLeFil(() -> colonne.setPrefWidth(LARGE), "élargir la colonne");

        Jugement rendu = juger();

        assertThat(rendu.montrees())
                .singleElement()
                .satisfies(cellule -> assertThat(cellule.coupee()).isFalse());
        assertThat(rendu.refus()).isNull();
    }

    @Test
    @DisplayName("#5113 : une colonne qui assume l'abrègement ET le rend au survol reste exemptée")
    void une_colonne_abregeable_qui_se_relit_reste_exemptee() {
        surLeFil(() -> ColonneAbregeable.assumer(colonne), "assumer l'abrègement de la colonne");

        Jugement rendu = juger();

        assertThat(rendu.montrees())
                .as("la cellule est coupée : c'est l'exemption qui retient le garde, pas la place")
                .singleElement()
                .satisfies(cellule -> assertThat(cellule.coupee()).isTrue());
        assertThat(rendu.refus()).isNull();
    }

    @Test
    @DisplayName("#5113 : la marque posée seule est refusée, elle cacherait un texte que rien ne rend")
    void une_marque_sans_infobulle_est_refusee() {
        surLeFil(() -> colonne.getStyleClass().add(LisibiliteCapture.ABREGEABLE), "marquer la colonne, sans plus");

        Jugement rendu = juger();

        assertThat(rendu.montrees())
                .singleElement()
                .satisfies(cellule -> assertThat(cellule.coupee()).isTrue());
        assertThat(rendu.refus())
                .as("une marque sans infobulle ferait taire le garde sur un texte illisible")
                .isInstanceOf(IllegalStateException.class);
        assertThat(rendu.refus().getMessage()).contains("sans infobulle").contains(LIBELLE);
    }

    @Test
    @DisplayName("#5113 : une infobulle qui dit autre chose que le texte de la cellule n'exempte pas")
    void une_infobulle_qui_ne_rend_pas_le_texte_n_exempte_pas() {
        surLeFil(
                () -> {
                    colonne.setCellFactory(
                            c -> ColonneBadge.cellule(ligne -> "badge-neutre", ligne -> "mesuré le 12 juin"));
                    colonne.getStyleClass().add(LisibiliteCapture.ABREGEABLE);
                },
                "poser une infobulle étrangère au texte, et la marque");

        Jugement rendu = juger();

        assertThat(rendu.montrees())
                .singleElement()
                .satisfies(cellule -> assertThat(cellule.coupee()).isTrue());
        assertThat(rendu.refus()).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("#5113 : l'infobulle sans la marque n'exempte pas non plus, l'abrègement doit être assumé")
    void une_infobulle_sans_marque_n_exempte_pas() {
        surLeFil(
                () -> colonne.setCellFactory(c -> ColonneBadge.cellule(ligne -> "badge-neutre", ligne -> ligne)),
                "poser l'infobulle, sans la marque");

        Jugement rendu = juger();

        assertThat(rendu.montrees())
                .singleElement()
                .satisfies(cellule -> assertThat(cellule.coupee()).isTrue());
        assertThat(rendu.refus()).isInstanceOf(IllegalStateException.class).hasMessageNotContaining("sans infobulle");
    }

    @Test
    @DisplayName("#5113 : une table qui assume l'abrègement exempte ses cellules qui se relisent au survol")
    void une_table_abregeable_exempte_ses_cellules_qui_se_relisent() {
        surLeFil(
                () -> {
                    colonne.setCellFactory(c -> ColonneBadge.cellule(ligne -> "badge-neutre", ligne -> ligne));
                    table.getStyleClass().add(LisibiliteCapture.ABREGEABLE);
                },
                "poser l'infobulle, et la marque sur la table");

        Jugement rendu = juger();

        assertThat(rendu.montrees())
                .singleElement()
                .satisfies(cellule -> assertThat(cellule.coupee()).isTrue());
        assertThat(rendu.refus()).isNull();
    }

    @Test
    @DisplayName("#5113 : les cellules vides d'une table ne sont pas jugées")
    void les_cellules_vides_ne_sont_pas_jugees() {
        surLeFil(() -> table.getItems().setAll(COURT), "ne garder qu'une ligne courte");

        Jugement rendu = juger();

        assertThat(rendu.montrees())
                .singleElement()
                .satisfies(cellule -> assertThat(cellule.coupee()).isFalse());
        assertThat(rendu.cellulesSansTexte())
                .as("la table a bien bâti des cellules vides sous sa ligne : sinon rien n'est éprouvé")
                .isPositive();
        assertThat(rendu.refus()).isNull();
    }

    @Test
    @DisplayName("#5113 : une table masquée n'est pas jugée, ses cellules coupées restant dans le graphe")
    void une_table_masquee_n_est_pas_jugee() {
        surLeFil(() -> table.setVisible(false), "masquer la table");

        Jugement rendu = juger();

        assertThat(rendu.montrees()).isEmpty();
        assertThat(rendu.masquees())
                .as("la cellule coupée existe toujours, masquée : sinon rien n'est éprouvé")
                .singleElement()
                .satisfies(cellule -> assertThat(cellule.coupee()).isTrue());
        assertThat(rendu.refus()).isNull();
    }

    @Test
    @DisplayName("#5113 : le refus suit ce qui est montré, une ligne sortie du champ n'est plus jugée")
    void une_ligne_sortie_du_champ_n_est_plus_jugee() {
        // Cinq lignes courtes, puis douze lignes au libellé trop long. Table haute, des lignes longues
        // sont montrées et le garde refuse. Table basse, elles sortent du champ, et JavaFX 26 les
        // retire du graphe : il n'y garde qu'une ligne de réserve, masquée et sans texte. Le second
        // verdict ne peut donc rougir par aucune mutation du garde ; c'est le premier qui le peut, et
        // le cas tient par la paire.
        surLeFil(
                () -> {
                    table.getItems().setAll(COURT, COURT, COURT, COURT, COURT);
                    for (int i = 0; i < 12; i++) {
                        table.getItems().add(LIBELLE);
                    }
                },
                "semer cinq lignes courtes puis douze longues");
        Jugement haute = juger();
        assertThat(haute.refus())
                .as("table haute, des lignes longues sont montrées et coupées : le garde refuse")
                .isInstanceOf(IllegalStateException.class);

        surLeFil(
                () -> {
                    table.setMinHeight(0);
                    table.setPrefHeight(80);
                    table.setMaxHeight(80);
                },
                "abaisser la table");
        Jugement basse = juger();

        assertThat(basse.cellules())
                .as("table basse, le graphe ne porte plus que des lignes courtes, toutes montrées")
                .isNotEmpty()
                .allSatisfy(cellule -> {
                    assertThat(cellule.recu()).isEqualTo(COURT);
                    assertThat(cellule.montree()).isTrue();
                });
        assertThat(basse.refus()).isNull();
    }
}
