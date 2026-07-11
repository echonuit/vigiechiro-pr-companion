package fr.univ_amu.iut.importation.viewmodel;

import fr.univ_amu.iut.commun.model.Horloge;
import fr.univ_amu.iut.commun.model.Prefixe;
import fr.univ_amu.iut.commun.model.Progression;
import fr.univ_amu.iut.commun.viewmodel.NavigationViewModel;
import fr.univ_amu.iut.commun.viewmodel.ProgressionOperation;
import fr.univ_amu.iut.importation.model.ExtracteurZip;
import fr.univ_amu.iut.importation.model.JetonAnnulation;
import fr.univ_amu.iut.importation.model.NuitAImporter;
import fr.univ_amu.iut.importation.model.ResultatImport;
import fr.univ_amu.iut.importation.model.ResultatImportMultiNuits;
import fr.univ_amu.iut.importation.model.ServiceImport;
import fr.univ_amu.iut.sites.model.ServiceSites;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

/// ViewModel de l'assistant **M-Import** (« Importer une nuit »), en **orchestrateur** (#183).
///
/// Couvre les **étapes 1 à 4** de la maquette en coordonnant trois préoccupations, chacune déléguée ou
/// portée ici :
///  1-2. choix du dossier + **inspection** (R9) → sous-VM [InspectionImportViewModel] ;
///  3.   **rattachement** (site / point / année / n° de passage) + aperçu (R6) → sous-VM
///       [RattachementImportViewModel] ;
///  4.   **exécution** de l'import (état, résultat, progression, verrou #54) → porté ici.
///
/// L'orchestrateur **compose** les sous-VM : `peutImporter` = inspection réussie **et** rattachement
/// complet ; [#preparerImport()] assemble la demande depuis le dossier (inspection) et le point/préfixe
/// (rattachement) ; après une inspection réussie, il transmet au rattachement l'exemple de nom
/// (valeur dérivée de l'inspection) pour l'aperçu. Côté **erreurs**, chaque préoccupation porte la
/// sienne (inspection : son propre message ; exécution : [#messageExecution]) ; l'orchestrateur les
/// **recompose** dans un unique message présenté à la vue ([#messageErreurProperty()]). L'API publique
/// exposée à la vue (dossier, inspection, rattachement…) **délègue** aux sous-VM (vue inchangée).
///
/// [#importer()] est **synchrone** ; la vue lance plutôt l'import sur un fil d'arrière-plan
/// ([ExecutionImport]) pour ne pas figer l'IHM, et relaie
/// la **progression déterminée** (#33) au fil JavaFX via [#appliquerProgression]. Seul `javafx.beans` /
/// `javafx.collections` est importé ici, jamais `javafx.scene` (règle `viewmodel_sans_javafx_ui`).
public class ImportationViewModel {

    private final ServiceImport serviceImport;

    /// Préférence **« conserver les originaux »** (#…), extraite dans un collaborateur partagé : le VM la
    /// lit au lancement de l'import (et l'y mémorise) ; la vue lie la case à sa propriété. Cf.
    /// [PreferenceConservation].
    private final PreferenceConservation conservation;

    /// Socle de navigation : la feature y pousse le **verrou** (#54) pour interdire de quitter
    /// l'assistant pendant un import (l'écran porte la seule vue/VM qui reçoit le résultat).
    private final NavigationViewModel navigation;

    /// Étapes 1-2 : sous-VM d'**inspection** (#183) — dossier source + état d'inspection.
    private final InspectionImportViewModel inspection;

    /// Étape 3 : sous-VM du **rattachement** (#183) — site / point / année / n° de passage + aperçu.
    private final RattachementImportViewModel rattachement;

    /// Message d'erreur **unifié** présenté à la vue : recomposé depuis l'erreur d'inspection (portée
    /// par le sous-VM inspection) et l'erreur d'exécution ([#messageExecution]).
    private final ReadOnlyStringWrapper messageErreur = new ReadOnlyStringWrapper(this, "messageErreur", "");

    /// Message d'erreur **propre à l'exécution** (rattachement incomplet, échec d'import). Combiné avec
    /// l'erreur d'inspection dans [#messageErreur] (le sous-VM inspection porte la sienne).
    private final ReadOnlyStringWrapper messageExecution = new ReadOnlyStringWrapper(this, "messageExecution", "");

    /// Conjonction d'activation du bouton « Importer cette nuit » : composée (inspection + rattachement).
    private final BooleanBinding peutImporter;

    /// Étape 4 : exécution de l'import (état + résultat), pilotée par [#importer()].
    private final ReadOnlyObjectWrapper<EtatImport> etat = new ReadOnlyObjectWrapper<>(this, "etat", EtatImport.PRET);
    private final ReadOnlyObjectWrapper<ResultatImport> resultat = new ReadOnlyObjectWrapper<>(this, "resultat", null);

    /// Résultat d'un import **multi-nuits** (un passage par nuit incluse), `null` hors de ce cas. La vue
    /// s'en sert pour un récapitulatif « N passages créés ». Le [#resultatProperty()] mono-nuit pointe
    /// alors sur la **première** nuit (compatibilité des consommateurs existants).
    private final ReadOnlyObjectWrapper<ResultatImportMultiNuits> resultatNuits =
            new ReadOnlyObjectWrapper<>(this, "resultatNuits", null);

    /// Suivi de la **progression déterminée** de l'import en cours (#33/#146), extrait dans un collaborateur
    /// dédié ([ProgressionOperation]) : fraction `[0, 1]` + libellé d'étape avec ETA. La vue s'y lie via
    /// [#progression()].
    private final ProgressionOperation progressionOperation = new ProgressionOperation();

    /// Table de suivi **par fichier** de l'import en cours (#947) : une [LigneFichierImport] par original
    /// de la nuit, avec son état (copie → transformation → terminé / rejeté). La vue lie sa `TableView` à
    /// [SuiviLignesFichiers#lignes()] via [#suiviFichiers()] ; le service la nourrit hors-thread via le
    /// relais du controller (`Platform.runLater`).
    private final SuiviLignesFichiers suiviFichiers = new SuiviLignesFichiers();

    /// Pré-contrôle R5 proactif (#108) extrait dans un collaborateur dédié ([ControleNumeroPassage]) :
    /// signale qu'un n° de passage est déjà pris (bloque [#peutImporter()]) et propose le prochain libre.
    /// Recalculé à chaque changement de point / année / n° de passage du rattachement.
    private final ControleNumeroPassage controleNumeroPassage;

    /// Coordination de l'import **multi-nuits** (#…) extraite dans un collaborateur dédié
    /// ([CoordinationNuits]) : auto-numérotation des nuits incluses + préparation/exécution de la demande.
    /// Il s'abonne lui-même au rattachement et à la table des nuits ; l'orchestrateur compose sa validité
    /// dans `peutImporter`.
    private final CoordinationNuits coordinationNuits;

    /// Exécution hors-thread de l'import mono-nuit, extraite dans un collaborateur dédié
    /// ([ExecutionImport]) : préoccupation sans lecture de `Property`, exposée via [#execution()].
    private final ExecutionImport execution;

    /// Dossier temporaire d'extraction d'un `.zip` choisi comme source (#139), à supprimer après import
    /// (succès ou échec) ou au changement de source ; `null` quand la source est un dossier déjà
    /// décompressé.
    private Path dossierTemporaireZip;

    /// Fichiers **rejetés** par le dernier import (#155), « nom — raison », pour les afficher dans M-Import.
    private final ObservableList<String> rejetsImport = FXCollections.observableArrayList();

    public ImportationViewModel(
            ServiceImport serviceImport,
            ServiceSites serviceSites,
            Horloge horloge,
            String idUtilisateur,
            NavigationViewModel navigation,
            PreferenceConservation conservation) {
        this.serviceImport = Objects.requireNonNull(serviceImport, "serviceImport");
        this.navigation = Objects.requireNonNull(navigation, "navigation");
        this.conservation = Objects.requireNonNull(conservation, "conservation");
        this.inspection = new InspectionImportViewModel(serviceImport);
        // Sous-VM rattachement (#183) : il valide serviceSites / horloge / idUtilisateur et préremplit
        // l'année courante.
        this.rattachement = new RattachementImportViewModel(serviceSites, horloge, idUtilisateur);
        // Pré-contrôle R5 proactif (#108) : observe lui-même le rattachement et entretient son état.
        this.controleNumeroPassage = new ControleNumeroPassage(serviceImport, rattachement);
        // Coordination multi-nuits (#…) : s'abonne au rattachement et à la table des nuits pour
        // auto-numéroter les nuits incluses et exposer la validité de cette numérotation.
        this.coordinationNuits = new CoordinationNuits(serviceImport, inspection, rattachement);
        this.execution = new ExecutionImport(serviceImport);

        // Changer de dossier source invalide l'inspection précédente : un nouveau dossier doit être
        // ré-inspecté (sinon le bouton Importer resterait actif et l'aperçu garderait l'ancien exemple).
        inspection.dossierSourceProperty().addListener((obs, ancien, nouveau) -> reinitialiserPourNouveauDossier());

        // Éditer le rattachement après un import terminé/échoué recrée une préparation non lancée que la
        // garde de navigation (#140) doit protéger : on ré-arme l'état PRET (cf. rearmerPreparationSiTerminee).
        rattachement.siteSelectionneProperty().addListener((obs, ancien, nouveau) -> rearmerPreparationSiTerminee());
        rattachement.pointSelectionneProperty().addListener((obs, ancien, nouveau) -> rearmerPreparationSiTerminee());
        rattachement.anneeProperty().addListener((obs, ancien, nouveau) -> rearmerPreparationSiTerminee());
        rattachement.numeroPassageProperty().addListener((obs, ancien, nouveau) -> rearmerPreparationSiTerminee());

        // Message unifié : recomposé dès que l'inspection ou l'exécution change le sien.
        inspection.messageErreurProperty().addListener((obs, ancien, nouveau) -> rafraichirMessage());
        messageExecution.addListener((obs, ancien, nouveau) -> rafraichirMessage());

        // peutImporter = inspection réussie ET rattachement complet ET (multi-nuits : numérotation valide,
        // déléguée à CoordinationNuits ; sinon : n° de passage libre #108) : composition par l'orchestrateur.
        peutImporter = Bindings.createBooleanBinding(
                () -> inspection.estInspecte()
                        && rattachement.estComplet()
                        && (inspection.plusieursNuits()
                                ? coordinationNuits.numerotationValideProperty().get()
                                : !controleNumeroPassage.estDejaUtilise()),
                inspection.inspecteProperty(),
                inspection.plusieursNuitsProperty(),
                rattachement.siteSelectionneProperty(),
                rattachement.pointSelectionneProperty(),
                rattachement.numeroPassageProperty(),
                controleNumeroPassage.dejaUtiliseProperty(),
                coordinationNuits.numerotationValideProperty());
    }

    /// Sous-VM d'**inspection** (étapes 1-2) : la vue **s'y lie directement** (dossier source, état
    /// d'inspection, journal/relevé, compte, nommage, avertissements #33). L'orchestrateur ne re-expose
    /// plus ces propriétés une à une (façade allégée — la vue dépend des sous-VM, pas d'un orchestrateur
    /// God Class).
    public InspectionImportViewModel inspection() {
        return inspection;
    }

    /// Sous-VM de **rattachement** (étape 3) : la vue **s'y lie directement** (site / point / année / n°,
    /// aperçu du préfixe, avertissement de discordance de préfixe #111).
    public RattachementImportViewModel rattachement() {
        return rattachement;
    }

    /// Sous-VM de **contrôle du n° de passage** (pré-contrôle d'unicité R5 #108) : la vue y délègue la
    /// correction en un clic (`utiliserProchainNumeroLibre`), le compte d'écrasement et l'écrasement
    /// destructif (#214). L'orchestrateur ne re-expose plus ces opérations une à une (façade allégée).
    public ControleNumeroPassage controleNumero() {
        return controleNumeroPassage;
    }

    /// Message d'erreur **unifié** (inspection ou exécution), vide en fonctionnement nominal.
    public ReadOnlyStringProperty messageErreurProperty() {
        return messageErreur.getReadOnlyProperty();
    }

    /// Avertissement de doublon R5 (#108), **vide** si le n° est libre ; sinon explique le doublon et
    /// propose le prochain n° libre (délégué à [ControleNumeroPassage], collaborateur **possédé par**
    /// l'orchestrateur — il dépend de `ServiceImport`). Sa non-vacuité signale à la fois l'avertissement à
    /// afficher et l'import bloqué (cf. `peutImporter`).
    public ReadOnlyStringProperty avertissementNumeroPassageProperty() {
        return controleNumeroPassage.avertissementProperty();
    }

    /// Conjonction d'activation du bouton « Importer cette nuit » : dossier inspecté + rattachement
    /// complet (site + point + n° de passage valides).
    public BooleanBinding peutImporter() {
        return peutImporter;
    }

    /// État de l'exécution de l'import (PRET / EN_COURS / TERMINE / ECHEC) : pilote l'affichage de la
    /// vue (assistant, progression, résumé, erreur).
    public ReadOnlyObjectProperty<EtatImport> etatProperty() {
        return etat.getReadOnlyProperty();
    }

    /// Résultat du dernier import réussi (passage/session créés, compteurs, anomalies) ; `null` tant
    /// qu'aucun import n'a abouti. En import multi-nuits, pointe sur la **première** nuit.
    public ReadOnlyObjectProperty<ResultatImport> resultatProperty() {
        return resultat.getReadOnlyProperty();
    }

    /// Résultat d'un import **multi-nuits** (un [ResultatImport] par passage créé) ; `null` hors de ce
    /// cas. La vue en tire un récapitulatif « N passages créés ».
    public ReadOnlyObjectProperty<ResultatImportMultiNuits> resultatNuitsProperty() {
        return resultatNuits.getReadOnlyProperty();
    }

    /// Suivi de la **progression** de l'import/décompression en cours (#33/#146) : la vue lie la barre à
    /// `progression().fractionProperty()` et le libellé à `progression().messageProperty()` ; le travail
    /// hors-thread appelle `progression().appliquer(...)`. Extrait dans [ProgressionOperation].
    public ProgressionOperation progression() {
        return progressionOperation;
    }

    /// Suivi **par fichier** de l'import en cours (#947) : ses [SuiviLignesFichiers#lignes()] (une
    /// [LigneFichierImport] par original) alimentent la table de M-Import, en complément de la barre
    /// globale. Réinitialisé au lancement de chaque import ([#marquerEnCours()]).
    public SuiviLignesFichiers suiviFichiers() {
        return suiviFichiers;
    }

    /// Recharge les sites de l'utilisateur courant (à l'ouverture de l'écran ou après création d'un
    /// site).
    public void chargerSites() {
        rattachement.chargerSites();
    }

    /// Pré-sélectionne le site `idSite` dans le rattachement (raccourci depuis la fiche d'un site).
    public void preselectionnerSite(Long idSite) {
        rattachement.preselectionnerSite(idSite);
    }

    /// Inspecte le dossier source courant **en lecture seule** (R9) via le sous-VM inspection. En cas
    /// de succès, transmet au rattachement l'exemple de nom (aperçu) ; en cas d'erreur (publiée par
    /// l'inspection), remet l'exécution et l'aperçu à zéro.
    public void inspecter() {
        inspection.inspecter();
        if (inspection.estInspecte()) {
            rattachement.definirOriginaux(inspection.nomsOriginaux());
        } else {
            reinitialiserExecution();
            rattachement.definirOriginaux(List.of());
        }
    }

    /// Lance l'import **mono-nuit de façon synchrone** (copie protégée R9 + renommage R6/R7 +
    /// transformation R10/R11). Pratique pour les tests et le chemin simple ; pour ne pas figer l'IHM (et
    /// pour le **multi-nuits**), la vue préfère le découpage `preparerImport`/`preparerImportNuits`
    /// (instantané) + `executerImport`/`executerImportNuits` (hors-thread) +
    /// `marquerEnCours`/`marquerTermine`/`marquerTermineNuits`/`marquerEchec` (sur le fil JavaFX).
    public void importer() {
        if (!peutImporter.get()) {
            // Message ciblé : si l'import est bloqué par un n° déjà pris (#108), on l'explique au lieu du
            // générique « Complétez le rattachement » (qui serait trompeur, le rattachement étant complet).
            messageExecution.set(controleNumeroPassage.messageBlocage(
                    "Complétez le rattachement (dossier inspecté, site, point) avant d'importer."));
            return;
        }
        marquerEnCours();
        try {
            marquerTermine(execution.executer(preparerImport()));
        } catch (RuntimeException echec) {
            marquerEchec(echec.getMessage());
        }
    }

    /// Passe l'état à `EN_COURS` et efface le message. À appeler sur le fil JavaFX, avant de
    /// lancer l'exécution en arrière-plan.
    public void marquerEnCours() {
        messageExecution.set("");
        progressionOperation.demarrer("Préparation…");
        suiviFichiers.reinitialiser(); // la table par fichier (#947) repart vide, le plan la remplira
        etat.set(EtatImport.EN_COURS);
        // Verrou de navigation (#54) : on ne doit pas quitter l'assistant tant que l'import tourne,
        // sinon son résultat (marquerTermine/marquerEchec) serait perdu en détachant la vue.
        navigation.setOperationCritique("l'import");
    }

    /// Passe l'état à `EXTRACTION` (#139/#146) : la barre de progression apparaît immédiatement pendant la
    /// décompression d'un `.zip` choisi comme source, avant l'inspection. À appeler **sur le fil JavaFX**
    /// juste avant de lancer l'extraction en arrière-plan. L'état revient à `PRET` tout seul quand la
    /// source extraite est posée (réinitialisation pour nouveau dossier) ou via [#signalerSourceIllisible].
    public void marquerExtractionEnCours() {
        messageExecution.set("");
        progressionOperation.demarrer("Préparation de la décompression…");
        etat.set(EtatImport.EXTRACTION);
        // Verrou de navigation (#54) : on ne doit pas quitter l'assistant pendant la décompression, sinon
        // le fil d'arrière-plan continuerait d'écrire un gros temporaire et posterait des mutations
        // Platform.runLater sur une vue détachée. Déverrouillé par reinitialiserExecution (fin ou erreur).
        navigation.setOperationCritique("l'import");
    }

    /// Résout la **source d'import** choisie (#139) : si `chemin` est un `.zip`, le décompresse vers un
    /// dossier temporaire (nettoyé après import) et renvoie ce dossier ; sinon renvoie le dossier tel
    /// quel. **Ne touche aucune `Property`** (IO seul) : à appeler **hors du fil JavaFX** (la vue lance
    /// l'extraction en arrière-plan pour ne pas figer l'IHM), puis à inspecter sur le fil JavaFX.
    ///
    /// @throws RuntimeException si l'archive est illisible ou invalide (zip-slip) ; la vue le signale via
    /// [#signalerSourceIllisible].
    public Path extraireSiZip(Path chemin) {
        return extraireSiZip(chemin, p -> {});
    }

    /// Variante de [#extraireSiZip(Path)] qui **notifie l'avancement** de la décompression (#146) via
    /// `surProgression` (un `Progression` « X / N fichiers » par fichier extrait). Le callback est invoqué
    /// hors du fil JavaFX : l'appelant (la vue) le marshale par `Platform.runLater` vers
    /// [#appliquerProgression].
    public Path extraireSiZip(Path chemin, Consumer<Progression> surProgression) {
        return extraireSiZip(chemin, surProgression, JetonAnnulation.neutre());
    }

    /// Variante **annulable** de l'extraction (#146) : `jeton` interrompt la décompression entre deux
    /// fichiers (le temporaire partiel est alors nettoyé par l'extracteur, et `dossierTemporaireZip` reste
    /// `null` puisque l'affectation n'aboutit pas).
    public Path extraireSiZip(Path chemin, Consumer<Progression> surProgression, JetonAnnulation jeton) {
        nettoyerTemporaireZip(); // une nouvelle source remplace l'éventuel zip précédent
        if (ExtracteurZip.estZip(chemin)) {
            Path base = serviceImport.racineWorkspace();
            // Filet anti-fuite : balaye les temporaires d'extraction abandonnés par un précédent écran
            // d'import (le VM est non-singleton, il ne garde pas leur référence) avant d'en créer un neuf.
            ExtracteurZip.nettoyerTemporairesResiduels(base);
            // Extraction sous le workspace (disque), pas dans le tmpfs RAM /tmp : une nuit de ~10 Go y
            // saturerait la RAM (ENOSPC). Cf. ExtracteurZip.
            dossierTemporaireZip = ExtracteurZip.extraireVersDossierTemporaire(chemin, base, surProgression, jeton);
            // Déplie un éventuel dossier racine unique (zip « compresser ce dossier ») pour que
            // l'inspection retrouve journal et WAV ; le temporaire à nettoyer reste dossierTemporaireZip.
            return ExtracteurZip.racineEffective(dossierTemporaireZip);
        }
        return chemin;
    }

    /// Signale (fil JavaFX) qu'une source choisie est illisible (zip invalide…) : remet l'inspection et
    /// l'exécution à zéro et publie `message` dans le message unifié (#139).
    public void signalerSourceIllisible(String message) {
        inspection.reinitialiser();
        reinitialiserExecution();
        nettoyerTemporaireZip();
        messageExecution.set(message);
    }

    /// Capture (sur le fil JavaFX) les entrées du rattachement courant dans un instantané immuable,
    /// pour les passer à [ExecutionImport#executer(DemandeImport)] sans relire de `Property` hors-thread.
    /// Précondition : rattachement complet ([#peutImporter()] vrai), garanti par l'appelant.
    public DemandeImport preparerImport() {
        // Mémorise le choix « conserver les originaux » au moment de lancer l'import (survit aux sessions).
        conservation.memoriser();
        return new DemandeImport(
                inspection.dossier(),
                rattachement.idPointSelectionne(),
                rattachement.prefixeCourant(),
                conservation.valeur());
    }

    /// Exécution du travail lourd de l'import mono-nuit (#33/#146/#947), extraite dans un collaborateur
    /// dédié ([ExecutionImport]) : la vue s'en sert hors-thread (`execution().executer(...)`), sans
    /// lecture de `Property`.
    public ExecutionImport execution() {
        return execution;
    }

    /// Instantané immuable des entrées d'un import, capturé sur le fil JavaFX par preparerImport.
    ///
    /// @param conserverOriginaux `true` pour copier les WAV dans `bruts/` (défaut), `false` pour les
    ///     transformer directement depuis la source sans les copier (économie d'espace).
    public record DemandeImport(Path dossier, Long idPoint, Prefixe prefixe, boolean conserverOriginaux) {}

    /// Coordination de l'import **multi-nuits** (#…) : la vue s'y adresse pour préparer la demande
    /// (nuits incluses + n° proposés) et l'exécuter hors-thread. Extraite pour garder l'orchestrateur
    /// mince (cf. [CoordinationNuits]).
    public CoordinationNuits coordinationNuits() {
        return coordinationNuits;
    }

    /// Instantané immuable des entrées d'un import multi-nuits (un passage par nuit incluse).
    ///
    /// @param prefixeBase préfixe R6 commun (carré/année/point) ; le n° de passage vient de chaque
    ///     [NuitAImporter]
    /// @param nuits nuits incluses, avec leur n° de passage attribué (auto-numérotation consécutive)
    public record DemandeImportNuits(
            Path dossier, Long idPoint, Prefixe prefixeBase, List<NuitAImporter> nuits, boolean conserverOriginaux) {
        public DemandeImportNuits {
            nuits = List.copyOf(nuits);
        }
    }

    /// Applique un import réussi (résultat exposé, état `TERMINE`). À appeler sur le fil JavaFX
    /// (depuis `Platform.runLater`).
    public void marquerTermine(ResultatImport resultatImport) {
        resultat.set(resultatImport);
        messageExecution.set("");
        // Rapport (#155) : on expose la liste des fichiers rejetés (« nom — raison ») pour M-Import.
        rejetsImport.setAll(resultatImport.rapport().rejetsFormates());
        resultatNuits.set(null); // import mono-nuit : pas de résultat agrégé
        etat.set(EtatImport.TERMINE);
        navigation.setOperationCritique(""); // l'import est fini : on peut de nouveau naviguer (#54)
        nettoyerTemporaireZip(); // les fichiers ont été copiés (R9) : le temporaire du zip n'est plus utile (#139)
    }

    /// Applique un import **multi-nuits** réussi (#…) : expose le résultat agrégé (un passage par nuit),
    /// pointe [#resultatProperty()] sur la première nuit (compatibilité), agrège les fichiers rejetés de
    /// **toutes** les nuits, état `TERMINE`. À appeler sur le fil JavaFX (depuis `Platform.runLater`).
    public void marquerTermineNuits(ResultatImportMultiNuits resultatMultiNuits) {
        resultatNuits.set(resultatMultiNuits);
        resultat.set(resultatMultiNuits.premier()); // compatibilité : le mono-résultat pointe la 1re nuit
        messageExecution.set("");
        rejetsImport.setAll(resultatMultiNuits.rejetsFormates()); // rejets cumulés de toutes les nuits (#155)
        etat.set(EtatImport.TERMINE);
        navigation.setOperationCritique(""); // l'import est fini : on peut de nouveau naviguer (#54)
        nettoyerTemporaireZip();
    }

    /// Fichiers rejetés (« nom — raison ») par le dernier import (#155), pour affichage dans M-Import.
    public ObservableList<String> rejetsImport() {
        return rejetsImport;
    }

    /// Applique un échec d'import : efface le résultat, renseigne le message, état `ECHEC`. À
    /// appeler sur le fil JavaFX (depuis `Platform.runLater`).
    public void marquerEchec(String message) {
        resultat.set(null);
        resultatNuits.set(null);
        messageExecution.set(message);
        etat.set(EtatImport.ECHEC);
        navigation.setOperationCritique(""); // l'import s'est arrêté : on déverrouille (#54)
        nettoyerTemporaireZip(); // échec : on nettoie aussi le temporaire du zip (#139)
    }

    /// Applique une **annulation** (#146) demandée par l'utilisateur (décompression ou import) : état
    /// neutre `ANNULE`, progression effacée, navigation déverrouillée, temporaire du zip nettoyé. Les
    /// fichiers partiels sur disque ont déjà été supprimés par la couche modèle (session d'import ou
    /// dossier d'extraction). À appeler sur le fil JavaFX (depuis `Platform.runLater`).
    public void marquerAnnule() {
        resultat.set(null);
        resultatNuits.set(null);
        messageExecution.set("");
        progressionOperation.reinitialiser();
        etat.set(EtatImport.ANNULE);
        navigation.setOperationCritique("");
        nettoyerTemporaireZip();
    }

    /// Recompose le message d'erreur **unifié** présenté à la vue : l'erreur d'inspection prime (elle
    /// précède l'import) ; à défaut, l'erreur d'exécution. Inspection et exécution étant temporellement
    /// exclusives, au plus l'une est non vide.
    private void rafraichirMessage() {
        String erreurInspection = inspection.messageErreurProperty().get();
        messageErreur.set(erreurInspection.isEmpty() ? messageExecution.get() : erreurInspection);
    }

    /// Remet tout à zéro au **changement de dossier source** : l'inspection (sous-VM), l'exécution et
    /// l'aperçu (rattachement). Un nouveau dossier doit être ré-inspecté avant tout import (donc
    /// `inspecte` repasse à `false` et `peutImporter` se désactive). Le message unifié se vide via
    /// [#rafraichirMessage()] (inspection + exécution remis à zéro).
    private void reinitialiserPourNouveauDossier() {
        inspection.reinitialiser();
        reinitialiserExecution();
        rattachement.definirOriginaux(List.of());
    }

    /// Supprime le dossier temporaire d'extraction d'un `.zip` (#139) s'il existe, et oublie la référence.
    /// Appelé après l'import (succès/échec) et au changement de source.
    private void nettoyerTemporaireZip() {
        if (dossierTemporaireZip != null) {
            ExtracteurZip.supprimerRecursivement(dossierTemporaireZip);
            dossierTemporaireZip = null;
        }
    }

    /// Nettoyage **au départ de l'écran** (#230) : supprime le temporaire d'extraction d'un `.zip`
    /// abandonné (préparé mais jamais importé) quand l'utilisateur quitte M-Import. Sans ce hook, un
    /// temporaire de plusieurs Go fuiterait jusqu'au prochain import `.zip` (le VM est non-singleton, il
    /// ne survit pas à la fermeture de l'écran). Idempotent : sans temporaire, ne fait rien.
    public void nettoyerAuDepart() {
        nettoyerTemporaireZip();
    }

    /// Remet l'état d'**exécution** à zéro (PRET, sans résultat, progression ni message d'exécution).
    /// Partagé par le changement de dossier et l'échec d'inspection.
    private void reinitialiserExecution() {
        etat.set(EtatImport.PRET);
        resultat.set(null);
        resultatNuits.set(null);
        rejetsImport.clear(); // #155
        progressionOperation.reinitialiser();
        messageExecution.set("");
        // Fin de toute opération longue : on lève le verrou de navigation posé par marquerExtractionEnCours
        // (#54). Appelé sur le chemin de succès (nouvelle source extraite posée) comme d'erreur
        // (signalerSourceIllisible). N'est jamais invoqué pendant un import EN_COURS (formulaire gelé).
        navigation.setOperationCritique("");
    }

    /// Ré-arme la préparation (`PRET`) dès qu'un champ du rattachement change après un import
    /// **terminé ou échoué**. Hors `EN_COURS`, le formulaire reste éditable : corriger un n° de passage
    /// après un échec (ou ajuster le rattachement après un succès) recrée un import préparé non lancé
    /// que la garde de navigation (#140) doit protéger. On efface au passage le résultat/erreur du
    /// précédent essai, qui ne décrit plus la préparation courante. (Changer de dossier source repasse,
    /// lui, par [#reinitialiserPourNouveauDossier()].)
    private void rearmerPreparationSiTerminee() {
        if (etat.get() == EtatImport.TERMINE || etat.get() == EtatImport.ECHEC) {
            resultat.set(null);
            resultatNuits.set(null);
            messageExecution.set("");
            etat.set(EtatImport.PRET);
        }
    }
}
