package fr.univ_amu.iut.analyse.viewmodel;

import fr.univ_amu.iut.analyse.model.AgregationAnalyse;
import fr.univ_amu.iut.analyse.model.ServiceAnalyse;
import fr.univ_amu.iut.commun.viewmodel.Filtres;
import fr.univ_amu.iut.commun.viewmodel.RetourOperation;
import fr.univ_amu.iut.commun.viewmodel.SourceObservations;
import fr.univ_amu.iut.validation.model.CarreEspeces;
import fr.univ_amu.iut.validation.model.EspeceAgregee;
import fr.univ_amu.iut.validation.model.ObservationAnalyse;
import fr.univ_amu.iut.validation.model.ObservationEspece;
import fr.univ_amu.iut.validation.model.StatutObservation;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;

/// ViewModel de l'écran transverse **« Espèces & observations »** (feature `analyse`, prisme
/// biodiversité). Pilote l'**inventaire** des observations de l'utilisateur, regroupé **par espèce** ou
/// **par carré** ([Regroupement]), filtré par **statut** de revue, **taxon parent** (groupe, #518) et un
/// **texte** (nom/code d'espèce, ou n°/nom de carré), avec **export CSV**.
///
/// Filtrage **client-side unifié** (#537) : les observations enrichies sont chargées **une fois**, puis
/// filtrées par le socle partagé [Filtres] (conjonction de prédicats sur une [FilteredList]) ; à chaque
/// changement, elles sont **agrégées en mémoire** ([AgregationAnalyse]) vers les tables, le résumé et la
/// carte. Le regroupement ne change que l'agrégation, pas le filtre. Agnostique de l'IHM (ArchUnit
/// `viewmodel_sans_javafx_ui`). Non-singleton (un VM frais par chargement d'écran).
public class AnalyseViewModel {

    /// Séparateur des compteurs dans les libellés (résumé, titre du détail).
    private static final String SEPARATEUR = " · ";

    private final ServiceAnalyse service;
    private final String idUtilisateur;

    private final ObjectProperty<Regroupement> regroupement =
            new SimpleObjectProperty<>(this, "regroupement", Regroupement.PAR_ESPECE);

    /// Observations enrichies de l'utilisateur (source complète), filtrées par le socle [#filtres] en une
    /// [FilteredList] ; agrégées à chaque changement vers les tables et la carte. Les filtres (statut, taxon
    /// parent #518, texte) sont **pilotés par la barre à puces** de la vue (#537, étape 6) via [#filtres()],
    /// et non plus par des propriétés du ViewModel.
    private final ObservableList<ObservationAnalyse> toutesObservations = FXCollections.observableArrayList();
    private final FilteredList<ObservationAnalyse> observationsFiltrees = new FilteredList<>(toutesObservations);
    private final Filtres<ObservationAnalyse> filtres = new Filtres<>(observationsFiltrees, this::agreger);

    /// **Taxons parents** présents dans les observations (source de la liste déroulante du filtre #518).
    private final ObservableList<String> groupesDisponibles = FXCollections.observableArrayList();

    private final ObservableList<EspeceAgregee> especes = FXCollections.observableArrayList();
    private final ObservableList<CarreEspeces> carres = FXCollections.observableArrayList();
    private final ReadOnlyStringWrapper resume = new ReadOnlyStringWrapper(this, "resume", "");
    /// Retour de la dernière opération (export, échec de chargement, action refusée), avec sa sévérité :
    /// rendu dans un bandeau, distinct de l'indice d'état vide de la table.
    private final ReadOnlyObjectWrapper<RetourOperation> retour =
            new ReadOnlyObjectWrapper<>(this, "retour", RetourOperation.AUCUN);

    /// Détail de l'espèce sélectionnée : ses observations à travers les passages (vide tant qu'aucune
    /// espèce n'est sélectionnée), et le titre du panneau.
    private final ObservableList<ObservationEspece> observations = FXCollections.observableArrayList();
    private final ReadOnlyStringWrapper detailTitre = new ReadOnlyStringWrapper(this, "detailTitre", "");

    /// Espèce dont le détail est affiché (`null` hors regroupement Par espèce ou sans sélection), mémorisée
    /// pour décrire la **source audio** au moment d'« Écouter / valider » sans la repasser par la vue.
    private EspeceAgregee especeSelectionnee;

    /// **Numéros de carré** où l'espèce sélectionnée est présente (distincts), pour la **surbrillance** de
    /// la carte de répartition. Vide tant qu'aucune espèce n'est sélectionnée.
    private final ObservableList<String> carresEspeceSelectionnee = FXCollections.observableArrayList();

    /// Inventaire **par carré** pour la **carte de répartition**, dérivé des observations filtrées (la carte
    /// est carré-centrée même en mode Par espèce).
    private final ObservableList<CarreEspeces> carresCarte = FXCollections.observableArrayList();

    public AnalyseViewModel(ServiceAnalyse service, String idUtilisateur) {
        this.service = Objects.requireNonNull(service, "service");
        this.idUtilisateur = Objects.requireNonNull(idUtilisateur, "idUtilisateur");
        // Le regroupement ne change que l'agrégation (mêmes observations filtrées). Les filtres sont posés
        // sur [#filtres] par la barre à puces de la vue, qui réagrège via le callback `agreger`.
        regroupement.addListener((obs, ancien, nouveau) -> agreger());
    }

    /// (Re)charge les observations enrichies de l'utilisateur, met à jour la liste des taxons parents, puis
    /// **agrège** selon les filtres et le regroupement courants. À appeler à l'ouverture de l'écran.
    public void rafraichir() {
        appliquer(chargerObservations());
    }

    /// **Lecture seule** des observations de l'utilisateur (requête base). Sans effet sur l'état
    /// observable : sûre à exécuter **hors du fil JavaFX** (#1208, déport via `IndicateurOccupation`).
    public List<ObservationAnalyse> chargerObservations() {
        return service.observationsAnalyse(idUtilisateur);
    }

    /// Applique des observations chargées : recompose la liste, les groupes disponibles, repart d'un
    /// détail vide et réagrège. **Mutations observables** : à exécuter **sur le fil JavaFX**.
    public void appliquer(List<ObservationAnalyse> observations) {
        toutesObservations.setAll(observations);
        groupesDisponibles.setAll(toutesObservations.stream()
                .map(ObservationAnalyse::groupe)
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .toList());
        selectionnerEspece(null, null); // rechargement : on repart d'un détail vide (le statut est indifférent)
        agreger();
    }

    /// Route l'échec d'un chargement vers le bandeau de l'écran (filet #795), à la place d'une exception
    /// non capturée remontant du fil de fond.
    public void signalerErreur(Throwable erreur) {
        String detail = erreur.getMessage();
        retour.set(RetourOperation.erreur(
                detail != null && !detail.isBlank() ? detail : "Chargement des observations impossible."));
    }

    /// Signale dans le bandeau qu'une action **n'a pas eu lieu**, faute de cible : guidage, pas échec
    /// technique. Sert au double-clic sur un taxon sans fiche (#1837), dont le silence se lisait comme
    /// une panne.
    public void signaler(String texte) {
        retour.set(RetourOperation.info(texte));
    }

    /// Efface le retour (l'utilisateur a lu le bandeau et le ferme). Le bandeau disparaît.
    public void effacerRetour() {
        retour.set(RetourOperation.AUCUN);
    }

    /// Agrège les observations **filtrées** vers les tables (selon le regroupement), la carte et le résumé.
    /// Callback du socle de filtres (rejoué à chaque changement de prédicat) et du changement de regroupement.
    private void agreger() {
        List<ObservationAnalyse> filtrees = List.copyOf(observationsFiltrees);
        carresCarte.setAll(AgregationAnalyse.parCarre(filtrees));
        int detections = filtrees.size();
        if (regroupement.get() == Regroupement.PAR_CARRE) {
            carres.setAll(AgregationAnalyse.parCarre(filtrees));
            especes.clear();
            resume.set(quantite(carres.size(), "carré") + SEPARATEUR + quantite(detections, "détection"));
        } else {
            especes.setAll(AgregationAnalyse.parEspece(filtrees));
            carres.clear();
            resume.set(quantite(especes.size(), "espèce") + SEPARATEUR + quantite(detections, "détection"));
        }
    }

    /// Charge dans le **détail** les observations de l'`espece` sélectionnée, à travers les passages
    /// (filtrées par le `statut` courant de la barre à puces, `null` = tous, cohérent avec l'inventaire).
    /// `null` (ou en regroupement Par carré) vide le panneau. Appelé par la vue quand la ligne sélectionnée
    /// de l'inventaire change ; le statut courant est lu par la vue sur la barre de filtres (#537, étape 6).
    public void selectionnerEspece(EspeceAgregee espece, StatutObservation statut) {
        if (espece == null || regroupement.get() == Regroupement.PAR_CARRE) {
            especeSelectionnee = null;
            observations.clear();
            carresEspeceSelectionnee.clear();
            detailTitre.set("");
            return;
        }
        especeSelectionnee = espece;
        List<ObservationEspece> detail = service.observationsDeLEspece(idUtilisateur, espece.code(), statut);
        observations.setAll(detail);
        carresEspeceSelectionnee.setAll(detail.stream()
                .map(ObservationEspece::numeroCarre)
                .filter(Objects::nonNull)
                .distinct()
                .toList());
        detailTitre.set(libelleEspece(espece) + SEPARATEUR + quantite(detail.size(), "observation"));
    }

    /// **Exporte** l'inventaire affiché (liste filtrée courante) en CSV vers `destination`. Sans dossier,
    /// l'appel est ignoré ; le bilan (ou l'erreur) va dans [#messageProperty()].
    ///
    /// @return `true` si le fichier a été écrit
    public boolean exporter(Path destination) {
        if (destination == null) {
            return false;
        }
        try {
            if (regroupement.get() == Regroupement.PAR_CARRE) {
                service.exporterCarres(destination, List.copyOf(carres));
            } else {
                service.exporterEspeces(destination, List.copyOf(especes));
            }
            retour.set(RetourOperation.succes("Inventaire exporté vers " + destination.getFileName() + "."));
            return true;
        } catch (RuntimeException echec) {
            retour.set(RetourOperation.erreur(echec.getMessage()));
            return false;
        }
    }

    /// Accord en nombre : `quantite(3, "espèce")` → `3 espèces` ; `quantite(1, "carré")` → `1 carré`.
    private static String quantite(int nombre, String unite) {
        return nombre + " " + unite + (nombre > 1 ? "s" : "");
    }

    /// Libellé d'une espèce pour le titre du détail : nom vernaculaire, sinon latin, sinon code.
    private static String libelleEspece(EspeceAgregee espece) {
        if (espece.nomVernaculaireFr() != null && !espece.nomVernaculaireFr().isBlank()) {
            return espece.nomVernaculaireFr();
        }
        if (espece.nomLatin() != null && !espece.nomLatin().isBlank()) {
            return espece.nomLatin();
        }
        return espece.code();
    }

    /// Décrit la **source audio** des observations de l'**espèce sélectionnée** pour la vue audio unifiée
    /// (#audio) : toutes ses détections à travers les passages de l'utilisateur courant, **avec le `statut`
    /// courant de la barre à puces** (`null` = tous). Construite ici car le ViewModel détient l'`idUtilisateur`
    /// et l'espèce sélectionnée ; le statut, lu par la vue sur la barre de filtres (#537, étape 6), est porté
    /// en texte (le socle `SourceObservations` ne dépend pas de `validation`). Précondition : une espèce est
    /// sélectionnée (le bouton « Écouter / valider » n'est actif qu'avec une observation, donc une espèce).
    public SourceObservations sourceAudioEspece(StatutObservation statut) {
        Objects.requireNonNull(especeSelectionnee, "aucune espèce sélectionnée");
        return new SourceObservations.ParEspece(
                idUtilisateur,
                especeSelectionnee.code(),
                statut == null ? null : statut.name(),
                libelleEspece(especeSelectionnee));
    }

    public ObjectProperty<Regroupement> regroupementProperty() {
        return regroupement;
    }

    /// Socle de filtres composables (#537) sur les observations : la **barre à puces** de la vue (#537,
    /// étape 6) y branche/retire ses prédicats (statut, taxon parent #518, texte). Le callback `agreger`
    /// réagrège tables, carte et résumé à chaque changement.
    public Filtres<ObservationAnalyse> filtres() {
        return filtres;
    }

    /// Taxons parents présents dans les observations (liste déroulante du filtre #518), triés.
    public ObservableList<String> groupesDisponibles() {
        return groupesDisponibles;
    }

    /// Inventaire par espèce filtré (alimenté quand le regroupement est [Regroupement#PAR_ESPECE]).
    public ObservableList<EspeceAgregee> especes() {
        return especes;
    }

    /// Inventaire par carré filtré (alimenté quand le regroupement est [Regroupement#PAR_CARRE]).
    public ObservableList<CarreEspeces> carres() {
        return carres;
    }

    /// Observations de l'espèce sélectionnée, à travers les passages (panneau détail).
    public ObservableList<ObservationEspece> observations() {
        return observations;
    }

    /// Numéros de carré (distincts) où l'espèce sélectionnée est présente, pour la surbrillance de la carte
    /// de répartition. Vide si aucune espèce n'est sélectionnée.
    public ObservableList<String> carresEspeceSelectionnee() {
        return carresEspeceSelectionnee;
    }

    /// Inventaire par carré alimentant la **carte de répartition** (dérivé des observations filtrées),
    /// indépendant du regroupement de la table.
    public ObservableList<CarreEspeces> carresCarte() {
        return carresCarte;
    }

    public ReadOnlyStringProperty detailTitreProperty() {
        return detailTitre.getReadOnlyProperty();
    }

    public ReadOnlyStringProperty resumeProperty() {
        return resume.getReadOnlyProperty();
    }

    /// Retour de la **dernière opération** avec sa sévérité, pour un bandeau de feedback visible.
    /// [RetourOperation#AUCUN] en nominal.
    public ReadOnlyObjectProperty<RetourOperation> retourProperty() {
        return retour.getReadOnlyProperty();
    }
}
