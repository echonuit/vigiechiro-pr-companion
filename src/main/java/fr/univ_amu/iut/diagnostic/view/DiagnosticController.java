package fr.univ_amu.iut.diagnostic.view;

import com.google.inject.Inject;
import fr.univ_amu.iut.commun.model.CauseLisible;
import fr.univ_amu.iut.commun.model.VersionApplication;
import fr.univ_amu.iut.commun.view.AxeHoraire;
import fr.univ_amu.iut.commun.view.BandeauRetour;
import fr.univ_amu.iut.commun.view.EmplacementNavigation;
import fr.univ_amu.iut.commun.view.EmplacementPassage;
import fr.univ_amu.iut.commun.view.ExportGraphe;
import fr.univ_amu.iut.commun.view.FiltreFichier;
import fr.univ_amu.iut.commun.view.GrapheNocturne;
import fr.univ_amu.iut.commun.view.LegendeExport;
import fr.univ_amu.iut.commun.view.LibelleRetour;
import fr.univ_amu.iut.commun.view.Lieu;
import fr.univ_amu.iut.commun.view.OuvrirPassage;
import fr.univ_amu.iut.commun.view.OuvrirSite;
import fr.univ_amu.iut.commun.view.ResumeStatut;
import fr.univ_amu.iut.commun.view.SelecteurFichierJavaFx;
import fr.univ_amu.iut.commun.view.SelecteurFichierModifiable;
import fr.univ_amu.iut.commun.viewmodel.ContextePassage;
import fr.univ_amu.iut.commun.viewmodel.ZonesStatut;
import fr.univ_amu.iut.diagnostic.model.CoherenceHoraire;
import fr.univ_amu.iut.diagnostic.model.MesureClimatique;
import fr.univ_amu.iut.diagnostic.viewmodel.DiagnosticViewModel;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import javafx.beans.binding.Bindings;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.ListChangeListener;
import javafx.fxml.FXML;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.layout.HBox;
import org.kordamp.ikonli.javafx.FontIcon;

/// Controller de l'écran **M-Diagnostic** (`Diagnostic.fxml`).
///
/// Pur câblage (patron CM4) : lie les contrôles au [DiagnosticViewModel], graphe T°/hygrométrie
/// (reconstruit depuis la série du VM), listes d'anomalies et d'évènements (R19), signalement
/// d'absence de relevé (R20) et disponibilité GPS. Aucun accès base de données ni logique métier
/// ici (règle ArchUnit `view_sans_jdbc`).
///
/// Implémente [ResumeStatut] (#693) : l'enregistreur diagnostiqué, jusqu'ici en sous-titre d'en-tête,
/// est déporté en barre de statut (le titre « Diagnostic matériel » étant redondant avec le fil d'Ariane).
public class DiagnosticController implements EmplacementNavigation, ResumeStatut {

    //   Le graphe T°/hygrométrie se reconstruit depuis viewModel.mesures() sur un axe temporel.
    private static final DateTimeFormatter HEURE = DateTimeFormatter.ofPattern("HH:mm");

    private final DiagnosticViewModel viewModel;
    private final OuvrirSite ouvrirSite;
    private final OuvrirPassage ouvrirPassage;

    /// Contexte de navigation (passage + site), mémorisé pour reconstruire le fil d'Ariane du chrome.
    ///
    /// **Observable** depuis #3548 : la barre de statut le lit, et un champ nu ne peut pas être déclaré
    /// en dépendance d'un binding. Sans cette propriété, la zone gauche restait vide quand l'ouverture
    /// de l'écran échouait, faute de quoi que ce soit à invalider.
    private final ObjectProperty<ContextePassage> contexte = new SimpleObjectProperty<>(this, "contexte");

    /// Enregistreur diagnostiqué, déporté en zone centre de la barre de statut (#693) au lieu d'un sous-titre.
    private final ReadOnlyObjectWrapper<ZonesStatut> zonesStatut =
            new ReadOnlyObjectWrapper<>(this, "zonesStatut", ZonesStatut.VIDE);

    @FXML
    private Label lblReleveAbsent;

    @FXML
    private Label lblTemperature;

    @FXML
    private GrapheNocturne grapheClimat;

    @FXML
    private ListView<String> listeAnomalies;

    @FXML
    private ListView<String> listeEvenements;

    @FXML
    private Label lblFenetreNuit;

    @FXML
    private Label lblPlagesHoraires;

    @FXML
    private Label lblAlerteHorsNuit;

    @FXML
    private HBox ligneGps;

    @FXML
    private FontIcon iconeGps;

    @FXML
    private Label lblGps;

    @FXML
    private Label lblRetour;

    @FXML
    private HBox bandeauRetour;

    @FXML
    private Button btnFermerRetour;

    @FXML
    private Button boutonExporterGraphe;

    /// Désignation du fichier, derrière le port du socle : un `FileChooser` natif en dur **fige** un test
    /// headless, et le geste ne serait pas testable.
    private final SelecteurFichierModifiable selecteur = new SelecteurFichierModifiable(new SelecteurFichierJavaFx(
            () -> this.boutonExporterGraphe.getScene().getWindow()));

    /// Porteur de désignation exposé aux tests.
    SelecteurFichierModifiable selecteur() {
        return selecteur;
    }

    @Inject
    public DiagnosticController(
            DiagnosticViewModel viewModel,
            OuvrirSite ouvrirSite,
            OuvrirPassage ouvrirPassage,
            VersionApplication version) {
        this.viewModel = Objects.requireNonNull(viewModel, "viewModel");
        this.ouvrirSite = Objects.requireNonNull(ouvrirSite, "ouvrirSite");
        this.ouvrirPassage = Objects.requireNonNull(ouvrirPassage, "ouvrirPassage");
        this.version = Objects.requireNonNull(version, "version");
    }

    /// Version empaquetée, estampillée sur l'image exportée.
    private final VersionApplication version;

    /// **Exporte le graphe climatique** en PNG : redessiné hors écran, jamais photographié (ADR 2348), et
    /// porteur de son contexte : une courbe de température qui quitte l'application sans dire de quelle
    /// nuit elle parle n'explique plus rien.
    @FXML
    private void exporterGraphe() {
        if (viewModel.mesures().isEmpty()) {
            return;
        }
        selecteur
                .enregistrerFichier("Exporter le graphe climatique", "climat-nuit.png", FiltreFichier.png())
                .ifPresent(this::ecrireGraphe);
    }

    /// Écrit l'image du graphe dans `fichier`. Publique parce que l'outil de capture s'en sert : la
    /// capture passe ainsi par le **vrai** chemin de production (ADR 0025).
    public void exporterVers(java.nio.file.Path fichier, LocalDate dateExport) {
        ecrireGraphe(fichier, dateExport);
    }

    private void ecrireGraphe(java.nio.file.Path fichier) {
        ecrireGraphe(fichier, LocalDate.now());
    }

    /// Date **en paramètre** et non lue à l'intérieur : les aperçus sont versionnés, et un
    /// `LocalDate.now()` interne reverserait un PNG différent à chaque jour de CI.
    private void ecrireGraphe(java.nio.file.Path fichier, LocalDate dateExport) {
        try {
            ExportGraphe.ecrire(
                    this::seriesClimat,
                    axe -> configurerAxeTemps(axe, instant(viewModel.mesures().get(0)), viewModel.mesures()),
                    "T° (°C) / Humidité (%)",
                    fenetreNuitSurAxe(),
                    List.of(
                            LegendeExport.identite(contexte.get()),
                            "Climat de la nuit · " + viewModel.mesures().size() + " mesures",
                            LegendeExport.provenance(version.libelle(), dateExport)),
                    fichier);
            viewModel.signalerExport(String.valueOf(fichier.getFileName()));
        } catch (RuntimeException echec) {
            viewModel.signalerEchecExport(CauseLisible.messageDe(echec));
        }
    }

    @Override
    public ReadOnlyObjectProperty<ZonesStatut> zonesStatutProperty() {
        return zonesStatut.getReadOnlyProperty();
    }

    /// Compose les 3 zones de la barre de statut (#1022) : identité du passage (gauche), matériel du
    /// diagnostic (centre), et à droite l'**alerte prioritaire** : hors-nuit puis relevé climatique absent.
    private ZonesStatut calculerZonesStatut() {
        ContextePassage courant = contexte.get();
        String gauche = courant == null ? "" : courant.identiteStatut();
        String releveAbsent = viewModel.releveClimatiqueAbsentProperty().get() ? "Relevé climatique absent" : "";
        // La barre de statut est neutre (ADR 0039) : elle n'affiche que le TEXTE de l'alerte, sa sévérité
        // restant portée par le label inline. `AUCUN` a un texte vide, que `premierNonVide` saute.
        String droite = ZonesStatut.premierNonVide(
                viewModel.alerteHorsNuitProperty().get().texte(), releveAbsent);
        return new ZonesStatut(gauche, materielCentre(), droite);
    }

    /// Zone centre : l'enregistreur, suivi du nombre de mesures climatiques quand un relevé est présent
    /// (« PR 1925492 · 10 mesures », #1498). Vide tant qu'aucun diagnostic n'est chargé.
    private String materielCentre() {
        String enregistreur = viewModel.enregistreurProperty().get();
        if (enregistreur.isEmpty() || viewModel.releveClimatiqueAbsentProperty().get()) {
            return enregistreur;
        }
        int nombre = viewModel.mesures().size();
        return enregistreur + " · " + nombre + (nombre > 1 ? " mesures" : " mesure");
    }

    @FXML
    private void initialize() {
        // Barre de statut 3 zones (#1022, EPIC #1016) : contexte du passage à gauche, matériel (enregistreur)
        // au centre, alerte prioritaire à droite (hors-nuit > relevé climatique absent).
        // #3548 : `contexte` figure dans la liste parce que le calcul le lit. `bind()` évalue tout de
        // suite, ici avant `ouvrirSur` : la zone gauche naît vide, et seule une dépendance déclarée la
        // refait. Sur le chemin d'erreur d'ouverture, aucune ne bougeait, et l'écran n'annonçait plus de
        // quel passage il parlait au moment précis où l'utilisateur en avait besoin.
        zonesStatut.bind(Bindings.createObjectBinding(
                this::calculerZonesStatut,
                contexte,
                viewModel.enregistreurProperty(),
                viewModel.alerteHorsNuitProperty(),
                viewModel.releveClimatiqueAbsentProperty(),
                viewModel.mesures()));
        lblTemperature
                .textProperty()
                .bind(Bindings.concat("Température en début de nuit : ", viewModel.temperatureProperty()));
        lblReleveAbsent.visibleProperty().bind(viewModel.releveClimatiqueAbsentProperty());
        lblReleveAbsent.managedProperty().bind(viewModel.releveClimatiqueAbsentProperty());

        listeAnomalies.setItems(viewModel.anomalies());
        listeEvenements.setItems(viewModel.evenements());

        viewModel.mesures().addListener((ListChangeListener<MesureClimatique>) changement -> {
            majGraphe();
            majFenetreNuit();
        });
        // La cohérence horaire est posée APRÈS les mesures : sans cet écouteur, l'aplat se calculerait
        // sur une fenêtre encore inconnue et ne s'afficherait jamais.
        viewModel.coherenceHoraireProperty().addListener((observable, avant, apres) -> majFenetreNuit());
        majGraphe();
        majFenetreNuit();

        // Encart cohérence horaires (#548) : la fenêtre nocturne réelle quand elle est calculable.
        lblFenetreNuit.textProperty().bind(viewModel.fenetreNuitProperty());
        lblFenetreNuit.visibleProperty().bind(viewModel.coherenceHoraireDisponibleProperty());
        lblPlagesHoraires.textProperty().bind(viewModel.plagesHorairesProperty());
        lblPlagesHoraires.visibleProperty().bind(viewModel.coherenceHoraireDisponibleProperty());
        lblPlagesHoraires.managedProperty().bind(lblPlagesHoraires.visibleProperty());
        lblFenetreNuit.managedProperty().bind(viewModel.coherenceHoraireDisponibleProperty());

        // Alerte « hors nuit » : texte, visibilité (présent/absent) et sévérité (couleur + icône) posés
        // par LibelleRetour depuis le RetourOperation du ViewModel (#2050).
        LibelleRetour.installer(lblAlerteHorsNuit, viewModel.alerteHorsNuitProperty());

        // Disponibilité GPS du point d'écoute (#1497) : ligne d'état permanente, affichée dès qu'un
        // diagnostic est chargé et découplée de la cohérence horaires (le repère ne disparaît plus
        // sur une nuit complète). L'absence est dite, jamais muette ; la provenance est le point
        // d'écoute (feature sites), pas le capteur.
        lblGps.textProperty()
                .bind(Bindings.createStringBinding(
                        () -> viewModel.gpsDisponibleProperty().get()
                                ? "GPS du point : disponible"
                                : "GPS du point : non renseigné (compléter la fiche site)",
                        viewModel.gpsDisponibleProperty()));
        viewModel.gpsDisponibleProperty().addListener((observable, avant, disponible) -> majEtatGps(disponible));
        majEtatGps(viewModel.gpsDisponibleProperty().get());
        var diagnosticCharge = viewModel.enregistreurProperty().isNotEmpty();
        ligneGps.visibleProperty().bind(diagnosticCharge);
        ligneGps.managedProperty().bind(diagnosticCharge);

        // Bandeau de retour partagé (ADR 0023) : libellé, visibilité, sévérité et croix de fermeture.
        BandeauRetour.installer(
                bandeauRetour, lblRetour, btnFermerRetour, viewModel.retourProperty(), viewModel::effacerRetour);
    }

    /// Ouvre le diagnostic du passage `passage`. Appelée par [NavigationDiagnostic] après le chargement
    /// du FXML ; mémorise le contexte pour le fil d'Ariane.
    public void ouvrirSur(ContextePassage passage) {
        this.contexte.set(passage);
        viewModel.ouvrirSur(passage.idPassage());
    }

    /// Emplacement dans le fil d'Ariane : `Mes sites › Carré N › Détails du passage N° X › Diagnostic
    /// matériel` (rendu par le chrome). Le segment passage rouvre M-Passage.
    @Override
    public List<Lieu> emplacement() {
        return EmplacementPassage.emplacementEnfant(contexte.get(), ouvrirSite, ouvrirPassage, "Diagnostic matériel");
    }

    /// Bascule l'icône et la classe d'état de la ligne GPS (#1497) : marqueur de localisation vert
    /// « disponible » ou triangle d'avertissement ambre « non renseigné ». Le libellé, lui, est lié.
    private void majEtatGps(boolean disponible) {
        iconeGps.setIconLiteral(disponible ? "fas-map-marker-alt" : "fas-exclamation-triangle");
        ligneGps.getStyleClass().removeAll("gps-disponible", "gps-absent");
        ligneGps.getStyleClass().add(disponible ? "gps-disponible" : "gps-absent");
    }

    /// Reconstruit les deux séries T°/hygrométrie sur un **axe temporel** : chaque point est placé à sa
    /// minute réelle depuis la première mesure, pour refléter l'espacement réel (et non des catégories
    /// équidistantes). L'axe est ensuite calibré par [#configurerAxeTemps].
    private void majGraphe() {
        XYChart.Series<Number, Number> temperature = new XYChart.Series<>();
        temperature.setName("T° (°C)");
        XYChart.Series<Number, Number> humidite = new XYChart.Series<>();
        humidite.setName("Humidité (%)");
        List<MesureClimatique> mesures = viewModel.mesures();
        NumberAxis axeTemps = (NumberAxis) grapheClimat.getXAxis();
        if (mesures.isEmpty()) {
            // Graphe vide (R20) : aucune échelle temporelle à montrer, on masque étiquettes et
            // graduations plutôt que d'exposer des minutes brutes (0, 10, ... 110).
            axeTemps.setTickLabelsVisible(false);
            axeTemps.setTickMarkVisible(false);
            axeTemps.setTickLabelFormatter(null);
        } else {
            LocalDateTime origine = instant(mesures.get(0));
            for (MesureClimatique mesure : mesures) {
                long minutes = Duration.between(origine, instant(mesure)).toMinutes();
                temperature.getData().add(new XYChart.Data<>(minutes, mesure.temperatureCelsius()));
                humidite.getData().add(new XYChart.Data<>(minutes, mesure.humiditePourcent()));
            }
            configurerAxeTemps(axeTemps, origine, mesures);
        }
        grapheClimat.getData().setAll(List.of(temperature, humidite));
    }

    /// Deux séries **neuves** T°/hygrométrie, pour l'export : une `XYChart.Series` n'appartient qu'à un
    /// graphe à la fois, emprunter celles de l'écran les lui retirerait.
    private List<XYChart.Series<Number, Number>> seriesClimat() {
        XYChart.Series<Number, Number> temperature = new XYChart.Series<>();
        temperature.setName("T° (°C)");
        XYChart.Series<Number, Number> humidite = new XYChart.Series<>();
        humidite.setName("Humidité (%)");
        List<MesureClimatique> mesures = viewModel.mesures();
        LocalDateTime origine = instant(mesures.get(0));
        for (MesureClimatique mesure : mesures) {
            long minutes = Duration.between(origine, instant(mesure)).toMinutes();
            temperature.getData().add(new XYChart.Data<>(minutes, mesure.temperatureCelsius()));
            humidite.getData().add(new XYChart.Data<>(minutes, mesure.humiditePourcent()));
        }
        return List.of(temperature, humidite);
    }

    /// La fenêtre nocturne en minutes sur l'axe, ou `null` si elle n'est pas calculable : l'image se
    /// dessine alors sans bande, comme l'écran.
    private double[] fenetreNuitSurAxe() {
        CoherenceHoraire coherence = viewModel.coherenceHoraireProperty().get();
        List<MesureClimatique> mesures = viewModel.mesures();
        if (coherence == null || !coherence.disponible() || mesures.isEmpty()) {
            return null;
        }
        LocalDateTime origine = instant(mesures.get(0));
        return new double[] {
            minutesDepuis(origine, coherence.coucherSoleil()), minutesDepuis(origine, coherence.leverSoleil())
        };
    }

    /// Pose l'**aplat de la nuit réelle** (coucher → lever au point d'écoute) derrière la courbe
    /// climatique, en convertissant ces deux heures en minutes depuis l'origine de l'axe.
    ///
    /// L'écran connaissait déjà cette fenêtre (il l'écrit sous le graphe et alerte quand
    /// l'enregistrement en déborde), mais elle ne se voyait pas **sur** la courbe, là où elle situe les
    /// mesures. Sans coordonnées au point, elle n'est pas calculable : l'aplat s'efface alors plutôt que
    /// d'inventer une nuit (#2617).
    private void majFenetreNuit() {
        List<MesureClimatique> mesures = viewModel.mesures();
        CoherenceHoraire coherence = viewModel.coherenceHoraireProperty().get();
        if (mesures.isEmpty() || coherence == null || !coherence.disponible()) {
            grapheClimat.definirFenetreNuit(null, null);
            return;
        }
        LocalDateTime origine = instant(mesures.get(0));
        grapheClimat.definirFenetreNuit(
                minutesDepuis(origine, coherence.coucherSoleil()), minutesDepuis(origine, coherence.leverSoleil()));
    }

    /// Minutes séparant `origine` de l'`heure` visée, en restant **dans la nuit** : une heure antérieure
    /// à l'origine appartient au lendemain (le lever, après une origine du soir), et compte donc en
    /// positif au-delà de minuit.
    private static double minutesDepuis(LocalDateTime origine, LocalTime heure) {
        LocalDateTime cible = origine.toLocalDate().atTime(heure);
        if (cible.isBefore(origine)) {
            cible = cible.plusDays(1);
        }
        return Duration.between(origine, cible).toMinutes();
    }

    /// Calibre l'axe du temps : bornes 0..durée, un pas « propre » visant 6 à 8 repères, et des
    /// étiquettes en `HH:mm` reconstruites depuis l'origine (au lieu d'un libellé par mesure).
    private void configurerAxeTemps(NumberAxis axeTemps, LocalDateTime origine, List<MesureClimatique> mesures) {
        long span = Duration.between(origine, instant(mesures.get(mesures.size() - 1)))
                .toMinutes();
        axeTemps.setTickLabelsVisible(true);
        axeTemps.setTickMarkVisible(true);
        // Bornes calées sur les mesures (l'écran montre la nuit telle qu'elle a été enregistrée), le
        // graduage en heures venant du socle partagé avec la courbe d'activité.
        AxeHoraire.graduerEnHeures(axeTemps, origine.toLocalTime(), Math.max(span, 1), pasLisibleMinutes(span));
    }

    private static LocalDateTime instant(MesureClimatique mesure) {
        return LocalDateTime.of(mesure.date(), mesure.heure());
    }

    /// Pas d'axe « propre » (multiple usuel de minutes) visant environ 7 repères sur `spanMinutes`.
    private static double pasLisibleMinutes(long spanMinutes) {
        double cible = Math.max(spanMinutes, 1) / 7.0;
        long[] paliers = {15, 30, 45, 60, 90, 120, 180, 240, 300};
        for (long pas : paliers) {
            if (pas >= cible) {
                return pas;
            }
        }
        return 360;
    }
}
