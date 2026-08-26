package fr.univ_amu.iut.qualification.view;

import fr.univ_amu.iut.commun.model.DepotDispositionColonnes;
import fr.univ_amu.iut.commun.model.VerdictFichier;
import fr.univ_amu.iut.commun.view.GestionnaireColonnes;
import fr.univ_amu.iut.commun.view.TableDonnees;
import fr.univ_amu.iut.commun.viewmodel.Formats;
import fr.univ_amu.iut.qualification.model.SequenceEnSelection;
import fr.univ_amu.iut.qualification.viewmodel.SelectionEcouteViewModel;
import java.util.List;
import java.util.Objects;
import javafx.beans.binding.Bindings;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;

/// Controller de la **colonne gauche de la vérification** (`SelectionEcoute.fxml`, #2745) : ce qu'il y
/// a à écouter, où l'on en est, et de quoi le régler.
///
/// Sous-vue de [QualificationController], à qui elle retire treize champs `@FXML` et leur câblage.
/// C'est le bloc le plus dense d'un écran par ailleurs très fragmenté : aucun autre ne dépassait sept
/// champs.
///
/// Ce qui traverse la frontière reste au parent : la colonne « Verdict » est câblée avec les **trois
/// boutons de la colonne droite** ([VerdictParFichier]), qui jugent la séquence courante ; le parent
/// l'obtient par [#colonneVerdict()].
///
/// Les deux gestes de l'en-tête arrivent en **fonctions**, pas en dépendances : « Personnaliser… »
/// ouvre une modale que seul le parent sait situer, et « Régénérer » passe par le **confirmateur et le
/// notificateur du parent**. Les fabriquer ici en donnerait de seconds, que les doubles des tests
/// parents n'atteindraient pas (ADR 0010 et 2745, cf. #3335).
public class SelectionEcouteController {

    @FXML
    private Label lblListeTitre;

    @FXML
    private Button boutonPersonnaliser;

    @FXML
    private Button boutonRegenerer;

    @FXML
    private MenuButton menuOutils;

    /// Barre tricolore des verdicts par fichier (#1524) : suit la liste, se recompose à chaque verdict.
    @FXML
    private BarreVerdicts barreVerdicts;

    @FXML
    private Label lblRepartitionVerdicts;

    /// Erreur de chargement / régénération (#795) : masquée tant qu'il n'y a pas d'erreur.
    @FXML
    private Label lblSelectionMessage;

    @FXML
    private TableView<SequenceEnSelection> tableSequences;

    @FXML
    private TableColumn<SequenceEnSelection, String> colPosition;

    @FXML
    private TableColumn<SequenceEnSelection, String> colFichier;

    @FXML
    private TableColumn<SequenceEnSelection, String> colDuree;

    @FXML
    private TableColumn<SequenceEnSelection, Boolean> colEcoute;

    @FXML
    private TableColumn<SequenceEnSelection, VerdictFichier> colVerdict;

    private Runnable personnaliser;
    private Runnable regenerer;

    /// Câble la colonne sur le modèle **du parent**, appelée depuis son `initialize()`.
    ///
    /// @param selectionVm sélection d'écoute : lignes, titre, message d'erreur, ligne courante
    /// @param depotColonnes disposition persistée des colonnes, par écran
    /// @param personnaliser ouvre la modale de personnalisation, que seul le parent sait situer
    /// @param regenerer régénère la sélection, en passant par les porteurs du parent (ADR 0010)
    void installer(
            SelectionEcouteViewModel selectionVm,
            DepotDispositionColonnes depotColonnes,
            Runnable personnaliser,
            Runnable regenerer) {
        Objects.requireNonNull(selectionVm, "selectionVm");
        this.personnaliser = Objects.requireNonNull(personnaliser, "personnaliser");
        this.regenerer = Objects.requireNonNull(regenerer, "regenerer");

        // Densité et habillage de table uniformes (#690).
        TableDonnees.uniformiser(tableSequences);
        // Sélecteur de colonnes (#920) : clic droit + ☰ « outils » ; disposition retenue par écran (#994).
        GestionnaireColonnes.installerEtPersister(
                tableSequences,
                menuOutils,
                colonnes(),
                Objects.requireNonNull(depotColonnes, "depotColonnes"),
                "qualification",
                "principale");

        lblListeTitre
                .textProperty()
                .bind(Bindings.createStringBinding(
                        () -> "Sélection d'écoute (" + selectionVm.lignes().size() + " séquences)",
                        selectionVm.lignes()));
        tableSequences.setItems(selectionVm.lignes());
        colPosition.setCellValueFactory(
                c -> new ReadOnlyStringWrapper(Integer.toString(c.getValue().position() + 1)));
        colFichier.setCellValueFactory(
                c -> new ReadOnlyStringWrapper(c.getValue().sequence().nomFichier()));
        colDuree.setCellValueFactory(c -> new ReadOnlyStringWrapper(
                Formats.dureeSecondes(c.getValue().sequence().dureeSecondes())));
        // État d'écoute posé en icône, pas écrit en glyphe (#2237) : un pictogramme d'état binaire se
        // pose comme le badge de verdict, il ne se glisse pas dans une chaîne « ✓/○ ».
        MarqueurEcoute.lier(colEcoute);
        tableSequences
                .getSelectionModel()
                .selectedItemProperty()
                .addListener((obs, ancien, nouveau) -> selectionVm.selectionner(nouveau));

        // Barre tricolore des verdicts par fichier (#1524) : suit la liste, se recompose à chaque verdict
        // rendu. Le résumé chiffré (« 7 Bon · 3 Mauvais · … ») légende la barre en dessous.
        barreVerdicts.suivre(selectionVm.lignes());
        lblRepartitionVerdicts.textProperty().bind(barreVerdicts.resumeProperty());

        // Erreur de chargement / régénération (#795) : jusqu'ici avalée (messageProperty non branché).
        lblSelectionMessage.textProperty().bind(selectionVm.messageProperty());
        lblSelectionMessage.visibleProperty().bind(selectionVm.messageProperty().isNotEmpty());
        lblSelectionMessage.managedProperty().bind(selectionVm.messageProperty().isNotEmpty());
    }

    /// Colonnes proposées au sélecteur (#920). « Fichier » est l'identité (verrouillée) ; les autres
    /// sont masquables.
    private List<GestionnaireColonnes.Colonne> colonnes() {
        return List.of(
                new GestionnaireColonnes.Colonne(colPosition, "N°", false),
                new GestionnaireColonnes.Colonne(colFichier, "Fichier", true),
                new GestionnaireColonnes.Colonne(colDuree, "Durée", false),
                new GestionnaireColonnes.Colonne(colEcoute, "Écouté", false),
                new GestionnaireColonnes.Colonne(colVerdict, "Verdict", false));
    }

    /// La table, pour ce que le parent pilote encore : la synchronisation de la sélection au clavier.
    TableView<SequenceEnSelection> table() {
        return tableSequences;
    }

    /// La colonne « Verdict », câblée par le parent avec les trois boutons de la colonne droite : le
    /// badge et les boutons jugent la même séquence, ils se câblent ensemble ([VerdictParFichier]).
    TableColumn<SequenceEnSelection, VerdictFichier> colonneVerdict() {
        return colVerdict;
    }

    @FXML
    private void personnaliser() {
        personnaliser.run();
    }

    @FXML
    private void regenerer() {
        regenerer.run();
    }
}
