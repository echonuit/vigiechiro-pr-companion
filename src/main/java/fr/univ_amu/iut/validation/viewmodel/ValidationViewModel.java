package fr.univ_amu.iut.validation.viewmodel;

import fr.univ_amu.iut.validation.model.ModeRevue;
import fr.univ_amu.iut.validation.model.ObservationStatut;
import fr.univ_amu.iut.validation.model.ServiceValidation;
import fr.univ_amu.iut.validation.model.StatutObservation;
import fr.univ_amu.iut.validation.model.Taxon;
import fr.univ_amu.iut.validation.model.VueValidation;
import java.nio.file.Path;
import java.util.Objects;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.ReadOnlyIntegerProperty;
import javafx.beans.property.ReadOnlyIntegerWrapper;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

/// ViewModel de l'écran **M-Vision-Tadarida** (validation taxonomique des résultats Tadarida d'un
/// passage, parcours P7).
///
/// Ouvert sur un `idPassage`, il lit [ServiceValidation#chargerValidation(Long)] et expose la liste
/// des observations (avec leur statut de revue), la sélection courante et son détail, ainsi que les
/// compteurs de progression (validées / corrigées / total). VM agnostique de l'IHM (règle ArchUnit
/// `viewmodel_sans_javafx_ui`) : seuls `javafx.beans`/`javafx.collections`. Non-singleton.
///
/// La revue est portée par [#valider()] (R15) et [#corriger(Taxon)] (R16) : chaque action
/// délègue au service, puis recharge la vue. L'export `_Vu` et l'import CSV restent à part.
public class ValidationViewModel {

    private final ServiceValidation service;

    /// Passage courant, conservé pour recharger la vue après une action de revue (valider/corriger).
    private Long idPassage;

    /// Identifiant du jeu de résultats courant (`identification_results`), `null` si aucun import.
    /// Conservé pour les actions d'export et de revue des incréments suivants.
    private Long idResultats;

    private final ObservableList<ObservationStatut> observations = FXCollections.observableArrayList();
    private final ObservableList<Taxon> taxons = FXCollections.observableArrayList();
    private final ObjectProperty<ObservationStatut> selection = new SimpleObjectProperty<>(this, "selection");
    private final ReadOnlyBooleanWrapper selectionPresente =
            new ReadOnlyBooleanWrapper(this, "selectionPresente", false);
    private final ReadOnlyBooleanWrapper resultatsDisponibles =
            new ReadOnlyBooleanWrapper(this, "resultatsDisponibles", false);
    private final BooleanProperty inclureMode = new SimpleBooleanProperty(this, "inclureMode", true);
    private final ObjectProperty<ModeRevue> modeRevue =
            new SimpleObjectProperty<>(this, "modeRevue", ModeRevue.ACTIVITE);

    private final ReadOnlyIntegerWrapper nombreTotal = new ReadOnlyIntegerWrapper(this, "nombreTotal", 0);
    private final ReadOnlyIntegerWrapper nombreValidees = new ReadOnlyIntegerWrapper(this, "nombreValidees", 0);
    private final ReadOnlyIntegerWrapper nombreCorrigees = new ReadOnlyIntegerWrapper(this, "nombreCorrigees", 0);
    private final ReadOnlyStringWrapper progression = new ReadOnlyStringWrapper(this, "progression", "");

    private final ReadOnlyStringWrapper detail = new ReadOnlyStringWrapper(this, "detail", "");
    private final ReadOnlyStringWrapper message = new ReadOnlyStringWrapper(this, "message", "");

    public ValidationViewModel(ServiceValidation service) {
        this.service = Objects.requireNonNull(service, "service");
        selection.addListener((obs, ancien, nouveau) -> majSelection(nouveau));
    }

    /// Ouvre la validation du passage `idPassage`. Une erreur (passage/résultats illisibles) est
    /// restituée dans [#messageProperty()] sans lever, l'écran restant vide. Un passage sans CSV
    /// importé n'est pas une erreur : la liste est vide et un message d'état neutre l'explique.
    public void ouvrirSur(Long idPassage) {
        this.idPassage = idPassage;
        reinitialiser();
        try {
            taxons.setAll(service.taxonsDisponibles());
            appliquer(service.chargerValidation(idPassage));
        } catch (RuntimeException echec) {
            reinitialiser();
            message.set(echec.getMessage());
        }
    }

    /// Valide l'observation sélectionnée selon le [#modeRevueProperty()] (R15, R18), puis recharge.
    /// En `ACTIVITE`, seule l'observation visée est validée ; en `INVENTAIRE`, la décision est
    /// propagée (`auto`) aux autres détections non touchées de la même espèce Tadarida. Sans
    /// sélection, l'appel est ignoré. Une erreur métier est restituée dans [#messageProperty()].
    ///
    /// @return `true` si la validation a été appliquée
    public boolean valider() {
        ObservationStatut courant = selection.get();
        if (courant == null || courant.observation().id() == null) {
            return false;
        }
        return appliquerAction(
                () -> service.validerSelonMode(courant.observation().id(), modeRevue.get()));
    }

    /// Corrige l'observation sélectionnée (R16 : retient le `taxon` de l'observateur, distinct de
    /// Tadarida) puis recharge la vue. Sans sélection ni taxon, l'appel est ignoré.
    ///
    /// Corriger vers la proposition Tadarida elle-même est refusé : ce serait une **validation**, pas
    /// une correction (le service la reclasserait `NON_TOUCHEE`, laissant la ligne « À revoir »
    /// malgré une saisie manuelle). On invite alors à utiliser [#valider()].
    ///
    /// @param taxon taxon retenu par l'observateur
    /// @return `true` si la correction a été appliquée
    public boolean corriger(Taxon taxon) {
        ObservationStatut courant = selection.get();
        if (courant == null || courant.observation().id() == null || taxon == null) {
            return false;
        }
        if (taxon.code().equals(courant.observation().taxonTadarida())) {
            message.set("Pour retenir la proposition Tadarida, utilisez « Valider » : corriger attend"
                    + " un autre taxon.");
            return false;
        }
        return appliquerAction(() -> service.corriger(courant.observation().id(), taxon.code(), null));
    }

    /// Importe un CSV Tadarida (`*-observations.csv` ou `_Vu.csv`, R23) pour le passage courant, puis
    /// recharge la vue. Sans passage ouvert ni fichier, l'appel est ignoré. Une erreur d'import
    /// (passage sans session, séquence ou taxon inconnu) est restituée dans [#messageProperty()].
    ///
    /// Un seul jeu de résultats par passage est permis (`identification_results.passage_id` est
    /// unique) : un second import est refusé en amont (il violerait la contrainte d'unicité). Côté
    /// vue, le bouton d'import est désactivé dès que des résultats existent.
    ///
    /// @param cheminCsv fichier CSV choisi par l'observateur
    /// @return `true` si l'import a réussi
    public boolean importer(Path cheminCsv) {
        if (idPassage == null || cheminCsv == null) {
            return false;
        }
        if (idResultats != null) {
            message.set("Des résultats Tadarida sont déjà importés pour ce passage : un seul jeu est permis.");
            return false;
        }
        return appliquerAction(() -> service.importer(idPassage, cheminCsv));
    }

    private boolean appliquerAction(Runnable action) {
        try {
            action.run();
            appliquer(service.chargerValidation(idPassage));
            return true;
        } catch (RuntimeException echec) {
            message.set(echec.getMessage());
            return false;
        }
    }

    private void appliquer(VueValidation vue) {
        idResultats = vue.idResultats();
        resultatsDisponibles.set(vue.idResultats() != null);
        observations.setAll(vue.observations());
        majCompteurs();
        message.set(messageEtat(vue));
    }

    /// Exporte le CSV `_Vu` réinjectable du jeu de résultats courant vers `destination` (R17). La
    /// colonne `validation_mode` (R24) est incluse selon [#inclureModeProperty()]. Sans résultats
    /// importés, l'appel est ignoré. Le chemin écrit (ou l'erreur) est restitué dans le message.
    ///
    /// @param destination fichier cible choisi par l'observateur
    /// @return `true` si le fichier a été écrit
    public boolean exporter(Path destination) {
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

    /// État neutre de l'écran : distingue l'absence d'import (`idResultats == null`) d'un CSV
    /// effectivement importé mais sans aucune détection (en-tête seul) ; vide en présence
    /// d'observations.
    private static String messageEtat(VueValidation vue) {
        if (vue.idResultats() == null) {
            return "Aucun résultat Tadarida importé pour ce passage.";
        }
        if (vue.observations().isEmpty()) {
            return "Résultats Tadarida importés, mais aucune détection à valider.";
        }
        return "";
    }

    private void majCompteurs() {
        int validees = compter(StatutObservation.VALIDEE);
        int corrigees = compter(StatutObservation.CORRIGEE);
        int total = observations.size();
        nombreTotal.set(total);
        nombreValidees.set(validees);
        nombreCorrigees.set(corrigees);
        progression.set(total == 0 ? "" : (validees + corrigees) + " / " + total + " revues");
    }

    private int compter(StatutObservation statut) {
        return (int) observations.stream().filter(o -> o.statut() == statut).count();
    }

    private void majSelection(ObservationStatut courant) {
        selectionPresente.set(courant != null);
        detail.set(courant == null ? "" : FormatObservation.detail(courant));
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

    /// Observations du passage (avec statut de revue), dans l'ordre d'import.
    public ObservableList<ObservationStatut> observations() {
        return observations;
    }

    /// Observation sélectionnée dans la liste (liée au modèle de sélection de la table par la vue).
    public ObjectProperty<ObservationStatut> selectionProperty() {
        return selection;
    }

    /// `true` dès qu'une observation est sélectionnée (activation des boutons valider/corriger).
    public ReadOnlyBooleanProperty selectionPresenteProperty() {
        return selectionPresente.getReadOnlyProperty();
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

    /// Mode de revue (R18) pilotant [#valider()] : `ACTIVITE` (une par une, défaut) ou `INVENTAIRE`
    /// (valider une espèce propage aux autres détections non touchées de la même espèce).
    public ObjectProperty<ModeRevue> modeRevueProperty() {
        return modeRevue;
    }

    /// Détail multi-ligne de l'observation sélectionnée, vide quand aucune n'est sélectionnée.
    public ReadOnlyStringProperty detailProperty() {
        return detail.getReadOnlyProperty();
    }

    /// Nombre total d'observations du passage.
    public ReadOnlyIntegerProperty nombreTotalProperty() {
        return nombreTotal.getReadOnlyProperty();
    }

    /// Nombre d'observations validées (R15 : taxon observateur = taxon Tadarida).
    public ReadOnlyIntegerProperty nombreValideesProperty() {
        return nombreValidees.getReadOnlyProperty();
    }

    /// Nombre d'observations corrigées (R16 : taxon observateur différent de Tadarida).
    public ReadOnlyIntegerProperty nombreCorrigeesProperty() {
        return nombreCorrigees.getReadOnlyProperty();
    }

    /// Avancement de la revue (`N / T revues`), vide tant qu'aucune observation n'est chargée.
    public ReadOnlyStringProperty progressionProperty() {
        return progression.getReadOnlyProperty();
    }

    /// Message d'état (erreur de chargement, ou absence de résultats importés), vide en nominal.
    public ReadOnlyStringProperty messageProperty() {
        return message.getReadOnlyProperty();
    }

    /// Identifiant du jeu de résultats Tadarida chargé, `null` si aucun import pour ce passage.
    public Long idResultats() {
        return idResultats;
    }
}
