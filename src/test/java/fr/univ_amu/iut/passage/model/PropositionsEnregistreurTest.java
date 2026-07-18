package fr.univ_amu.iut.passage.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import fr.univ_amu.iut.passage.model.dao.EnregistrementOriginalDao;
import fr.univ_amu.iut.passage.model.dao.EnregistreurDao;
import fr.univ_amu.iut.passage.model.dao.JournalDuCapteurDao;
import fr.univ_amu.iut.passage.model.dao.SessionDao;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Numéros de série à proposer quand l'utilisateur doit désigner l'enregistreur lui-même (#1828), sur DAO
/// **mockés** : les noms de fichiers de la nuit d'abord (journal, puis originaux), les enregistreurs déjà
/// connus du poste ensuite, sans doublon et sans sentinelle.
class PropositionsEnregistreurTest {

    private static final Long ID_PASSAGE = 42L;
    private static final Long ID_SESSION = 7L;

    private EnregistreurDao enregistreurDao;
    private SessionDao sessionDao;
    private JournalDuCapteurDao journauxDao;
    private EnregistrementOriginalDao originauxDao;
    private PropositionsEnregistreur propositions;

    @BeforeEach
    void preparer() {
        enregistreurDao = mock(EnregistreurDao.class);
        sessionDao = mock(SessionDao.class);
        journauxDao = mock(JournalDuCapteurDao.class);
        originauxDao = mock(EnregistrementOriginalDao.class);
        propositions = new PropositionsEnregistreur(enregistreurDao, sessionDao, journauxDao, originauxDao);

        lenient().when(enregistreurDao.findAll()).thenReturn(List.of());
        lenient().when(sessionDao.trouverParPassage(anyLong())).thenReturn(Optional.of(session()));
        lenient().when(journauxDao.trouverParSession(anyLong())).thenReturn(Optional.empty());
        lenient().when(originauxDao.findBySession(anyLong())).thenReturn(List.of());
    }

    @Test
    @DisplayName("#1828 : le n° du JOURNAL de la nuit est proposé en premier (c'est la source de l'import)")
    void serie_du_journal_en_tete() {
        when(journauxDao.trouverParSession(ID_SESSION))
                .thenReturn(Optional.of(journal("/ws/sessions/Car130711-2026-Pass1-Z41/LogPR1925492.txt")));
        when(enregistreurDao.findAll()).thenReturn(List.of(new Enregistreur("1997632", null, null)));

        assertThat(propositions.pour(ID_PASSAGE))
                .as("celui de la nuit prime sur les autres appareils connus du poste")
                .containsExactly("1925492", "1997632");
    }

    @Test
    @DisplayName("#1828 : à défaut de journal, le n° se lit dans les NOMS des originaux (import en mode dégradé)")
    void serie_des_noms_de_fichiers() {
        when(originauxDao.findBySession(ID_SESSION))
                .thenReturn(List.of(
                        original("Car130711-2026-Pass1-Z41-PaRecPR1925492_20260703_220529_000.wav"),
                        original("Car130711-2026-Pass1-Z41-PaRecPR1925492_20260703_220534_000.wav")));

        assertThat(propositions.pour(ID_PASSAGE))
                .as("un seul appareil, malgré deux fichiers : pas de doublon")
                .containsExactly("1925492");
    }

    @Test
    @DisplayName("#1828 : les sentinelles ne sont jamais proposées — « INCONNU » n'aiderait personne")
    void sentinelles_ecartees() {
        when(enregistreurDao.findAll())
                .thenReturn(List.of(
                        new Enregistreur(Enregistreur.INCONNU, null, null),
                        new Enregistreur(Enregistreur.INCONNU_IMPORT, null, null),
                        new Enregistreur("1997632", null, null)));

        assertThat(propositions.pour(ID_PASSAGE)).containsExactly("1997632");
    }

    @Test
    @DisplayName("#1828 : sans session (squelette rapatrié), restent les enregistreurs connus du poste")
    void squelette_sans_fichier() {
        when(sessionDao.trouverParPassage(ID_PASSAGE)).thenReturn(Optional.empty());
        when(enregistreurDao.findAll()).thenReturn(List.of(new Enregistreur("1997632", null, null)));

        assertThat(propositions.pour(ID_PASSAGE))
                .as("aucun fichier à lire, mais l'appareil d'une autre nuit reste le meilleur candidat")
                .containsExactly("1997632");
    }

    @Test
    @DisplayName("#1828 : rien à proposer → liste vide (l'utilisateur saisira le numéro à la main)")
    void rien_a_proposer() {
        assertThat(propositions.pour(ID_PASSAGE)).isEmpty();
    }

    private static SessionDEnregistrement session() {
        return new SessionDEnregistrement(ID_SESSION, "/ws/sessions/Car130711-2026-Pass1-Z41", 0L, 0L, ID_PASSAGE);
    }

    private static JournalDuCapteur journal(String chemin) {
        return new JournalDuCapteur(1L, chemin, null, null, ID_SESSION);
    }

    private static EnregistrementOriginal original(String nom) {
        return new EnregistrementOriginal(1L, nom, "/ws/originaux/" + nom, null, null, null, ID_SESSION, null);
    }
}
