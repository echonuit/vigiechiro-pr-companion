package fr.univ_amu.iut.saison.view;

import com.google.inject.Inject;
import fr.univ_amu.iut.commun.model.NormalisationTexte;
import fr.univ_amu.iut.commun.view.ColonneBadge;
import fr.univ_amu.iut.commun.view.DoubleClicLigne;
import fr.univ_amu.iut.commun.view.OuvrirPassage;
import fr.univ_amu.iut.commun.view.OuvrirSite;
import fr.univ_amu.iut.commun.view.RafraichirAuRetour;
import fr.univ_amu.iut.commun.view.SuitLaRevision;
import fr.univ_amu.iut.commun.viewmodel.ContexteSite;
import fr.univ_amu.iut.passage.model.Campagne;
import fr.univ_amu.iut.saison.model.CasePassage;
import fr.univ_amu.iut.saison.model.LigneSaison;
import fr.univ_amu.iut.saison.viewmodel.SaisonViewModel;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.transformation.SortedList;
import javafx.fxml.FXML;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.util.StringConverter;

/// Controller de l'écran **M-Saison** : une ligne par point suivi, l'état de ses deux passages en
/// pastilles (couleurs reprises du modèle), la colonne « reste à faire », un sélecteur d'année et le
/// double-clic vers le passage concerné (ou le carré du point s'il n'y a pas encore de passage).
///
/// Implémente [RafraichirAuRetour] : revenir d'un passage ouvert depuis le tableau recharge le solde,
/// pour ne pas reproduire le défaut de compteurs figés d'accueil (#1376).
public class SaisonController implements RafraichirAuRetour, SuitLaRevision {

    private static final DateTimeFormatter JOUR_MOIS = DateTimeFormatter.ofPattern("dd/MM");

    @FXML
    private ComboBox<Integer> choixAnnee;

    @FXML
    private Label lblCampagne;

    @FXML
    private ComboBox<Campagne> choixCampagne;

    @FXML
    private Label lblResume;

    @FXML
    private Label lblSignalement;

    @FXML
    private TextField champRechercheLieu;

    @FXML
    private CheckBox caseResteAFaire;

    @FXML
    private TableView<LigneSaison> tableSaison;

    @FXML
    private TableColumn<LigneSaison, String> colCarre;

    @FXML
    private TableColumn<LigneSaison, String> colNomSite;

    @FXML
    private TableColumn<LigneSaison, String> colPoint;

    @FXML
    private TableColumn<LigneSaison, String> colCommune;

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
        // Le nom du carré a sa PROPRE colonne (#3289). La recherche de cet écran retient une ligne sur
        // ce nom depuis #3219, et l'écran n'ayant pas de puce « Lieu », il n'apparaissait NULLE PART -
        // une recherche qui trouve sans montrer demande de croire son résultat sur parole.
        //
        // Qualifier la colonne « Carré » en « 640380 · Vallon », comme les entrées de la puce ailleurs,
        // a été essayé d'abord : la capture régénérée montrait « 640380 · … ». Dans une table dense, ce
        // remède remplaçait un numéro nu par un numéro suivi d'une ellipse, qui n'apprend rien. La
        // question que #3219 laissait ouverte est donc tranchée, et par l'image.
        colNomSite.setCellValueFactory(c -> new ReadOnlyStringWrapper(
                c.getValue().nomSite() == null ? "" : c.getValue().nomSite()));
        colPoint.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().codePoint()));
        // La commune du point (#3313). Une ligne porte UN point, donc une seule commune : le critère
        // de l'ADR 2861 est satisfait. Non résolue, la cellule reste vide - c'est la convention de cette
        // table, qui laisse déjà vide « Hors protocole » quand il n'y a rien à dire.
        colCommune.setCellValueFactory(c -> new ReadOnlyStringWrapper(
                c.getValue().commune() == null ? "" : c.getValue().commune()));

        colPassage1.setCellValueFactory(
                c -> new ReadOnlyStringWrapper(texteCase(c.getValue().passage1())));
        colPassage1.setCellFactory(col -> ColonneBadge.cellule(ligne -> classeCase(ligne.passage1())));
        colPassage2.setCellValueFactory(
                c -> new ReadOnlyStringWrapper(texteCase(c.getValue().passage2())));
        colPassage2.setCellFactory(col -> ColonneBadge.cellule(ligne -> classeCase(ligne.passage2())));

        // Hors protocole (#2525) : les nuits opportunistes du point, hors du décompte des deux passages
        // attendus. Cellule vide dans le cas courant : la colonne ne parle que quand il y a de quoi.
        colHorsProtocole.setCellValueFactory(c -> new ReadOnlyStringWrapper(texteHorsProtocole(c.getValue())));
        colHorsProtocole.setCellFactory(col -> ColonneBadge.cellule(ligne -> "badge-opportuniste"));

        colResteAFaire.setCellValueFactory(c -> new ReadOnlyStringWrapper(
                c.getValue().aJour() ? "rien" : c.getValue().resteAFaire()));

        // La table montre les lignes que les deux filtres laissent passer (#3103) ; le résumé et le
        // signalement restent calculés sur le solde entier. Chercher un lieu ne change pas ce qu'il y a
        // à faire cette année.
        //
        // ⚠️ La `FilteredList` est non modifiable : posée telle quelle, `TableView` renonce à trier et
        // vide son `sortOrder` en silence. D'où la `SortedList` par-dessus, comme sur les cinq écrans à
        // barre de filtres.
        SortedList<LigneSaison> lignesTriees = new SortedList<>(viewModel.lignesFiltrees());
        lignesTriees.comparatorProperty().bind(tableSaison.comparatorProperty());
        tableSaison.setItems(lignesTriees);
        installerFiltres();
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
        installerFiltreCampagne();
    }

    /// Filtre par campagne (#2610), **effacé de la mise en page** quand il n'y a rien à proposer :
    /// fonctionnalité coupée, ou aucune campagne créée.
    ///
    /// Le tableau **et** le résumé se restreignent ensemble, le ViewModel rechargeant le solde entier
    /// à chaque changement : les deux viennent de la même source, comme l'exige #2356.
    private void installerFiltreCampagne() {
        boolean aProposer = !viewModel.campagnes().isEmpty();
        lblCampagne.setVisible(aProposer);
        lblCampagne.setManaged(aProposer);
        choixCampagne.setVisible(aProposer);
        choixCampagne.setManaged(aProposer);
        if (!aProposer) {
            return;
        }
        choixCampagne.setItems(viewModel.campagnes());
        // Convertisseur écrit ici : `Convertisseurs` existe dans `multisite` et `importation`, mais en
        // portée paquet dans chacun. Le mutualiser dépasserait cette issue.
        choixCampagne.setConverter(new StringConverter<Campagne>() {
            @Override
            public String toString(Campagne campagne) {
                return campagne == null ? "Toutes les campagnes" : campagne.nom();
            }

            @Override
            public Campagne fromString(String libelle) {
                return null; // liste non éditable : la conversion inverse n'a pas de sens
            }
        });
        choixCampagne.valueProperty().bindBidirectional(viewModel.campagneSelectionneeProperty());
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

    /// Recharge la saison affichée quand la **donnée** a bougé ailleurs. Passe par le même chemin que
    /// le rafraîchissement au retour : l'année choisie et la campagne retenue sont conservées, le
    /// ViewModel les mémorisant délibérément.
    @Override
    public void rafraichirDepuisLaDonnee() {
        viewModel.charger(viewModel.annee());
    }

    private void ouvrirLigne(LigneSaison ligne) {
        // Les nuits hors protocole comptent ici : depuis #2525 elles ne sont plus dans les colonnes de
        // passage, mais un point qui n'a QU'une nuit opportuniste doit rester ouvrable d'un double-clic
        // : sans quoi la seule nuit existante deviendrait inatteignable depuis cet écran.
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
    /// médian quand il y en a plusieurs. **`null`** dans le cas courant : c'est ce que la cellule badge
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

    /// Branche les deux filtres ajoutés par #3103 sur le socle `Filtres` du view-model.
    ///
    /// Un champ vide et une case décochée ne posent **aucun** prédicat plutôt qu'un prédicat neutre :
    /// c'est la même sémantique que la barre à puces des autres écrans, où rien de coché n'écarte rien.
    private void installerFiltres() {
        champRechercheLieu
                .textProperty()
                .addListener((obs, avant, saisie) ->
                        viewModel.filtres().definir(CriteresSaison.RECHERCHE, predicatDeRecherche(saisie)));
        caseResteAFaire
                .selectedProperty()
                .addListener((obs, avant, cochee) -> viewModel
                        .filtres()
                        .definir(
                                CriteresSaison.RESTE_A_FAIRE,
                                Boolean.TRUE.equals(cochee) ? CriteresSaison.resteAFaire() : null));
    }

    /// `null` quand la saisie ne cherche rien : le filtre est alors **retiré**, pas neutralisé.
    ///
    /// ⚠️ La différence n'est pas observable depuis l'écran - un prédicat neutre l'est par définition -
    /// et aucun test ne la garde donc. C'est un choix de cohérence avec le socle, où un critère sans
    /// valeur n'est pas un critère posé : le jour où cet écran gagnerait des puces ou `saufLui`, un
    /// filtre « recherche » enregistré mais neutre s'y afficherait comme actif.
    private static Predicate<LigneSaison> predicatDeRecherche(String saisie) {
        if (saisie == null || NormalisationTexte.normaliser(saisie).isEmpty()) {
            return null;
        }
        return ligne -> CriteresSaison.rechercheTexte().test(ligne, saisie);
    }
}
