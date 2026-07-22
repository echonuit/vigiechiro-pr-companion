package fr.univ_amu.iut.passage.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import fr.univ_amu.iut.commun.model.HorlogeFigee;
import fr.univ_amu.iut.commun.model.Workspace;
import fr.univ_amu.iut.commun.persistence.DataAccessException;
import fr.univ_amu.iut.commun.persistence.MigrationSchema;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.commun.persistence.UniteDeTravail;
import fr.univ_amu.iut.fixture.JeuDeDonneesPassage;
import fr.univ_amu.iut.passage.model.dao.EnregistrementOriginalDao;
import fr.univ_amu.iut.passage.model.dao.SequenceDao;
import fr.univ_amu.iut.passage.model.dao.SessionDao;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/// Garde-fou d'**atomicité** de l'adoption des originaux d'une nuit reconstruite (#1959, #1968).
///
/// L'adoption inscrit les enregistrements retrouvés et y rattache leurs séquences - des milliers d'ordres
/// sur une vraie nuit. Ils sont groupés dans une [UniteDeTravail] : le motif était le **coût** (près de
/// 6700 commits auto-commités, plus de deux minutes d'attente), l'atomicité est venue avec.
///
/// C'est elle que ce test tient : une écriture qui échoue en cours de route ne doit laisser **aucun**
/// original à moitié adopté. Sans transaction, les originaux déjà insérés survivraient à l'échec, et la
/// nuit se retrouverait avec des enregistrements dont les séquences ne pointent nulle part.
class AdoptionOriginauxReconstruitsTest {

    private static final int FREQUENCE_HZ = 384_000;

    /// Assez d'octets pour que la durée déduite ne soit pas nulle : l'en-tête WAV en fait 44.
    private static final int TAILLE_BRUT = 4096;

    @TempDir
    Path dossier;

    private SourceDeDonnees source;
    private SessionDao sessionDao;
    private EnregistrementOriginalDao originalDao;
    private SequenceDao sequenceDao;
    private SessionDEnregistrement session;
    private long idPlaceholder;
    private Path bruts;

    @BeforeEach
    void preparer() {
        source = new SourceDeDonnees(new Workspace(dossier));
        new MigrationSchema(source).migrer();
        // Topologie semée par la fixture partagée (#1258) : ce test parle d'adoption, pas de plomberie.
        // Son enregistrement original tient lieu de placeholder - c'est exactement son rôle sur une nuit
        // reconstruite : porter les séquences en attendant les vrais.
        JeuDeDonneesPassage jeu = JeuDeDonneesPassage.dans(source).semer();
        sessionDao = new SessionDao(source);
        originalDao = new EnregistrementOriginalDao(source);
        sequenceDao = new SequenceDao(source);
        session = sessionDao.trouverParPassage(jeu.idPassage()).orElseThrow();
        idPlaceholder = jeu.idOriginal();
        // Les bruts vivent sous le @TempDir : seul leur CHEMIN inscrit en base dérive de la session,
        // la lecture, elle, porte sur le fichier réel que la fixture nous laisse placer où on veut.
        bruts = dossier.resolve("bruts");
    }

    @Test
    @DisplayName("#1959 : une écriture qui échoue en route ne laisse aucun original à moitié adopté")
    void une_ecriture_qui_echoue_ne_laisse_rien_de_moitie_adopte() throws IOException {
        List<BrutRebranche> rebranches =
                List.of(brutAvecSaSequence("PaRec_001.wav"), brutAvecSaSequence("PaRec_002.wav"));
        // Échoue au SECOND rattachement : le premier original est donc déjà inséré quand la panne survient.
        AdoptionOriginauxReconstruits adoption = adoptionAvec(new SequenceDaoQuiLache(source, 2));

        assertThatThrownBy(() -> adoption.adopter(session, placeholders(), rebranches, FREQUENCE_HZ))
                .as("l'échec d'écriture remonte, il n'est pas avalé")
                .isInstanceOf(RuntimeException.class);

        assertThat(originalDao.findBySession(session.id()))
                .as("tout est annulé : seul le placeholder subsiste, aucun original n'a été adopté à moitié")
                .extracting(EnregistrementOriginal::id)
                .containsExactly(idPlaceholder);
    }

    @Test
    @DisplayName("Le chemin nominal adopte les originaux et retire le placeholder devenu orphelin")
    void le_chemin_nominal_adopte_et_nettoie() throws IOException {
        List<BrutRebranche> rebranches =
                List.of(brutAvecSaSequence("PaRec_001.wav"), brutAvecSaSequence("PaRec_002.wav"));

        adoptionAvec(sequenceDao).adopter(session, placeholders(), rebranches, FREQUENCE_HZ);

        assertThat(originalDao.findBySession(session.id()))
                .extracting(EnregistrementOriginal::nomFichier)
                .as("les deux bruts sont adoptés, le placeholder vidé est parti")
                .containsExactlyInAnyOrder("PaRec_001.wav", "PaRec_002.wav");
    }

    @Test
    @DisplayName("L'original adopté porte ce que le brut prouve : chemin, taille, durée, fréquence, empreinte")
    void l_original_adopte_porte_ce_que_le_brut_prouve() throws IOException {
        List<BrutRebranche> rebranches = List.of(brutAvecSaSequence("PaRec_001.wav"));
        adoptionAvec(sequenceDao).adopter(session, placeholders(), rebranches, FREQUENCE_HZ);

        EnregistrementOriginal adopte = originalDao.findBySession(session.id()).stream()
                .filter(original -> "PaRec_001.wav".equals(original.nomFichier()))
                .findFirst()
                .orElseThrow();
        assertThat(adopte.cheminFichier())
                .as("le chemin canonique est celui des bruts de la session, pas celui du dossier source")
                .isEqualTo(Path.of(session.cheminRacine())
                        .resolve("bruts")
                        .resolve("PaRec_001.wav")
                        .toString());
        assertThat(adopte.tailleOctets())
                .as("la taille est relevée sur le fichier réel")
                .isEqualTo(TAILLE_BRUT);
        assertThat(adopte.dureeSecondes())
                .as("la durée se déduit de la taille : (octets - en-tête) / 2 octets par trame / fréquence")
                .isCloseTo((TAILLE_BRUT - 44) / 2.0 / FREQUENCE_HZ, within(1e-9));
        assertThat(adopte.frequenceEchantillonnageHz()).isEqualTo(FREQUENCE_HZ);
        assertThat(adopte.sha256())
                .as("l'empreinte capturée à la régénération est inscrite, sans re-lecture (#1726)")
                .isEqualTo("empreinte-PaRec_001.wav");
    }

    @Test
    @DisplayName("Un brut réduit à son en-tête n'a pas de durée : on ne l'invente pas")
    void un_brut_sans_echantillon_n_a_pas_de_duree() throws IOException {
        List<BrutRebranche> rebranches = List.of(brutDeTaille("PaRec_003.wav", 44));

        adoptionAvec(sequenceDao).adopter(session, placeholders(), rebranches, FREQUENCE_HZ);

        assertThat(originalDao.findBySession(session.id()))
                .filteredOn(original -> "PaRec_003.wav".equals(original.nomFichier()))
                .singleElement()
                .extracting(EnregistrementOriginal::dureeSecondes)
                .as("44 octets, c'est l'en-tête et rien d'autre : aucune trame, donc aucune durée")
                .isNull();
    }

    // --- Fixture ---------------------------------------------------------------------------------

    private AdoptionOriginauxReconstruits adoptionAvec(SequenceDao dao) {
        return new AdoptionOriginauxReconstruits(
                originalDao,
                dao,
                sessionDao,
                new UniteDeTravail(source),
                new HorlogeFigee(LocalDateTime.of(2026, 7, 19, 12, 0)));
    }

    /// Le placeholder de la nuit : l'unique original que porte un passage reconstruit, et que l'adoption
    /// remplace par les vrais.
    private List<EnregistrementOriginal> placeholders() {
        return List.of(originalDao.findById(idPlaceholder).orElseThrow());
    }

    /// Un brut sur disque, et **sa** séquence en base, rattachée au placeholder comme après une
    /// reconstruction.
    private BrutRebranche brutAvecSaSequence(String nomBrut) throws IOException {
        return brutDeTaille(nomBrut, TAILLE_BRUT);
    }

    private BrutRebranche brutDeTaille(String nomBrut, int octets) throws IOException {
        Files.createDirectories(bruts);
        Path source = Files.write(bruts.resolve(nomBrut), new byte[octets]);
        SequenceDEcoute sequence = sequenceDao.insert(new SequenceDEcoute(
                null,
                nomBrut.replace(".wav", "_000.wav"),
                idPlaceholder,
                0,
                0.0,
                5.0,
                dossier.resolve("transformes").resolve(nomBrut).toString(),
                false,
                session.id(),
                null,
                null,
                null));
        return new BrutRebranche(new BrutInventorie(source, nomBrut, 2048L), List.of(sequence), "empreinte-" + nomBrut);
    }

    /// `SequenceDao` qui lâche au n-ième rattachement, pour éprouver le rollback.
    ///
    /// Il compte les **deux** variantes, transactionnelle ou non, et lâche sur l'une comme sur l'autre. Sans
    /// quoi le garde-fou ne verrait rien d'une adoption qui repasserait aux écritures auto-commitées : la
    /// panne ne se déclencherait plus, et le test échouerait pour la mauvaise raison.
    private static final class SequenceDaoQuiLache extends SequenceDao {

        private final int rangDeLaPanne;
        private int rattachements;

        private SequenceDaoQuiLache(SourceDeDonnees source, int rangDeLaPanne) {
            super(source);
            this.rangDeLaPanne = rangDeLaPanne;
        }

        @Override
        public void majOriginal(Connection connexion, long idSequence, long idEnregistrementOriginal)
                throws SQLException {
            if (lachePeutEtre()) {
                throw new SQLException("Panne simulée au rattachement " + rattachements);
            }
            super.majOriginal(connexion, idSequence, idEnregistrementOriginal);
        }

        @Override
        public void majOriginal(long idSequence, long idEnregistrementOriginal) {
            if (lachePeutEtre()) {
                throw new DataAccessException("Panne simulée au rattachement " + rattachements, null);
            }
            super.majOriginal(idSequence, idEnregistrementOriginal);
        }

        private boolean lachePeutEtre() {
            rattachements++;
            return rattachements == rangDeLaPanne;
        }
    }
}
