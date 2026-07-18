package fr.univ_amu.iut.passage.model;

import com.google.inject.Inject;
import fr.univ_amu.iut.passage.model.dao.EnregistrementOriginalDao;
import fr.univ_amu.iut.passage.model.dao.SequenceDao;
import fr.univ_amu.iut.passage.model.dao.SessionDao;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/// Fenêtre **réellement observée** d'une nuit (#1878) : premier et dernier enregistrement effectifs,
/// lus dans les traces locales.
///
/// Quand une nuit a des **fichiers** ou des **observations**, ses bornes ne sont pas une opinion : elles
/// sont **connues**. Les champs `start_time` / `end_time` du passage, eux, sont une valeur *déclarée* -
/// saisie, importée, ou rapatriée de la plateforme - et peuvent donc avoir dérivé. #1860 l'a montré :
/// une conversion fautive suffisait à décaler la nuit, et l'erreur **composait** à chaque aller-retour
/// avec la plateforme.
///
/// Faire autorité sur les preuves rend la cohérence **structurelle** plutôt que tributaire d'une
/// conversion juste : la nuit se réaligne d'elle-même sur ce qui a été enregistré.
///
/// Deux sources, par ordre d'autorité :
///
/// 1. les **noms des fichiers originaux** (`…_AAAAMMJJ_HHMMSS…`, R7) - l'horodatage posé par
///    l'enregistreur lui-même, sur la carte SD ;
/// 2. à défaut, l'horodatage de capture des **séquences** (`recorded_at`) - disponible sur une nuit
///    régénérée ou réactivée, dont les originaux ont été purgés.
///
/// Une nuit **squelette** (rapatriée de la plateforme, sans fichier ni séquence) n'a aucune preuve :
/// [#pour] renvoie alors vide, et l'appelant garde les valeurs déclarées.
public class FenetreObserveeNuit {

    /// Horodatage `_AAAAMMJJ_HHMMSS` d'un nom de fichier (R7). Même motif que le pré-check de
    /// `qualification`, qui compare déjà plage observée et plage déclarée sans en tirer de conséquence.
    private static final Pattern HORODATAGE = Pattern.compile("_(\\d{8})_(\\d{6})");

    private final SessionDao sessions;
    private final EnregistrementOriginalDao originaux;
    private final SequenceDao sequences;

    @Inject
    public FenetreObserveeNuit(SessionDao sessions, EnregistrementOriginalDao originaux, SequenceDao sequences) {
        this.sessions = Objects.requireNonNull(sessions, "sessions");
        this.originaux = Objects.requireNonNull(originaux, "originaux");
        this.sequences = Objects.requireNonNull(sequences, "sequences");
    }

    /// Bornes observées de la nuit du passage, ou **vide** s'il n'existe aucune preuve locale.
    ///
    /// Une nuit réduite à **un seul** enregistrement ne délimite aucune fenêtre : ses deux bornes
    /// coïncideraient, et l'envoi les prendrait pour une nuit à cheval sur minuit (la règle « la fin ne
    /// suit pas le début » ajouterait un jour). On préfère alors ne rien affirmer.
    public Optional<Bornes> pour(Long idPassage) {
        return sessions.trouverParPassage(idPassage).flatMap(session -> {
            List<LocalDateTime> horodatages = depuisLesOriginaux(session.id());
            if (horodatages.size() < 2) {
                horodatages = depuisLesSequences(session.id());
            }
            return horodatages.size() < 2
                    ? Optional.empty()
                    : Optional.of(new Bornes(horodatages.get(0), horodatages.get(horodatages.size() - 1)));
        });
    }

    /// Horodatages lus dans les **noms des fichiers originaux**, triés.
    private List<LocalDateTime> depuisLesOriginaux(Long idSession) {
        return originaux.findBySession(idSession).stream()
                .map(original -> horodatageDe(original.nomFichier()))
                .flatMap(Optional::stream)
                .sorted()
                .toList();
    }

    /// Horodatages de capture des **séquences**, triés (repli quand les originaux ont été purgés).
    private List<LocalDateTime> depuisLesSequences(Long idSession) {
        return sequences.findBySession(idSession).stream()
                .map(SequenceDEcoute::horodatageCapture)
                .filter(Objects::nonNull)
                .sorted(Comparator.naturalOrder())
                .toList();
    }

    /// Horodatage `_AAAAMMJJ_HHMMSS` d'un nom de fichier, s'il y figure et qu'il désigne un instant réel.
    private static Optional<LocalDateTime> horodatageDe(String nomFichier) {
        if (nomFichier == null) {
            return Optional.empty();
        }
        Matcher trouve = HORODATAGE.matcher(nomFichier);
        if (!trouve.find()) {
            return Optional.empty();
        }
        String date = trouve.group(1);
        String heure = trouve.group(2);
        try {
            return Optional.of(LocalDateTime.of(
                    LocalDate.of(
                            Integer.parseInt(date.substring(0, 4)),
                            Integer.parseInt(date.substring(4, 6)),
                            Integer.parseInt(date.substring(6, 8))),
                    LocalTime.of(
                            Integer.parseInt(heure.substring(0, 2)),
                            Integer.parseInt(heure.substring(2, 4)),
                            Integer.parseInt(heure.substring(4, 6)))));
        } catch (RuntimeException invalide) {
            return Optional.empty();
        }
    }

    /// Bornes d'une nuit telles que les enregistrements les attestent.
    ///
    /// @param debut premier enregistrement
    /// @param fin dernier enregistrement
    public record Bornes(LocalDateTime debut, LocalDateTime fin) {

        /// Format des colonnes `start_time` / `end_time`.
        private static final DateTimeFormatter HEURE_BASE = DateTimeFormatter.ofPattern("HH:mm:ss");

        public Bornes {
            Objects.requireNonNull(debut, "debut");
            Objects.requireNonNull(fin, "fin");
        }

        /// `true` si ces bornes contredisent les heures déclarées du passage, **à la minute près**.
        ///
        /// La comparaison porte sur des [LocalTime], jamais sur les chaînes : la base contient les deux
        /// écritures (`21:00` et `21:00:00`) selon le chemin qui a écrit la ligne, et les comparer
        /// littéralement annoncerait une dérive là où il n'y a qu'une différence de format. Une borne
        /// illisible vaut « contredit » : mieux vaut réécrire une valeur prouvée qu'en garder une qu'on
        /// ne sait même pas relire.
        public boolean contredisent(Passage passage) {
            return !memeMinute(passage.heureDebut(), debut) || !memeMinute(passage.heureFin(), fin);
        }

        private static boolean memeMinute(String declaree, LocalDateTime observee) {
            try {
                return LocalTime.parse(declaree)
                        .truncatedTo(ChronoUnit.MINUTES)
                        .equals(observee.toLocalTime().truncatedTo(ChronoUnit.MINUTES));
            } catch (RuntimeException illisible) {
                return false;
            }
        }

        /// Heure au format de la base (`HH:mm:ss`).
        public String heure(LocalDateTime moment) {
            return moment.toLocalTime().truncatedTo(ChronoUnit.SECONDS).format(HEURE_BASE);
        }

        /// Date de la nuit au format de la base (`AAAA-MM-JJ`), celle du **premier** enregistrement.
        public String dateEnregistrement() {
            return debut.toLocalDate().toString();
        }
    }
}
