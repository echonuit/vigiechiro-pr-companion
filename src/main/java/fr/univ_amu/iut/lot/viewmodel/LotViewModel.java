package fr.univ_amu.iut.lot.viewmodel;

import fr.univ_amu.iut.commun.model.Alerte;
import fr.univ_amu.iut.commun.model.StatutWorkflow;
import fr.univ_amu.iut.commun.viewmodel.Formats;
import fr.univ_amu.iut.lot.model.ArchiveDepot;
import fr.univ_amu.iut.lot.model.EtatLot;
import fr.univ_amu.iut.lot.model.ServiceLot;
import java.util.List;
import java.util.Objects;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

/// ViewModel de l'écran **M-Lot** (préparation et dépôt d'un passage, parcours P4, épopée E4).
///
/// Ouvert sur un `idPassage`, il lit [ServiceLot#consulterLot(Long)] et pilote le **dépôt en deux
/// temps** : [#preparer()] (Vérifié → Prêt à déposer, R14 + cohérence) puis [#deposer()] (Prêt à
/// déposer → Déposé). Chaque action délègue au service puis recharge l'état. VM agnostique de l'IHM
/// (règle ArchUnit `viewmodel_sans_javafx_ui`) : seuls `javafx.beans`/`javafx.collections`.
/// Non-singleton.
public class LotViewModel {

    private final ServiceLot service;
    private Long idPassage;

    private final ReadOnlyStringWrapper statut = new ReadOnlyStringWrapper(this, "statut", "");
    private final ReadOnlyStringWrapper cheminDossier = new ReadOnlyStringWrapper(this, "cheminDossier", "");
    private final ReadOnlyStringWrapper recap = new ReadOnlyStringWrapper(this, "recap", "");
    private final ObservableList<String> alertes = FXCollections.observableArrayList();
    private final ReadOnlyBooleanWrapper peutPreparer = new ReadOnlyBooleanWrapper(this, "peutPreparer", false);
    private final ReadOnlyBooleanWrapper peutDeposer = new ReadOnlyBooleanWrapper(this, "peutDeposer", false);
    private final ReadOnlyBooleanWrapper depose = new ReadOnlyBooleanWrapper(this, "depose", false);
    private final ReadOnlyBooleanWrapper peutGenererArchives =
            new ReadOnlyBooleanWrapper(this, "peutGenererArchives", false);
    private final ObservableList<String> archives = FXCollections.observableArrayList();
    private final ReadOnlyStringWrapper titreArchives = new ReadOnlyStringWrapper(this, "titreArchives", "");
    private final ReadOnlyStringWrapper message = new ReadOnlyStringWrapper(this, "message", "");

    public LotViewModel(ServiceLot service) {
        this.service = Objects.requireNonNull(service, "service");
        // Titre reflétant le **plafond configuré** (#110) : en Mo base 1000 (cohérent avec la contrainte
        // « 700 Mo » Tadarida), et non Formats.octetsLisibles qui raisonne en base 1024.
        long plafondMo = service.plafondArchiveOctets() / 1_000_000;
        titreArchives.set("🗜 Archives de dépôt Tadarida (≤ " + plafondMo + " Mo)");
    }

    /// Ouvre l'écran de dépôt du passage `idPassage`. Une erreur (passage introuvable) est restituée
    /// dans [#messageProperty()] sans lever, l'écran restant vide.
    public void ouvrirSur(Long idPassage) {
        this.idPassage = idPassage;
        reinitialiser();
        try {
            appliquer(service.consulterLot(idPassage));
        } catch (RuntimeException echec) {
            reinitialiser();
            message.set(echec.getMessage());
        }
    }

    /// Prépare le lot (R14 + cohérence) : Vérifié → Prêt à déposer, puis recharge. Sans passage
    /// ouvert, l'appel est ignoré. Une erreur métier est restituée dans [#messageProperty()].
    ///
    /// @return `true` si la préparation a réussi
    public boolean preparer() {
        return appliquerAction(() -> service.preparerLot(idPassage));
    }

    /// Marque le passage déposé après téléversement manuel : Prêt à déposer → Déposé, puis recharge.
    ///
    /// @return `true` si le dépôt a été enregistré
    public boolean deposer() {
        return appliquerAction(() -> service.marquerDepose(idPassage));
    }

    /// Génère les **archives ZIP de dépôt** (#110) : séquences scindées en `<préfixe>-N.zip` ≤ 700 Mo
    /// dans le sous-dossier `depot/` de la session. Publie la liste produite dans [#archives()]. Sans
    /// passage ouvert, l'appel est ignoré ; une erreur métier est restituée dans [#messageProperty()].
    ///
    /// @return `true` si au moins une archive a été générée
    public boolean genererArchives() {
        if (idPassage == null) {
            return false;
        }
        try {
            List<ArchiveDepot> produites = service.genererArchivesDepot(idPassage);
            archives.setAll(produites.stream().map(LotViewModel::archiveLisible).toList());
            message.set(produites.size() + " archive(s) de dépôt générée(s) dans le sous-dossier « depot/ ».");
            return true;
        } catch (RuntimeException echec) {
            message.set(echec.getMessage());
            return false;
        }
    }

    private static String archiveLisible(ArchiveDepot archive) {
        return archive.chemin().getFileName()
                + " · "
                + archive.nombreFichiers()
                + " fichiers · "
                + Formats.octetsLisibles(archive.tailleOctets());
    }

    private boolean appliquerAction(Runnable action) {
        if (idPassage == null) {
            return false;
        }
        try {
            action.run();
            appliquer(service.consulterLot(idPassage));
            return true;
        } catch (RuntimeException echec) {
            message.set(echec.getMessage());
            return false;
        }
    }

    private void appliquer(EtatLot etat) {
        statut.set(etat.statut().libelle());
        cheminDossier.set(etat.cheminDossier() == null ? "" : etat.cheminDossier());
        recap.set(recapLisible(etat));
        alertes.setAll(etat.alertesBloquantes().stream().map(Alerte::message).toList());
        boolean bloque = !etat.alertesBloquantes().isEmpty();
        peutPreparer.set(etat.statut() == StatutWorkflow.VERIFIE && !bloque);
        peutDeposer.set(etat.statut() == StatutWorkflow.PRET_A_DEPOSER);
        depose.set(etat.statut() == StatutWorkflow.DEPOSE);
        // Les archives de dépôt (#110) se génèrent dès que le lot est prêt (séquences figées) : Prêt à
        // déposer ou déjà déposé.
        peutGenererArchives.set(
                etat.statut() == StatutWorkflow.PRET_A_DEPOSER || etat.statut() == StatutWorkflow.DEPOSE);
        message.set(messageEtat(etat));
    }

    private static String recapLisible(EtatLot etat) {
        String volume = etat.volumeSequencesOctets() == null
                ? "volume inconnu"
                : Formats.octetsLisibles(etat.volumeSequencesOctets());
        return etat.nombreSequences() + " séquences · " + volume;
    }

    private static String messageEtat(EtatLot etat) {
        if (etat.statut() == StatutWorkflow.DEPOSE) {
            return "Passage déposé le " + etat.deposeLe() + ".";
        }
        if (!etat.alertesBloquantes().isEmpty()) {
            return "Cohérence : corrigez les alertes ci-dessous avant de préparer le lot.";
        }
        return "";
    }

    private void reinitialiser() {
        statut.set("");
        cheminDossier.set("");
        recap.set("");
        alertes.clear();
        peutPreparer.set(false);
        peutDeposer.set(false);
        depose.set(false);
        peutGenererArchives.set(false);
        archives.clear();
        message.set("");
    }

    /// Libellé du statut workflow courant du passage.
    public ReadOnlyStringProperty statutProperty() {
        return statut.getReadOnlyProperty();
    }

    /// Chemin du dossier de session à téléverser manuellement (R22), vide si pas de session.
    public ReadOnlyStringProperty cheminDossierProperty() {
        return cheminDossier.getReadOnlyProperty();
    }

    /// Récapitulatif du lot (`N séquences · X Mo`).
    public ReadOnlyStringProperty recapProperty() {
        return recap.getReadOnlyProperty();
    }

    /// Messages des alertes de cohérence bloquantes (R14), vide si conforme.
    public ObservableList<String> alertes() {
        return alertes;
    }

    /// `true` si le lot peut être préparé (passage Vérifié et aucune alerte bloquante).
    public ReadOnlyBooleanProperty peutPreparerProperty() {
        return peutPreparer.getReadOnlyProperty();
    }

    /// `true` si le passage peut être marqué déposé (statut Prêt à déposer).
    public ReadOnlyBooleanProperty peutDeposerProperty() {
        return peutDeposer.getReadOnlyProperty();
    }

    /// `true` si le passage est déjà déposé.
    public ReadOnlyBooleanProperty deposeProperty() {
        return depose.getReadOnlyProperty();
    }

    /// `true` si les archives de dépôt peuvent être générées (lot préparé : Prêt à déposer ou Déposé).
    public ReadOnlyBooleanProperty peutGenererArchivesProperty() {
        return peutGenererArchives.getReadOnlyProperty();
    }

    /// Récapitulatifs lisibles des archives ZIP de dépôt produites (#110), vide tant qu'aucune génération.
    public ObservableList<String> archives() {
        return archives;
    }

    /// Titre de la section archives, intégrant le **plafond configuré** (ex. « …(≤ 700 Mo) », #110).
    public ReadOnlyStringProperty titreArchivesProperty() {
        return titreArchives.getReadOnlyProperty();
    }

    /// Message d'état ou d'erreur (déposé, alertes, échec d'action), vide en fonctionnement nominal.
    public ReadOnlyStringProperty messageProperty() {
        return message.getReadOnlyProperty();
    }
}
