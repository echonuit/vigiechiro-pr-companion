package fr.univ_amu.iut.audit.view;

import com.google.inject.Inject;
import fr.univ_amu.iut.audit.model.ConstatAudit;
import fr.univ_amu.iut.audit.viewmodel.AuditViewModel;
import fr.univ_amu.iut.commun.view.DoubleClicLigne;
import fr.univ_amu.iut.commun.view.ExecuteurTache;
import fr.univ_amu.iut.commun.view.GestionnaireColonnes;
import fr.univ_amu.iut.commun.view.IndicateurBlocage;
import fr.univ_amu.iut.commun.view.IndicateurOccupation;
import fr.univ_amu.iut.commun.view.OuvrirPassage;
import fr.univ_amu.iut.commun.view.TableDonnees;
import fr.univ_amu.iut.commun.viewmodel.ContexteSite;
import java.util.Objects;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.StackPane;

/// Écran **Audit de cohérence** (feature `audit`) : affiche le résultat de l'audit disque / base global
/// (fichiers manquants ou orphelins, préfixes non conformes, unités déposées divergentes) sous forme de
/// table de constats, avec un résumé et un bouton de relance. Pur câblage vers l'[AuditViewModel].
public class AuditController {

    @FXML
    private StackPane hoteOccupation;

    @FXML
    private Label lblResume;

    @FXML
    private TableView<ConstatAudit> tableConstats;

    @FXML
    private TableColumn<ConstatAudit, String> colSeverite;

    @FXML
    private TableColumn<ConstatAudit, String> colCategorie;

    @FXML
    private TableColumn<ConstatAudit, String> colPassage;

    @FXML
    private TableColumn<ConstatAudit, String> colCible;

    @FXML
    private TableColumn<ConstatAudit, String> colDetail;

    @FXML
    private Button boutonVerifierEnLigne;

    @FXML
    private Button boutonAuditerPassage;

    /// Enveloppe du bouton : un `Button` désactivé n'affiche pas d'infobulle, l'explication se pose donc
    /// sur son conteneur (socle #789).
    @FXML
    private StackPane enveloppeAuditerPassage;

    private final AuditViewModel viewModel;
    private final ExecuteurTache executeur;

    /// Contrat socle de navigation vers M-Passage (#1347) : `audit` ne dépend pas du `view` de `passage`.
    private final OuvrirPassage ouvrirPassage;

    private IndicateurOccupation occupation;

    @Inject
    public AuditController(AuditViewModel viewModel, ExecuteurTache executeur, OuvrirPassage ouvrirPassage) {
        this.viewModel = Objects.requireNonNull(viewModel, "viewModel");
        this.executeur = Objects.requireNonNull(executeur, "executeur");
        this.ouvrirPassage = Objects.requireNonNull(ouvrirPassage, "ouvrirPassage");
    }

    @FXML
    private void initialize() {
        occupation = new IndicateurOccupation(hoteOccupation, executeur);
        colSeverite.setCellValueFactory(c -> texte(c.getValue().severite().name()));
        colCategorie.setCellValueFactory(c -> texte(c.getValue().categorie().name()));
        colPassage.setCellValueFactory(c -> texte(
                c.getValue().idPassage() == null
                        ? "-"
                        : String.valueOf(c.getValue().idPassage())));
        colCible.setCellValueFactory(c -> texte(c.getValue().cible()));
        colDetail.setCellValueFactory(c -> texte(c.getValue().detail()));
        tableConstats.setItems(viewModel.constats());
        tableConstats.setPlaceholder(new Label("Aucun écart de cohérence détecté."));
        // Un constat cite un passage : le double-clic l'ouvre (#1347). Jusqu'ici la table nommait le
        // coupable et laissait l'utilisateur le retrouver à la main, alors que partout ailleurs dans
        // l'application une ligne de table s'ouvre au double-clic.
        TableDonnees.uniformiserNavigable(tableConstats);
        // Double-clic → ouvre le passage cité ; clic droit sélectionne la ligne pour le menu de ligne (#1796).
        DoubleClicLigne.installer(tableConstats, this::ouvrirLePassage);
        lblResume.textProperty().bind(viewModel.resumeProperty());
        // Le voile bloque déjà l'écran pendant la vérification ; le grisage du bouton rend l'état
        // « en cours » lisible sans setDisable posé à la main (#1254).
        boutonVerifierEnLigne.disableProperty().bind(occupation.enCoursProperty());
        // « Auditer ce passage » n'a de sens que sur un constat qui cite un passage : le bouton l'annonce
        // en restant désactivé, plutôt que de ne rien faire au clic (affordance #789).
        BooleanBinding sansPassageSelectionne = Bindings.createBooleanBinding(
                () -> {
                    ConstatAudit selection = tableConstats.getSelectionModel().getSelectedItem();
                    return selection == null || selection.idPassage() == null;
                },
                tableConstats.getSelectionModel().selectedItemProperty());
        boutonAuditerPassage.disableProperty().bind(sansPassageSelectionne);
        IndicateurBlocage.expliquer(
                enveloppeAuditerPassage,
                Bindings.when(sansPassageSelectionne)
                        .then("Sélectionnez un constat qui cite un passage pour n'auditer que celui-ci.")
                        .otherwise("Relance l'audit sur ce seul passage (après l'avoir réparé)."));
        // Menu de ligne au clic droit (#1796), en miroir du double-clic et du bouton : « Ouvrir le passage »
        // et « Auditer ce passage », désactivés (affordance #789) quand le constat ne cite aucun passage. La
        // table reçoit du même coup son sélecteur « Colonnes… » (elle n'en avait pas).
        MenuItem itemOuvrirPassage = new MenuItem("Ouvrir le passage");
        itemOuvrirPassage.disableProperty().bind(sansPassageSelectionne);
        itemOuvrirPassage.setOnAction(
                evenement -> ouvrirLePassage(tableConstats.getSelectionModel().getSelectedItem()));
        MenuItem itemAuditerPassage = new MenuItem("Auditer ce passage");
        itemAuditerPassage.disableProperty().bind(sansPassageSelectionne);
        itemAuditerPassage.setOnAction(evenement -> auditerLePassageSelectionne());
        GestionnaireColonnes.installerClicDroit(
                tableConstats,
                GestionnaireColonnes.colonnesParDefaut(tableConstats),
                itemOuvrirPassage,
                itemAuditerPassage);
        viewModel.rafraichir();
    }

    @FXML
    private void rafraichir() {
        viewModel.rafraichir();
    }

    /// Vérification **en ligne** (confrontation au serveur) : exécutée **hors du fil JavaFX** (réseau)
    /// sous l'overlay d'occupation (#1254), puis le résultat (ou l'erreur, filet #795) est appliqué sur
    /// le fil JavaFX.
    @FXML
    private void verifierEnLigne() {
        occupation.occuper(
                "Vérification en ligne…",
                viewModel::calculerAvecEnLigne,
                viewModel::appliquer,
                viewModel::signalerErreur);
    }

    /// Ouvre le passage cité par `constat` (#1347). Un constat qui ne cite aucun passage (ou dont le site
    /// est introuvable) n'ouvre rien : il n'y a pas de destination, et un message d'erreur serait du bruit.
    private void ouvrirLePassage(ConstatAudit constat) {
        viewModel
                .contexteDuPassage(constat.idPassage())
                .ifPresent(contexte -> ouvrirPassage.ouvrir(
                        constat.idPassage(),
                        new ContexteSite(contexte.numeroCarre(), contexte.codePoint(), contexte.nomSite())));
    }

    /// Audit **ciblé** du passage sélectionné (#1347) : après avoir réparé une nuit, on veut vérifier
    /// **celle-là**, pas relancer tout le workspace.
    @FXML
    private void auditerLePassageSelectionne() {
        ConstatAudit selection = tableConstats.getSelectionModel().getSelectedItem();
        if (selection != null && selection.idPassage() != null) {
            viewModel.auditerPassage(selection.idPassage());
        }
    }

    private static ReadOnlyStringWrapper texte(String valeur) {
        return new ReadOnlyStringWrapper(valeur);
    }
}
