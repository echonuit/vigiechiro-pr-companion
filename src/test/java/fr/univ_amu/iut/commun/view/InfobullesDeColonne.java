package fr.univ_amu.iut.commun.view;

import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.recette.Attente;
import java.util.List;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableView;

/// Lit ce qu'une colonne de table offre **au survol**, pour les colonnes qui assument de couper
/// leur texte par [ColonneAbregeable] (#5113).
///
/// Une colonne marquée sans infobulle montrerait un texte coupé que rien ne permet de relire. Ce juge
/// tient donc les deux ensemble, cellule par cellule, sur l'écran réel.
public final class InfobullesDeColonne {

    private static final long DELAI_MS = 5_000L;

    private InfobullesDeColonne() {}

    /// Une cellule telle qu'elle est à l'écran : son texte, celui de son infobulle (`null` si elle
    /// n'en porte pas), et si elle porte la marque d'abrègement.
    public record Cellule(String texte, String infobulle, boolean marquee) {

        /// Vrai si la cellule porte un texte.
        public boolean renseignee() {
            return texte != null && !texte.isBlank();
        }
    }

    /// Les cellules que la table a bâties pour la colonne intitulée `titre`, vides comprises.
    public static List<Cellule> lire(TableView<?> table, String titre) {
        return Attente.surLeFil(
                () -> {
                    table.applyCss();
                    table.layout();
                    return table.lookupAll(".table-cell").stream()
                            .filter(TableCell.class::isInstance)
                            .<TableCell<?, ?>>map(noeud -> (TableCell<?, ?>) noeud)
                            .filter(cellule -> cellule.getTableColumn() != null
                                    && titre.equals(cellule.getTableColumn().getText()))
                            .map(cellule -> new Cellule(
                                    cellule.getText(),
                                    cellule.getTooltip() == null
                                            ? null
                                            : cellule.getTooltip().getText(),
                                    cellule.getStyleClass().contains(ColonneAbregeable.MARQUE)))
                            .toList();
                },
                "lire les infobulles de la colonne « " + titre + " »",
                DELAI_MS);
    }

    /// Refuse si la colonne `titre` ne montre pas `attendus`, ou si une de ses cellules renseignées
    /// n'est pas marquée, ou ne rend pas son texte entier au survol.
    ///
    /// L'appelant nomme les textes qu'il a semés : sans cela, une table restée vide passerait ce juge
    /// sans avoir rien montré.
    public static void seRelisentAuSurvol(TableView<?> table, String titre, String... attendus) {
        List<Cellule> renseignees =
                lire(table, titre).stream().filter(Cellule::renseignee).toList();
        assertThat(renseignees)
                .as("les cellules renseignées de la colonne « %s »", titre)
                .extracting(Cellule::texte)
                .contains(attendus);
        assertThat(renseignees)
                .as(
                        "chaque cellule de « %s » est marquée « %s » ET rend son texte entier au survol :"
                                + " la marque sans l'infobulle cache un texte que rien ne permet de relire",
                        titre, ColonneAbregeable.MARQUE)
                .allSatisfy(cellule -> {
                    assertThat(cellule.marquee()).isTrue();
                    assertThat(cellule.infobulle()).isEqualTo(cellule.texte());
                });
    }
}
