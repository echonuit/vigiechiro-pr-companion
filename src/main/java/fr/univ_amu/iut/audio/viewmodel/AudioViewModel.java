package fr.univ_amu.iut.audio.viewmodel;

import fr.univ_amu.iut.commun.viewmodel.SourceObservations;
import fr.univ_amu.iut.validation.model.LigneObservationAudio;
import fr.univ_amu.iut.validation.model.ModeRevue;
import fr.univ_amu.iut.validation.model.ServiceValidation;
import fr.univ_amu.iut.validation.model.StatutObservation;
import fr.univ_amu.iut.validation.model.Taxon;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.ReadOnlyIntegerProperty;
import javafx.beans.property.ReadOnlyIntegerWrapper;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;

/// ViewModel de la **vue audio unifiée** (#audio) : un espace de travail unique pour écouter, valider /
/// corriger et archiver en référence un **ensemble d'observations**, quelle que soit sa provenance.
///
/// L'ensemble est décrit par une [SourceObservations] (passage / lot de passages / espèce / corpus de
/// référence) que le VM **résout** en `List<LigneObservationAudio>` via [ServiceValidation], puis pilote :
/// sélection, chemin audio courant (E7.S3), valider (R15/R18), corriger (R16), **basculer la référence**
/// (`is_reference`, action commune à toutes les sources), et — pour la seule source `ParPassage` —
/// l'import du CSV Tadarida (R23) et l'export `_Vu` (R17). L'export de la bibliothèque (source
/// `References`) sera branché avec la vue.
///
/// Feature **`audio`** (puits) : dépend des **modèles** des features `validation` et — à terme —
/// `bibliotheque`, jamais de leur `view`/`viewmodel` ; aucune feature ne dépend de `audio`, le graphe de
/// slices reste acyclique. VM agnostique de l'IHM (`viewmodel_sans_javafx_ui` : seuls `javafx.beans` /
/// `javafx.collections`). Non-singleton.
///
/// `@SuppressWarnings("PMD.GodClass")` (bypass documenté du ruleset) : la logique a déjà été extraite
/// dans [FormatLigneAudio] (rendu) et `ResolveurSourceAudio` (dépiautage des variantes de source). Le
/// signal restant tient aux **~19 accesseurs de propriétés observables** d'un ViewModel MVVM complet,
/// qui abaissent mécaniquement la cohésion (chaque getter touche un champ distinct) sans concentration
/// de comportement : même forme que `validation.viewmodel.ValidationViewModel`.
@SuppressWarnings("PMD.GodClass")
public class AudioViewModel {

    private final ServiceValidation service;
    private final ResolveurSourceAudio resolveur;

    /// Source courante (provenance + portée). Conservée pour recharger après une action de revue.
    private SourceObservations source;

    /// Jeu de résultats du passage courant (source `ParPassage` seulement), `null` sinon ou sans import.
    private Long idResultats;

    private final ObservableList<LigneObservationAudio> observations = FXCollections.observableArrayList();
    private final FilteredList<LigneObservationAudio> observationsFiltrees = new FilteredList<>(observations);
    private final ObjectProperty<StatutObservation> filtreStatut =
            new SimpleObjectProperty<>(this, "filtreStatut", null);
    private final ObservableList<Taxon> taxons = FXCollections.observableArrayList();
    private final ObjectProperty<LigneObservationAudio> selection = new SimpleObjectProperty<>(this, "selection");
    private final ReadOnlyBooleanWrapper selectionPresente =
            new ReadOnlyBooleanWrapper(this, "selectionPresente", false);
    private final ReadOnlyBooleanWrapper selectionReference =
            new ReadOnlyBooleanWrapper(this, "selectionReference", false);
    private final ReadOnlyObjectWrapper<Path> cheminAudioCourant =
            new ReadOnlyObjectWrapper<>(this, "cheminAudioCourant");

    private final BooleanProperty inclureMode = new SimpleBooleanProperty(this, "inclureMode", true);
    private final ObjectProperty<ModeRevue> modeRevue =
            new SimpleObjectProperty<>(this, "modeRevue", ModeRevue.ACTIVITE);

    /// `true` quand un jeu de résultats est chargé (active l'export `_Vu` de la source `ParPassage`).
    private final ReadOnlyBooleanWrapper resultatsDisponibles =
            new ReadOnlyBooleanWrapper(this, "resultatsDisponibles", false);

    private final ReadOnlyIntegerWrapper nombreTotal = new ReadOnlyIntegerWrapper(this, "nombreTotal", 0);
    private final ReadOnlyIntegerWrapper nombreValidees = new ReadOnlyIntegerWrapper(this, "nombreValidees", 0);
    private final ReadOnlyIntegerWrapper nombreCorrigees = new ReadOnlyIntegerWrapper(this, "nombreCorrigees", 0);
    private final ReadOnlyStringWrapper progression = new ReadOnlyStringWrapper(this, "progression", "");

    private final ReadOnlyStringWrapper detail = new ReadOnlyStringWrapper(this, "detail", "");
    private final ReadOnlyStringWrapper message = new ReadOnlyStringWrapper(this, "message", "");

    public AudioViewModel(ServiceValidation service) {
        this.service = Objects.requireNonNull(service, "service");
        this.resolveur = new ResolveurSourceAudio(service);
        selection.addListener((obs, ancien, nouveau) -> majSelection(nouveau));
        filtreStatut.addListener((obs, ancien, nouveau) ->
                observationsFiltrees.setPredicate(nouveau == null ? null : ligne -> ligne.statut() == nouveau));
    }

    /// Ouvre la vue audio sur l'ensemble décrit par `source`. Une erreur de chargement est restituée
    /// dans [#messageProperty()] sans lever, l'écran restant vide. Un ensemble vide n'est pas une
    /// erreur : la liste est vide et un message d'état neutre l'explique.
    public void ouvrirSur(SourceObservations source) {
        this.source = Objects.requireNonNull(source, "source");
        reinitialiser();
        try {
            taxons.setAll(service.taxonsDisponibles());
            idResultats = resolveur.idResultats(source);
            resultatsDisponibles.set(idResultats != null);
            charger();
        } catch (RuntimeException echec) {
            reinitialiser();
            message.set(echec.getMessage());
        }
    }

    /// Valide l'observation sélectionnée selon le [#modeRevueProperty()] (R15, R18), puis recharge.
    /// Disponible **dans toutes les sources**. Sans sélection, l'appel est ignoré.
    ///
    /// @return `true` si la validation a été appliquée
    public boolean valider() {
        LigneObservationAudio courant = selection.get();
        if (courant == null) {
            return false;
        }
        return appliquerAction(() -> service.validerSelonMode(courant.idObservation(), modeRevue.get()));
    }

    /// Corrige l'observation sélectionnée (R16 : retient le `taxon` de l'observateur, distinct de
    /// Tadarida) puis recharge. Corriger vers la proposition Tadarida elle-même est refusé (ce serait une
    /// **validation**) : on invite alors à utiliser [#valider()]. Sans sélection ni taxon, l'appel est
    /// ignoré.
    ///
    /// @param taxon taxon retenu par l'observateur
    /// @return `true` si la correction a été appliquée
    public boolean corriger(Taxon taxon) {
        LigneObservationAudio courant = selection.get();
        if (courant == null || taxon == null) {
            return false;
        }
        if (taxon.code().equals(courant.taxonTadarida())) {
            message.set("Pour retenir la proposition Tadarida, utilisez « Valider » : corriger attend"
                    + " un autre taxon.");
            return false;
        }
        return appliquerAction(() -> service.corriger(courant.idObservation(), taxon.code(), null));
    }

    /// Bascule l'**archivage en référence** (`is_reference`) de l'observation sélectionnée puis recharge.
    /// Action **commune à toutes les sources** : c'est le maillon manquant qui alimente le corpus de
    /// référence depuis l'application. Sans sélection, l'appel est ignoré.
    ///
    /// @return `true` si la bascule a été appliquée
    public boolean basculerReference() {
        LigneObservationAudio courant = selection.get();
        if (courant == null) {
            return false;
        }
        return appliquerAction(() -> service.marquerReference(courant.idObservation(), !courant.reference()));
    }

    /// Importe un CSV Tadarida (R23) pour le passage courant, puis recharge. Réservé à la source
    /// `ParPassage` ; un second import (résultats déjà présents) est refusé. Sans passage ni fichier,
    /// l'appel est ignoré.
    ///
    /// @param cheminCsv fichier CSV choisi par l'observateur
    /// @return `true` si l'import a réussi
    public boolean importer(Path cheminCsv) {
        Long idPassage = ResolveurSourceAudio.idPassage(source);
        if (idPassage == null || cheminCsv == null) {
            return false;
        }
        if (idResultats != null) {
            message.set("Des résultats Tadarida sont déjà importés pour ce passage : un seul jeu est permis.");
            return false;
        }
        boolean ok = appliquerAction(() -> service.importer(idPassage, cheminCsv));
        if (ok) {
            idResultats = service.resultatsDuPassage(idPassage).orElse(null);
            resultatsDisponibles.set(idResultats != null);
        }
        return ok;
    }

    /// Exporte le CSV `_Vu` réinjectable du jeu de résultats courant (R17, R24). Réservé à la source
    /// `ParPassage` avec des résultats importés. Le chemin écrit (ou l'erreur) est restitué dans le
    /// message.
    ///
    /// @param destination fichier cible choisi par l'observateur
    /// @return `true` si le fichier a été écrit
    public boolean exporterVu(Path destination) {
        if (idResultats == null || destination == null) {
            return false;
        }
        try {
            Path ecrit = service.exporter(idResultats, destination, inclureMode.get());
            message.set("Fichier _Vu exporté : " + ecrit.getFileName());
            return true;
        } catch (RuntimeException echec) {
            message.set(echec.getMessage());
            return false;
        }
    }

    private boolean appliquerAction(Runnable action) {
        try {
            action.run();
            charger();
            return true;
        } catch (RuntimeException echec) {
            message.set(echec.getMessage());
            return false;
        }
    }

    /// Recharge les lignes de la source courante en préservant la sélection (par identifiant
    /// d'observation), puis met à jour compteurs et message d'état.
    private void charger() {
        Long observationSelectionnee =
                selection.get() == null ? null : selection.get().idObservation();
        List<LigneObservationAudio> lignes = resolveur.lignes(source);
        observations.setAll(lignes);
        reselectionner(observationSelectionnee);
        majCompteurs();
        message.set(lignes.isEmpty() ? ResolveurSourceAudio.messageVide(source) : "");
    }

    private void reselectionner(Long idObservation) {
        if (idObservation == null) {
            return;
        }
        observations.stream()
                .filter(ligne -> ligne.idObservation() == idObservation)
                .findFirst()
                .ifPresent(selection::set);
    }

    private void majCompteurs() {
        int total = observations.size();
        int validees = (int) observations.stream()
                .filter(ligne -> ligne.statut() == StatutObservation.VALIDEE)
                .count();
        int corrigees = (int) observations.stream()
                .filter(ligne -> ligne.statut() == StatutObservation.CORRIGEE)
                .count();
        nombreTotal.set(total);
        nombreValidees.set(validees);
        nombreCorrigees.set(corrigees);
        progression.set(total == 0 ? "" : (validees + corrigees) + " / " + total + " revues");
    }

    private void majSelection(LigneObservationAudio courant) {
        selectionPresente.set(courant != null);
        selectionReference.set(courant != null && courant.reference());
        detail.set(courant == null ? "" : FormatLigneAudio.detail(courant));
        cheminAudioCourant.set(
                courant == null
                        ? null
                        : service.cheminAudio(courant.idSequence()).orElse(null));
    }

    private void reinitialiser() {
        idResultats = null;
        resultatsDisponibles.set(false);
        selection.set(null);
        observations.clear();
        taxons.clear();
        detail.set("");
        nombreTotal.set(0);
        nombreValidees.set(0);
        nombreCorrigees.set(0);
        progression.set("");
        message.set("");
    }

    /// Source courante (provenance + portée), `null` avant la première ouverture. Porte aussi les
    /// **capacités** (statiques) de la source : la vue lit `source().permetWorkflowTadarida()` (import
    /// CSV / export `_Vu`) et `source().permetExportBibliotheque()` pour afficher les actions propres,
    /// et bâtit le fil d'Ariane selon la provenance.
    public SourceObservations source() {
        return source;
    }

    /// Lignes **filtrées** (par [#filtreStatutProperty()]) à afficher dans la table.
    public ObservableList<LigneObservationAudio> observationsFiltrees() {
        return observationsFiltrees;
    }

    /// Lignes **non filtrées** (les compteurs de progression les reflètent intégralement).
    public ObservableList<LigneObservationAudio> observations() {
        return observations;
    }

    /// Filtre de statut de la table (`null` = tous) : À revoir / Validée / Corrigée.
    public ObjectProperty<StatutObservation> filtreStatutProperty() {
        return filtreStatut;
    }

    /// Observation sélectionnée (liée au modèle de sélection de la table par la vue).
    public ObjectProperty<LigneObservationAudio> selectionProperty() {
        return selection;
    }

    /// `true` dès qu'une observation est sélectionnée (activation des actions de revue).
    public ReadOnlyBooleanProperty selectionPresenteProperty() {
        return selectionPresente.getReadOnlyProperty();
    }

    /// `true` si l'observation sélectionnée est déjà en référence (libellé du bouton bascule : marquer
    /// vs retirer).
    public ReadOnlyBooleanProperty selectionReferenceProperty() {
        return selectionReference.getReadOnlyProperty();
    }

    /// Taxons connus en base, pour le sélecteur de correction (R16).
    public ObservableList<Taxon> taxons() {
        return taxons;
    }

    /// `true` dès qu'un jeu de résultats est chargé (activation du bouton d'export `_Vu`).
    public ReadOnlyBooleanProperty resultatsDisponiblesProperty() {
        return resultatsDisponibles.getReadOnlyProperty();
    }

    /// Inclure la colonne `validation_mode` (R24) à l'export `_Vu` (case à cocher, vraie par défaut).
    public BooleanProperty inclureModeProperty() {
        return inclureMode;
    }

    /// Mode de revue (R18) pilotant [#valider()] : `ACTIVITE` (défaut) ou `INVENTAIRE` (propagation).
    public ObjectProperty<ModeRevue> modeRevueProperty() {
        return modeRevue;
    }

    /// Chemin du fichier audio (séquence transformée) de l'observation sélectionnée, ou `null` (E7.S3).
    public ReadOnlyObjectProperty<Path> cheminAudioCourantProperty() {
        return cheminAudioCourant.getReadOnlyProperty();
    }

    /// Détail multi-ligne de l'observation sélectionnée, vide quand aucune n'est sélectionnée.
    public ReadOnlyStringProperty detailProperty() {
        return detail.getReadOnlyProperty();
    }

    /// Nombre total d'observations de la source.
    public ReadOnlyIntegerProperty nombreTotalProperty() {
        return nombreTotal.getReadOnlyProperty();
    }

    /// Nombre d'observations validées (R15).
    public ReadOnlyIntegerProperty nombreValideesProperty() {
        return nombreValidees.getReadOnlyProperty();
    }

    /// Nombre d'observations corrigées (R16).
    public ReadOnlyIntegerProperty nombreCorrigeesProperty() {
        return nombreCorrigees.getReadOnlyProperty();
    }

    /// Avancement de la revue (`N / T revues`), vide tant qu'aucune observation n'est chargée.
    public ReadOnlyStringProperty progressionProperty() {
        return progression.getReadOnlyProperty();
    }

    /// Message d'état (erreur de chargement, ou source vide), vide en nominal.
    public ReadOnlyStringProperty messageProperty() {
        return message.getReadOnlyProperty();
    }

    /// Identifiant du jeu de résultats Tadarida chargé, `null` hors source `ParPassage` ou sans import.
    public Long idResultats() {
        return idResultats;
    }
}
