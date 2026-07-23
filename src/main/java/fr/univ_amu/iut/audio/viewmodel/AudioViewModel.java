package fr.univ_amu.iut.audio.viewmodel;

import fr.univ_amu.iut.bibliotheque.model.ServiceBibliotheque;
import fr.univ_amu.iut.commun.model.PlageNuit;
import fr.univ_amu.iut.commun.model.SondeAccessibilite;
import fr.univ_amu.iut.commun.viewmodel.Filtres;
import fr.univ_amu.iut.commun.viewmodel.RetourOperation;
import fr.univ_amu.iut.commun.viewmodel.SourceObservations;
import fr.univ_amu.iut.passage.model.ServiceDisponibiliteAudio;
import fr.univ_amu.iut.validation.model.LigneObservationAudio;
import fr.univ_amu.iut.validation.model.MarquageDouteux;
import fr.univ_amu.iut.validation.model.ModeRevue;
import fr.univ_amu.iut.validation.model.PlageNuitPassage;
import fr.univ_amu.iut.validation.model.RevueEnLot;
import fr.univ_amu.iut.validation.model.SaisieCertitude;
import fr.univ_amu.iut.validation.model.ServiceValidation;
import fr.univ_amu.iut.validation.model.Taxon;
import fr.univ_amu.iut.validation.model.ValidationManuelle;
import fr.univ_amu.iut.validation.model.dao.ProjectionsAudioDao;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
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

    /// Disponibilité de l'audio pour l'écran (#1301) : bandeau, présence des fichiers. Collaborateur
    /// cohésif, extrait pour la même raison que les autres (PMD GodClass).
    private final DisponibiliteEcoute disponibiliteEcoute;

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
    /// État **dérivé de la sélection** (présence / observation / proposition Tadarida / référence / douteux),
    /// extrait dans [EtatSelectionAudio] pour la cohésion (seuil PMD GodClass) : pilote l'activation et les
    /// libellés des boutons de la barre d'actions.
    private final EtatSelectionAudio etatSelection = new EtatSelectionAudio();
    /// Ce que le panneau d'écoute doit savoir de la sélection : chemin servi, absence, divergence.
    private final EtatEcouteAudio etatEcoute = new EtatEcouteAudio();

    /// Texte du bandeau « passage archivé / audio partiel » (#1301), vide quand l'audio est complet
    /// ou que la source couvre plusieurs passages (le gating ligne à ligne reste actif).
    private final ReadOnlyStringWrapper bandeauArchive = new ReadOnlyStringWrapper(this, "bandeauArchive", "");

    private final BooleanProperty inclureMode = new SimpleBooleanProperty(this, "inclureMode", true);
    private final ObjectProperty<ModeRevue> modeRevue =
            new SimpleObjectProperty<>(this, "modeRevue", ModeRevue.ACTIVITE);

    /// `true` quand un jeu de résultats est chargé (active l'export `_Vu` de la source `ParPassage`).
    private final ReadOnlyBooleanWrapper resultatsDisponibles =
            new ReadOnlyBooleanWrapper(this, "resultatsDisponibles", false);

    /// `true` quand le passage courant n'a **aucune observation ancrée** à la plateforme (#1596) : passage
    /// reconstruit par CSV (#1565) tant qu'il n'a pas été réactivé (#1571). L'IHM grise alors « publier les
    /// corrections » (rien n'y est publiable sans ancrage). `false` hors source `ParPassage`.
    private final ReadOnlyBooleanWrapper publicationImpossible =
            new ReadOnlyBooleanWrapper(this, "publicationImpossible", false);

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

    /// @param fichierPresent présence d'un fichier sur disque : `Files::exists` en production ;
    ///     injecté pour rester testable avec des chemins factices (`p -> true` préserve les
    ///     fixtures, `p -> false` simule un audio disparu, #1301)
    /// La discussion avec le validateur (#1417 lire, #1418 répondre), en un seul collaborateur.
    private final DiscussionValidateur discussion;

    public AudioViewModel(
            ServiceValidation service,
            ProjectionsAudioDao projectionsAudio,
            PlageNuitPassage plageNuitPassage,
            ValidationManuelle validationManuelle,
            MarquageDouteux marquageDouteux,
            SaisieCertitude saisieCertitude,
            RevueEnLot revueEnLot,
            ServiceBibliotheque bibliotheque,
            ServiceDisponibiliteAudio disponibilite,
            Predicate<Path> fichierPresent,
            DiscussionValidateur discussion) {
        this.service = Objects.requireNonNull(service, "service");
        this.discussion = Objects.requireNonNull(discussion, "discussion");
        this.disponibiliteEcoute = new DisponibiliteEcoute(disponibilite, fichierPresent);
        this.resolveur = new ResolveurSourceAudio(service, projectionsAudio, plageNuitPassage);
        this.exporteur = new ExporteurAudio(service, bibliotheque);
        this.actions = new ActionsRevueAudio(
                service,
                Objects.requireNonNull(validationManuelle, "validationManuelle"),
                Objects.requireNonNull(marquageDouteux, "marquageDouteux"),
                Objects.requireNonNull(saisieCertitude, "saisieCertitude"),
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
    /// Collaborateur des **actions de revue** (validation, correction, référence, douteux, commentaire),
    /// unitaires et **en lot** (#479). Exposé pour que la barre d'actions (`ActionsSelectionAudio`) et les
    /// colonnes éditables l'invoquent directement, sans multiplier les délégations sur ce ViewModel.
    public ActionsRevueAudio actions() {
        return actions;
    }

    public void ouvrirSur(SourceObservations source) {
        Objects.requireNonNull(source, "source");
        try {
            appliquerOuverture(source, chargerOuverture(source));
        } catch (RuntimeException echec) {
            signalerErreur(source, echec);
        }
    }

    /// **Lecture seule** des données d'ouverture (taxons + résolution de la source). Sûre **hors du fil
    /// JavaFX** (#1214, déport via `IndicateurOccupation`).
    public DonneesOuverture chargerOuverture(SourceObservations source) {
        // #1596 : l'absence d'ancrage plateforme n'a de sens que pour un passage unique (seule source où la
        // publication de corrections est offerte) ; on l'évalue ici, hors fil, plutôt que sur le fil JavaFX.
        Long idPassage = ResolveurSourceAudio.idPassage(source);
        return new DonneesOuverture(
                service.taxonsDisponibles(),
                resolveur.idResultats(source),
                resolveur.lignes(source),
                disponibiliteEcoute.decompte(source),
                idPassage != null && service.publicationImpossible(idPassage));
    }

    /// Applique les données d'ouverture (taxons, résultats, table). **Mutations observables** : sur le
    /// fil JavaFX. Un ensemble vide n'est pas une erreur (l'indice d'état vide l'explique).
    public void appliquerOuverture(SourceObservations source, DonneesOuverture donnees) {
        this.source = source;
        reinitialiser();
        taxons.setAll(donnees.taxons());
        idResultats = donnees.idResultats();
        resultatsDisponibles.set(idResultats != null);
        observations.setAll(donnees.lignes());
        bandeauArchive.set(DisponibiliteEcoute.texteBandeau(donnees.decompteAudio()));
        publicationImpossible.set(donnees.publicationImpossible());
        filtres.appliquer();
    }

    /// Route l'échec d'un chargement vers le bandeau d'erreur de l'écran (filet #795), sans lever.
    public void signalerErreur(SourceObservations source, Throwable erreur) {
        this.source = source;
        reinitialiser();
        String detail = erreur.getMessage();
        messages.erreur(detail != null && !detail.isBlank() ? detail : "Chargement des sons impossible.");
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

    /// Bascule le drapeau **douteux** (#160) de l'observation **sélectionnée** puis recharge. Sans sélection
    /// (ou sur une séquence sans observation), l'appel est sans effet utile.
    ///
    /// @return `true` si la bascule a été appliquée
    public boolean basculerDouteux() {
        return actions.basculerDouteux();
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
        return actions.importer(ResolveurSourceAudio.idPassage(source), idResultats, cheminCsv, remplacer, id -> {
            idResultats = id;
            resultatsDisponibles.set(id != null);
        });
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
    /// **Sonde d'abord la destination** (#2426) : un dossier inutilisable (un fichier, non créable, non
    /// inscriptible) est refusé **avant** de lancer la copie, avec le motif, comme pour la sauvegarde
    /// ([SondeAccessibilite], harmonisation #2258). Sans cette garde, l'échec ne se découvrait qu'à
    /// mi-parcours, sur un message d'E/S brut et après une copie partielle.
    ///
    /// @param destination dossier cible choisi par l'observateur
    /// @return `true` si l'export a réussi
    public boolean exporterBibliotheque(Path destination) {
        if (destination == null) {
            return false;
        }
        SondeAccessibilite.Verdict verdict = SondeAccessibilite.sonder(destination);
        if (!verdict.accessible()) {
            messages.avertissement("Dossier inutilisable : " + verdict.motif() + " (" + destination + ").");
            return false;
        }
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
        etatSelection.maj(courant);
        detail.set(present ? FormatLigneAudio.detail(courant) : "");
        Path chemin = present ? service.cheminAudio(courant.idSequence()).orElse(null) : null;
        etatEcoute.maj(
                chemin,
                disponibiliteEcoute::manquant,
                ignore -> service.divergenceAudio(courant.idSequence()).orElse(""));
    }

    private void reinitialiser() {
        idResultats = null;
        resultatsDisponibles.set(false);
        selection.set(null);
        observations.clear();
        taxons.clear();
        detail.set("");
        comptage.set(ComptageAudio.VIDE);
        etatEcoute.reinitialiser();
        bandeauArchive.set("");
        publicationImpossible.set(false);
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

    /// État **dérivé de la sélection** (présence / observation / proposition Tadarida / référence / douteux),
    /// où la barre d'actions branche l'activation et les libellés de ses boutons. Extrait dans
    /// [EtatSelectionAudio] pour la cohésion (seuil PMD GodClass).
    /// Ce que le panneau d'écoute doit savoir de la sélection (chemin servi, absence, divergence),
    /// exposé en bloc comme [#etatSelection()] : un collaborateur, pas quatre accesseurs.
    public EtatEcouteAudio etatEcoute() {
        return etatEcoute;
    }

    public EtatSelectionAudio etatSelection() {
        return etatSelection;
    }

    /// Taxons connus en base, pour le sélecteur de correction (R16).
    public ObservableList<Taxon> taxons() {
        return taxons;
    }

    /// `true` dès qu'un jeu de résultats est chargé (activation du bouton d'export `_Vu`).
    public ReadOnlyBooleanProperty resultatsDisponiblesProperty() {
        return resultatsDisponibles.getReadOnlyProperty();
    }

    /// `true` quand le passage courant est **sans ancrage plateforme** (#1596) : reconstruit par CSV et
    /// pas encore réactivé, donc sans correction publiable. La vue grise « publier les corrections » et en
    /// explique la cause (le libellé de l'item invite à réactiver le passage). `false` dès qu'au moins une
    /// observation est ancrée, ou hors source `ParPassage`.
    public ReadOnlyBooleanProperty publicationImpossibleProperty() {
        return publicationImpossible.getReadOnlyProperty();
    }

    /// Inclure la colonne `validation_mode` (R24) à l'export `_Vu` (case à cocher, vraie par défaut).
    public BooleanProperty inclureModeProperty() {
        return inclureMode;
    }

    /// Mode de revue (R18) pilotant [#valider()] : `ACTIVITE` (défaut) ou `INVENTAIRE` (propagation).
    public ObjectProperty<ModeRevue> modeRevueProperty() {
        return modeRevue;
    }

    /// Texte du bandeau de disponibilité de l'audio du passage (#1301) : vide = rien à signaler
    /// (bandeau masqué), sinon « passage archivé » ou « audio partiel n/total » avec la voie de
    /// retour.
    public ReadOnlyStringProperty bandeauArchiveProperty() {
        return bandeauArchive.getReadOnlyProperty();
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

    /// Signale dans le bandeau qu'une action **n'a pas eu lieu**, faute de cible : guidage, pas échec
    /// technique (sévérité `INFO`). Sert au double-clic sur un taxon sans fiche (#1834), dont le silence
    /// se lisait comme une panne.
    public void signaler(String texte) {
        messages.info(texte);
    }

    /// Efface le retour d'opération (l'utilisateur a lu le bandeau et le ferme). Le bandeau disparaît.
    public void effacerRetour() {
        messages.effacerRetour();
    }

    /// La **discussion avec le validateur** (#1417 / #1418) : lire le fil, savoir qui l'on est, répondre.
    public DiscussionValidateur discussion() {
        return discussion;
    }
}
