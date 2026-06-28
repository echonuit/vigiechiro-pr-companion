package fr.univ_amu.iut.analyse.viewmodel;

import fr.univ_amu.iut.analyse.model.ServiceAnalyse;
import fr.univ_amu.iut.commun.model.NormalisationTexte;
import fr.univ_amu.iut.validation.model.CarreEspeces;
import fr.univ_amu.iut.validation.model.EspeceAgregee;
import fr.univ_amu.iut.validation.model.ObservationEspece;
import fr.univ_amu.iut.validation.model.StatutObservation;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

/// ViewModel de l'écran transverse **« Espèces & observations »** (feature `analyse`, prisme
/// biodiversité). Pilote l'**inventaire** des observations de l'utilisateur, regroupé **par espèce** ou
/// **par carré** ([Regroupement]), filtré par **statut** de revue (`null` = toutes) et par un **texte**
/// (nom/code d'espèce, ou n°/nom de carré), avec **export CSV**.
///
/// Le filtre de statut ré-interroge le service (filtrage SQL) ; le filtre texte s'applique **en mémoire**
/// sur la liste chargée (pas de requête par frappe). Agnostique de l'IHM (ArchUnit
/// `viewmodel_sans_javafx_ui`). Non-singleton (un VM frais par chargement d'écran).
public class AnalyseViewModel {

    /// Séparateur des compteurs dans les libellés (résumé, titre du détail).
    private static final String SEPARATEUR = " · ";

    private final ServiceAnalyse service;
    private final String idUtilisateur;

    private final ObjectProperty<Regroupement> regroupement =
            new SimpleObjectProperty<>(this, "regroupement", Regroupement.PAR_ESPECE);
    /// Filtre de statut de revue ; `null` = toutes les observations.
    private final ObjectProperty<StatutObservation> filtreStatut = new SimpleObjectProperty<>(this, "filtreStatut");
    /// Filtre texte (insensible casse/accents) ; vide = aucun filtre.
    private final StringProperty filtreTexte = new SimpleStringProperty(this, "filtreTexte", "");

    /// Listes **complètes** (réponse du service) avant le filtre texte, conservées pour re-filtrer en
    /// mémoire sans ré-interroger la base à chaque frappe.
    private List<EspeceAgregee> especesTous = List.of();
    private List<CarreEspeces> carresTous = List.of();

    private final ObservableList<EspeceAgregee> especes = FXCollections.observableArrayList();
    private final ObservableList<CarreEspeces> carres = FXCollections.observableArrayList();
    private final ReadOnlyStringWrapper resume = new ReadOnlyStringWrapper(this, "resume", "");
    private final ReadOnlyStringWrapper message = new ReadOnlyStringWrapper(this, "message", "");

    /// Détail de l'espèce sélectionnée : ses observations à travers les passages (vide tant qu'aucune
    /// espèce n'est sélectionnée), et le titre du panneau.
    private final ObservableList<ObservationEspece> observations = FXCollections.observableArrayList();
    private final ReadOnlyStringWrapper detailTitre = new ReadOnlyStringWrapper(this, "detailTitre", "");

    /// **Numéros de carré** où l'espèce sélectionnée est présente (distincts), pour la **surbrillance** de
    /// la carte de répartition. Vide tant qu'aucune espèce n'est sélectionnée.
    private final ObservableList<String> carresEspeceSelectionnee = FXCollections.observableArrayList();

    /// Inventaire **par carré** pour la **carte de répartition**, chargé quel que soit le regroupement
    /// (la carte est carré-centrée même en mode Par espèce), filtré par le statut courant.
    private final ObservableList<CarreEspeces> carresCarte = FXCollections.observableArrayList();

    public AnalyseViewModel(ServiceAnalyse service, String idUtilisateur) {
        this.service = Objects.requireNonNull(service, "service");
        this.idUtilisateur = Objects.requireNonNull(idUtilisateur, "idUtilisateur");
        regroupement.addListener((obs, ancien, nouveau) -> rafraichir());
        filtreStatut.addListener((obs, ancien, nouveau) -> rafraichir());
        filtreTexte.addListener((obs, ancien, nouveau) -> appliquerFiltreTexte());
    }

    /// (Re)charge depuis le service la liste **active** (selon le regroupement et le statut), puis applique
    /// le filtre texte. À appeler à l'ouverture de l'écran ; ensuite déclenché par tout changement.
    public void rafraichir() {
        StatutObservation statut = filtreStatut.get();
        // La carte de répartition est carré-centrée quel que soit le regroupement : on tient son inventaire
        // par carré à jour (filtré par statut) indépendamment de la table affichée. En mode Par carré, la
        // table et la carte partagent le même inventaire → une seule requête (réutilisée).
        List<CarreEspeces> carresPourLaCarte;
        if (regroupement.get() == Regroupement.PAR_CARRE) {
            carresTous = service.inventaireParCarre(idUtilisateur, statut);
            especesTous = List.of();
            carresPourLaCarte = carresTous;
        } else {
            especesTous = service.inventaireParEspece(idUtilisateur, statut);
            carresTous = List.of();
            carresPourLaCarte = service.inventaireParCarre(idUtilisateur, statut);
        }
        carresCarte.setAll(carresPourLaCarte);
        // L'inventaire a changé : l'ancienne sélection de détail est périmée.
        selectionnerEspece(null);
        appliquerFiltreTexte();
    }

    /// Charge dans le **détail** les observations de l'`espece` sélectionnée, à travers les passages
    /// (filtrées par le statut courant, cohérent avec l'inventaire). `null` (ou en regroupement Par carré)
    /// vide le panneau. Appelé par la vue quand la ligne sélectionnée de l'inventaire change.
    public void selectionnerEspece(EspeceAgregee espece) {
        if (espece == null || regroupement.get() == Regroupement.PAR_CARRE) {
            observations.clear();
            carresEspeceSelectionnee.clear();
            detailTitre.set("");
            return;
        }
        List<ObservationEspece> detail =
                service.observationsDeLEspece(idUtilisateur, espece.code(), filtreStatut.get());
        observations.setAll(detail);
        carresEspeceSelectionnee.setAll(detail.stream()
                .map(ObservationEspece::numeroCarre)
                .filter(Objects::nonNull)
                .distinct()
                .toList());
        detailTitre.set(libelleEspece(espece) + SEPARATEUR + quantite(detail.size(), "observation"));
    }

    /// Filtre **en mémoire** la liste active par le texte courant, et met à jour résumé/listes affichées.
    private void appliquerFiltreTexte() {
        String aiguille = NormalisationTexte.normaliser(filtreTexte.get());
        if (regroupement.get() == Regroupement.PAR_CARRE) {
            carres.setAll(carresTous.stream()
                    .filter(c -> correspond(aiguille, c.numeroCarre(), c.nomSite()))
                    .toList());
            especes.clear();
            int detections =
                    carres.stream().mapToInt(CarreEspeces::nbObservations).sum();
            resume.set(quantite(carres.size(), "carré") + SEPARATEUR + quantite(detections, "détection"));
        } else {
            especes.setAll(especesTous.stream()
                    .filter(e -> correspond(aiguille, e.code(), e.nomVernaculaireFr(), e.nomLatin()))
                    .toList());
            carres.clear();
            int detections =
                    especes.stream().mapToInt(EspeceAgregee::nbObservations).sum();
            resume.set(quantite(especes.size(), "espèce") + SEPARATEUR + quantite(detections, "détection"));
        }
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
            message.set("Inventaire exporté vers " + destination.getFileName() + ".");
            return true;
        } catch (RuntimeException echec) {
            message.set(echec.getMessage());
            return false;
        }
    }

    /// Vrai si au moins un des `champs` (non nuls) contient l'`aiguille` normalisée (vide → toujours vrai).
    private static boolean correspond(String aiguille, String... champs) {
        if (aiguille.isEmpty()) {
            return true;
        }
        for (String champ : champs) {
            if (champ != null && NormalisationTexte.normaliser(champ).contains(aiguille)) {
                return true;
            }
        }
        return false;
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

    public ObjectProperty<Regroupement> regroupementProperty() {
        return regroupement;
    }

    public ObjectProperty<StatutObservation> filtreStatutProperty() {
        return filtreStatut;
    }

    public StringProperty filtreTexteProperty() {
        return filtreTexte;
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

    /// Inventaire par carré alimentant la **carte de répartition** (toujours chargé, filtré par statut),
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

    public ReadOnlyStringProperty messageProperty() {
        return message.getReadOnlyProperty();
    }
}
