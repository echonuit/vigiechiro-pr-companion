package fr.univ_amu.iut.importation.model;

import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/// Détection du cas limite **« mélange »** d'un dossier de carte SD (story #33) : un dossier
/// est censé contenir **une seule nuit** d'**un seul enregistreur**. On dérive des noms de fichiers
/// originaux (`PaRecPR<série>_<yyyyMMdd>_<HHmmss>.wav`, motif conservé même après préfixage R7) :
///
/// - l'ensemble des **numéros de série** d'enregistreur présents ;
/// - l'ensemble des **dates** (jours calendaires) d'acquisition.
///
/// **Règle (validée)** : une nuit s'étale légitimement sur **deux dates consécutives** (soir J →
/// matin J+1). Il y a donc « mélange » si **plusieurs enregistreurs** (>1 série) **ou plusieurs
/// nuits** (plus de deux dates, ou deux dates non consécutives). C'est un **avertissement**, jamais
/// un blocage : l'observateur reste maître de lancer l'import.
///
/// Objet de transport pur (aucune dépendance JavaFX) ; les noms illisibles sont simplement ignorés.
public record AnalyseMelange(SortedSet<String> series, SortedSet<LocalDate> nuits) {

    private static final Pattern MOTIF = Pattern.compile("PaRecPR(\\d+)_(\\d{8})_\\d{6}");
    private static final DateTimeFormatter FORMAT_DATE = DateTimeFormatter.ofPattern("yyyyMMdd", Locale.ROOT);

    public AnalyseMelange {
        series = Collections.unmodifiableSortedSet(new TreeSet<>(Objects.requireNonNull(series, "series")));
        nuits = Collections.unmodifiableSortedSet(new TreeSet<>(Objects.requireNonNull(nuits, "nuits")));
    }

    /// Analyse les noms des fichiers `originaux` : extrait séries et dates du motif `PaRecPR…`.
    public static AnalyseMelange depuis(List<Path> originaux) {
        Objects.requireNonNull(originaux, "originaux");
        SortedSet<String> series = new TreeSet<>();
        SortedSet<LocalDate> nuits = new TreeSet<>();
        for (Path original : originaux) {
            Matcher correspondance = MOTIF.matcher(original.getFileName().toString());
            if (correspondance.find()) {
                try {
                    // On valide la date AVANT d'enregistrer la série : un nom au motif correct mais à
                    // la date impossible (ex. 20269999) est ignoré *entièrement*, sinon sa série
                    // gonflerait à tort le compte d'enregistreurs.
                    LocalDate jour = LocalDate.parse(correspondance.group(2), FORMAT_DATE);
                    series.add(correspondance.group(1));
                    nuits.add(jour);
                } catch (DateTimeParseException illisible) {
                    // Date impossible : on ignore ce nom sans interrompre l'analyse.
                }
            }
        }
        return new AnalyseMelange(series, nuits);
    }

    /// Vrai si les originaux portent plus d'un numéro de série (plusieurs enregistreurs).
    public boolean plusieursEnregistreurs() {
        return series.size() > 1;
    }

    /// Vrai si les acquisitions couvrent plus d'une nuit : plus de deux dates, ou deux dates non
    /// consécutives (au-delà du chevauchement soir → matin d'une même nuit).
    public boolean plusieursNuits() {
        if (nuits.size() <= 1) {
            return false;
        }
        if (nuits.size() > 2) {
            return true;
        }
        return nuits.last().toEpochDay() - nuits.first().toEpochDay() > 1;
    }

    /// Vrai si un mélange (enregistreurs ou nuits) est détecté → avertissement à l'inspection.
    public boolean melange() {
        return plusieursEnregistreurs() || plusieursNuits();
    }
}
