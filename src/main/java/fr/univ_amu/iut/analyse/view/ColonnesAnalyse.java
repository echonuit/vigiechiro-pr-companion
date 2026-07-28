package fr.univ_amu.iut.analyse.view;

import fr.univ_amu.iut.commun.view.ColonneBadge;
import fr.univ_amu.iut.validation.model.CarreEspeces;
import fr.univ_amu.iut.validation.model.EspeceAgregee;
import fr.univ_amu.iut.validation.model.MarqueurEspecesAEnjeu;
import fr.univ_amu.iut.validation.model.ObservationEspece;
import java.util.function.Function;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.value.ObservableValue;
import javafx.scene.control.TableColumn;

/// Câblage des colonnes des **trois tables** de l'écran « Espèces & observations » : l'inventaire par
/// espèce, la synthèse par carré, et le détail des observations de l'espèce sélectionnée.
///
/// Sorti de [AnalyseController] comme [fr.univ_amu.iut.audio.view.ColonnesAudio] l'a été du contrôleur de
/// la revue, et pour la même raison : ce contrôleur est au plafond de `NcssCount`, et vingt-et-une
/// affectations de `cellValueFactory` forment une unité cohésive qui n'a rien à faire dans un
/// `initialize`.
///
/// Chaque table a ses colonnes groupées en **objet-paramètre** : le constructeur canonique d'un record est
/// exempté d'`ExcessiveParameterList`, là où une méthode à huit paramètres serait refusée.
final class ColonnesAnalyse {

    private ColonnesAnalyse() {}

    /// Colonnes de l'**inventaire par espèce** (une ligne = une espèce).
    record Especes(
            TableColumn<EspeceAgregee, String> espece,
            TableColumn<EspeceAgregee, String> groupe,
            TableColumn<EspeceAgregee, String> detections,
            TableColumn<EspeceAgregee, String> passages,
            TableColumn<EspeceAgregee, String> carres,
            TableColumn<EspeceAgregee, String> points,
            TableColumn<EspeceAgregee, String> periode) {}

    /// Colonnes de la **synthèse par carré**.
    record Carres(
            TableColumn<CarreEspeces, String> carre,
            TableColumn<CarreEspeces, String> site,
            TableColumn<CarreEspeces, String> richesse,
            TableColumn<CarreEspeces, String> detections,
            TableColumn<CarreEspeces, String> periode) {}

    /// Colonnes du **détail** : les observations de l'espèce sélectionnée, à travers les passages.
    record Observations(
            TableColumn<ObservationEspece, String> passage,
            TableColumn<ObservationEspece, String> carre,
            TableColumn<ObservationEspece, String> richesse,
            TableColumn<ObservationEspece, String> point,
            TableColumn<ObservationEspece, String> tadarida,
            TableColumn<ObservationEspece, String> observateur,
            TableColumn<ObservationEspece, String> statut) {}

    /// L'inventaire par espèce. Le nom porte le **repère « espèce à enjeu »** (#2353) : ici une ligne
    /// **est** une espèce, l'information lui appartient déjà — inutile de dépenser une colonne pour la
    /// porter à côté.
    static void especes(Especes col, MarqueurEspecesAEnjeu marqueurEnjeu) {
        col.espece().setCellValueFactory(c -> texte(FormatAnalyse.libelleEspece(c.getValue())));
        col.espece().setCellFactory(colonne -> CelluleEspeceAEnjeu.cellule(marqueurEnjeu));
        col.groupe()
                .setCellValueFactory(
                        c -> texte(FormatAnalyse.ouTiret(c.getValue().groupe())));
        col.detections().setCellValueFactory(c -> texte(c.getValue().nbObservations()));
        col.passages().setCellValueFactory(c -> texte(c.getValue().nbPassages()));
        col.carres().setCellValueFactory(c -> texte(c.getValue().nbCarres()));
        col.points().setCellValueFactory(c -> texte(c.getValue().nbPoints()));
        col.periode()
                .setCellValueFactory(c -> texte(FormatAnalyse.periode(
                        c.getValue().anneeMin(), c.getValue().anneeMax())));
    }

    /// La synthèse par carré.
    static void carres(Carres col) {
        col.carre().setCellValueFactory(c -> texte(c.getValue().numeroCarre()));
        col.site()
                .setCellValueFactory(
                        c -> texte(FormatAnalyse.ouTiret(c.getValue().nomSite())));
        col.richesse().setCellValueFactory(c -> texte(c.getValue().richesse()));
        col.detections().setCellValueFactory(c -> texte(c.getValue().nbObservations()));
        col.periode()
                .setCellValueFactory(c -> texte(FormatAnalyse.periode(
                        c.getValue().anneeMin(), c.getValue().anneeMax())));
    }

    /// Le détail des observations. `richesseDuCarre` est fourni par l'écran : la richesse d'un carré se lit
    /// sur l'agrégation courante, que cette classe n'a pas à connaître.
    static void observations(Observations col, Function<String, String> richesseDuCarre) {
        col.passage().setCellValueFactory(c -> texte(FormatAnalyse.libellePassage(c.getValue())));
        col.carre().setCellValueFactory(c -> texte(c.getValue().numeroCarre()));
        col.richesse()
                .setCellValueFactory(
                        c -> texte(richesseDuCarre.apply(c.getValue().numeroCarre())));
        col.point().setCellValueFactory(c -> texte(c.getValue().codePoint()));
        col.tadarida()
                .setCellValueFactory(c -> texte(FormatAnalyse.taxonEtProb(
                        c.getValue().taxonTadarida(), c.getValue().probTadarida())));
        col.observateur()
                .setCellValueFactory(c -> texte(FormatAnalyse.taxonEtProb(
                        c.getValue().taxonObservateur(), c.getValue().probObservateur())));
        col.statut()
                .setCellValueFactory(
                        c -> texte(FormatAnalyse.libelleStatut(c.getValue().statut())));
        // Statut de revue en badge (#691), cohérent avec les autres tables de données.
        col.statut().setCellFactory(colonne -> ColonneBadge.cellule(obs -> FormatAnalyse.classeStatut(obs.statut())));
    }

    private static ObservableValue<String> texte(Object valeur) {
        return new ReadOnlyStringWrapper(String.valueOf(valeur));
    }
}
