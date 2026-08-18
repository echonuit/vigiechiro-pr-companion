package fr.univ_amu.iut.audit.view;

import com.google.inject.Inject;
import fr.univ_amu.iut.audit.model.ConstatAudit;
import fr.univ_amu.iut.audit.viewmodel.AuditViewModel;
import fr.univ_amu.iut.audit.viewmodel.RetraitOrphelins;
import fr.univ_amu.iut.commun.model.DepotVues;
import fr.univ_amu.iut.commun.view.BandeauRetour;
import fr.univ_amu.iut.commun.view.ConfirmateurModifiable;
import fr.univ_amu.iut.commun.view.DoubleClicLigne;
import fr.univ_amu.iut.commun.view.ExecuteurTache;
import fr.univ_amu.iut.commun.view.GestionnaireColonnes;
import fr.univ_amu.iut.commun.view.IndicateurBlocage;
import fr.univ_amu.iut.commun.view.IndicateurOccupation;
import fr.univ_amu.iut.commun.view.MemoireFiltres;
import fr.univ_amu.iut.commun.view.MenuCopier;
import fr.univ_amu.iut.commun.view.OuvrirPassage;
import fr.univ_amu.iut.commun.view.RafraichirAuRetour;
import fr.univ_amu.iut.commun.view.TableDonnees;
import fr.univ_amu.iut.commun.viewmodel.ContexteSite;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.transformation.SortedList;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;

/// Écran **Audit de cohérence** (feature `audit`) : affiche le résultat de l'audit disque / base global
/// (fichiers manquants ou orphelins, préfixes non conformes, unités déposées divergentes) sous forme de
/// table de constats, avec un résumé et un bouton de relance. Pur câblage vers l'[AuditViewModel].
public class AuditController implements RafraichirAuRetour {

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

    @FXML
    private Button boutonRetirerOrphelins;

    /// Enveloppe du bouton de retrait : même raison que ci-dessus (un bouton désactivé n'a pas d'infobulle).
    @FXML
    private StackPane enveloppeRetirerOrphelins;

    /// Enveloppe du bouton : un `Button` désactivé n'affiche pas d'infobulle, l'explication se pose donc
    /// sur son conteneur (socle #789).
    @FXML
    private StackPane enveloppeAuditerPassage;

    @FXML
    private TextField champRecherche;

    @FXML
    private MenuButton menuAjoutFiltre;

    @FXML
    private FlowPane pucesFiltres;

    @FXML
    private FlowPane barreOnglets;

    @FXML
    private Button boutonToutEffacer;

    @FXML
    private HBox bandeauRetour;

    @FXML
    private Label lblRetour;

    @FXML
    private Button btnFermerRetour;

    private final AuditViewModel viewModel;
    private final ExecuteurTache executeur;

    /// Contrat socle de navigation vers M-Passage (#1347) : `audit` ne dépend pas du `view` de `passage`.
    private final OuvrirPassage ouvrirPassage;

    private final MemoireFiltres memoireFiltres;
    private final DepotVues depotVues;

    /// Confirmation du **seul geste destructif** de l'écran (#3482) : porteur injectable du socle
    /// (#1013), stub déterministe en test. Un `Alert.showAndWait()` en dur figerait TestFX headless, et
    /// le retrait resterait à jamais non testé.
    private final ConfirmateurModifiable confirmateur = new ConfirmateurModifiable();

    /// Porteur de confirmation exposé aux tests : `confirmateur().definir(stub)`.
    ConfirmateurModifiable confirmateur() {
        return confirmateur;
    }

    private IndicateurOccupation occupation;

    @Inject
    public AuditController(
            AuditViewModel viewModel,
            ExecuteurTache executeur,
            OuvrirPassage ouvrirPassage,
            MemoireFiltres memoireFiltres,
            DepotVues depotVues) {
        this.viewModel = Objects.requireNonNull(viewModel, "viewModel");
        this.executeur = Objects.requireNonNull(executeur, "executeur");
        this.ouvrirPassage = Objects.requireNonNull(ouvrirPassage, "ouvrirPassage");
        this.memoireFiltres = Objects.requireNonNull(memoireFiltres, "memoireFiltres");
        this.depotVues = Objects.requireNonNull(depotVues, "depotVues");
    }

    @FXML
    private void initialize() {
        occupation = new IndicateurOccupation(hoteOccupation, executeur);
        colSeverite.setCellValueFactory(c -> texte(c.getValue().severite().libelle()));
        colCategorie.setCellValueFactory(c -> texte(c.getValue().categorie().libelle()));
        colPassage.setCellValueFactory(c -> texte(
                c.getValue().idPassage() == null
                        ? "-"
                        : String.valueOf(c.getValue().idPassage())));
        colCible.setCellValueFactory(c -> texte(c.getValue().cible()));
        colDetail.setCellValueFactory(c -> texte(c.getValue().detail()));
        // La table montre les constats **que la barre laisse passer** (#3100) ; le résumé et le verdict
        // restent calculés sur l'audit entier. Filtrer masque des lignes, cela ne rend pas l'écran sain.
        //
        // ⚠️ La `FilteredList` est **non modifiable** : posée telle quelle, `TableView` renonce à trier et
        // **vide le sortOrder en silence** - la table cesse d'être triable sans que rien ne le dise. Le
        // `SortedList` par-dessus, comparateur lié à celui de la table, est le même montage que les
        // quatre autres écrans.
        SortedList<ConstatAudit> constatsTries = new SortedList<>(viewModel.constatsFiltres());
        constatsTries.comparatorProperty().bind(tableConstats.comparatorProperty());
        tableConstats.setItems(constatsTries);
        tableConstats.setPlaceholder(new Label("Aucun écart de cohérence détecté."));
        FiltresVuesAudit.installer(
                new FiltresVuesAudit.Barre(
                        champRecherche, menuAjoutFiltre, pucesFiltres, barreOnglets, boutonToutEffacer),
                tableConstats,
                viewModel,
                memoireFiltres,
                depotVues,
                GestionnaireColonnes.colonnesParDefaut(tableConstats));
        BandeauRetour.installer(
                bandeauRetour, lblRetour, btnFermerRetour, viewModel.retourProperty(), viewModel::effacerRetour);
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
        // Retrait des dossiers orphelins (#3482) : le libellé PORTE le nombre, pour qu'on sache ce que
        // le clic va emporter avant de cliquer - et non seulement dans la modale qui suit.
        BooleanBinding sansOrphelin = Bindings.createBooleanBinding(
                () -> viewModel.dossiersOrphelins().isEmpty(), viewModel.constats());
        boutonRetirerOrphelins.disableProperty().bind(sansOrphelin.or(occupation.enCoursProperty()));
        boutonRetirerOrphelins
                .textProperty()
                .bind(Bindings.createStringBinding(
                        () -> {
                            int nombre = viewModel.dossiersOrphelins().size();
                            return nombre == 0
                                    ? "Retirer les dossiers orphelins"
                                    : "Retirer " + nombre + " dossier(s) orphelin(s)";
                        },
                        viewModel.constats()));
        IndicateurBlocage.expliquer(
                enveloppeRetirerOrphelins,
                Bindings.when(sansOrphelin)
                        .then("Aucun dossier de session sans passage : il n'y a rien à retirer.")
                        .otherwise("Supprime du disque les dossiers de session qu'aucun passage ne réclame."));
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
                itemAuditerPassage,
                MenuCopier.creer(
                        tableConstats,
                        new MenuCopier.Entree<>(
                                "N° de passage",
                                constat -> constat.idPassage() == null ? "" : String.valueOf(constat.idPassage())),
                        new MenuCopier.Entree<>("Motif", ConstatAudit::detail)));
        viewModel.rafraichir();
    }

    @FXML
    private void rafraichir() {
        viewModel.rafraichir();
    }

    /// Relit l'audit au retour (#3964).
    ///
    /// Cet écran **audite la base** : tout écran qui écrit le périme. Il portait déjà le geste, sous un
    /// bouton ; il ne le rejouait simplement pas quand on revenait sur lui, et affichait alors un
    /// inventaire d'avant les écritures qu'on venait de faire.
    @Override
    public void rafraichirAuRetour() {
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

    /// Retire du disque les dossiers de session qu'aucun passage ne réclame (#3482, ADR-3482).
    ///
    /// ## Trois temps, et deux d'entre eux hors du fil JavaFX
    ///
    /// 1. **mesurer** ce que pèsent les dossiers (parcours disque) ;
    /// 2. **demander** confirmation en chiffrant la perte - sur le fil JavaFX, c'est une modale ;
    /// 3. **retirer**, puis relancer l'audit et poser le compte rendu de ce qui s'est **réellement**
    ///    produit.
    ///
    /// La mesure précède la question parce qu'une confirmation qui ne chiffre rien ne permet pas de
    /// décider : « retirer 3 dossiers » et « retirer 3 dossiers, 42 Go » n'appellent pas la même réponse.
    @FXML
    private void retirerLesDossiersOrphelins() {
        List<Path> dossiers = viewModel.dossiersOrphelins();
        if (dossiers.isEmpty()) {
            return;
        }
        occupation.occuper(
                "Mesure des dossiers…",
                () -> viewModel.mesurer(dossiers),
                octets -> demanderPuisRetirer(dossiers, octets),
                viewModel::signalerErreur);
    }

    private void demanderPuisRetirer(List<Path> dossiers, long octets) {
        if (!confirmateur.confirmer(RetraitOrphelins.confirmation(dossiers, octets))) {
            return;
        }
        occupation.occuper(
                "Retrait des dossiers…",
                () -> viewModel.retirer(dossiers),
                retour -> {
                    // Relancer AVANT de poser le retour : l'audit rafraîchi vide la liste des orphelins
                    // (donc le bouton et son libellé), et `appliquer` ne touche pas au bandeau.
                    viewModel.rafraichir();
                    viewModel.appliquerRetour(retour);
                },
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
