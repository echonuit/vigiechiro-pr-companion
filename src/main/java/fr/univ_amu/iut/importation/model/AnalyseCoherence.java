package fr.univ_amu.iut.importation.model;

import java.nio.file.Path;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
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
/// - **date incohérente** : une date des WAV ne tombe dans aucune des nuits que le journal raconte
///   (une nuit s'étale du soir `J` au matin `J + 1`). Le journal est circulaire : sur une carte
///   réutilisée, sa première ligne raconte souvent une nuit effacée depuis, et c'est pourquoi on
///   juge les nuits qu'il raconte plutôt que sa première date, laquelle ne sert que faute de cycle
///   lisible. **Carte de plusieurs nuits** : le journal circulaire ne raconte parfois que les
///   premières, donc une seule nuit racontée suffit, et la date n'est incohérente que si le journal
///   n'en raconte **aucune** (#5669) ; sans cycle lisible, elle n'est pas jugée.
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

    /// Les nuits que le journal **raconte**, une par cycle d'acquisition (#5631). Vide sans cycle lisible :
    /// la date se juge alors sur la première ligne du journal, comme avant.
    private final SortedSet<LocalDate> nuitsJournal;

    private AnalyseCoherence(
            String serieJournal,
            String serieReleve,
            LocalDate dateJournal,
            SortedSet<String> seriesFichiers,
            SortedSet<LocalDate> nuitsFichiers,
            SortedSet<LocalDate> nuitsJournal) {
        this.serieJournal = serieJournal;
        this.serieReleve = serieReleve;
        this.dateJournal = dateJournal;
        this.seriesFichiers = seriesFichiers;
        this.nuitsFichiers = nuitsFichiers;
        this.nuitsJournal = nuitsJournal;
    }

    /// Confronte l'identité déclarée (journal, nom du relevé) aux séries/nuits dérivées des `originaux`
    /// (réutilise [AnalyseMelange] pour l'extraction côté WAV).
    public static AnalyseCoherence depuis(JournalParse journal, Path cheminReleve, List<Path> originaux) {
        return depuis(journal, cheminReleve, originaux, List.of());
    }

    /// La même confrontation, avec les **cycles d'acquisition** que l'inspection tire du journal (#5631) :
    /// le journal est circulaire, et sa première ligne raconte souvent une nuit effacée de la carte
    /// depuis. Ce sont les nuits qu'il raconte, pas la première, qui disent s'il décrit les
    /// enregistrements.
    public static AnalyseCoherence depuis(
            JournalParse journal, Path cheminReleve, List<Path> originaux, List<CycleAcquisition> cycles) {
        Objects.requireNonNull(originaux, "originaux");
        Objects.requireNonNull(cycles, "cycles");
        SortedSet<LocalDate> nuitsJournal = new java.util.TreeSet<>();
        cycles.forEach(cycle -> nuitsJournal.add(cycle.dateNuit()));
        AnalyseMelange fichiers = AnalyseMelange.depuis(originaux);
        return new AnalyseCoherence(
                journal == null ? null : journal.numeroSerie(),
                serieDuReleve(cheminReleve),
                journal == null ? null : journal.dateDebut(),
                fichiers.series(),
                fichiers.nuits(),
                nuitsJournal);
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

    /// Les nuits que le journal raconte, une par cycle d'acquisition (#5631) : c'est sur elles que la date
    /// se juge. Vide sans cycle lisible, et c'est alors [#dateJournal] qui est jugée.
    public SortedSet<LocalDate> nuitsJournal() {
        return nuitsJournal;
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

    /// Vrai si **au moins une** date de fichier ne tombe dans la fenêtre `[J, J + 1]` d'aucune nuit que
    /// le journal raconte, ou, faute de cycle lisible, de sa première date : un simple recouvrement ne
    /// suffit pas, sinon des fichiers de la nuit *suivante* passeraient à la faveur du seul `J+1`. Sur
    /// une carte de plusieurs nuits, vrai seulement si **aucune** n'est racontée, et jamais sans cycle.
    /// Neutre si la date du journal ou les dates des WAV manquent.
    public boolean dateIncoherente() {
        if (dateJournal == null || nuitsFichiers.isEmpty()) {
            return false;
        }
        boolean plusieursNuits = ChronoUnit.DAYS.between(nuitsFichiers.first(), nuitsFichiers.last()) > 1;
        if (!nuitsJournal.isEmpty()) {
            // Les nuits que le journal raconte, et non sa première ligne (#5631). Sur une carte de
            // plusieurs nuits, le journal circulaire a pu perdre les dernières : une seule nuit racontée
            // suffit à dire qu'il vient de cette carte, et aucune dit qu'il n'en vient pas (#5669).
            return plusieursNuits
                    ? nuitsFichiers.stream().noneMatch(this::estRacontee)
                    : nuitsFichiers.stream().anyMatch(date -> !estRacontee(date));
        }
        // Sans cycle lisible, seule la première ligne reste, et elle ne date qu'une nuit : une carte de
        // plusieurs nuits n'est pas jugée sur elle.
        if (plusieursNuits) {
            return false;
        }
        LocalDate matin = dateJournal.plusDays(1);
        return nuitsFichiers.stream().anyMatch(nuit -> nuit.isBefore(dateJournal) || nuit.isAfter(matin));
    }

    /// Vrai si la date d'un enregistrement tombe dans la fenêtre soir `J`, matin `J + 1` d'une nuit que
    /// le journal raconte.
    private boolean estRacontee(LocalDate date) {
        return nuitsJournal.stream().anyMatch(nuit -> !date.isBefore(nuit) && !date.isAfter(nuit.plusDays(1)));
    }

    /// Vrai si une incohérence (série ou date) est détectée → avertissement à l'inspection.
    public boolean incoherent() {
        return serieIncoherente() || dateIncoherente();
    }
}
