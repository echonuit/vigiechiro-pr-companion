package fr.univ_amu.iut.importation.viewmodel;

import fr.univ_amu.iut.importation.model.EtatNommage;
import fr.univ_amu.iut.importation.model.RapportInspection;
import fr.univ_amu.iut.importation.model.ServiceImport;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.ReadOnlyIntegerProperty;
import javafx.beans.property.ReadOnlyIntegerWrapper;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.property.SimpleObjectProperty;

/// Sous-ViewModel de **M-Import** — étapes 1 et 2 : choix du **dossier source** et son **inspection
/// en lecture seule** (R9).
///
/// Extrait de [ImportationViewModel] (#183) : cet objet ne porte **que** l'état d'inspection (dossier,
/// rapport, présence journal/relevé, compte, nommage, avertissements #33) et **son propre message
/// d'erreur** d'inspection ([#messageErreurProperty()]) ; l'orchestrateur le compose avec l'erreur
/// d'exécution dans son message unifié. Il ne connaît ni le rattachement ni l'exécution. Pour le
/// rattachement (aperçu + discordance de préfixe #111), il n'expose pas son rapport mais une **valeur
/// dérivée** : les noms d'origine ([#nomsOriginaux()]), ce qui évite de coupler le rattachement à
/// l'inspection.
///
/// VM agnostique de l'IHM (règle ArchUnit `viewmodel_sans_javafx_ui`) : seul `javafx.beans` est
/// importé, jamais `javafx.scene`.
public class InspectionImportViewModel {

    private final ServiceImport serviceImport;

    /// Dossier source choisi (carte SD ou copie disque), modifiable par la vue (champ + « Parcourir »).
    private final ObjectProperty<Path> dossierSource = new SimpleObjectProperty<>(this, "dossierSource");

    private final ReadOnlyBooleanWrapper inspecte = new ReadOnlyBooleanWrapper(this, "inspecte", false);
    private final ReadOnlyBooleanWrapper aUnJournal = new ReadOnlyBooleanWrapper(this, "aUnJournal", false);
    private final ReadOnlyBooleanWrapper aUnReleveClimatique =
            new ReadOnlyBooleanWrapper(this, "aUnReleveClimatique", false);
    private final ReadOnlyIntegerWrapper nombreOriginaux = new ReadOnlyIntegerWrapper(this, "nombreOriginaux", 0);
    private final ReadOnlyObjectWrapper<EtatNommage> etatNommage =
            new ReadOnlyObjectWrapper<>(this, "etatNommage", null);
    private final ReadOnlyStringWrapper resumeJournal = new ReadOnlyStringWrapper(this, "resumeJournal", "");
    private final ReadOnlyStringWrapper avertissementMelange =
            new ReadOnlyStringWrapper(this, "avertissementMelange", "");
    private final ReadOnlyStringWrapper avertissementIncoherence =
            new ReadOnlyStringWrapper(this, "avertissementIncoherence", "");

    /// Message d'erreur **propre à l'inspection** (dossier non choisi, chemin invalide), vide après une
    /// inspection réussie. L'orchestrateur le compose avec l'erreur d'exécution dans son message unifié.
    private final ReadOnlyStringWrapper messageErreur = new ReadOnlyStringWrapper(this, "messageErreur", "");

    /// Rapport d'inspection courant, conservé pour l'aperçu du préfixe (exemple de nom d'origine) et
    /// les tranches suivantes. `null` tant qu'aucune inspection n'a réussi.
    private RapportInspection rapport;

    public InspectionImportViewModel(ServiceImport serviceImport) {
        this.serviceImport = Objects.requireNonNull(serviceImport, "serviceImport");
    }

    /// Inspecte le dossier source courant **en lecture seule** (R9). En cas de succès, met à jour les
    /// propriétés d'inspection ([#estInspecte()] vrai, rapport disponible) et vide [#messageErreurProperty()] ;
    /// sinon (dossier non choisi ou chemin invalide), remet l'état d'inspection à zéro et publie le
    /// message d'erreur dans [#messageErreurProperty()].
    public void inspecter() {
        Path dossier = dossierSource.get();
        if (dossier == null) {
            reinitialiser();
            messageErreur.set("Choisissez d'abord un dossier source.");
            return;
        }
        try {
            RapportInspection inspection = serviceImport.inspecter(dossier);
            rapport = inspection;
            aUnJournal.set(inspection.aUnJournal());
            aUnReleveClimatique.set(inspection.aUnReleveClimatique());
            nombreOriginaux.set(inspection.nombreOriginaux());
            etatNommage.set(inspection.etatNommage());
            resumeJournal.set(inspection
                    .journalOptionnel()
                    .map(journal -> "PR n° " + journal.numeroSerie())
                    .orElse(""));
            avertissementMelange.set(AvertissementMelange.rediger(inspection.melange()));
            avertissementIncoherence.set(AvertissementIncoherence.rediger(inspection.coherence()));
            inspecte.set(true);
            messageErreur.set("");
        } catch (RuntimeException echec) {
            reinitialiser();
            messageErreur.set(echec.getMessage());
        }
    }

    /// Remet l'état d'inspection à zéro (plus de rapport, message d'inspection effacé) : appelé au
    /// changement de dossier source (orchestrateur) et en cas d'échec d'inspection. Les propriétés
    /// dérivées repassent à leur valeur initiale, donc `inspecte` à `false` (ce qui désactive
    /// `peutImporter`).
    public void reinitialiser() {
        rapport = null;
        inspecte.set(false);
        aUnJournal.set(false);
        aUnReleveClimatique.set(false);
        nombreOriginaux.set(0);
        etatNommage.set(null);
        resumeJournal.set("");
        avertissementMelange.set("");
        avertissementIncoherence.set("");
        messageErreur.set("");
    }

    /// Dossier source courant (pour assembler la demande d'import).
    public Path dossier() {
        return dossierSource.get();
    }

    /// `true` dès qu'une inspection a réussi (condition nécessaire de [ImportationViewModel#peutImporter()]).
    public boolean estInspecte() {
        return inspecte.get();
    }

    /// Rapport de la dernière inspection réussie ; `null` sinon.
    public RapportInspection rapport() {
        return rapport;
    }

    /// Noms de **tous** les enregistrements originaux inspectés (liste vide avant toute inspection). C'est
    /// cette **valeur dérivée** (de simples `String`) que l'orchestrateur transmet au rattachement, plutôt
    /// que le rapport, pour ne pas coupler les deux sous-VM. Le rattachement s'en sert pour l'aperçu (le
    /// premier nom) **et** pour détecter une discordance de préfixe (#111) sur l'ensemble du dossier.
    public List<String> nomsOriginaux() {
        return rapport == null
                ? List.of()
                : rapport.originaux().stream()
                        .map(p -> p.getFileName().toString())
                        .toList();
    }

    /// Dossier source à inspecter puis importer (lié au champ + bouton « Parcourir » de la vue).
    public ObjectProperty<Path> dossierSourceProperty() {
        return dossierSource;
    }

    /// `true` dès qu'une inspection a réussi (pilote l'affichage de la section « Inspection »).
    public ReadOnlyBooleanProperty inspecteProperty() {
        return inspecte.getReadOnlyProperty();
    }

    /// `true` si un journal du capteur (LogPR) a été détecté dans le dossier.
    public ReadOnlyBooleanProperty aUnJournalProperty() {
        return aUnJournal.getReadOnlyProperty();
    }

    /// `true` si un relevé climatique (THLog) est présent (R20 : son absence est signalée).
    public ReadOnlyBooleanProperty aUnReleveClimatiqueProperty() {
        return aUnReleveClimatique.getReadOnlyProperty();
    }

    /// Nombre d'enregistrements originaux (WAV) détectés dans le dossier.
    public ReadOnlyIntegerProperty nombreOriginauxProperty() {
        return nombreOriginaux.getReadOnlyProperty();
    }

    /// État du nommage des fichiers (`BRUT`, `PREFIXE`, `VIDE`) : pilotera le scénario de renommage.
    public ReadOnlyObjectProperty<EtatNommage> etatNommageProperty() {
        return etatNommage.getReadOnlyProperty();
    }

    /// Résumé lisible du journal détecté (ex. `PR n° 1925492`), vide si aucun journal.
    public ReadOnlyStringProperty resumeJournalProperty() {
        return resumeJournal.getReadOnlyProperty();
    }

    /// Avertissement « mélange » (#33), vide si le dossier paraît homogène (une nuit, un enregistreur).
    public ReadOnlyStringProperty avertissementMelangeProperty() {
        return avertissementMelange.getReadOnlyProperty();
    }

    /// Avertissement « incohérence » (#33), vide si l'identité déclarée concorde avec les enregistrements.
    public ReadOnlyStringProperty avertissementIncoherenceProperty() {
        return avertissementIncoherence.getReadOnlyProperty();
    }

    /// Message d'erreur **propre à l'inspection** (dossier non choisi, chemin invalide), vide après un
    /// succès. L'orchestrateur le combine avec l'erreur d'exécution dans son message unifié.
    public ReadOnlyStringProperty messageErreurProperty() {
        return messageErreur.getReadOnlyProperty();
    }
}
