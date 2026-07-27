package fr.univ_amu.iut.saison.view;

import com.google.inject.Inject;
import fr.univ_amu.iut.commun.view.ColonneBadge;
import fr.univ_amu.iut.commun.view.DoubleClicLigne;
import fr.univ_amu.iut.commun.view.OuvrirPassage;
import fr.univ_amu.iut.commun.view.OuvrirSite;
import fr.univ_amu.iut.commun.view.RafraichirAuRetour;
import fr.univ_amu.iut.commun.viewmodel.ContexteSite;
import fr.univ_amu.iut.saison.model.CasePassage;
import fr.univ_amu.iut.saison.model.LigneSaison;
import fr.univ_amu.iut.saison.viewmodel.SaisonViewModel;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.stream.Collectors;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;

/// Controller de l'écran **M-Saison** : une ligne par point suivi, l'état de ses deux passages en
/// pastilles (couleurs reprises du modèle), la colonne « reste à faire », un sélecteur d'année et le
/// double-clic vers le passage concerné (ou le carré du point s'il n'y a pas encore de passage).
///
/// Implémente [RafraichirAuRetour] : revenir d'un passage ouvert depuis le tableau recharge le solde,
/// pour ne pas reproduire le défaut de compteurs figés d'accueil (#1376).
public class SaisonController implements RafraichirAuRetour {

    private static final DateTimeFormatter JOUR_MOIS = DateTimeFormatter.ofPattern("dd/MM");

    @FXML
    private ComboBox<Integer> choixAnnee;

    @FXML
    private Label lblResume;

    @FXML
    private Label lblSignalement;

    @FXML
    private TableView<LigneSaison> tableSaison;

    @FXML
    private TableColumn<LigneSaison, String> colCarre;

    @FXML
    private TableColumn<LigneSaison, String> colPoint;

    @FXML
    private TableColumn<LigneSaison, String> colPassage1;

    @FXML
    private TableColumn<LigneSaison, String> colPassage2;

    @FXML
    private TableColumn<LigneSaison, String> colHorsProtocole;

    @FXML
    private TableColumn<LigneSaison, String> colResteAFaire;

    private final SaisonViewModel viewModel;
    private final OuvrirPassage ouvrirPassage;
    private final OuvrirSite ouvrirSite;

    // Choisir une année dans le ComboBox recharge le solde ; on neutralise l'écouteur pendant qu'on
    // programme sa valeur initiale, pour ne pas déclencher un rechargement en boucle.
    private boolean programmationSelection;

    @Inject
    public SaisonController(SaisonViewModel viewModel, OuvrirPassage ouvrirPassage, OuvrirSite ouvrirSite) {
        this.viewModel = Objects.requireNonNull(viewModel, "viewModel");
        this.ouvrirPassage = Objects.requireNonNull(ouvrirPassage, "ouvrirPassage");
        this.ouvrirSite = Objects.requireNonNull(ouvrirSite, "ouvrirSite");
    }

    @FXML
    private void initialize() {
        colCarre.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().numeroCarre()));
        colPoint.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().codePoint()));

        colPassage1.setCellValueFactory(
                c -> new ReadOnlyStringWrapper(texteCase(c.getValue().passage1())));
        colPassage1.setCellFactory(col -> ColonneBadge.cellule(ligne -> classeCase(ligne.passage1())));
        colPassage2.setCellValueFactory(
                c -> new ReadOnlyStringWrapper(texteCase(c.getValue().passage2())));
        colPassage2.setCellFactory(col -> ColonneBadge.cellule(ligne -> classeCase(ligne.passage2())));

        // Hors protocole (#2525) : les nuits opportunistes du point, hors du décompte des deux passages
        // attendus. Cellule vide dans le cas courant — la colonne ne parle que quand il y a de quoi.
        colHorsProtocole.setCellValueFactory(c -> new ReadOnlyStringWrapper(texteHorsProtocole(c.getValue())));
        colHorsProtocole.setCellFactory(col -> ColonneBadge.cellule(ligne -> "badge-opportuniste"));

        colResteAFaire.setCellValueFactory(c -> new ReadOnlyStringWrapper(
                c.getValue().aJour() ? "rien" : c.getValue().resteAFaire()));

        tableSaison.setItems(viewModel.lignes());
        lblResume.textProperty().bind(viewModel.resumeProperty());
        lblSignalement.textProperty().bind(viewModel.signalementProperty());
        // Le signalement ne réserve sa place que lorsqu'il a quelque chose à dire.
        lblSignalement.managedProperty().bind(viewModel.signalementProperty().isNotEmpty());
        lblSignalement.visibleProperty().bind(viewModel.signalementProperty().isNotEmpty());

        DoubleClicLigne.installer(tableSaison, this::ouvrirLigne);

        choixAnnee.valueProperty().addListener((obs, ancien, nouveau) -> {
            if (!programmationSelection && nouveau != null) {
                viewModel.charger(nouveau);
            }
        });

        viewModel.chargerCourant();
        peuplerAnnees();
    }

    private void peuplerAnnees() {
        programmationSelection = true;
        choixAnnee.setItems(FXCollections.observableArrayList(viewModel.anneesProposees()));
        choixAnnee.setValue(viewModel.annee());
        programmationSelection = false;
    }

    @Override
    public void rafraichirAuRetour() {
        // De retour d'un passage/point ouvert depuis le tableau : le solde a pu changer (#1376). On
        // recharge la saison affichée sans déranger le choix d'année.
        viewModel.charger(viewModel.annee());
    }

    private void ouvrirLigne(LigneSaison ligne) {
        // Les nuits hors protocole comptent ici : depuis #2525 elles ne sont plus dans les colonnes de
        // passage, mais un point qui n'a QU'une nuit opportuniste doit rester ouvrable d'un double-clic
        // — sans quoi la seule nuit existante deviendrait inatteignable depuis cet écran.
        ligne.toutesLesCases()
                .filter(CasePassage::presente)
                .findFirst()
                .ifPresentOrElse(
                        cas -> ouvrirPassage.ouvrir(cas.idPassage(), contexte(ligne)),
                        // Aucune nuit encore : on ouvre le carré du point pour en saisir une.
                        () -> ouvrirSite.ouvrirDetail(ligne.numeroCarre()));
    }

    private static ContexteSite contexte(LigneSaison ligne) {
        return new ContexteSite(ligne.numeroCarre(), ligne.codePoint(), null);
    }

    /// Texte de la pastille d'un passage : « Non planifié » si absent, sinon l'état (ou « Inexploitable »
    /// pour un verdict A_JETER) suivi de la date.
    private static String texteCase(CasePassage cas) {
        if (!cas.presente()) {
            return "Non planifié";
        }
        String etat;
        if (cas.opportuniste()) {
            etat = "Opportuniste"; // hors protocole (carré d'un tiers, #2525)
        } else {
            etat = cas.inexploitable() ? "Inexploitable" : cas.statut().libelle();
        }
        return cas.date() == null ? etat : etat + " · " + cas.date().format(JOUR_MOIS);
    }

    /// Colonne « Hors protocole » (#2525) : les nuits opportunistes du point, séparées par un point
    /// médian quand il y en a plusieurs. **`null`** dans le cas courant — c'est ce que la cellule badge
    /// interprète comme « rien à afficher » ([ColonneBadge]). Rendre `""` lui ferait poser une pastille
    /// vide, et surtout une classe CSS nulle qui casse le recyclage des cellules au défilement.
    private static String texteHorsProtocole(LigneSaison ligne) {
        if (ligne.horsProtocole().isEmpty()) {
            return null;
        }
        return ligne.horsProtocole().stream()
                .map(cas -> cas.date() == null
                        ? "Opportuniste"
                        : "Opportuniste · " + cas.date().format(JOUR_MOIS))
                .collect(Collectors.joining(" · "));
    }

    /// Classe CSS de la pastille : couleurs **reprises du modèle** (statut, ou verdict pour un passage
    /// inexploitable), pastille en creux « Non planifié » pour un passage absent.
    private static String classeCase(CasePassage cas) {
        if (!cas.presente()) {
            return "badge-non-planifie";
        }
        if (cas.opportuniste()) {
            return "badge-opportuniste";
        }
        return cas.inexploitable() ? ColonneBadge.classe(cas.verdict()) : ColonneBadge.classe(cas.statut());
    }
}
