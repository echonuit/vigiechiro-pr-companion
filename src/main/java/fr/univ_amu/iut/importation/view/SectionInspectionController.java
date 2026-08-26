package fr.univ_amu.iut.importation.view;

import fr.univ_amu.iut.commun.view.VisibiliteGeree;
import fr.univ_amu.iut.commun.view.VueCompteRendu;
import fr.univ_amu.iut.commun.viewmodel.CompteRendu;
import fr.univ_amu.iut.importation.viewmodel.InspectionImportViewModel;
import java.util.Objects;
import javafx.beans.binding.Bindings;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.javafx.FontIcon;

/// Controller de la section **« 2. Inspection du dossier »** (`SectionInspection.fxml`, #2745).
///
/// Sous-vue de [ImportationController], à qui elle retire neuf champs `@FXML` et leur câblage.
/// C'est un **compte rendu en lecture seule** de ce que l'inspection a trouvé : journal du capteur,
/// relevé climatique, enregistrements, état du nommage, avertissements, découpage par nuit. Elle ne
/// pilote rien et ne collecte aucune saisie, ce qui en faisait la carte la moins dispersée de l'écran
/// (mesuré : ses champs n'étaient lus que par son propre câblage, là où ceux du rattachement
/// débordaient dans la construction de l'import).
///
/// Elle reçoit son modèle du parent et n'injecte rien : les ViewModel sont non-singleton, se les
/// procurer en donnerait de nouveaux, vides (ADR 2745, gardé par
/// `DecisionsRespecteesTest#une_sous_vue_ne_s_injecte_pas_son_modele`).
public class SectionInspectionController {

    @FXML
    private Label labelJournal;

    @FXML
    private FontIcon iconeJournal;

    @FXML
    private Label labelReleve;

    @FXML
    private FontIcon iconeReleve;

    @FXML
    private Label labelOriginaux;

    @FXML
    private FontIcon iconeOriginaux;

    @FXML
    private Label labelNommage;

    /// Ce que l'inspection a relevé (#33, #147), rendu comme un compte rendu plutôt que trois libellés.
    @FXML
    private VBox zoneAvertissements;

    /// Découpage par nuit : visible seulement quand la carte en contient plusieurs.
    @FXML
    private VBox zoneNuits;

    /// Câble la section sur le modèle **du parent**, appelée depuis son `lierDossierEtInspection`.
    ///
    /// @param inspection état de l'inspection du dossier, d'où viennent tous les libellés
    /// @param blocageNumerotation avertissement de blocage de la numérotation multi-nuits (#801)
    void installer(InspectionImportViewModel inspection, ReadOnlyObjectProperty<CompteRendu> blocageNumerotation) {
        Objects.requireNonNull(inspection, "inspection");

        // Présence dite par l'icône et la couleur, plus par un glyphe dans le texte (#2099, ADR 0035).
        DetailInspection.lier(
                labelJournal,
                iconeJournal,
                inspection.aUnJournalProperty(),
                Bindings.createStringBinding(
                        () -> inspection.aUnJournalProperty().get()
                                ? "Journal du capteur : "
                                        + inspection.resumeJournalProperty().get()
                                : "Aucun journal LogPR : import en mode dégradé (enregistreur déduit des"
                                        + " fichiers, paramètres limités)",
                        inspection.aUnJournalProperty(),
                        inspection.resumeJournalProperty()));
        DetailInspection.lier(
                labelReleve,
                iconeReleve,
                inspection.aUnReleveClimatiqueProperty(),
                Bindings.createStringBinding(
                        () -> inspection.aUnReleveClimatiqueProperty().get()
                                ? "Relevé climatique détecté"
                                : "Relevé climatique absent",
                        inspection.aUnReleveClimatiqueProperty()));
        DetailInspection.lierPresent(
                labelOriginaux,
                iconeOriginaux,
                inspection.nombreOriginauxProperty().asString("%d enregistrement(s) WAV détecté(s)"));
        labelNommage
                .textProperty()
                .bind(Bindings.createStringBinding(
                        () -> "État du nommage : "
                                + FormatsImport.libelleNommage(
                                        inspection.etatNommageProperty().get()),
                        inspection.etatNommageProperty()));

        // Trois libellés jusqu'ici, un compte rendu désormais : ils décrivent le même dossier au même
        // instant, et chacun joignait ses listes dans une phrase (#2050).
        inspection.avertissementsProperty().addListener((observable, avant, rendu) -> afficherAvertissements(rendu));
        afficherAvertissements(inspection.avertissementsProperty().get());

        // Table des nuits : construite par programme ([TableNuits]) et insérée dans sa zone, visible
        // seulement quand la carte contient plusieurs nuits.
        VisibiliteGeree.lier(zoneNuits, inspection.plusieursNuitsProperty());
        // Table + avertissement de blocage de la numérotation multi-nuits (#801), délégués à un helper
        // dédié pour garder ce controller sous le plafond de taille.
        ZoneNuits.remplir(zoneNuits, inspection.nuits(), blocageNumerotation);
    }

    private void afficherAvertissements(CompteRendu rendu) {
        zoneAvertissements
                .getChildren()
                .setAll(VueCompteRendu.rendre(rendu, VueCompteRendu.SANS_PLAFOND)
                        .getChildren());
        zoneAvertissements.getStyleClass().setAll(VueCompteRendu.CLASSE_RACINE);
        zoneAvertissements.setVisible(!rendu.estVide());
        zoneAvertissements.setManaged(!rendu.estVide());
    }
}
