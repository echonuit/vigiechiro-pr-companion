package fr.univ_amu.iut.audio.viewmodel;

import fr.univ_amu.iut.bibliotheque.model.ServiceBibliotheque;
import fr.univ_amu.iut.commun.model.PlageNuit;
import fr.univ_amu.iut.commun.viewmodel.Filtres;
import fr.univ_amu.iut.commun.viewmodel.SourceObservations;
import fr.univ_amu.iut.validation.model.BilanImport;
import fr.univ_amu.iut.validation.model.LigneObservationAudio;
import fr.univ_amu.iut.validation.model.ModeRevue;
import fr.univ_amu.iut.validation.model.RevueEnLot;
import fr.univ_amu.iut.validation.model.ServiceValidation;
import fr.univ_amu.iut.validation.model.Taxon;
import fr.univ_amu.iut.validation.model.ValidationManuelle;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
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
/// La logique non-MVVM est sortie en collaborateurs cohésifs : [FormatLigneAudio] (rendu),
/// [ResolveurSourceAudio] (dépiautage des variantes de source) et [ComptageAudio] (compteurs de revue
/// regroupés en un seul value object), pour que le ViewModel garde la seule responsabilité d'orchestrer
/// la revue.
public class AudioViewModel {

    private final ServiceValidation service;
    private final ResolveurSourceAudio resolveur;
    private final ExporteurAudio exporteur;

    /// Actions de revue (unitaires + en lot), déléguées à un collaborateur pour que le VM garde la seule
    /// orchestration (cohésion, seuil PMD). Voir [ActionsRevueAudio].
    private final ActionsRevueAudio actions;

    /// Source courante (provenance + portée). Conservée pour recharger après une action de revue.
    private SourceObservations source;

    /// Jeu de résultats du passage courant (source `ParPassage` seulement), `null` sinon ou sans import.
    private Long idResultats;

    private final ObservableList<LigneObservationAudio> observations = FXCollections.observableArrayList();
    private final FilteredList<LigneObservationAudio> observationsFiltrees = new FilteredList<>(observations);
    private final ObservableList<Taxon> taxons = FXCollections.observableArrayList();
    private final ObjectProperty<LigneObservationAudio> selection = new SimpleObjectProperty<>(this, "selection");
    /// Vrai dès qu'une **ligne** est sélectionnée (observation ou séquence non identifiée). Pilote le bouton
    /// **Corriger** : on peut toujours affecter un taxon à la sélection (correction d'une observation, ou
    /// validation manuelle d'une séquence non identifiée).
    private final ReadOnlyBooleanWrapper selectionPresente =
            new ReadOnlyBooleanWrapper(this, "selectionPresente", false);
    /// Vrai quand la sélection porte une **observation** (id non nul, y compris une observation manuelle).
    /// Pilote le bouton **Référence** (on ne peut archiver que ce qui est déjà une observation).
    private final ReadOnlyBooleanWrapper selectionAvecObservation =
            new ReadOnlyBooleanWrapper(this, "selectionAvecObservation", false);
    /// Vrai quand la sélection porte une **proposition Tadarida** (observation avec `taxon_tadarida`).
    /// Pilote le bouton **Valider** (« retenir la proposition Tadarida » n'a de sens que s'il y en a une).
    private final ReadOnlyBooleanWrapper selectionAvecTadarida =
            new ReadOnlyBooleanWrapper(this, "selectionAvecTadarida", false);
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

    private final ReadOnlyObjectWrapper<ComptageAudio> comptage =
            new ReadOnlyObjectWrapper<>(this, "comptage", ComptageAudio.VIDE);

    private final ReadOnlyStringWrapper detail = new ReadOnlyStringWrapper(this, "detail", "");

    /// Messagerie de la vue : indice d'**état vide** (placeholder gris) et **retour d'opération** (bandeau
    /// coloré par sévérité), deux canaux distincts pour qu'une erreur d'import ne soit plus noyée dans le
    /// placeholder « aucune observation ». Voir [MessagesAudio].
    private final MessagesAudio messages = new MessagesAudio();

    /// Filtres **composables** (#470) appliqués à [#observationsFiltrees]. À chaque changement, le callback
    /// recalcule les **compteurs** sur le sous-ensemble affiché **et** l'**indice d'état vide** (distingue
    /// « source sans observation » de « filtres qui masquent tout »). Déclaré après ses dépendances
    /// (`comptage`, `messages`, `source`) que le callback référence.
    private final Filtres<LigneObservationAudio> filtres = new Filtres<>(observationsFiltrees, () -> {
        comptage.set(ComptageAudio.de(observationsFiltrees));
        messages.majEtatVide(
                observations.isEmpty(), observationsFiltrees.isEmpty(), ResolveurSourceAudio.messageVide(source));
    });

    public AudioViewModel(
            ServiceValidation service,
            ValidationManuelle validationManuelle,
            RevueEnLot revueEnLot,
            ServiceBibliotheque bibliotheque) {
        this.service = Objects.requireNonNull(service, "service");
        this.resolveur = new ResolveurSourceAudio(service);
        this.exporteur = new ExporteurAudio(service, bibliotheque);
        this.actions = new ActionsRevueAudio(
                service,
                Objects.requireNonNull(validationManuelle, "validationManuelle"),
                Objects.requireNonNull(revueEnLot, "revueEnLot"),
                selection::get,
                modeRevue::get,
                this::charger,
                messages);
        selection.addListener((obs, ancien, nouveau) -> majSelection(nouveau));
    }

    /// Ouvre la vue audio sur l'ensemble décrit par `source`. Une erreur de chargement est restituée
    /// dans [#retourProperty()] (bandeau d'erreur) sans lever, l'écran restant vide. Un ensemble vide
    /// n'est pas une erreur : la liste est vide et l'indice d'état vide ([#messageProperty()]) l'explique.
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
            messages.erreur(echec.getMessage());
        }
    }

    /// Plage **nuit** par défaut à proposer au filtre « Heure » (#549) : déléguée à [ResolveurSourceAudio]
    /// (dépiautage de la source), vide si la source ne cible pas un passage unique.
    public Optional<PlageNuit> plageNuitParDefaut() {
        return resolveur.plageNuit(source);
    }

    /// Valide l'observation **sélectionnée** selon le [#modeRevueProperty()] (R15, R18), puis recharge.
    /// Sans sélection, l'appel est ignoré. Délégué à [ActionsRevueAudio].
    ///
    /// @return `true` si la validation a été appliquée
    public boolean valider() {
        return actions.valider();
    }

    /// Corrige l'observation **sélectionnée** (R16 : retient le `taxon` de l'observateur, distinct de
    /// Tadarida) puis recharge. Corriger vers la proposition Tadarida elle-même est refusé (utiliser
    /// [#valider()]). Sans sélection ni taxon, l'appel est ignoré.
    ///
    /// @param taxon taxon retenu par l'observateur
    /// @return `true` si la correction a été appliquée
    public boolean corriger(Taxon taxon) {
        return actions.corriger(taxon);
    }

    /// Bascule l'**archivage en référence** (`is_reference`) de l'observation **sélectionnée** puis recharge.
    /// Sans sélection, l'appel est ignoré.
    ///
    /// @return `true` si la bascule a été appliquée
    public boolean basculerReference() {
        return actions.basculerReference();
    }

    /// Enregistre (ou efface) le **commentaire** de l'observation d'identifiant `idObservation`, puis
    /// recharge. Par identifiant (et non la sélection) pour servir l'**édition inline** de la case commentaire.
    ///
    /// @return `true` si l'enregistrement a réussi
    public boolean commenter(long idObservation, String texte) {
        return actions.commenter(idObservation, texte);
    }

    /// Valide **en lot** les observations `ids` (mode Activité, sans propagation), en une transaction (#479).
    ///
    /// @return le nombre validé
    public int validerLot(List<Long> ids) {
        return actions.validerLot(ids);
    }

    /// Corrige **en lot** les observations `ids` vers `taxon`, en une transaction (#479).
    ///
    /// @return le nombre corrigé
    public int corrigerLot(List<Long> ids, Taxon taxon) {
        return actions.corrigerLot(ids, taxon);
    }

    /// **Marque ou retire** en lot (`reference`) les observations `ids` du corpus de référence (#479).
    ///
    /// @return le nombre traité
    public int basculerReferenceLot(List<Long> ids, boolean reference) {
        return actions.marquerReferenceLot(ids, reference);
    }

    /// Importe un CSV Tadarida (R23) pour le passage courant, puis recharge. Réservé à la source
    /// `ParPassage`. Si un jeu de résultats existe déjà : l'import est **refusé** sauf si `remplacer` est
    /// vrai — auquel cas le jeu existant (et ses observations, cascade) est **supprimé** avant d'importer
    /// le nouveau (réimport, confirmation gérée par la vue). Sans passage ni fichier, l'appel est ignoré.
    ///
    /// @param cheminCsv fichier CSV choisi par l'observateur
    /// @param remplacer `true` pour remplacer un jeu existant (réimport) plutôt que de refuser
    /// @return `true` si l'import a réussi
    public boolean importer(Path cheminCsv, boolean remplacer) {
        Long idPassage = ResolveurSourceAudio.idPassage(source);
        if (idPassage == null || cheminCsv == null) {
            return false;
        }
        if (!remplacer && idResultats != null) {
            messages.info("Des résultats Tadarida sont déjà importés pour ce passage : un seul jeu est permis.");
            return false;
        }
        try {
            // Réimport **atomique** : remplace l'ancien jeu dans une seule transaction (un CSV invalide
            // n'efface jamais l'ancien). Premier import : insertion simple.
            BilanImport bilan =
                    remplacer ? service.reimporter(idPassage, cheminCsv) : service.importer(idPassage, cheminCsv);
            charger();
            idResultats = bilan.idResultats();
            resultatsDisponibles.set(idResultats != null);
            messages.succesImport(bilan);
            return true;
        } catch (RuntimeException echec) {
            messages.erreur(echec.getMessage());
            return false;
        }
    }

    /// Exporte le CSV `_Vu` réinjectable du jeu de résultats courant (R17, R24). Réservé à la source
    /// `ParPassage` avec des résultats importés. Le chemin écrit (ou l'erreur) est restitué dans le message.
    ///
    /// @param destination fichier cible choisi par l'observateur
    /// @return `true` si le fichier a été écrit
    public boolean exporterVu(Path destination) {
        ExporteurAudio.ResultatExport resultat = exporteur.vu(idResultats, destination, inclureMode.get());
        messages.export(resultat.reussi(), resultat.message());
        return resultat.reussi();
    }

    /// Exporte la **bibliothèque de sons de référence** vers le dossier `destination` (P10). Réservé à la
    /// source `References`. Le bilan (ou l'erreur d'écriture) est restitué dans le message.
    ///
    /// @param destination dossier cible choisi par l'observateur
    /// @return `true` si l'export a réussi
    public boolean exporterBibliotheque(Path destination) {
        ExporteurAudio.ResultatExport resultat = exporteur.bibliotheque(destination);
        messages.export(resultat.reussi(), resultat.message());
        return resultat.reussi();
    }

    /// Exporte en **CSV** les observations **actuellement affichées** (filtres appliqués) vers
    /// `destination` (#149). Le sous-ensemble est **figé** au moment de l'appel. Le bilan (ou l'erreur
    /// d'écriture) est restitué dans le message.
    ///
    /// @param destination fichier CSV cible choisi par l'observateur
    /// @return `true` si le fichier a été écrit
    public boolean exporterObservations(Path destination) {
        ExporteurAudio.ResultatExport resultat = exporteur.observations(List.copyOf(observationsFiltrees), destination);
        messages.export(resultat.reussi(), resultat.message());
        return resultat.reussi();
    }

    /// Recharge les lignes de la source courante en **préservant la sélection**, puis met à jour compteurs
    /// et indice d'état vide.
    private void charger() {
        LigneObservationAudio selectionnee = selection.get();
        List<LigneObservationAudio> lignes = resolveur.lignes(source);
        observations.setAll(lignes);
        reselectionner(selectionnee);
        // Ré-applique les filtres actifs ; le callback recompte ET met à jour l'indice d'état vide (source
        // vide vs filtres qui masquent tout) sur le sous-ensemble affiché.
        filtres.appliquer();
    }

    /// Repositionne la sélection après un rechargement : sur la **même ligne** si elle existe encore,
    /// **sinon `null`**. Indispensable car une action peut faire **disparaître** la ligne sélectionnée de la
    /// source (ex. retirer `is_reference` sur `References`) : garder l'ancienne sélection laisserait détail /
    /// audio / boutons alimentés par une ligne absente de la liste.
    private void reselectionner(LigneObservationAudio avant) {
        LigneObservationAudio retrouvee = avant == null
                ? null
                : observations.stream()
                        .filter(avant::estLaMemeLigneQue)
                        .findFirst()
                        .orElse(null);
        selection.set(retrouvee);
    }

    private void majSelection(LigneObservationAudio courant) {
        boolean present = courant != null;
        boolean avecObservation = present && courant.idObservation() != null;
        selectionPresente.set(present);
        selectionAvecObservation.set(avecObservation);
        selectionAvecTadarida.set(avecObservation && courant.taxonTadarida() != null);
        selectionReference.set(present && courant.reference());
        detail.set(present ? FormatLigneAudio.detail(courant) : "");
        cheminAudioCourant.set(
                present ? service.cheminAudio(courant.idSequence()).orElse(null) : null);
    }

    private void reinitialiser() {
        idResultats = null;
        resultatsDisponibles.set(false);
        selection.set(null);
        observations.clear();
        taxons.clear();
        detail.set("");
        comptage.set(ComptageAudio.VIDE);
        messages.reinitialiser();
    }

    /// Source courante (provenance + portée), `null` avant la première ouverture. Porte aussi les
    /// **capacités** (statiques) de la source : la vue lit `source().permetWorkflowTadarida()` (import
    /// CSV / export `_Vu`) et `source().permetExportBibliotheque()` pour afficher les actions propres,
    /// et bâtit le fil d'Ariane selon la provenance.
    public SourceObservations source() {
        return source;
    }

    /// Lignes **filtrées** (conjonction des filtres actifs, cf. [#filtres]) à afficher dans la table. Les
    /// compteurs ([#comptageProperty()]) reflètent ce **sous-ensemble affiché** (#470).
    public ObservableList<LigneObservationAudio> observationsFiltrees() {
        return observationsFiltrees;
    }

    /// Filtres **composables** de la table (#470/#471) : la barre de filtres (patron « à la Notion ») y
    /// branche/retire ses critères (statut, chauves-souris, taxon, références, proba, texte) via
    /// [Filtres#definir] ; la conjonction est appliquée à [#observationsFiltrees] et les compteurs
    /// suivent le sous-ensemble affiché.
    public Filtres<LigneObservationAudio> filtres() {
        return filtres;
    }

    /// Observation sélectionnée (liée au modèle de sélection de la table par la vue).
    public ObjectProperty<LigneObservationAudio> selectionProperty() {
        return selection;
    }

    /// `true` dès qu'une **ligne** est sélectionnée (bouton Corriger : affecter un taxon, correction ou
    /// validation manuelle).
    public ReadOnlyBooleanProperty selectionPresenteProperty() {
        return selectionPresente.getReadOnlyProperty();
    }

    /// `true` si la sélection porte une **observation** (bouton Référence) ; `false` pour une séquence non
    /// identifiée pas encore validée.
    public ReadOnlyBooleanProperty selectionAvecObservationProperty() {
        return selectionAvecObservation.getReadOnlyProperty();
    }

    /// `true` si la sélection porte une **proposition Tadarida** (bouton Valider) ; `false` pour une
    /// observation manuelle ou une séquence non identifiée (rien à « retenir »).
    public ReadOnlyBooleanProperty selectionAvecTadaridaProperty() {
        return selectionAvecTadarida.getReadOnlyProperty();
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

    /// Compteurs de progression de la revue (total / validées / corrigées + libellé d'avancement),
    /// regroupés en un seul value object [ComptageAudio].
    public ReadOnlyObjectProperty<ComptageAudio> comptageProperty() {
        return comptage.getReadOnlyProperty();
    }

    /// Indice d'**état vide** de la table (« aucune observation… »), vide en nominal. Réservé au
    /// placeholder gris : le retour des opérations passe par [#retourProperty()].
    public ReadOnlyStringProperty messageProperty() {
        return messages.etatVideProperty();
    }

    /// Retour de la **dernière opération** (import / export / valider / corriger) avec sa sévérité, pour
    /// un bandeau de feedback visible. [RetourOperation#AUCUN] en nominal.
    public ReadOnlyObjectProperty<RetourOperation> retourProperty() {
        return messages.retourProperty();
    }

    /// Efface le retour d'opération (l'utilisateur a lu le bandeau et le ferme). Le bandeau disparaît.
    public void effacerRetour() {
        messages.effacerRetour();
    }
}
