package fr.univ_amu.iut.multisite.viewmodel;

import fr.univ_amu.iut.commun.viewmodel.Filtres;
import fr.univ_amu.iut.multisite.model.CarreAgrege;
import fr.univ_amu.iut.multisite.model.LignePassage;
import fr.univ_amu.iut.multisite.model.ServiceMultisite;
import fr.univ_amu.iut.multisite.model.TriMultisite;
import fr.univ_amu.iut.sites.model.ServiceSites;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;

/// ViewModel de l'écran **M-Multisite** (vue agrégée des passages de tous les sites de
/// l'utilisateur, parcours P5, story E5, statut **SHOULD**).
///
/// Expose le tableau des [lignes][LignePassage], le **tri** ([TriMultisite]) et l'**export CSV**.
///
/// **Filtrage côté client (#537).** Les passages sont chargés **une seule fois** ([#rafraichir()]) puis
/// filtrés **en mémoire** via le socle partagé [Filtres] : la **barre à puces** de la vue (#537 étape 6b)
/// branche/retire ses prédicats sur [#filtres()], sans ré-interroger le service. Le **tri nommé**
/// ré-ordonne la liste publiée. Les **vues mémorisées** ne sont plus gérées ici : elles vivent dans le
/// composant partagé `commun.view.GestionnaireVues` (onglets « à la Notion »), adossé à la barre de filtres.
///
/// VM agnostique de l'IHM (règle ArchUnit `viewmodel_sans_javafx_ui`) : seuls
/// `javafx.beans`/`javafx.collections`. Non-singleton (un VM frais par chargement de vue).
public class MultisiteViewModel {

    private final ServiceMultisite service;
    private final String idUtilisateur;

    /// File des déplacements de points en attente (mode édition des positions, #154). Responsabilité
    /// extraite : le ViewModel l'expose, la vue la pilote.
    private final PositionsEnAttente positionsEnAttente;

    private final ObjectProperty<TriMultisite> tri = new SimpleObjectProperty<>(this, "tri", TriMultisite.PAR_SITE);

    /// Tous les passages de l'utilisateur, chargés une fois ([#rafraichir()]). Source **non filtrée**
    /// du socle : les filtres et le tri travaillent dessus en mémoire, sans ré-interroger le service.
    private final ObservableList<LignePassage> tousLesPassages = FXCollections.observableArrayList();

    private final FilteredList<LignePassage> passagesFiltres = new FilteredList<>(tousLesPassages);

    /// Lignes **publiées** vers la vue : sous-ensemble filtré, ré-ordonné par le tri nommé. La vue y
    /// pose par-dessus un [javafx.collections.transformation.SortedList] pour le tri par clic
    /// d'en-tête (#145) ; cette liste reste donc la même instance au fil des rafraîchissements.
    private final ObservableList<LignePassage> lignes = FXCollections.observableArrayList();

    /// Agrégat des carrés pour la carte (#152) : vue d'ensemble **non filtrée** (carrés + points + statut).
    private final ObservableList<CarreAgrege> carresCarte = FXCollections.observableArrayList();

    private final ReadOnlyBooleanWrapper nonVide = new ReadOnlyBooleanWrapper(this, "nonVide", false);
    private final ReadOnlyStringWrapper resume = new ReadOnlyStringWrapper(this, "resume", "");
    private final ReadOnlyStringWrapper message = new ReadOnlyStringWrapper(this, "message", "");

    /// Socle de filtres composables (#537) : recompose la conjonction sur [#passagesFiltres] puis
    /// publie via [#publierLignes()]. Déclaré après ses dépendances (la liste filtrée).
    private final Filtres<LignePassage> filtres = new Filtres<>(passagesFiltres, this::publierLignes);

    public MultisiteViewModel(ServiceMultisite service, ServiceSites serviceSites, String idUtilisateur) {
        this.service = Objects.requireNonNull(service, "service");
        this.idUtilisateur = Objects.requireNonNull(idUtilisateur, "idUtilisateur");
        this.positionsEnAttente = new PositionsEnAttente(serviceSites, this::rafraichirCarte, message::set);
        // Le tri nommé ne re-filtre pas : il ré-ordonne la liste publiée. Les filtres sont posés sur
        // [#filtres] par la barre à puces de la vue (#537 étape 6b).
        tri.addListener((obs, ancien, nouveau) -> publierLignes());
    }

    /// (Re)charge **tous** les passages de l'utilisateur, puis ré-applique filtres et tri courants.
    /// À appeler à l'ouverture de l'écran et après une modification des données (retour d'un passage
    /// édité). Les changements de filtre ou de tri **ne rechargent pas** : ils re-filtrent /
    /// ré-ordonnent en mémoire.
    public void rafraichir() {
        tousLesPassages.setAll(service.listerPassages(idUtilisateur));
        filtres.appliquer();
    }

    /// Données de l'écran chargées **hors du fil JavaFX** (#1209) : passages du tableau + agrégat des
    /// carrés de la carte, les deux requêtes base réunies pour une seule occupation.
    public record DonneesMultisite(List<LignePassage> passages, List<CarreAgrege> carte) {}

    /// **Lecture seule** des données de l'écran (deux requêtes base). Sans effet sur l'état observable :
    /// sûre à exécuter **hors du fil JavaFX** (#1209, déport via `IndicateurOccupation`).
    public DonneesMultisite charger() {
        return new DonneesMultisite(service.listerPassages(idUtilisateur), service.agregerPourCarte(idUtilisateur));
    }

    /// Applique des données chargées : recompose le tableau (avec filtres) et l'agrégat de la carte.
    /// **Mutations observables** : à exécuter **sur le fil JavaFX**.
    public void appliquer(DonneesMultisite donnees) {
        tousLesPassages.setAll(donnees.passages());
        filtres.appliquer();
        carresCarte.setAll(donnees.carte());
    }

    /// Route l'échec d'un chargement vers le message de l'écran (filet #795), à la place d'une exception
    /// non capturée remontant du fil de fond.
    public void signalerErreur(Throwable erreur) {
        String detail = erreur.getMessage();
        message.set(detail != null && !detail.isBlank() ? detail : "Chargement des passages impossible.");
    }

    /// Callback du socle (`apresApplication`) : ré-ordonne le sous-ensemble filtré selon le tri
    /// nommé, le publie dans [#lignes], et met à jour le résumé et l'indice d'état vide.
    private void publierLignes() {
        List<LignePassage> triees = new ArrayList<>(passagesFiltres);
        triees.sort(tri.get().comparateur());
        lignes.setAll(triees);
        nonVide.set(!lignes.isEmpty());
        resume.set(lignes.size() + " passage(s) affiché(s).");
        message.set("");
    }

    /// (Re)charge l'agrégat des carrés pour la **carte** (#152), vue d'ensemble **non filtrée**.
    /// **Séparé** de [#rafraichir()] : la carte ne dépend ni des filtres ni du tri du tableau, donc on ne
    /// la recalcule pas à chaque changement de filtre/tri (coût inutile), mais seulement aux moments où les
    /// données changent (ouverture de l'écran, retour après modification d'un passage), à la charge de la
    /// vue (controller).
    public void rafraichirCarte() {
        carresCarte.setAll(service.agregerPourCarte(idUtilisateur));
    }

    /// File des déplacements de points **en attente** (mode édition de la carte, #154) : la vue y met les
    /// marqueurs glissés, puis enregistre ou abandonne. Voir [PositionsEnAttente].
    public PositionsEnAttente positionsEnAttente() {
        return positionsEnAttente;
    }

    /// Exporte les lignes **internes** du tableau (sous-ensemble filtré, tri nommé) en CSV vers
    /// `destination`. La vue préfère [#exporter(Path, List)] pour exporter l'ordre **affiché** (tri par
    /// clic d'en-tête inclus).
    public boolean exporter(Path destination) {
        return exporter(destination, lignes);
    }

    /// Exporte les **lignes fournies** en CSV vers `destination` (P5-CA5). Permet à la vue d'exporter
    /// l'ordre **réellement affiché** (le tri par clic d'en-tête vit côté `TableView`, pas dans le
    /// ViewModel, cf. #291). Sans dossier, l'appel est ignoré ; le bilan (ou l'erreur) va dans
    /// [#messageProperty()].
    ///
    /// @param destination fichier cible choisi par l'observateur
    /// @param lignesAExporter lignes à écrire, dans l'ordre voulu
    /// @return `true` si le fichier a été écrit
    public boolean exporter(Path destination, List<LignePassage> lignesAExporter) {
        if (destination == null) {
            return false;
        }
        try {
            service.exporterCsvVers(destination, lignesAExporter);
            message.set("Tableau exporté vers " + destination.getFileName() + " (" + lignesAExporter.size()
                    + " ligne(s)).");
            return true;
        } catch (RuntimeException echec) {
            message.set(echec.getMessage());
            return false;
        }
    }

    public ObservableList<LignePassage> lignes() {
        return lignes;
    }

    /// Agrégat des carrés pour la **carte** (#152) : carrés + points (GPS, statut dominant) de l'utilisateur,
    /// vue d'ensemble non filtrée. La couche `view` le traduit en marqueurs/emprises.
    public ObservableList<CarreAgrege> carresCarte() {
        return carresCarte;
    }

    /// Socle de filtres composables (#537) sur les passages : la **barre à puces** de la vue (#537 étape 6b)
    /// y branche/retire ses prédicats (carré, statut, verdict, année), et la **carte** y pose une puce carré
    /// au clic. Le callback `publierLignes` ré-ordonne et publie à chaque changement.
    public Filtres<LignePassage> filtres() {
        return filtres;
    }

    public ObjectProperty<TriMultisite> triProperty() {
        return tri;
    }

    public ReadOnlyBooleanProperty nonVideProperty() {
        return nonVide.getReadOnlyProperty();
    }

    public ReadOnlyStringProperty resumeProperty() {
        return resume.getReadOnlyProperty();
    }

    public ReadOnlyStringProperty messageProperty() {
        return message.getReadOnlyProperty();
    }
}
