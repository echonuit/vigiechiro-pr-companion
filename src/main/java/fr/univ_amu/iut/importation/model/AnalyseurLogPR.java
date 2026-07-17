package fr.univ_amu.iut.importation.model;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/// Analyseur du journal du capteur `LogPR<n>.txt` (C9, R19) : transforme le texte brut du firmware
/// Teensy en un [JournalParse] exploitable.
///
/// C'est la **seule source d'identité de l'enregistreur** (n° de série) et des paramètres
/// d'acquisition d'une nuit : aucune autre étape de l'import ne connaît le matériel. Le journal
/// est **circulaire** (R19) : des entrées anciennes peuvent manquer, donc tous les champs hors n°
/// de série sont tolérants à l'absence.
///
/// Format d'une ligne (observé sur le sample `LogPR1925492.txt`) :
///
/// ```
/// JJ/MM/AA - HH:MM:SS PR<serie> <message>
/// 22/04/26 - 16:02:21 PR1925492 Paramètres : Acquisi. 20:25-07:47, Fe384kHz ... Bd. Freq.
/// 8-120kHz ...
/// ```
///
/// Le parsing est purement positionnel/regex (aucune dépendance externe) et **déterministe** :
/// deux lectures du même fichier produisent le même [JournalParse].
public final class AnalyseurLogPR {

    /// En dessous de ce pourcentage de batterie, on lève une anomalie (batterie faible).
    public static final int SEUIL_BATTERIE_FAIBLE = 20;

    private static final Pattern LIGNE =
            Pattern.compile("^(\\d{2})/(\\d{2})/(\\d{2})\\s*-\\s*(\\d{2}):(\\d{2}):(\\d{2})\\s+PR\\d+\\s+(.*)$");
    private static final Pattern SERIE_EXPLICITE = Pattern.compile("num[ée]ro de s[ée]rie\\s+(\\d+)");
    private static final Pattern SERIE_PREFIXE = Pattern.compile("PR(\\d+)");
    private static final Pattern FENETRE = Pattern.compile("Acquisi\\.?\\s*(\\d{1,2}:\\d{2})-(\\d{1,2}:\\d{2})");
    private static final Pattern FREQUENCE = Pattern.compile("Fe\\s*(\\d+)\\s*kHz");
    private static final Pattern SENSIBILITE = Pattern.compile("S\\.\\s*R\\.\\s*([^,]+)");
    private static final Pattern BANDE = Pattern.compile("Bd\\.\\s*Freq\\.\\s*([^,]+)");
    private static final Pattern POURCENTAGE = Pattern.compile("(\\d{1,3})\\s*%");

    /// Analyse le fichier journal pointé par `fichierLog` (lu en UTF-8).
    ///
    /// @throws UncheckedIOException si le fichier est illisible
    public JournalParse analyser(Path fichierLog) {
        Objects.requireNonNull(fichierLog, "fichierLog");
        try {
            return analyser(Files.readAllLines(fichierLog, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException("Lecture impossible du journal " + fichierLog, e);
        }
    }

    /// Analyse un journal déjà découpé en lignes (utile pour les tests). Toutes les règles
    /// d'extraction sont concentrées ici.
    ///
    /// @throws IllegalArgumentException si aucun n° de série ne peut être déterminé (le journal
    /// n'est pas exploitable : il n'identifie aucun enregistreur)
    public JournalParse analyser(List<String> lignes) {
        Objects.requireNonNull(lignes, "lignes");

        String numeroSerie = null;
        String versionModele = null;
        LocalDate dateDebut = null;
        String heureDebut = null;
        String heureFin = null;
        Integer frequenceHz = null;
        String bandePassante = null;
        String sensibilite = null;
        Boolean sondePresente = null;
        String parametresBruts = null;
        List<LigneJournal> evenements = new ArrayList<>();
        List<LigneJournal> anomalies = new ArrayList<>();

        for (String brute : lignes) {
            Matcher m = LIGNE.matcher(brute.strip());
            if (!m.matches()) {
                continue; // ligne non conforme (en-tête isolé, séparateur) : ignorée
            }
            LocalDateTime horodatage = LocalDateTime.of(
                    LocalDate.of(
                            2000 + Integer.parseInt(m.group(3)),
                            Integer.parseInt(m.group(2)),
                            Integer.parseInt(m.group(1))),
                    LocalTime.of(
                            Integer.parseInt(m.group(4)), Integer.parseInt(m.group(5)), Integer.parseInt(m.group(6))));
            if (dateDebut == null) {
                dateDebut = horodatage.toLocalDate();
            }
            String message = m.group(7).strip();

            if (numeroSerie == null) {
                numeroSerie = extraire(SERIE_EXPLICITE, message);
            }
            if (message.contains("Démarrage") && versionModele == null) {
                versionModele = extraireVersion(message);
            }
            if (message.contains("Sonde")) {
                sondePresente = message.toLowerCase(Locale.ROOT).contains("présente");
            }
            if (message.startsWith("Param") && parametresBruts == null) {
                parametresBruts = apres(message, ":");
                heureDebut = normaliserHeure(extraireGroupe(FENETRE, message, 1));
                heureFin = normaliserHeure(extraireGroupe(FENETRE, message, 2));
                String hz = extraire(FREQUENCE, message);
                frequenceHz = hz == null ? null : Integer.parseInt(hz) * 1000;
                bandePassante = nettoyer(extraire(BANDE, message));
                sensibilite = nettoyer(extraire(SENSIBILITE, message));
            }

            collecterEvenement(horodatage, message, evenements);
            collecterAnomalie(horodatage, message, anomalies);
        }

        if (numeroSerie == null) {
            numeroSerie = serieDepuisPrefixe(lignes);
        }
        if (numeroSerie == null) {
            throw new IllegalArgumentException(
                    "Journal LogPR inexploitable : aucun numéro de série d'enregistreur trouvé.");
        }
        if (sondePresente != null && !sondePresente) {
            // Propriété du déploiement (pas rattachée à une ligne datée) : horodatage null → toutes les nuits.
            anomalies.add(new LigneJournal(null, "Sonde température/hygrométrie absente ou défaillante."));
        }

        return new JournalParse(
                numeroSerie,
                versionModele,
                dateDebut,
                heureDebut,
                heureFin,
                frequenceHz,
                bandePassante,
                sensibilite,
                sondePresente != null && sondePresente,
                parametresBruts,
                evenements,
                anomalies);
    }

    /// Évènements remarquables conservés (changements de mode, réveils, mises en veille).
    private static void collecterEvenement(LocalDateTime horodatage, String message, List<LigneJournal> evenements) {
        if (message.startsWith("###") || message.contains("Wakeup") || message.startsWith("Mise en veille")) {
            evenements.add(new LigneJournal(horodatage, message));
        }
    }

    /// Détection conservatrice des anomalies (R19) : réveil non programmé, batterie faible, erreur
    /// SD.
    private static void collecterAnomalie(LocalDateTime horodatage, String message, List<LigneJournal> anomalies) {
        if (message.contains("Wakeup") && !message.contains("ALARM")) {
            anomalies.add(new LigneJournal(horodatage, "Réveil non programmé : " + message));
        }
        String minuscule = message.toLowerCase(Locale.ROOT);
        if (minuscule.contains("erreur")
                || minuscule.contains("error")
                || minuscule.contains("échec")
                || minuscule.contains("fail")) {
            anomalies.add(new LigneJournal(horodatage, message));
        }
        if (message.startsWith("Batteries internes") || message.contains("Bat. Interne")) {
            Matcher pct = POURCENTAGE.matcher(message);
            if (pct.find()) {
                int niveau = Integer.parseInt(pct.group(1));
                if (niveau < SEUIL_BATTERIE_FAIBLE) {
                    anomalies.add(new LigneJournal(horodatage, "Batterie faible (" + niveau + "%) : " + message));
                }
            }
        }
    }

    /// Reconstruit la version/modèle depuis la ligne de démarrage, en écartant la cadence CPU.
    private static String extraireVersion(String message) {
        int virgule = message.indexOf(',');
        if (virgule < 0) {
            return null;
        }
        List<String> parts = new ArrayList<>();
        for (String part : message.substring(virgule + 1).split(",")) {
            String t = part.strip();
            if (!t.isEmpty() && !t.startsWith("CPU")) {
                parts.add(t);
            }
        }
        return parts.isEmpty() ? null : String.join(", ", parts);
    }

    /// Cherche le n° de série dans le préfixe `PR<n>` d'une ligne quelconque (repli).
    private static String serieDepuisPrefixe(List<String> lignes) {
        for (String ligne : lignes) {
            Matcher m = SERIE_PREFIXE.matcher(ligne);
            if (m.find()) {
                return m.group(1);
            }
        }
        return null;
    }

    private static String extraire(Pattern pattern, String texte) {
        Matcher m = pattern.matcher(texte);
        return m.find() ? m.group(1).strip() : null;
    }

    private static String extraireGroupe(Pattern pattern, String texte, int groupe) {
        Matcher m = pattern.matcher(texte);
        return m.find() ? m.group(groupe).strip() : null;
    }

    private static String nettoyer(String valeur) {
        if (valeur == null) {
            return null;
        }
        String t = valeur.strip();
        return t.isEmpty() ? null : t;
    }

    private static String apres(String texte, String separateur) {
        int i = texte.indexOf(separateur);
        return i < 0 ? texte.strip() : texte.substring(i + separateur.length()).strip();
    }

    /// Normalise `HH:MM` en `HH:MM:SS` (format ISO des colonnes heure).
    private static String normaliserHeure(String heure) {
        if (heure == null) {
            return null;
        }
        String[] parties = heure.split(":");
        return String.format(Locale.ROOT, "%02d:%02d:00", Integer.parseInt(parties[0]), Integer.parseInt(parties[1]));
    }
}
