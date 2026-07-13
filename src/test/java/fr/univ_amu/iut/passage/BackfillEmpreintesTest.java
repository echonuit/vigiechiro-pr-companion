package fr.univ_amu.iut.passage;

import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.commun.model.Empreintes;
import fr.univ_amu.iut.commun.model.Protocole;
import fr.univ_amu.iut.commun.model.StatutWorkflow;
import fr.univ_amu.iut.commun.model.Utilisateur;
import fr.univ_amu.iut.commun.model.Workspace;
import fr.univ_amu.iut.commun.model.dao.UtilisateurDao;
import fr.univ_amu.iut.commun.persistence.MigrationSchema;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.passage.model.BackfillEmpreintes;
import fr.univ_amu.iut.passage.model.EnregistrementOriginal;
import fr.univ_amu.iut.passage.model.Enregistreur;
import fr.univ_amu.iut.passage.model.Passage;
import fr.univ_amu.iut.passage.model.SequenceDEcoute;
import fr.univ_amu.iut.passage.model.SessionDEnregistrement;
import fr.univ_amu.iut.passage.model.dao.EnregistrementOriginalDao;
import fr.univ_amu.iut.passage.model.dao.EnregistreurDao;
import fr.univ_amu.iut.passage.model.dao.PassageDao;
import fr.univ_amu.iut.passage.model.dao.SequenceDao;
import fr.univ_amu.iut.passage.model.dao.SessionDao;
import fr.univ_amu.iut.sites.model.PointDEcoute;
import fr.univ_amu.iut.sites.model.Site;
import fr.univ_amu.iut.sites.model.dao.PointDao;
import fr.univ_amu.iut.sites.model.dao.SiteDao;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/// Rétro-remplissage des preuves d'identité (#1299) sur une base SQLite jetable et de vrais
/// fichiers sous `@TempDir` : lignes remplies avec les bonnes valeurs, fichiers absents laissés
/// explicitement sans empreinte, idempotence, et ciblage par session.
class BackfillEmpreintesTest {

    private static final String ID_USER = "u-1";
    private static final String SERIE = "1925492";

    @TempDir
    Path dossier;

    private PassageDao passageDao;
    private SessionDao sessionDao;
    private EnregistrementOriginalDao originalDao;
    private SequenceDao sequenceDao;
    private BackfillEmpreintes backfill;
    private Long idPoint;

    @BeforeEach
    void preparer() {
        SourceDeDonnees source = new SourceDeDonnees(new Workspace(dossier));
        new MigrationSchema(source).migrer();
        new UtilisateurDao(source).insert(new Utilisateur(ID_USER, "Testeur"));
        Site site = new SiteDao(source)
                .insert(new Site(null, "040962", null, Protocole.STANDARD, null, "2026-05-01", ID_USER));
        idPoint = new PointDao(source)
                .insert(new PointDEcoute(null, "A1", null, null, null, site.id()))
                .id();
        new EnregistreurDao(source).insert(new Enregistreur(SERIE, null, null));
        passageDao = new PassageDao(source);
        sessionDao = new SessionDao(source);
        originalDao = new EnregistrementOriginalDao(source);
        sequenceDao = new SequenceDao(source);
        backfill = new BackfillEmpreintes(sequenceDao, originalDao);
    }

    @Test
    @DisplayName("Fichiers présents : taille et empreinte posées, recalculables depuis le disque")
    void fichiers_presents_remplis() throws IOException {
        Long idSession = creerSession(1);
        Path fichier = ecrireFichier("seq_000.wav", 100_000);
        long idSequence = insererSequence(idSession, "seq_000.wav", fichier).id();
        Path brut = ecrireFichier("orig.wav", 50_000);
        long idOriginal = insererOriginal(idSession, brut).id();

        BackfillEmpreintes.Bilan bilan = backfill.remplirTout();

        assertThat(bilan.sequencesRemplies()).isEqualTo(1);
        assertThat(bilan.originauxRemplis()).isEqualTo(1);
        SequenceDEcoute sequence = sequenceDao.findById(idSequence).orElseThrow();
        assertThat(sequence.tailleOctets()).isEqualTo(100_000L);
        assertThat(sequence.empreinte()).isEqualTo(Empreintes.empreinteCourte(fichier));
        assertThat(originalDao.findById(idOriginal).orElseThrow().tailleOctets())
                .isEqualTo(50_000L);
    }

    @Test
    @DisplayName("Fichier absent : la ligne reste explicitement sans empreinte, comptée ignorée")
    void fichier_absent_reste_sans_empreinte() {
        Long idSession = creerSession(1);
        long idSequence = insererSequence(
                        idSession, "partie.wav", dossier.resolve("transformes").resolve("partie.wav"))
                .id();

        BackfillEmpreintes.Bilan bilan = backfill.remplirTout();

        assertThat(bilan.sequencesRemplies()).isZero();
        assertThat(bilan.sequencesIgnorees()).isEqualTo(1);
        SequenceDEcoute sequence = sequenceDao.findById(idSequence).orElseThrow();
        assertThat(sequence.tailleOctets()).isNull();
        assertThat(sequence.empreinte()).isNull();
    }

    @Test
    @DisplayName("Idempotent : une seconde passe ne retouche aucune ligne déjà renseignée")
    void idempotent() throws IOException {
        Long idSession = creerSession(1);
        insererSequence(idSession, "seq_000.wav", ecrireFichier("seq_000.wav", 1_000));
        assertThat(backfill.remplirTout().sequencesRemplies()).isEqualTo(1);

        BackfillEmpreintes.Bilan secondePasse = backfill.remplirTout();

        assertThat(secondePasse.sequencesRemplies()).isZero();
        assertThat(secondePasse.sequencesIgnorees()).isZero();
    }

    @Test
    @DisplayName("remplirSession ne touche que les séquences de la session visée (pré-archivage #1300)")
    void remplir_session_cible_la_session() throws IOException {
        Long idSession1 = creerSession(1);
        Long idSession2 = creerSession(2);
        long idDansSession1 = insererSequence(idSession1, "s1.wav", ecrireFichier("s1.wav", 1_000))
                .id();
        long idDansSession2 = insererSequence(idSession2, "s2.wav", ecrireFichier("s2.wav", 1_000))
                .id();

        BackfillEmpreintes.Bilan bilan = backfill.remplirSession(idSession1);

        assertThat(bilan.sequencesRemplies()).isEqualTo(1);
        assertThat(sequenceDao.findById(idDansSession1).orElseThrow().empreinte())
                .isNotNull();
        assertThat(sequenceDao.findById(idDansSession2).orElseThrow().empreinte())
                .as("la séquence de l'autre session reste sans empreinte")
                .isNull();
    }

    // --- Fixture ---------------------------------------------------------------------------------

    private Long creerSession(int numeroPassage) {
        Long idPassage = passageDao
                .insert(new Passage(
                        null,
                        numeroPassage,
                        2026,
                        "2026-06-20",
                        "21:30:00",
                        "05:15:00",
                        null,
                        StatutWorkflow.IMPORTE,
                        null,
                        null,
                        null,
                        null,
                        idPoint,
                        SERIE))
                .id();
        return sessionDao
                .insert(new SessionDEnregistrement(
                        null, dossier.resolve("session" + numeroPassage) + "", null, null, idPassage))
                .id();
    }

    private Path ecrireFichier(String nom, int taille) throws IOException {
        Path transformes = Files.createDirectories(dossier.resolve("transformes"));
        byte[] contenu = new byte[taille];
        for (int i = 0; i < taille; i++) {
            contenu[i] = (byte) (i * 31 + 17);
        }
        return Files.write(transformes.resolve(nom), contenu);
    }

    private SequenceDEcoute insererSequence(Long idSession, String nom, Path fichier) {
        Long idOriginal = insererOriginal(
                        idSession, dossier.resolve("bruts-absents").resolve(nom))
                .id();
        return sequenceDao.insert(
                new SequenceDEcoute(null, nom, idOriginal, 0, 0.0, 5.0, fichier.toString(), false, idSession));
    }

    private EnregistrementOriginal insererOriginal(Long idSession, Path fichier) {
        return originalDao.insert(new EnregistrementOriginal(
                null, fichier.getFileName().toString(), fichier.toString(), null, null, null, idSession));
    }
}
