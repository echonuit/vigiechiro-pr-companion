package fr.univ_amu.iut.analyse.view;

import com.google.inject.Inject;
import fr.univ_amu.iut.analyse.model.ContactHoraire;
import fr.univ_amu.iut.analyse.model.CourbeEspece;
import fr.univ_amu.iut.analyse.model.LargeurTranche;
import fr.univ_amu.iut.analyse.model.PointActivite;
import fr.univ_amu.iut.analyse.viewmodel.ActiviteViewModel;
import fr.univ_amu.iut.commun.model.DepotVues;
import fr.univ_amu.iut.commun.model.Nuit;
import fr.univ_amu.iut.commun.model.PlageNuit;
import fr.univ_amu.iut.commun.model.VersionApplication;
import fr.univ_amu.iut.commun.view.BandeauRetour;
import fr.univ_amu.iut.commun.view.EmplacementNavigation;
import fr.univ_amu.iut.commun.view.EmplacementPassage;
import fr.univ_amu.iut.commun.view.FiltreFichier;
import fr.univ_amu.iut.commun.view.GestionnaireFiltres;
import fr.univ_amu.iut.commun.view.GestionnaireVues;
import fr.univ_amu.iut.commun.view.Lieu;
import fr.univ_amu.iut.commun.view.OuvrirPassage;
import fr.univ_amu.iut.commun.view.OuvrirSite;
import fr.univ_amu.iut.commun.view.SelecteurFichierJavaFx;
import fr.univ_amu.iut.commun.view.SelecteurFichierModifiable;
import fr.univ_amu.iut.commun.viewmodel.ContextePassage;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import javafx.beans.binding.Bindings;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.SetChangeListener;
import javafx.fxml.FXML;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.util.StringConverter;

/// Controller de l'écran **M-Activite** (`Activite.fxml`, #2352, lot 2 du chantier #2348).
///
/// Pur câblage (patron CM4, comme [fr.univ_amu.iut.diagnostic.view.DiagnosticController]) : lie les
/// contrôles au [ActiviteViewModel]. La courbe se trace sur un **axe nocturne fixe 18 h → 8 h** (et non
/// 0 h → 24 h, qui couperait la nuit en deux) : l'abscisse est le nombre de minutes depuis 18 h, l'heure
/// du jour est reconstruite en étiquette. Aucun accès base ni logique métier ici (règle ArchUnit
/// `view_sans_jdbc`).
public class ActiviteController implements EmplacementNavigation {

    /// Largeur de la fenêtre nocturne affichée, en minutes : de 18 h à 8 h le lendemain, soit 14 heures.
    private static final int MINUTES_FENETRE = 14 * 60;

    /// Origine de l'axe : 18 h. Chaque contact est placé à sa distance en minutes de ce repère, et les
    /// étiquettes de graduation reconstruisent l'heure du jour à partir de lui.
    private static final LocalTime DEBUT_FENETRE = LocalTime.of(18, 0);

    private static final DateTimeFormatter HEURE = DateTimeFormatter.ofPattern("HH");

    /// Format de l'heure d'un point au survol : heure et minute (`22:30`), plus précis que l'étiquette
    /// d'axe (`HH`), puisqu'on donne la valeur exacte d'une tranche.
    private static final DateTimeFormatter HEURE_MINUTE = DateTimeFormatter.ofPattern("HH:mm");

    private final ActiviteViewModel viewModel;
    private final OuvrirSite ouvrirSite;
    private final OuvrirPassage ouvrirPassage;

    /// Version empaquetée, estampillée sur l'image exportée : sans elle, impossible de savoir avec quelle
    /// version de l'application une image trouvée dans un dossier a été produite.
    private final VersionApplication version;

    /// Dépôt des vues mémorisées : alimente les onglets et enregistre celles de l'utilisateur.
    private final DepotVues depotVues;

    /// Contexte de navigation (passage + site), mémorisé pour reconstruire le fil d'Ariane du chrome.
    private ContextePassage contexte;

    @FXML
    private GrapheNocturne grapheActivite;

    @FXML
    private ChoiceBox<LargeurTranche> choixTranche;

    @FXML
    private FlowPane selecteurEspeces;

    @FXML
    private Label lblTitreEspeces;

    @FXML
    private Label lblEtatVide;

    @FXML
    private TextField champRecherche;

    @FXML
    private MenuButton menuAjoutFiltre;

    @FXML
    private FlowPane pucesFiltres;

    /// Conteneur des onglets de vues mémorisées (socle `GestionnaireVues`, #623).
    @FXML
    private FlowPane barreOnglets;

    @FXML
    private Button boutonExporterImage;

    @FXML
    private HBox bandeauRetour;

    @FXML
    private Label lblRetour;

    @FXML
    private Button btnFermerRetour;

    /// Barre de filtres cascadés (patron « à la Notion »), gardée en champ pour vivre autant que l'écran.
    private GestionnaireFiltres<ContactHoraire> gestionnaireFiltres;

    /// Désignation du fichier d'export, derrière le port du socle : un `FileChooser` natif en dur **fige**
    /// un test TestFX headless, et l'action ne serait pas testable du tout.
    private final SelecteurFichierModifiable selecteur = new SelecteurFichierModifiable(
            // `this.boutonExporterImage` : le champ @FXML est déclaré plus haut mais reste nul jusqu'au
            // chargement ; la fenêtre n'est lue qu'au clic.
            new SelecteurFichierJavaFx(() -> this.boutonExporterImage.getScene().getWindow()));

    /// Porteur de désignation exposé aux tests : `selecteur().definir(...)`.
    SelecteurFichierModifiable selecteur() {
        return selecteur;
    }

    @Inject
    public ActiviteController(
            ActiviteViewModel viewModel,
            OuvrirSite ouvrirSite,
            OuvrirPassage ouvrirPassage,
            VersionApplication version,
            DepotVues depotVues) {
        this.viewModel = Objects.requireNonNull(viewModel, "viewModel");
        this.ouvrirSite = Objects.requireNonNull(ouvrirSite, "ouvrirSite");
        this.ouvrirPassage = Objects.requireNonNull(ouvrirPassage, "ouvrirPassage");
        this.version = Objects.requireNonNull(version, "version");
        this.depotVues = Objects.requireNonNull(depotVues, "depotVues");
    }

    @FXML
    private void initialize() {
        configurerAxeNocturne();

        // Barre de filtres cascadés (socle Filtres) : Carré et Taxon parent en puces, recherche texte
        // permanente. Chaque changement ré-agrège le sous-ensemble via le callback du ViewModel.
        gestionnaireFiltres = new GestionnaireFiltres<>(
                champRecherche,
                menuAjoutFiltre,
                pucesFiltres,
                viewModel.filtres(),
                List.of(
                        CriteresActivite.carre(viewModel::carresDisponibles),
                        CriteresActivite.point(viewModel::pointsDisponibles),
                        CriteresActivite.nuit(viewModel::nuitsDisponibles),
                        CriteresActivite.groupe(viewModel::groupesDisponibles)),
                CriteresActivite.rechercheTexte());

        // Onglets de vues (#623) : les vues par défaut partitionnent par catégorie du référentiel, et
        // l'écran s'ouvre sur « Chiroptères » — Tadarida détecte aussi orthoptères et micromammifères,
        // qui n'ont rien à faire dans la présélection des cinq taxons les plus contactés.
        GestionnaireVues.avecDialogue(
                barreOnglets, gestionnaireFiltres, depotVues, "activite", CriteresActivite.vuesParDefaut());

        choixTranche.setItems(FXCollections.observableArrayList(LargeurTranche.values()));
        choixTranche.setConverter(new StringConverter<>() {
            @Override
            public String toString(LargeurTranche tranche) {
                return tranche == null ? "" : libelleTranche(tranche);
            }

            @Override
            public LargeurTranche fromString(String texte) {
                return null;
            }
        });
        choixTranche.valueProperty().bindBidirectional(viewModel.trancheProperty());

        // Le graphe cède la place à un état vide tant qu'aucune espèce n'a été détectée cette nuit.
        // L'état vide nomme la dimension responsable (rien chargé, filtres trop stricts, aucune espèce
        // cochée) et s'affiche dès qu'aucune courbe n'est tracée. Le cadre du graphe reste visible tant
        // qu'il y a des données, pour qu'on puisse cocher une espèce.
        lblEtatVide
                .textProperty()
                .bind(Bindings.createStringBinding(
                        viewModel::messageEtatVide, viewModel.courbesAffichees(), viewModel.especes()));
        lblEtatVide.visibleProperty().bind(Bindings.isEmpty(viewModel.courbesAffichees()));
        lblEtatVide.managedProperty().bind(Bindings.isEmpty(viewModel.courbesAffichees()));
        grapheActivite.visibleProperty().bind(Bindings.isNotEmpty(viewModel.especes()));
        grapheActivite.managedProperty().bind(Bindings.isNotEmpty(viewModel.especes()));

        // Rien de tracé, rien à exporter : le bouton le dit en se grisant, plutôt que d'accepter un clic
        // sans effet (affordance, cf. #790).
        boutonExporterImage.disableProperty().bind(Bindings.isEmpty(viewModel.courbesAffichees()));

        // Bandeau de retour mutualisé (#1837) : dit l'export réussi et, surtout, l'export échoué.
        BandeauRetour.installer(
                bandeauRetour, lblRetour, btnFermerRetour, viewModel.retourProperty(), viewModel::effacerRetour);

        // Sans espèce détectée, le titre du sélecteur s'efface avec lui : un intitulé suivi de rien laisse
        // croire à un affichage tronqué (constaté sur l'aperçu de l'état vide).
        lblTitreEspeces.visibleProperty().bind(Bindings.isNotEmpty(viewModel.especes()));
        lblTitreEspeces.managedProperty().bind(Bindings.isNotEmpty(viewModel.especes()));

        viewModel.courbesAffichees().addListener((ListChangeListener<CourbeEspece>) changement -> majGraphe());
        viewModel.especes().addListener((ListChangeListener<CourbeEspece>) changement -> construireSelecteur());
        viewModel.especesSelectionnees().addListener((SetChangeListener<String>) changement -> construireSelecteur());
        viewModel.plageNuitProperty().addListener((observable, ancienne, plage) -> majFenetreNuit(plage));
        majGraphe();
        construireSelecteur();
        majFenetreNuit(viewModel.plageNuitProperty().get());
    }

    /// Reporte la fenêtre nocturne du ViewModel sur l'aplat du graphe : convertit les heures pleines
    /// (coucher, lever) en minutes sur l'axe, ou efface l'aplat quand la plage est absente (`null` : vue
    /// multi-nuits ou passage sans GPS).
    private void majFenetreNuit(PlageNuit plage) {
        if (plage == null) {
            grapheActivite.definirFenetreNuit(null, null);
            return;
        }
        grapheActivite.definirFenetreNuit(minutesSurAxe(plage.heureDebut()), minutesSurAxe(plage.heureFin()));
    }

    /// **Exporte l'image** de la courbe : demande où écrire, puis **redessine** le graphe hors écran (jamais
    /// une capture de l'affichage, qui rendrait une image noire dès que le graphe est masqué) en
    /// l'estampillant de son contexte. Sans courbe tracée, il n'y a rien à exporter : on ne produit pas un
    /// fichier vide.
    @FXML
    private void exporterImage() {
        if (viewModel.courbesAffichees().isEmpty()) {
            return;
        }
        selecteur
                .enregistrerFichier(
                        "Exporter l'image de la courbe d'activité", "activite-nuit.png", FiltreFichier.png())
                .ifPresent(this::exporterVers);
    }

    /// Écrit l'image de la courbe dans `fichier`, datée du jour.
    public void exporterVers(java.nio.file.Path fichier) {
        exporterVers(fichier, LocalDate.now());
    }

    /// Écrit l'image de la courbe dans `fichier`, en la datant de `dateExport`. Publique, et la date
    /// **passée en paramètre**, parce que l'outil de capture s'en sert pour produire l'aperçu de l'export :
    /// la capture passe ainsi par le **vrai** chemin de production (ADR 0025) plutôt que d'en reconstruire
    /// une imitation, tout en restant **déterministe** — un `LocalDate.now()` interne reverserait un PNG
    /// différent chaque jour dans un dépôt qui les versionne.
    public void exporterVers(java.nio.file.Path fichier, LocalDate dateExport) {
        try {
            ExportImageActivite.ecrire(
                    List.copyOf(viewModel.courbesAffichees()),
                    ActiviteController::configurerAxeNocturne,
                    fenetreNuitSurAxe(),
                    lignesLegende(dateExport),
                    fichier);
            viewModel.signalerExport(String.valueOf(fichier.getFileName()));
        } catch (RuntimeException echec) {
            // Disque plein, dossier en lecture seule, rendu refusé par la garde des libellés : sans ce
            // rattrapage, l'exception remonte au fil JavaFX, qui l'avale — le bouton « ne fait rien ».
            viewModel.signalerEchecExport(motif(echec));
        }
    }

    /// Message d'un échec, à défaut de message une mention du type : une chaîne vide dans le bandeau ne
    /// vaudrait pas mieux que le silence qu'on corrige.
    private static String motif(RuntimeException echec) {
        String message = echec.getMessage();
        return message == null || message.isBlank() ? echec.getClass().getSimpleName() : message;
    }

    /// Les lignes de contexte estampillées sur l'image : identité (carré, point, passage), réglages
    /// (tranche, filtres actifs) et provenance (version, date).
    private List<String> lignesLegende(LocalDate dateExport) {
        return List.of(
                LegendeExportActivite.identite(contexte),
                LegendeExportActivite.reglages(
                        viewModel.trancheProperty().get().minutes(), gestionnaireFiltres.decrire()),
                LegendeExportActivite.provenance(version.libelle(), dateExport));
    }

    /// La fenêtre nocturne en minutes sur l'axe (`[début, fin]`), ou `null` si elle n'est pas connue : l'image
    /// se dessine alors sans aplat, comme l'écran.
    private double[] fenetreNuitSurAxe() {
        PlageNuit plage = viewModel.plageNuitProperty().get();
        if (plage == null) {
            return null;
        }
        return new double[] {minutesSurAxe(plage.heureDebut()), minutesSurAxe(plage.heureFin())};
    }

    /// Ouvre l'activité **d'un passage** (entrée depuis M-Passage). Appelée par [NavigationActivite] après
    /// le chargement du FXML ; mémorise le contexte pour le fil d'Ariane.
    public void ouvrirSur(ContextePassage passage) {
        this.contexte = passage;
        viewModel.chargerPassage(passage.idPassage());
    }

    /// Ouvre l'activité de **tous les passages** de l'utilisateur (entrée transverse, écran racine). Sans
    /// contexte de passage : le fil d'Ariane se réduit au segment courant.
    public void ouvrirTout(String idUtilisateur) {
        this.contexte = null;
        viewModel.chargerUtilisateur(idUtilisateur);
    }

    /// Emplacement dans le fil d'Ariane. Depuis un passage : `Mes sites › Carré N › Détails du passage N° X
    /// › Activité de la nuit`. En transverse (racine, sans contexte) : le seul segment courant, le chrome
    /// préfixant « Accueil ».
    @Override
    public List<Lieu> emplacement() {
        if (contexte == null) {
            return List.of(Lieu.courant("Activité de la nuit"));
        }
        return EmplacementPassage.emplacementEnfant(contexte, ouvrirSite, ouvrirPassage, "Activité de la nuit");
    }

    /// Axe des abscisses **fixe** de 0 (18 h) à 840 minutes (8 h le lendemain), une graduation par heure,
    /// étiquetée `HH` en reconstruisant l'heure du jour depuis 18 h. Fixe et non calé sur les données pour
    /// que deux nuits se comparent d'un coup d'œil.
    private void configurerAxeNocturne() {
        configurerAxeNocturne((NumberAxis) grapheActivite.getXAxis());
    }

    /// La configuration de l'axe nocturne, isolée pour être appliquée **aussi** à l'axe neuf de l'image
    /// exportée : l'export redessine le graphe, il doit donc reposer sur le même repère que l'écran.
    static void configurerAxeNocturne(NumberAxis axeTemps) {
        axeTemps.setAutoRanging(false);
        axeTemps.setLowerBound(0);
        axeTemps.setUpperBound(MINUTES_FENETRE);
        axeTemps.setTickUnit(60);
        axeTemps.setMinorTickCount(0);
        axeTemps.setTickLabelFormatter(new StringConverter<>() {
            @Override
            public String toString(Number minutes) {
                return DEBUT_FENETRE.plusMinutes(minutes.longValue()).format(HEURE);
            }

            @Override
            public Number fromString(String texte) {
                return 0;
            }
        });
    }

    /// Reconstruit une série par espèce affichée : chaque tranche devient un point placé à sa minute
    /// depuis 18 h. Le nom de série (légende) est le nom vernaculaire, ou le code à défaut.
    private void majGraphe() {
        grapheActivite.getData().setAll(versSeries(viewModel.courbesAffichees()));
    }

    /// Traduit des courbes en séries de graphe. Partagée avec [ExportImageActivite], qui redessine les
    /// mêmes courbes hors écran : l'image montre ainsi exactement ce que l'écran montre.
    static List<XYChart.Series<Number, Number>> versSeries(List<CourbeEspece> courbes) {
        return courbes.stream().map(ActiviteController::versSerie).toList();
    }

    /// Traduit une courbe en série de graphe. Visible du paquet : [ExportImageActivite] la réutilise pour
    /// **redessiner** les mêmes courbes hors écran, plutôt que d'emprunter les séries de l'écran (une série
    /// n'appartient qu'à un graphe à la fois).
    private static XYChart.Series<Number, Number> versSerie(CourbeEspece courbe) {
        XYChart.Series<Number, Number> serie = new XYChart.Series<>();
        serie.setName(nomAffiche(courbe));
        for (PointActivite point : courbe.points()) {
            XYChart.Data<Number, Number> donnee =
                    new XYChart.Data<>(minutesDepuis18h(point.debutTranche()), point.nombre());
            installerInfobulle(donnee, texteInfobulle(nomAffiche(courbe), point));
            serie.getData().add(donnee);
        }
        return serie;
    }

    /// Texte de l'infobulle d'un point : espèce, heure de la tranche (`HH:mm`) et nombre de contacts, avec
    /// l'accord singulier/pluriel. La valeur exacte que l'axe ne donne qu'approximativement.
    static String texteInfobulle(String espece, PointActivite point) {
        String heure = point.debutTranche().toLocalTime().format(HEURE_MINUTE);
        String contacts = point.nombre() > 1 ? " contacts" : " contact";
        return espece + " · " + heure + " · " + point.nombre() + contacts;
    }

    /// Pose l'infobulle sur le symbole du point. Le nœud n'existe qu'une fois le graphe mis en page : on
    /// l'attend via `nodeProperty` (et on le prend s'il est déjà là).
    private static void installerInfobulle(XYChart.Data<Number, Number> donnee, String texte) {
        if (donnee.getNode() != null) {
            Tooltip.install(donnee.getNode(), new Tooltip(texte));
        }
        donnee.nodeProperty().addListener((observable, ancien, noeud) -> {
            if (noeud != null) {
                Tooltip.install(noeud, new Tooltip(texte));
            }
        });
    }

    /// (Re)construit le sélecteur d'espèces : une case par espèce de la nuit, cochée si sélectionnée. La
    /// reconstruction reflète toujours l'état du ViewModel (espèces présentes et sélection courante) ;
    /// l'écouteur de chaque case est posé **après** son état initial, pour ne pas se déclencher pendant la
    /// construction.
    private void construireSelecteur() {
        selecteurEspeces.getChildren().clear();
        for (CourbeEspece courbe : viewModel.especes()) {
            String taxon = courbe.taxon();
            CheckBox case_ = new CheckBox(nomAffiche(courbe) + " (" + courbe.total() + ")");
            case_.setSelected(viewModel.especesSelectionnees().contains(taxon));
            case_.selectedProperty().addListener((observable, avant, coche) -> majSelection(taxon, coche));
            selecteurEspeces.getChildren().add(case_);
        }
    }

    private void majSelection(String taxon, boolean coche) {
        if (coche) {
            viewModel.especesSelectionnees().add(taxon);
        } else {
            viewModel.especesSelectionnees().remove(taxon);
        }
    }

    private static String nomAffiche(CourbeEspece courbe) {
        return courbe.nomEspece() != null ? courbe.nomEspece() : courbe.taxon();
    }

    private static String libelleTranche(LargeurTranche tranche) {
        return tranche == LargeurTranche.HEURE ? "1 h" : tranche.minutes() + " min";
    }

    /// Minutes écoulées depuis 18 h le soir de la nuit à laquelle appartient `instant` (bascule à midi,
    /// [Nuit]). Un contact à 23:30 est à 330 min ; un contact à 02:00 le lendemain est à 480 min : l'ordre
    /// à cheval sur minuit est préservé.
    private static long minutesDepuis18h(LocalDateTime instant) {
        LocalDate soir = Nuit.de(instant);
        return Duration.between(soir.atTime(DEBUT_FENETRE), instant).toMinutes();
    }

    /// Position, en minutes sur l'axe nocturne (0 = 18 h), d'une **heure pleine** de la fenêtre (coucher ou
    /// lever). Le soir (≥ 18 h) est à `(h − 18) × 60`, le matin (< 18 h, après minuit) à `(h + 6) × 60`.
    /// Bornée à `[0, 840]` pour un coucher précoce ou un lever tardif qui déborderait de la fenêtre.
    static double minutesSurAxe(int heure) {
        double minutes = heure >= 18 ? (heure - 18) * 60.0 : (heure + 6) * 60.0;
        return Math.max(0, Math.min(MINUTES_FENETRE, minutes));
    }
}
