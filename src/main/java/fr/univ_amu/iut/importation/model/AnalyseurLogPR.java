package fr.univ_amu.iut.importation.model;

import fr.univ_amu.iut.commun.model.LectureBornee;
import fr.univ_amu.iut.commun.model.PlafondLecture;
import java.io.IOException;
import java.io.UncheckedIOException;
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

    /// Analyse le fichier journal pointé par `fichierLog` (lu en UTF-8), **sous plafond** (#3222) : le
    /// journal vient de la carte, donc du dehors, et sa taille n'est pas contrôlée.
    ///
    /// @throws UncheckedIOException si le fichier est illisible
    /// @throws fr.univ_amu.iut.commun.model.EntreeTropVolumineuse si le journal dépasse le plafond ; le
    ///     message nomme la limite (cf. [PlafondLecture#journalCapteur()])
    public JournalParse analyser(Path fichierLog) {
        Objects.requireNonNull(fichierLog, "fichierLog");
        try {
            return analyser(LectureBornee.lignes(fichierLog, PlafondLecture.journalCapteur()));
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
        List<ConfigurationAcquisition> configurations = new ArrayList<>();

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
            if (message.startsWith("Param")) {
                // #3460 : TOUTES les configurations sont collectées, horodatées. Un capteur laissé
                // plusieurs nuits au même point en pose une par session, et la garde d'origine
                // (`&& parametresBruts == null`) ne retenait que la première : une nuit repartait avec
                // la fréquence d'échantillonnage d'une autre, en silence.
                ConfigurationAcquisition configuration = configurationDepuis(horodatage, message);
                configurations.add(configuration);
                if (parametresBruts == null) {
                    // Les champs plats décrivent la PREMIÈRE configuration, comme avant : l'inspection
                    // et l'affichage les lisent sans connaître de nuit. L'appariement par nuit se fait
                    // dans JournalParse#configurationPourNuit, à l'import.
                    parametresBruts = configuration.brut();
                    heureDebut = configuration.heureDebut();
                    heureFin = configuration.heureFin();
                    frequenceHz = configuration.frequenceEchantillonnageHz();
                    bandePassante = configuration.bandePassante();
                    sensibilite = configuration.sensibilite();
                }
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
                anomalies,
                configurations);
    }

    /// Une configuration d'acquisition lue sur sa ligne « Paramètres : … », datée de cette ligne.
    private static ConfigurationAcquisition configurationDepuis(LocalDateTime horodatage, String message) {
        String hz = extraire(FREQUENCE, message);
        return new ConfigurationAcquisition(
                horodatage,
                normaliserHeure(extraireGroupe(FENETRE, message, 1)),
                normaliserHeure(extraireGroupe(FENETRE, message, 2)),
                hz == null ? null : Integer.parseInt(hz) * 1000,
                nettoyer(extraire(BANDE, message)),
                nettoyer(extraire(SENSIBILITE, message)),
                apres(message, ":"));
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
        if (MotifDeReveil.estUnReveil(message) && !MotifDeReveil.estVoulu(message)) {
            anomalies.add(new LigneJournal(horodatage, "Réveil non programmé : " + message));
        }
        String minuscule = message.toLowerCase(Locale.ROOT);
        if (minuscule.contains("erreur")
                || minuscule.contains("error")
                || minuscule.contains("échec")
                || minuscule.contains("fail")) {
            anomalies.add(new LigneJournal(horodatage, message));
        }
        collecterBatterieFaible(horodatage, message, anomalies);
    }

    /// Batterie faible : trois conditions successives, écrites en clauses gardes.
    ///
    /// La ligne doit d'abord être une ligne de batterie, l'enregistreur l'écrivant sous deux formes
    /// selon le moment (`Batteries internes 3.2V (12%)` au démarrage, `Bat. Interne 4.0 90%` à la
    /// mise en veille). Elle doit ensuite porter un pourcentage lisible : le format varie assez pour
    /// qu'une ligne de batterie sans pourcentage exploitable reste possible, et on préfère alors ne
    /// rien signaler plutôt que de deviner. Le niveau doit enfin être sous le seuil.
    ///
    /// Chaque condition non remplie sort de la méthode. Les mêmes tests imbriqués formaient
    /// auparavant le seul point du dépôt à trois niveaux d'imbrication.
    private static void collecterBatterieFaible(
            LocalDateTime horodatage, String message, List<LigneJournal> anomalies) {
        if (!message.startsWith("Batteries internes") && !message.contains("Bat. Interne")) {
            return;
        }
        Matcher pourcentage = POURCENTAGE.matcher(message);
        if (!pourcentage.find()) {
            return;
        }
        int niveau = Integer.parseInt(pourcentage.group(1));
        if (niveau < SEUIL_BATTERIE_FAIBLE) {
            anomalies.add(new LigneJournal(horodatage, "Batterie faible (" + niveau + "%) : " + message));
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
