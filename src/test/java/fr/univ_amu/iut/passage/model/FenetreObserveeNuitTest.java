package fr.univ_amu.iut.passage.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import fr.univ_amu.iut.passage.model.dao.EnregistrementOriginalDao;
import fr.univ_amu.iut.passage.model.dao.SequenceDao;
import fr.univ_amu.iut.passage.model.dao.SessionDao;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Fenêtre réellement observée d'une nuit (#1878).
///
/// L'enjeu : distinguer ce que la nuit **déclare** (colonnes `start_time` / `end_time`, qui peuvent avoir
/// dérivé) de ce qu'elle **prouve** (horodatages des fichiers et des séquences).
class FenetreObserveeNuitTest {

    private static final Long ID_PASSAGE = 42L;
    private static final Long ID_SESSION = 7L;

    private SessionDao sessions;
    private EnregistrementOriginalDao originaux;
    private SequenceDao sequences;
    private FenetreObserveeNuit fenetre;

    @BeforeEach
    void preparer() {
        sessions = mock(SessionDao.class);
        originaux = mock(EnregistrementOriginalDao.class);
        sequences = mock(SequenceDao.class);
        fenetre = new FenetreObserveeNuit(sessions, originaux, sequences);
        when(sessions.trouverParPassage(ID_PASSAGE)).thenReturn(Optional.of(session()));
    }

    @Test
    @DisplayName("Les noms des fichiers originaux font autorité : premier et dernier enregistrement")
    void bornes_depuis_les_originaux() {
        when(originaux.findBySession(ID_SESSION))
                .thenReturn(List.of(
                        original("Car640380-2026-Pass2-Z1_20260704_233015.wav"),
                        original("Car640380-2026-Pass2-Z1_20260704_210000.wav"),
                        original("Car640380-2026-Pass2-Z1_20260705_060000.wav")));

        Optional<FenetreObserveeNuit.Bornes> bornes = fenetre.pour(ID_PASSAGE);

        // L'ordre de lecture n'a pas à être trié : ce sont les bornes qui comptent.
        assertThat(bornes).isPresent();
        assertThat(bornes.get().debut()).isEqualTo(LocalDateTime.of(2026, 7, 4, 21, 0));
        assertThat(bornes.get().fin()).isEqualTo(LocalDateTime.of(2026, 7, 5, 6, 0));
        verify(sequences, never()).findBySession(anyLong());
    }

    @Test
    @DisplayName("Originaux purgés → repli sur l'horodatage de capture des séquences")
    void bornes_depuis_les_sequences() {
        when(originaux.findBySession(ID_SESSION)).thenReturn(List.of());
        when(sequences.findBySession(ID_SESSION))
                .thenReturn(List.of(
                        sequence(LocalDateTime.of(2026, 7, 5, 5, 30)), sequence(LocalDateTime.of(2026, 7, 4, 21, 15))));

        Optional<FenetreObserveeNuit.Bornes> bornes = fenetre.pour(ID_PASSAGE);

        assertThat(bornes).isPresent();
        assertThat(bornes.get().debut()).isEqualTo(LocalDateTime.of(2026, 7, 4, 21, 15));
        assertThat(bornes.get().fin()).isEqualTo(LocalDateTime.of(2026, 7, 5, 5, 30));
    }

    @Test
    @DisplayName("Nuit squelette (ni fichier ni séquence) → aucune preuve, on n'invente pas")
    void squelette_sans_preuve() {
        when(originaux.findBySession(ID_SESSION)).thenReturn(List.of());
        when(sequences.findBySession(ID_SESSION)).thenReturn(List.of());

        assertThat(fenetre.pour(ID_PASSAGE)).isEmpty();
    }

    @Test
    @DisplayName("Un SEUL enregistrement ne délimite pas une nuit : deux bornes égales deviendraient 24 h")
    void un_seul_enregistrement_ne_prouve_rien() {
        when(originaux.findBySession(ID_SESSION))
                .thenReturn(List.of(original("Car640380-2026-Pass2-Z1_20260704_210000.wav")));
        when(sequences.findBySession(ID_SESSION)).thenReturn(List.of());

        // Début = fin ferait croire à une nuit à cheval sur minuit à l'envoi (la règle « la fin ne suit pas
        // le début » ajoute un jour) : c'est le mécanisme qui a produit les nuits de 24 h de #1860.
        assertThat(fenetre.pour(ID_PASSAGE)).isEmpty();
    }

    @Test
    @DisplayName("Un nom de fichier sans horodatage lisible est ignoré, sans faire échouer les autres")
    void nom_sans_horodatage_ignore() {
        when(originaux.findBySession(ID_SESSION))
                .thenReturn(List.of(
                        original("enregistrement-sans-date.wav"),
                        original("Car640380-2026-Pass2-Z1_20260704_210000.wav"),
                        original("Car640380-2026-Pass2-Z1_20260705_060000.wav")));

        assertThat(fenetre.pour(ID_PASSAGE)).isPresent();
    }

    @Test
    @DisplayName("contredisent : compare des heures, pas des chaînes (« 21:00 » et « 21:00:00 » concordent)")
    void contredisent_compare_des_heures() {
        FenetreObserveeNuit.Bornes bornes =
                new FenetreObserveeNuit.Bornes(LocalDateTime.of(2026, 7, 4, 21, 0), LocalDateTime.of(2026, 7, 5, 6, 0));

        // La base contient les deux écritures selon le chemin qui a écrit la ligne : les comparer
        // littéralement annoncerait une dérive là où il n'y a qu'une différence de format.
        assertThat(bornes.contredisent(passageAvec("21:00", "06:00"))).isFalse();
        assertThat(bornes.contredisent(passageAvec("21:00:00", "06:00:00"))).isFalse();
        assertThat(bornes.contredisent(passageAvec("15:00", "15:00"))).isTrue();
        assertThat(bornes.contredisent(passageAvec("n'importe quoi", "06:00")))
                .as("une borne illisible vaut « contredit » : mieux vaut réécrire une valeur prouvée")
                .isTrue();
    }

    @Test
    @DisplayName("heure : format des colonnes de la base, secondes comprises")
    void heure_au_format_de_la_base() {
        FenetreObserveeNuit.Bornes bornes =
                new FenetreObserveeNuit.Bornes(LocalDateTime.of(2026, 7, 4, 21, 0), LocalDateTime.of(2026, 7, 5, 6, 5));

        assertThat(bornes.heure(bornes.debut())).isEqualTo("21:00:00");
        assertThat(bornes.heure(bornes.fin())).isEqualTo("06:05:00");
        assertThat(bornes.dateEnregistrement()).isEqualTo("2026-07-04");
    }

    // Les entites du domaine sont des records : on les CONSTRUIT (ce sont des objets-valeurs), on ne les
    // mocke pas - Mockito ne sait de toute facon pas stubber une classe finale.

    private static SessionDEnregistrement session() {
        return new SessionDEnregistrement(ID_SESSION, "/tmp/nuit", 0L, 0L, ID_PASSAGE);
    }

    private static EnregistrementOriginal original(String nomFichier) {
        return new EnregistrementOriginal(
                null, nomFichier, "/tmp/nuit/" + nomFichier, 5.0, 384000, null, ID_SESSION, 0L);
    }

    private static SequenceDEcoute sequence(LocalDateTime capture) {
        return new SequenceDEcoute(
                null, "seq.wav", null, 0, 0.0, 5.0, "/tmp/nuit/seq.wav", false, ID_SESSION, capture, 0L, null);
    }

    private static Passage passageAvec(String heureDebut, String heureFin) {
        return new Passage(
                ID_PASSAGE,
                2,
                2026,
                "2026-07-04",
                heureDebut,
                heureFin,
                null,
                null,
                null,
                null,
                null,
                null,
                1L,
                "1997632");
    }
}
