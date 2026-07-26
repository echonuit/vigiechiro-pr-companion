package fr.univ_amu.iut.passage.model;

import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.commun.model.Prefixe;
import fr.univ_amu.iut.commun.model.PresenceFichiers;
import fr.univ_amu.iut.commun.model.Protocole;
import fr.univ_amu.iut.commun.model.StatutWorkflow;
import fr.univ_amu.iut.commun.model.Utilisateur;
import fr.univ_amu.iut.commun.model.Verdict;
import fr.univ_amu.iut.commun.model.Workspace;
import fr.univ_amu.iut.commun.model.dao.UtilisateurDao;
import fr.univ_amu.iut.commun.persistence.MigrationSchema;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
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
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/// Disponibilité de l'audio local sur une base SQLite jetable et de **vrais fichiers** sous
/// `@TempDir` (même patron que l'audit de cohérence) : les trois états observés, le décompte, le
/// cache et son invalidation, et la garantie de coût (un balayage par dossier, pas un accès par
/// séquence).
class ServiceDisponibiliteAudioTest {

    private static final String ID_USER = "u-1";
    private static final String SERIE = "1925492";
    private static final Prefixe PREFIXE = new Prefixe("040962", 2026, 1, "A1");
    private static final String NOM_ORIGINAL = PREFIXE.nommerOriginal("PaRecPR" + SERIE + "_20260620_213000.wav");
    private static final int NB_SEQUENCES = 3;

    @TempDir
    Path dossier;

    private Path transformes;
    private SessionDao sessionDao;
    private SequenceDao sequenceDao;
    private PassageDao passageDao;
    private EnregistrementOriginalDao originalDao;
    private ServiceDisponibiliteAudio service;
    private Long idPoint;

    @BeforeEach
    void preparer() {
        SourceDeDonnees source = new SourceDeDonnees(new Workspace(dossier));
        new MigrationSchema(source).migrer();
        new UtilisateurDao(source).insert(new Utilisateur(ID_USER, "Testeur"));
        Site site = new SiteDao(source)
                .insert(new Site(null, "040962", "Étang", Protocole.STANDARD, null, "2026-05-01", ID_USER));
        idPoint = new PointDao(source)
                .insert(new PointDEcoute(null, "A1", null, null, null, site.id()))
                .id();
        new EnregistreurDao(source).insert(new Enregistreur(SERIE, "V1.01", null));

        passageDao = new PassageDao(source);
        sessionDao = new SessionDao(source);
        originalDao = new EnregistrementOriginalDao(source);
        sequenceDao = new SequenceDao(source);
        transformes = dossier.resolve(PREFIXE.nomDossierSession()).resolve("transformes");
        service = new ServiceDisponibiliteAudio(sessionDao, sequenceDao, new Workspace(dossier));
    }

    @Test
    @DisplayName("Toutes les séquences sur disque : COMPLETE, décompte n/n")
    void toutes_presentes_complete() throws IOException {
        Long idPassage = creerPassageAvecSequences();

        assertThat(service.decompte(idPassage)).isEqualTo(new DecompteAudio(NB_SEQUENCES, NB_SEQUENCES));
        assertThat(service.disponibilite(idPassage)).isEqualTo(DisponibiliteAudio.COMPLETE);
    }

    @Test
    @DisplayName("Aucune séquence sur disque : ABSENTE, décompte 0/n")
    void aucune_presente_absente() throws IOException {
        Long idPassage = creerPassageAvecSequences();
        supprimerSequences(NB_SEQUENCES);

        assertThat(service.decompte(idPassage)).isEqualTo(new DecompteAudio(0, NB_SEQUENCES));
        assertThat(service.disponibilite(idPassage)).isEqualTo(DisponibiliteAudio.ABSENTE);
    }

    @Test
    @DisplayName("Une partie des séquences sur disque : PARTIELLE, avec le bon décompte")
    void une_partie_partielle() throws IOException {
        Long idPassage = creerPassageAvecSequences();
        supprimerSequences(1);

        assertThat(service.decompte(idPassage)).isEqualTo(new DecompteAudio(NB_SEQUENCES - 1, NB_SEQUENCES));
        assertThat(service.disponibilite(idPassage)).isEqualTo(DisponibiliteAudio.PARTIELLE);
    }

    @Test
    @DisplayName("Passage jamais importé (aucune session) : ABSENTE, décompte 0/0")
    void passage_jamais_importe() {
        Long idPassage = creerPassage();

        assertThat(service.decompte(idPassage)).isEqualTo(new DecompteAudio(0, 0));
        assertThat(service.disponibilite(idPassage)).isEqualTo(DisponibiliteAudio.ABSENTE);
    }

    @Test
    @DisplayName("Cache : le décompte est servi tel quel jusqu'à invalider(idPassage)")
    void cache_puis_invalidation() throws IOException {
        Long idPassage = creerPassageAvecSequences();
        assertThat(service.disponibilite(idPassage)).isEqualTo(DisponibiliteAudio.COMPLETE);

        supprimerSequences(1);

        assertThat(service.disponibilite(idPassage))
                .as("décompte en cache, le disque a pourtant changé")
                .isEqualTo(DisponibiliteAudio.COMPLETE);
        service.invalider(idPassage);
        assertThat(service.decompte(idPassage)).isEqualTo(new DecompteAudio(NB_SEQUENCES - 1, NB_SEQUENCES));
    }

    @Test
    @DisplayName("invaliderTout : le cache entier est resynchronisé au prochain accès")
    void invalider_tout() throws IOException {
        Long idPassage = creerPassageAvecSequences();
        assertThat(service.disponibilite(idPassage)).isEqualTo(DisponibiliteAudio.COMPLETE);

        supprimerSequences(NB_SEQUENCES);
        service.invaliderTout();

        assertThat(service.disponibilite(idPassage)).isEqualTo(DisponibiliteAudio.ABSENTE);
    }

    @Test
    @DisplayName("Coût : un seul balayage de dossier pour toutes les séquences d'un passage")
    void un_seul_balayage_pour_le_passage() throws IOException {
        Long idPassage = creerPassageAvecSequences();
        AtomicInteger acces = new AtomicInteger();
        ServiceDisponibiliteAudio compte = new ServiceDisponibiliteAudio(
                sessionDao, sequenceDao, new PresenceFichiers(new Workspace(dossier), dossierBalaye -> {
                    acces.incrementAndGet();
                    return nomsReels(dossierBalaye);
                }));

        DecompteAudio decompte = compte.decompte(idPassage);

        assertThat(decompte).isEqualTo(new DecompteAudio(NB_SEQUENCES, NB_SEQUENCES));
        assertThat(acces)
                .as("accès disque pour " + NB_SEQUENCES + " séquences d'un même dossier")
                .hasValue(1);
    }

    // --- Fixture ---------------------------------------------------------------------------------

    private Long creerPassage() {
        return passageDao
                .insert(new Passage(
                        null,
                        1,
                        2026,
                        "2026-06-20",
                        "21:30:00",
                        "05:15:00",
                        null,
                        StatutWorkflow.VERIFIE,
                        Verdict.OK,
                        null,
                        null,
                        null,
                        idPoint,
                        SERIE,
                        null))
                .id();
    }

    /// Passage complet : session, original et [#NB_SEQUENCES] séquences dont les fichiers existent
    /// sous `transformes/`.
    private Long creerPassageAvecSequences() throws IOException {
        Long idPassage = creerPassage();
        Path racineSession = dossier.resolve(PREFIXE.nomDossierSession());
        Long idSession = sessionDao
                .insert(new SessionDEnregistrement(null, racineSession.toString(), 4096L, 4096L, idPassage))
                .id();
        Files.createDirectories(transformes);
        Long idOriginal = originalDao
                .insert(new EnregistrementOriginal(
                        null,
                        NOM_ORIGINAL,
                        racineSession.resolve("bruts").resolve(NOM_ORIGINAL).toString(),
                        15.0,
                        384_000,
                        null,
                        idSession))
                .id();
        for (int index = 0; index < NB_SEQUENCES; index++) {
            String nomSequence = PREFIXE.nommerSequence(NOM_ORIGINAL, index);
            Path sequence = Files.write(transformes.resolve(nomSequence), new byte[16]);
            sequenceDao.insert(new SequenceDEcoute(
                    null, nomSequence, idOriginal, index, index * 5.0, 5.0, sequence.toString(), true, idSession));
        }
        return idPassage;
    }

    private void supprimerSequences(int nombre) throws IOException {
        for (int index = 0; index < nombre; index++) {
            Files.delete(transformes.resolve(PREFIXE.nommerSequence(NOM_ORIGINAL, index)));
        }
    }

    /// Balayage réel réutilisé par le test à compteur (même contrat que le balayeur par défaut).
    private static Set<String> nomsReels(Path dossier) {
        if (!Files.isDirectory(dossier)) {
            return Set.of();
        }
        try (Stream<Path> enfants = Files.list(dossier)) {
            return enfants.map(e -> e.getFileName().toString()).collect(java.util.stream.Collectors.toSet());
        } catch (IOException e) {
            return Set.of();
        }
    }
}
