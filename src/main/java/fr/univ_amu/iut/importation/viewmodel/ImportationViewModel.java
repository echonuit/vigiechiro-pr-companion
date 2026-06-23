package fr.univ_amu.iut.importation.viewmodel;

import fr.univ_amu.iut.commun.model.Horloge;
import fr.univ_amu.iut.commun.model.Prefixe;
import fr.univ_amu.iut.commun.viewmodel.NavigationViewModel;
import fr.univ_amu.iut.importation.model.Progression;
import fr.univ_amu.iut.importation.model.ResultatImport;
import fr.univ_amu.iut.importation.model.ServiceImport;
import fr.univ_amu.iut.sites.model.ServiceSites;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.property.ReadOnlyDoubleProperty;
import javafx.beans.property.ReadOnlyDoubleWrapper;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.ReadOnlyStringWrapper;

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
/// ([#executerImport(DemandeImport, java.util.function.Consumer)]) pour ne pas figer l'IHM, et relaie
/// la **progression déterminée** (#33) au fil JavaFX via [#appliquerProgression]. Seul `javafx.beans` /
/// `javafx.collections` est importé ici, jamais `javafx.scene` (règle `viewmodel_sans_javafx_ui`).
///
/// TODO (M-Import) : implémentez les corps des méthodes publiques (chargerSites, inspecter, importer,
/// preparerImport, executerImport, marquer*) ; les propriétés observables et le binding peutImporter
/// sont fournis. Patron de référence : SitesViewModel (feature sites).
public class ImportationViewModel {

    private final ServiceImport serviceImport;

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

    /// Progression déterminée de l'import en cours (#33) : fraction `[0, 1]` pour la barre et libellé
    /// d'étape (« Transformation 45/191 »). Alimentées par [#appliquerProgression] sur le fil JavaFX.
    private final ReadOnlyDoubleWrapper progression = new ReadOnlyDoubleWrapper(this, "progression", 0.0);
    private final ReadOnlyStringWrapper messageProgression = new ReadOnlyStringWrapper(this, "messageProgression", "");

    /// Pré-contrôle R5 proactif (#108) extrait dans un collaborateur dédié ([ControleNumeroPassage]) :
    /// signale qu'un n° de passage est déjà pris (bloque [#peutImporter()]) et propose le prochain libre.
    /// Recalculé à chaque changement de point / année / n° de passage du rattachement.
    private final ControleNumeroPassage controleNumeroPassage;

    public ImportationViewModel(
            ServiceImport serviceImport,
            ServiceSites serviceSites,
            Horloge horloge,
            String idUtilisateur,
            NavigationViewModel navigation) {
        this.serviceImport = Objects.requireNonNull(serviceImport, "serviceImport");
        this.navigation = Objects.requireNonNull(navigation, "navigation");
        this.inspection = new InspectionImportViewModel(serviceImport);
        // Sous-VM rattachement (#183) : il valide serviceSites / horloge / idUtilisateur et préremplit
        // l'année courante.
        this.rattachement = new RattachementImportViewModel(serviceSites, horloge, idUtilisateur);
        // Pré-contrôle R5 proactif (#108) : observe lui-même le rattachement et entretient son état.
        this.controleNumeroPassage = new ControleNumeroPassage(serviceImport, rattachement);

        // --solution--
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
        // --end-solution--

        // peutImporter = inspection réussie ET rattachement complet ET n° de passage libre (#108) :
        // composition par l'orchestrateur.
        peutImporter = Bindings.createBooleanBinding(
                () -> inspection.estInspecte() && rattachement.estComplet() && !controleNumeroPassage.estDejaUtilise(),
                inspection.inspecteProperty(),
                rattachement.siteSelectionneProperty(),
                rattachement.pointSelectionneProperty(),
                rattachement.numeroPassageProperty(),
                controleNumeroPassage.dejaUtiliseProperty());
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
    /// qu'aucun import n'a abouti.
    public ReadOnlyObjectProperty<ResultatImport> resultatProperty() {
        return resultat.getReadOnlyProperty();
    }

    /// Fraction de progression de l'import en cours (`[0, 1]`), pour la barre déterminée (#33).
    public ReadOnlyDoubleProperty progressionProperty() {
        return progression.getReadOnlyProperty();
    }

    /// Libellé d'étape de l'import en cours (« Copie X/N », « Transformation X/N »).
    public ReadOnlyStringProperty messageProgressionProperty() {
        return messageProgression.getReadOnlyProperty();
    }

    /// Recharge les sites de l'utilisateur courant (à l'ouverture de l'écran ou après création d'un
    /// site).
    public void chargerSites() {
        // TODO (M-Import) : rechargez les sites de l'utilisateur courant (serviceSites.listerSites).
        // --solution--
        rattachement.chargerSites();
        // --end-solution--
    }

    /// Inspecte le dossier source courant **en lecture seule** (R9) via le sous-VM inspection. En cas
    /// de succès, transmet au rattachement l'exemple de nom (aperçu) ; en cas d'erreur (publiée par
    /// l'inspection), remet l'exécution et l'aperçu à zéro.
    public void inspecter() {
        // TODO (M-Import) : inspectez le dossier source (inspection.inspecter) ; en cas de succès
        //   (inspection.estInspecte), transmettez les noms d'originaux au rattachement (definirOriginaux) ;
        //   sinon, remettez l'exécution et l'aperçu à zéro (l'inspection a déjà publié son message).
        // --solution--
        inspection.inspecter();
        if (inspection.estInspecte()) {
            rattachement.definirOriginaux(inspection.nomsOriginaux());
        } else {
            reinitialiserExecution();
            rattachement.definirOriginaux(List.of());
        }
        // --end-solution--
    }

    /// Renseigne le n° de passage avec le **prochain n° libre** proposé par le pré-contrôle R5 (#108) :
    /// corrige un doublon en un clic (délégué à [ControleNumeroPassage]).
    public void utiliserProchainNumeroLibre() {
        controleNumeroPassage.utiliserProchainNumeroLibre();
    }

    /// Lance l'import de la nuit **de façon synchrone** (copie protégée R9 + renommage R6/R7 +
    /// transformation R10/R11). Pratique pour les tests et le chemin simple ; pour ne pas figer
    /// l'IHM, la vue préfère le découpage `preparerImport` (instantané) + `executerImport`
    /// (hors-thread) + `marquerEnCours`/`marquerTermine`/`marquerEchec` (sur le fil JavaFX).
    public void importer() {
        // TODO (M-Import) : import synchrone : vérifiez peutImporter, préparez (preparerImport),
        //   marquerEnCours, exécutez (executerImport) puis marquerTermine / marquerEchec.
        // --solution--
        if (!peutImporter.get()) {
            // Message ciblé : si l'import est bloqué par un n° déjà pris (#108), on l'explique au lieu du
            // générique « Complétez le rattachement » (qui serait trompeur, le rattachement étant complet).
            messageExecution.set(controleNumeroPassage.messageBlocage(
                    "Complétez le rattachement (dossier inspecté, site, point) avant d'importer."));
            return;
        }
        DemandeImport demande = preparerImport();
        marquerEnCours();
        try {
            marquerTermine(executerImport(demande));
        } catch (RuntimeException echec) {
            marquerEchec(echec.getMessage());
        }
        // --end-solution--
    }

    /// Passe l'état à `EN_COURS` et efface le message. À appeler sur le fil JavaFX, avant de
    /// lancer l'exécution en arrière-plan.
    public void marquerEnCours() {
        // TODO (M-Import) : passez l'état à EN_COURS (progression 0, verrou de navigation #54).
        // --solution--
        messageExecution.set("");
        progression.set(0.0);
        messageProgression.set("Préparation…");
        etat.set(EtatImport.EN_COURS);
        // Verrou de navigation (#54) : on ne doit pas quitter l'assistant tant que l'import tourne,
        // sinon son résultat (marquerTermine/marquerEchec) serait perdu en détachant la vue.
        navigation.setNavigationVerrouillee(true);
        // --end-solution--
    }

    /// Applique un point de progression de l'import en cours (#33) : met à jour la fraction et le
    /// libellé d'étape. À appeler sur le fil JavaFX (depuis `Platform.runLater`), car le callback du
    /// service s'exécute hors-thread.
    public void appliquerProgression(Progression p) {
        // TODO (M-Import) : mettez à jour la fraction et le libellé de progression (#33).
        // --solution--
        progression.set(p.fraction());
        messageProgression.set(p.libelle());
        // --end-solution--
    }

    /// Capture (sur le fil JavaFX) les entrées du rattachement courant dans un instantané immuable,
    /// pour les passer à [#executerImport(DemandeImport)] sans relire de `Property` hors-thread.
    /// Précondition : rattachement complet ([#peutImporter()] vrai), garanti par l'appelant.
    public DemandeImport preparerImport() {
        // TODO (M-Import) : capturez le rattachement courant (dossier, point, préfixe) dans un
        //   DemandeImport immuable (à passer à executerImport hors-thread).
        // --solution--
        return new DemandeImport(
                inspection.dossier(), rattachement.idPointSelectionne(), rattachement.prefixeCourant());
        // --end-solution--
        /* --student--
        throw new UnsupportedOperationException("À implémenter (M-Import)");
        --end-student-- */
    }

    /// Exécute le travail lourd de l'import (copie + renommage + transformation) via
    /// [ServiceImport#importer], à partir d'un instantané. **Ne lit aucune `Property` et ne mute
    /// rien** : sûr sur un fil d'arrière-plan.
    ///
    /// @return le résultat de l'import
    /// @throws RuntimeException si l'import échoue (refus métier R5, journal manquant…)
    public ResultatImport executerImport(DemandeImport demande) {
        // TODO (M-Import) : déléguez à la variante avec suivi de progression (no-op).
        // --solution--
        return executerImport(demande, progres -> {});
        // --end-solution--
        /* --student--
        throw new UnsupportedOperationException("À implémenter (M-Import)");
        --end-student-- */
    }

    /// Variante avec **suivi de progression** (#33) : `progres` est notifié sur le fil d'exécution de
    /// l'import ; la vue le relaie au fil JavaFX (via [#appliquerProgression]). **Ne mute aucune
    /// `Property`** ici : sûr sur un fil d'arrière-plan.
    public ResultatImport executerImport(DemandeImport demande, Consumer<Progression> progres) {
        // TODO (M-Import) : exécutez l'import (serviceImport.importer) en relayant la progression.
        // --solution--
        return serviceImport.importer(demande.dossier(), demande.idPoint(), demande.prefixe(), progres);
        // --end-solution--
        /* --student--
        throw new UnsupportedOperationException("À implémenter (M-Import)");
        --end-student-- */
    }

    /// Instantané immuable des entrées d'un import, capturé sur le fil JavaFX par preparerImport.
    public record DemandeImport(Path dossier, Long idPoint, Prefixe prefixe) {}

    /// Applique un import réussi (résultat exposé, état `TERMINE`). À appeler sur le fil JavaFX
    /// (depuis `Platform.runLater`).
    public void marquerTermine(ResultatImport resultatImport) {
        // TODO (M-Import) : exposez le résultat, passez l'état à TERMINE, déverrouillez la navigation (#54).
        // --solution--
        resultat.set(resultatImport);
        messageExecution.set("");
        etat.set(EtatImport.TERMINE);
        navigation.setNavigationVerrouillee(false); // l'import est fini : on peut de nouveau naviguer (#54)
        // --end-solution--
    }

    /// Applique un échec d'import : efface le résultat, renseigne le message, état `ECHEC`. À
    /// appeler sur le fil JavaFX (depuis `Platform.runLater`).
    public void marquerEchec(String message) {
        // TODO (M-Import) : effacez le résultat, publiez le message, passez l'état à ECHEC, déverrouillez (#54).
        // --solution--
        resultat.set(null);
        messageExecution.set(message);
        etat.set(EtatImport.ECHEC);
        navigation.setNavigationVerrouillee(false); // l'import s'est arrêté : on déverrouille (#54)
        // --end-solution--
    }

    // --solution--
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

    /// Remet l'état d'**exécution** à zéro (PRET, sans résultat, progression ni message d'exécution).
    /// Partagé par le changement de dossier et l'échec d'inspection.
    private void reinitialiserExecution() {
        etat.set(EtatImport.PRET);
        resultat.set(null);
        progression.set(0.0);
        messageProgression.set("");
        messageExecution.set("");
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
            messageExecution.set("");
            etat.set(EtatImport.PRET);
        }
    }
    // --end-solution--
}
