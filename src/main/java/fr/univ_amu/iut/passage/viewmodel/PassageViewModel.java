package fr.univ_amu.iut.passage.viewmodel;

import fr.univ_amu.iut.commun.model.StatutWorkflow;
import fr.univ_amu.iut.commun.model.Verdict;
import fr.univ_amu.iut.commun.persistence.ServicePurgeOriginaux;
import fr.univ_amu.iut.commun.viewmodel.ContexteSite;
import fr.univ_amu.iut.commun.viewmodel.Formats;
import fr.univ_amu.iut.passage.model.DetailPassage;
import fr.univ_amu.iut.passage.model.ServicePassage;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.ReadOnlyIntegerProperty;
import javafx.beans.property.ReadOnlyIntegerWrapper;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

/// ViewModel de l'écran pivot **M-Passage** : fiche d'identité d'un passage, **stepper** de statut
/// workflow et statistiques (volumes, durée audible, nombre de séquences).
///
/// Ouvert sur un `idPassage` + un [ContexteSite] (carré/code/nom fournis par la navigation, pour
/// éviter une dépendance `passage → sites`). Le calcul passe par la projection
/// [ServicePassage#detailPassage(Long)]. VM agnostique de l'IHM (règle ArchUnit
/// `viewmodel_sans_javafx_ui`) : seuls `javafx.beans`/`javafx.collections`. Non-singleton.
public class PassageViewModel {

    private final ServicePassage service;
    private final ServicePurgeOriginaux purge;

    private final ReadOnlyStringWrapper titreContexte = new ReadOnlyStringWrapper(this, "titreContexte", "");
    private final ReadOnlyStringWrapper plageHoraire = new ReadOnlyStringWrapper(this, "plageHoraire", "");
    private final ReadOnlyStringWrapper enregistreur = new ReadOnlyStringWrapper(this, "enregistreur", "");
    private final ReadOnlyObjectWrapper<StatutWorkflow> statut = new ReadOnlyObjectWrapper<>(this, "statut");
    private final ReadOnlyObjectWrapper<Verdict> verdict = new ReadOnlyObjectWrapper<>(this, "verdict");
    private final ReadOnlyStringWrapper volumeBruts = new ReadOnlyStringWrapper(this, "volumeBruts", "");
    private final ReadOnlyStringWrapper volumeTransformes = new ReadOnlyStringWrapper(this, "volumeTransformes", "");
    private final ReadOnlyStringWrapper dureeAudible = new ReadOnlyStringWrapper(this, "dureeAudible", "");
    private final ReadOnlyIntegerWrapper nombreSequences = new ReadOnlyIntegerWrapper(this, "nombreSequences", 0);
    private final ObservableList<EtapeWorkflow> etapes = FXCollections.observableArrayList();
    private final ReadOnlyBooleanWrapper verificationDisponible =
            new ReadOnlyBooleanWrapper(this, "verificationDisponible", false);
    private final ReadOnlyBooleanWrapper validationVerrouillee =
            new ReadOnlyBooleanWrapper(this, "validationVerrouillee", true);
    private final ReadOnlyBooleanWrapper depotDisponible = new ReadOnlyBooleanWrapper(this, "depotDisponible", false);
    private final ReadOnlyBooleanWrapper annulationDepotDisponible =
            new ReadOnlyBooleanWrapper(this, "annulationDepotDisponible", false);
    private final ReadOnlyBooleanWrapper suppressionPossible =
            new ReadOnlyBooleanWrapper(this, "suppressionPossible", false);
    private final ReadOnlyBooleanWrapper purgeDisponible = new ReadOnlyBooleanWrapper(this, "purgeDisponible", false);
    private final ReadOnlyObjectWrapper<ActionRecommandee> actionRecommandee =
            new ReadOnlyObjectWrapper<>(this, "actionRecommandee", ActionRecommandee.AUCUNE);
    private final ReadOnlyStringWrapper message = new ReadOnlyStringWrapper(this, "message", "");

    /// Identifiant du passage affiché, mémorisé pour les actions (ex. suppression).
    private Long idPassage;

    /// Numéro de passage dans l'année (R3), pour le libellé du fil d'Ariane ; 0 tant qu'aucun passage
    /// n'est chargé.
    private int numeroPassage;

    public PassageViewModel(ServicePassage service, ServicePurgeOriginaux purge) {
        this.service = Objects.requireNonNull(service, "service");
        this.purge = Objects.requireNonNull(purge, "purge");
    }

    /// Ouvre l'écran sur le passage `idPassage`, avec le contexte site fourni par la navigation.
    /// Une erreur (passage introuvable) est restituée dans [#messageProperty()] sans lever.
    public void ouvrirSur(Long idPassage, ContexteSite contexte) {
        this.idPassage = idPassage;
        reinitialiser();
        try {
            appliquer(service.detailPassage(idPassage), contexte);
            message.set("");
        } catch (RuntimeException echec) {
            reinitialiser();
            message.set(echec.getMessage());
        }
    }

    /// Supprime le passage courant (action « Supprimer » de M-Passage). Délègue à
    /// [ServicePassage#supprimer] ; la [fr.univ_amu.iut.commun.model.RegleMetierException] d'un
    /// passage déposé remonte à la vue, qui l'affiche (même patron que la suppression d'un site).
    public void supprimer() {
        service.supprimer(idPassage);
    }

    /// Annule le dépôt du passage courant : le ramène de « Déposé » à « Prêt à déposer » sans supprimer
    /// les validations Tadarida déjà saisies. Délègue à [ServicePassage#annulerDepot] ; la
    /// [fr.univ_amu.iut.commun.model.RegleMetierException] d'un passage non déposé remonte à la vue, qui
    /// l'affiche. Le rechargement de l'affichage est à la charge de l'appelant (rejeu de [#ouvrirSur]).
    public void annulerDepot() {
        service.annulerDepot(idPassage);
    }

    /// Purge les **originaux** (`bruts/`) du passage courant pour récupérer l'espace disque : supprime les
    /// fichiers via [ServicePurgeOriginaux] (socle) puis marque les originaux purgés en base
    /// ([ServicePassage#marquerOriginauxPurges]). Les séquences transformées, la validation et le dépôt
    /// sont **conservés**. Le rechargement de l'affichage (volume bruts → 0) est à la charge de l'appelant
    /// (rejeu de [#ouvrirSur]).
    public void purgerOriginaux() {
        service.cheminSession(idPassage).ifPresent(purge::purgerSession);
        service.marquerOriginauxPurges(idPassage);
    }

    private void appliquer(DetailPassage detail, ContexteSite contexte) {
        titreContexte.set("Carré "
                + contexte.numeroCarre()
                + " / "
                + contexte.codePoint()
                + " / N° "
                + detail.numeroPassage()
                + " ("
                + detail.annee()
                + ")");
        numeroPassage = detail.numeroPassage();
        plageHoraire.set(detail.dateEnregistrement() + "  " + detail.heureDebut() + " → " + detail.heureFin());
        enregistreur.set("PR " + detail.idEnregistreur());
        statut.set(detail.statut());
        verdict.set(detail.verdict());
        volumeBruts.set(Formats.octetsLisibles(detail.volumeOriginauxOctets()));
        volumeTransformes.set(Formats.octetsLisibles(detail.volumeSequencesOctets()));
        dureeAudible.set(Formats.dureeLisible(detail.dureeAudibleSecondes()));
        nombreSequences.set(detail.nombreSequences());
        etapes.setAll(construireEtapes(detail.statut()));
        verificationDisponible.set(detail.statut().ordinal() >= StatutWorkflow.TRANSFORME.ordinal());
        validationVerrouillee.set(detail.statut() != StatutWorkflow.DEPOSE);
        // Accès à l'écran de dépôt (M-Lot) dès le passage vérifié ET **même une fois déposé** (#…) : on doit
        // pouvoir y revenir pour consulter les archives ou les supprimer, sans avoir à annuler le dépôt.
        depotDisponible.set(detail.statut().ordinal() >= StatutWorkflow.VERIFIE.ordinal());
        annulationDepotDisponible.set(detail.statut() == StatutWorkflow.DEPOSE);
        // Suppression bloquée sur un passage déposé (le service la refuse) : on grise le bouton en amont au
        // lieu de laisser l'utilisateur découvrir le refus après la confirmation. Il faut d'abord annuler
        // le dépôt.
        suppressionPossible.set(detail.statut() != StatutWorkflow.DEPOSE);
        // Purge possible tant qu'il reste des originaux sur disque (volume > 0) ; après purge, il tombe à 0.
        purgeDisponible.set(detail.volumeOriginauxOctets() > 0);
        actionRecommandee.set(prochaineAction(detail.statut()));
    }

    /// Déduit la prochaine action recommandée du statut (progression linéaire du workflow) : la carte
    /// correspondante est mise en avant dans M-Passage.
    private static ActionRecommandee prochaineAction(StatutWorkflow statut) {
        return switch (statut) {
            case IMPORTE -> ActionRecommandee.AUCUNE;
            case TRANSFORME -> ActionRecommandee.VERIFIER;
            // « Dépôt en cours » (#980) : un dépôt interrompu se reprend depuis M-Lot → même mise en
            // avant que « déposer » (la carte Lot porte la reprise).
            case VERIFIE, PRET_A_DEPOSER, DEPOT_EN_COURS -> ActionRecommandee.DEPOSER;
            case DEPOSE -> ActionRecommandee.VALIDER;
        };
    }

    private void reinitialiser() {
        titreContexte.set("");
        plageHoraire.set("");
        enregistreur.set("");
        statut.set(null);
        verdict.set(null);
        volumeBruts.set("");
        volumeTransformes.set("");
        dureeAudible.set("");
        nombreSequences.set(0);
        numeroPassage = 0;
        etapes.clear();
        verificationDisponible.set(false);
        validationVerrouillee.set(true);
        depotDisponible.set(false);
        annulationDepotDisponible.set(false);
        suppressionPossible.set(false);
        purgeDisponible.set(false);
        actionRecommandee.set(ActionRecommandee.AUCUNE);
    }

    /// Étapes du stepper : les 5 statuts **jalons** du workflow. Le statut technique « Dépôt en cours »
    /// (#980) n'est pas un jalon : tant que le dépôt automatique n'est pas terminé, le jalon courant
    /// reste « Prêt à déposer » (le détail du dépôt — unités téléversées, reprise — vit dans M-Lot).
    private static List<EtapeWorkflow> construireEtapes(StatutWorkflow courant) {
        StatutWorkflow jalon = courant == StatutWorkflow.DEPOT_EN_COURS ? StatutWorkflow.PRET_A_DEPOSER : courant;
        List<EtapeWorkflow> liste = new ArrayList<>();
        for (StatutWorkflow etape : StatutWorkflow.values()) {
            if (etape == StatutWorkflow.DEPOT_EN_COURS) {
                continue;
            }
            EtatEtape etat;
            if (etape.ordinal() < jalon.ordinal()) {
                etat = EtatEtape.FRANCHIE;
            } else if (etape == jalon) {
                etat = EtatEtape.COURANTE;
            } else {
                etat = EtatEtape.A_VENIR;
            }
            liste.add(new EtapeWorkflow(etape, etat));
        }
        return liste;
    }

    /// Numéro de passage dans l'année (0 si aucun passage chargé), pour le libellé du fil d'Ariane.
    public int getNumeroPassage() {
        return numeroPassage;
    }

    /// Titre d'identité du passage (`Carré 640380 / A1 / N° 2 (2026)`).
    public ReadOnlyStringProperty titreContexteProperty() {
        return titreContexte.getReadOnlyProperty();
    }

    /// Plage horaire de la nuit (`date  début → fin`).
    public ReadOnlyStringProperty plageHoraireProperty() {
        return plageHoraire.getReadOnlyProperty();
    }

    /// Enregistreur (`PR <n° de série>`).
    public ReadOnlyStringProperty enregistreurProperty() {
        return enregistreur.getReadOnlyProperty();
    }

    /// Statut workflow courant du passage.
    public ReadOnlyObjectProperty<StatutWorkflow> statutProperty() {
        return statut.getReadOnlyProperty();
    }

    /// Verdict de vérification, ou `null` tant qu'aucun n'est posé.
    public ReadOnlyObjectProperty<Verdict> verdictProperty() {
        return verdict.getReadOnlyProperty();
    }

    /// Volume des enregistrements bruts, formaté (`Ko`/`Mo`/`Go`).
    public ReadOnlyStringProperty volumeBrutsProperty() {
        return volumeBruts.getReadOnlyProperty();
    }

    /// Volume des séquences transformées, formaté.
    public ReadOnlyStringProperty volumeTransformesProperty() {
        return volumeTransformes.getReadOnlyProperty();
    }

    /// Durée audible cumulée, formatée (`Xh Ymin` ou `X min Y s`).
    public ReadOnlyStringProperty dureeAudibleProperty() {
        return dureeAudible.getReadOnlyProperty();
    }

    /// Nombre de séquences d'écoute de la session.
    public ReadOnlyIntegerProperty nombreSequencesProperty() {
        return nombreSequences.getReadOnlyProperty();
    }

    /// Étapes du stepper de statut (5 statuts, du plus ancien au dépôt), avec leur état.
    public ObservableList<EtapeWorkflow> etapes() {
        return etapes;
    }

    /// `true` si la vérification par échantillonnage est possible (passage au moins transformé).
    public ReadOnlyBooleanProperty verificationDisponibleProperty() {
        return verificationDisponible.getReadOnlyProperty();
    }

    /// `true` tant que la validation Tadarida est verrouillée (passage non encore déposé).
    public ReadOnlyBooleanProperty validationVerrouilleeProperty() {
        return validationVerrouillee.getReadOnlyProperty();
    }

    /// `true` quand la préparation/dépôt est pertinente (passage Vérifié ou Prêt à déposer).
    public ReadOnlyBooleanProperty depotDisponibleProperty() {
        return depotDisponible.getReadOnlyProperty();
    }

    /// `true` quand l'annulation du dépôt est pertinente (passage déjà **déposé**) : l'action ramène le
    /// passage à « Prêt à déposer » sans toucher aux validations Tadarida déjà saisies.
    public ReadOnlyBooleanProperty annulationDepotDisponibleProperty() {
        return annulationDepotDisponible.getReadOnlyProperty();
    }

    /// `true` quand le passage peut être supprimé (tout statut **sauf** Déposé). Un passage déposé doit
    /// d'abord voir son dépôt annulé ; le bouton « Supprimer » est grisé en conséquence, avec un tooltip
    /// d'explication (cf. [fr.univ_amu.iut.commun.view.IndicateurBlocage]).
    public ReadOnlyBooleanProperty suppressionPossibleProperty() {
        return suppressionPossible.getReadOnlyProperty();
    }

    /// `true` quand des **originaux** sont encore stockés (volume bruts > 0) : la purge est alors proposée
    /// pour récupérer l'espace disque.
    public ReadOnlyBooleanProperty purgeDisponibleProperty() {
        return purgeDisponible.getReadOnlyProperty();
    }

    /// Prochaine action recommandée du workflow (carte mise en avant), dérivée du statut. Se déplace
    /// au fil de l'avancement : Vérifier → Préparer le dépôt → Sons & validation.
    public ReadOnlyObjectProperty<ActionRecommandee> actionRecommandeeProperty() {
        return actionRecommandee.getReadOnlyProperty();
    }

    /// Message d'erreur (passage introuvable), vide en fonctionnement nominal.
    public ReadOnlyStringProperty messageProperty() {
        return message.getReadOnlyProperty();
    }
}
