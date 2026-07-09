package fr.univ_amu.iut.importation.viewmodel;

import fr.univ_amu.iut.importation.model.AnalyseMelange;
import fr.univ_amu.iut.importation.model.EtatNommage;
import fr.univ_amu.iut.importation.model.JournalParse;
import fr.univ_amu.iut.importation.model.NuitDetectee;
import fr.univ_amu.iut.importation.model.PassageExistant;
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
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

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

    /// Avertissement **« nuit déjà importée »** (#147) : non vide quand un passage existe déjà en base
    /// pour le même enregistreur et la même date que la nuit inspectée (détection de doublon, non
    /// bloquante). Recalculé à chaque inspection.
    private final ReadOnlyStringWrapper avertissementNuitExistante =
            new ReadOnlyStringWrapper(this, "avertissementNuitExistante", "");

    /// Message d'erreur **propre à l'inspection** (dossier non choisi, chemin invalide), vide après une
    /// inspection réussie. L'orchestrateur le compose avec l'erreur d'exécution dans son message unifié.
    private final ReadOnlyStringWrapper messageErreur = new ReadOnlyStringWrapper(this, "messageErreur", "");

    /// **Nuits détectées** dans le dossier (#…) : une carte laissée tourner plusieurs nuits en produit
    /// plusieurs (une ligne [NuitVM] par nuit), sinon une seule. Repeuplée à chaque inspection. La vue
    /// n'affiche la table que lorsqu'il y en a plusieurs ([#plusieursNuitsProperty()]).
    private final ObservableList<NuitVM> nuits = FXCollections.observableArrayList();

    /// `true` quand la carte contient **plus d'une** nuit : pilote l'affichage de la table des nuits et
    /// bascule l'import sur le chemin multi-nuits (un passage par nuit incluse).
    private final ReadOnlyBooleanWrapper plusieursNuits = new ReadOnlyBooleanWrapper(this, "plusieursNuits", false);

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
            avertissementNuitExistante.set(detecterNuitExistante(inspection));
            peuplerNuits(inspection);
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
        avertissementNuitExistante.set("");
        messageErreur.set("");
        nuits.clear();
        plusieursNuits.set(false);
    }

    /// Construit la **table des nuits** (une ligne [NuitVM] par nuit détectée) à partir de la partition du
    /// rapport, en marquant chaque nuit **déjà importée** (#147). La série d'enregistreur (commune à la
    /// carte) provient du journal, sinon des noms de WAV (mode dégradé #107). Les cases « inclure » sont
    /// vraies par défaut ; la numérotation proposée est fixée plus tard par l'orchestrateur (elle dépend
    /// du rattachement).
    private void peuplerNuits(RapportInspection inspection) {
        String serie = serieDeLaCarte(inspection);
        List<NuitVM> lignes = inspection.partitionNuits().stream()
                .map(nuit -> {
                    NuitVM ligne = new NuitVM(nuit);
                    ligne.definirStatutDejaImportee(statutDejaImporteeDe(serie, nuit));
                    return ligne;
                })
                .toList();
        nuits.setAll(lignes);
        plusieursNuits.set(lignes.size() > 1);
    }

    /// Numéro de série de l'enregistreur de la carte (commun à toutes les nuits) : issu du **journal**
    /// s'il est présent, sinon **reconstitué des noms de WAV** (mode dégradé #107). `null` si indéterminable.
    private String serieDeLaCarte(RapportInspection inspection) {
        JournalParse journal = inspection
                .journalOptionnel()
                .filter(j -> j.numeroSerie() != null)
                .orElse(null);
        if (journal != null) {
            return journal.numeroSerie();
        }
        AnalyseMelange analyse = AnalyseMelange.depuis(inspection.originaux());
        return analyse.series().isEmpty() ? null : analyse.series().first();
    }

    /// Badge « déjà importée » (#147) d'une nuit (même enregistreur + même date en base), vide sinon.
    private String statutDejaImporteeDe(String serie, NuitDetectee nuit) {
        if (serie == null) {
            return "";
        }
        List<PassageExistant> existants =
                serviceImport.nuitDejaImportee(serie, nuit.dateNuit().toString());
        return (existants == null || existants.isEmpty()) ? "" : "déjà importée";
    }

    /// Détecte (lecture base via le service) si la nuit inspectée a déjà été importée (#147) : même
    /// enregistreur + même date. L'identité vient du **journal** s'il est présent, sinon — mode dégradé
    /// (#107) — elle est **reconstituée des noms de WAV** (comme à l'import), pour que la détection couvre
    /// aussi les réimports sans journal. Sans identité exploitable, rien à signaler. Mise en phrase
    /// déléguée à [AvertissementNuitExistante].
    private String detecterNuitExistante(RapportInspection rapport) {
        String serie;
        String date;
        JournalParse journal =
                rapport.journalOptionnel().filter(j -> j.dateDebut() != null).orElse(null);
        if (journal != null) {
            serie = journal.numeroSerie();
            date = journal.dateDebut().toString();
        } else {
            AnalyseMelange analyse = AnalyseMelange.depuis(rapport.originaux());
            if (analyse.series().isEmpty() || analyse.nuits().isEmpty()) {
                return "";
            }
            serie = analyse.series().first();
            date = analyse.nuits().first().toString();
        }
        List<PassageExistant> existants = serviceImport.nuitDejaImportee(serie, date);
        return AvertissementNuitExistante.rediger(serie, date, existants == null ? List.of() : existants);
    }

    /// Recalcule l'avertissement « nuit déjà importée » (#147) depuis la **dernière inspection**, sans
    /// relire le disque : la base a pu changer entre-temps (un import vient d'aboutir). À appeler **au
    /// moment de lancer un import** (#214) pour que la détection reflète l'état **courant** de la base, et
    /// non l'instantané figé à l'inspection (sinon réimporter la même nuit sur un n° libre passerait sans
    /// confirmation). Sans inspection courante, l'avertissement reste vide.
    public void rafraichirNuitExistante() {
        avertissementNuitExistante.set(rapport == null ? "" : detecterNuitExistante(rapport));
        if (rapport != null) {
            // Rafraîchit les badges par nuit **en place** (sans reconstruire la table, pour préserver les
            // cases « inclure » cochées par l'utilisateur).
            String serie = serieDeLaCarte(rapport);
            for (NuitVM ligne : nuits) {
                ligne.definirStatutDejaImportee(statutDejaImporteeDe(serie, ligne.nuit()));
            }
        }
    }

    /// Nuits détectées dans le dossier inspecté (une ligne par nuit ; liste vide avant toute inspection).
    /// La vue s'y lie pour la table des nuits ; l'orchestrateur en tire la demande d'import multi-nuits.
    public ObservableList<NuitVM> nuits() {
        return nuits;
    }

    /// `true` quand la carte contient plus d'une nuit (pilote la table des nuits et le chemin multi-nuits).
    public boolean plusieursNuits() {
        return plusieursNuits.get();
    }

    /// Propriété « plusieurs nuits » (visibilité de la table des nuits dans la vue).
    public ReadOnlyBooleanProperty plusieursNuitsProperty() {
        return plusieursNuits.getReadOnlyProperty();
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

    /// Avertissement « nuit déjà importée » (#147), vide si aucun passage n'existe en base pour cet
    /// enregistreur à cette date (détection de doublon, non bloquante).
    public ReadOnlyStringProperty avertissementNuitExistanteProperty() {
        return avertissementNuitExistante.getReadOnlyProperty();
    }

    /// Message d'erreur **propre à l'inspection** (dossier non choisi, chemin invalide), vide après un
    /// succès. L'orchestrateur le combine avec l'erreur d'exécution dans son message unifié.
    public ReadOnlyStringProperty messageErreurProperty() {
        return messageErreur.getReadOnlyProperty();
    }
}
