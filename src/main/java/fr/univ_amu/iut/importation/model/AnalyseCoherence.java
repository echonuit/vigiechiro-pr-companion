package fr.univ_amu.iut.importation.model;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/// Détection du cas limite **« incohérence »** d'un dossier de carte SD (story #33) : ici, ce ne sont
/// pas les WAV qui se contredisent entre eux (ça, c'est [AnalyseMelange]), mais l'**identité
/// déclarée** (journal `LogPR` et relevé climatique `THLog`) qui **contredit les enregistrements** :
///
/// - **série incohérente** : le n° de série annoncé par le journal et/ou par le nom du relevé
///   `PaRecPR<série>_THLog.csv` ne figure pas parmi les séries portées par les WAV ;
/// - **date incohérente** : la date du journal ne tombe pas dans la nuit des WAV (une nuit s'étale
///   au plus de `dateJournal` (soir) à `dateJournal + 1` (matin)).
///
/// C'est un **avertissement** à l'inspection (jamais un blocage). Objet de transport pur (aucune
/// dépendance JavaFX) ; les sources illisibles ou absentes sont simplement neutres.
public final class AnalyseCoherence {

    private static final Pattern MOTIF_RELEVE = Pattern.compile("PaRecPR(\\d+)_THLog");

    private final String serieJournal;
    private final String serieReleve;
    private final LocalDate dateJournal;
    private final SortedSet<String> seriesFichiers;
    private final SortedSet<LocalDate> nuitsFichiers;

    private AnalyseCoherence(
            String serieJournal,
            String serieReleve,
            LocalDate dateJournal,
            SortedSet<String> seriesFichiers,
            SortedSet<LocalDate> nuitsFichiers) {
        this.serieJournal = serieJournal;
        this.serieReleve = serieReleve;
        this.dateJournal = dateJournal;
        this.seriesFichiers = seriesFichiers;
        this.nuitsFichiers = nuitsFichiers;
    }

    /// Confronte l'identité déclarée (journal, nom du relevé) aux séries/nuits dérivées des `originaux`
    /// (réutilise [AnalyseMelange] pour l'extraction côté WAV).
    public static AnalyseCoherence depuis(JournalParse journal, Path cheminReleve, List<Path> originaux) {
        Objects.requireNonNull(originaux, "originaux");
        AnalyseMelange fichiers = AnalyseMelange.depuis(originaux);
        return new AnalyseCoherence(
                journal == null ? null : journal.numeroSerie(),
                serieDuReleve(cheminReleve),
                journal == null ? null : journal.dateDebut(),
                fichiers.series(),
                fichiers.nuits());
    }

    private static String serieDuReleve(Path cheminReleve) {
        if (cheminReleve == null) {
            return null;
        }
        Matcher correspondance = MOTIF_RELEVE.matcher(cheminReleve.getFileName().toString());
        return correspondance.find() ? correspondance.group(1) : null;
    }

    /// N° de série annoncé par le journal du capteur, s'il a été lu.
    public Optional<String> serieJournal() {
        return Optional.ofNullable(serieJournal);
    }

    /// N° de série déduit du nom du relevé climatique `PaRecPR<série>_THLog.csv`, s'il est présent.
    public Optional<String> serieReleve() {
        return Optional.ofNullable(serieReleve);
    }

    /// Date de la nuit annoncée par le journal, si elle a été lue.
    public Optional<LocalDate> dateJournal() {
        return Optional.ofNullable(dateJournal);
    }

    /// Séries d'enregistreur portées par les noms des WAV (peut être vide).
    public SortedSet<String> seriesFichiers() {
        return seriesFichiers;
    }

    /// Dates d'acquisition portées par les noms des WAV (peut être vide).
    public SortedSet<LocalDate> nuitsFichiers() {
        return nuitsFichiers;
    }

    /// Séries déclarées (journal et/ou relevé) **absentes** des WAV : si non vide, l'identité annoncée
    /// ne correspond pas aux enregistrements.
    public SortedSet<String> seriesDeclareesAbsentes() {
        SortedSet<String> absentes = new TreeSet<>();
        serieJournal().filter(serie -> !seriesFichiers.contains(serie)).ifPresent(absentes::add);
        serieReleve().filter(serie -> !seriesFichiers.contains(serie)).ifPresent(absentes::add);
        return absentes;
    }

    /// Vrai si une série déclarée (journal/relevé) ne figure pas parmi celles des WAV. Neutre si aucun
    /// WAV n'est exploitable (rien à comparer).
    public boolean serieIncoherente() {
        return !seriesFichiers.isEmpty() && !seriesDeclareesAbsentes().isEmpty();
    }

    /// Vrai si **au moins une** date de fichier sort de la nuit du journal (`[dateJournal,
    /// dateJournal + 1]`). On exige que **toutes** les dates des WAV tiennent dans cette fenêtre : un
    /// simple recouvrement ne suffit pas, sinon des fichiers de la nuit *suivante* (`{J+1, J+2}`)
    /// passeraient à la faveur du seul `J+1`. Neutre si la date du journal ou les dates des WAV manquent.
    public boolean dateIncoherente() {
        if (dateJournal == null || nuitsFichiers.isEmpty()) {
            return false;
        }
        LocalDate matin = dateJournal.plusDays(1);
        return nuitsFichiers.stream().anyMatch(nuit -> nuit.isBefore(dateJournal) || nuit.isAfter(matin));
    }

    /// Vrai si une incohérence (série ou date) est détectée → avertissement à l'inspection.
    public boolean incoherent() {
        return serieIncoherente() || dateIncoherente();
    }
}
