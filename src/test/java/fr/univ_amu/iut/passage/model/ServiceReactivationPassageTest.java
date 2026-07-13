package fr.univ_amu.iut.passage.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import fr.univ_amu.iut.commun.model.Empreintes;
import fr.univ_amu.iut.commun.model.FichierWav;
import fr.univ_amu.iut.commun.model.Prefixe;
import fr.univ_amu.iut.commun.model.Progression;
import fr.univ_amu.iut.commun.model.Protocole;
import fr.univ_amu.iut.commun.model.RegleMetierException;
import fr.univ_amu.iut.commun.model.StatutWorkflow;
import fr.univ_amu.iut.commun.model.Utilisateur;
import fr.univ_amu.iut.commun.model.Verdict;
import fr.univ_amu.iut.commun.model.Workspace;
import fr.univ_amu.iut.commun.model.dao.UtilisateurDao;
import fr.univ_amu.iut.commun.persistence.MigrationSchema;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.passage.model.VerdictIdentite.NiveauConfiance;
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
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/// Réactivation d'un passage archivé (#1302) sur une base SQLite jetable et de **vrais WAV**
/// synthétiques sous `@TempDir` : les bons fichiers rebranchent, les homonymes de contenu différent
/// sont refusés et motivés, un réimport partiel laisse le passage en `PARTIELLE`, et rejouer
/// l'opération est sans effet.
class ServiceReactivationPassageTest {

    private static final String ID_USER = "u-1";
    private static final String SERIE = "1925492";
    private static final Prefixe PREFIXE = new Prefixe("040962", 2026, 1, "A1");
    private static final String SEQ_1 = "Car040962-2026-Pass1-A1-PaRec_20260620_213000_000.wav";
    private static final String SEQ_2 = "Car040962-2026-Pass1-A1-PaRec_20260620_213005_000.wav";
    private static final double FREQUENCE_REELLE_HZ = 384_000;
    private static final double DUREE_REELLE_S = 0.5;

    @TempDir
    Path dossier;

    private Path transformes;
    private Path sauvegarde;
    private PassageDao passageDao;
    private SessionDao sessionDao;
    private SequenceDao sequenceDao;
    private EnregistrementOriginalDao originalDao;
    private ServiceDisponibiliteAudio disponibilite;
    private ServiceReactivationPassage service;
    private Long idPoint;
    private Long idPassage;
    private Long idSession;

    @BeforeEach
    void preparer() throws IOException {
        SourceDeDonnees source = new SourceDeDonnees(new Workspace(dossier));
        new MigrationSchema(source).migrer();
        new UtilisateurDao(source).insert(new Utilisateur(ID_USER, "Testeur"));
        Site site = new SiteDao(source)
                .insert(new Site(null, "040962", "Étang", Protocole.STANDARD, null, "2026-05-01", ID_USER));
        idPoint = new PointDao(source)
                .insert(new PointDEcoute(null, "A1", null, null, null, site.id()))
                .id();
        new EnregistreurDao(source).insert(new Enregistreur(SERIE, null, null));
        passageDao = new PassageDao(source);
        sessionDao = new SessionDao(source);
        sequenceDao = new SequenceDao(source);
        originalDao = new EnregistrementOriginalDao(source);
        transformes = Files.createDirectories(
                dossier.resolve(PREFIXE.nomDossierSession()).resolve("transformes"));
        sauvegarde = Files.createDirectories(dossier.resolve("sauvegarde-utilisateur"));
        disponibilite = new ServiceDisponibiliteAudio(sessionDao, sequenceDao, new Workspace(dossier));
        service = new ServiceReactivationPassage(
                sessionDao,
                sequenceDao,
                new VerificationIdentiteAudio(),
                disponibilite,
                Optional.empty()); // pas de cris : cascade structurelle (injecteur partiel)
    }

    @Test
    @DisplayName("Les bons fichiers réimportés réactivent le passage : COMPLETE, marqueur d'archivage effacé")
    void bons_fichiers_reactivent() throws IOException {
        archiverAvecSauvegarde(true, true);

        RapportReactivation rapport = service.reactiver(idPassage, sauvegarde, progres -> {});

        assertThat(rapport.reactivees()).isEqualTo(2);
        assertThat(rapport.divergentes()).isZero();
        assertThat(rapport.manquantes()).isZero();
        assertThat(rapport.complete()).isTrue();
        assertThat(disponibilite.disponibilite(idPassage)).isEqualTo(DisponibiliteAudio.COMPLETE);
        assertThat(sessionDao.trouverParPassage(idPassage).orElseThrow().archivee())
                .as("l'audio est revenu : le passage n'est plus archivé")
                .isFalse();
        assertThat(sessionDao.trouverParPassage(idPassage).orElseThrow().volumeSequencesOctets())
                .as("la fiche du passage retrouve son volume")
                .isPositive();
    }

    @Test
    @DisplayName("Fichiers homonymes de contenu différent : rien n'est rebranché, chaque écart est motivé")
    void homonymes_differents_refuses() throws IOException {
        archiverAvecSauvegarde(true, true);
        // L'utilisateur désigne une AUTRE nuit : mêmes noms, autre audio (le piège que ferme #1299).
        ecrireWav(sauvegarde.resolve(SEQ_1), 13);
        ecrireWav(sauvegarde.resolve(SEQ_2), 13);

        RapportReactivation rapport = service.reactiver(idPassage, sauvegarde, progres -> {});

        assertThat(rapport.reactivees()).isZero();
        assertThat(rapport.divergentes()).isEqualTo(2);
        assertThat(rapport.ecarts())
                .allSatisfy(ecart -> assertThat(ecart.motif()).isNotBlank());
        assertThat(disponibilite.disponibilite(idPassage))
                .as("aucun fichier douteux n'est rebranché en silence")
                .isEqualTo(DisponibiliteAudio.ABSENTE);
        assertThat(sessionDao.trouverParPassage(idPassage).orElseThrow().archivee())
                .isTrue();
    }

    @Test
    @DisplayName("Réimport partiel : le passage passe en PARTIELLE, le marqueur d'archivage est conservé")
    void reimport_partiel_reste_partielle() throws IOException {
        archiverAvecSauvegarde(true, false); // une seule séquence dans la sauvegarde

        RapportReactivation rapport = service.reactiver(idPassage, sauvegarde, progres -> {});

        assertThat(rapport.reactivees()).isEqualTo(1);
        assertThat(rapport.manquantes()).isEqualTo(1);
        assertThat(rapport.decompte()).isEqualTo(new DecompteAudio(1, 2));
        assertThat(disponibilite.disponibilite(idPassage)).isEqualTo(DisponibiliteAudio.PARTIELLE);
        assertThat(sessionDao.trouverParPassage(idPassage).orElseThrow().archivee())
                .as("les absences restantes sont toujours expliquées par l'archivage")
                .isTrue();
    }

    @Test
    @DisplayName("Idempotent : rejouer la réactivation sur un passage déjà réactivé est sans effet")
    void idempotent() throws IOException {
        archiverAvecSauvegarde(true, true);
        service.reactiver(idPassage, sauvegarde, progres -> {});

        RapportReactivation second = service.reactiver(idPassage, sauvegarde, progres -> {});

        assertThat(second.dejaPresentes()).isEqualTo(2);
        assertThat(second.reactivees()).isZero();
        assertThat(second.divergentes()).isZero();
        assertThat(second.complete()).isTrue();
    }

    @Test
    @DisplayName("Sans empreinte (import ancien) : la cascade structurelle réactive, en confiance FORTE")
    void sans_empreinte_cascade_structurelle() throws IOException {
        archiverAvecSauvegarde(false, true); // séquences insérées sans taille ni empreinte

        RapportReactivation rapport = service.reactiver(idPassage, sauvegarde, progres -> {});

        assertThat(rapport.reactivees()).isEqualTo(2);
        assertThat(rapport.confianceMinimale())
                .as("sans empreinte, la preuve structurelle seule vaut FORTE (#1309)")
                .isEqualTo(NiveauConfiance.FORTE);
        assertThat(rapport.complete()).isTrue();
    }

    @Test
    @DisplayName("Avec empreinte : le rebranchement est en CERTITUDE, et la progression est notifiée")
    void avec_empreinte_certitude_et_progression() throws IOException {
        archiverAvecSauvegarde(true, true);
        List<Progression> avancement = new ArrayList<>();

        RapportReactivation rapport = service.reactiver(idPassage, sauvegarde, avancement::add);

        assertThat(rapport.confianceMinimale()).isEqualTo(NiveauConfiance.CERTITUDE);
        assertThat(avancement).hasSize(2);
        assertThat(avancement.get(avancement.size() - 1).fraction()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("#1309 : les cris attendus de chaque séquence sont demandés au port (vérification acoustique)")
    void cris_attendus_consultes() throws IOException {
        archiverAvecSauvegarde(false, true); // sans empreinte : la cascade descend jusqu'à l'acoustique
        List<Long> sequencesInterrogees = new ArrayList<>();
        ServiceReactivationPassage avecCris = new ServiceReactivationPassage(
                sessionDao, sequenceDao, new VerificationIdentiteAudio(), disponibilite, Optional.of(idSequence -> {
                    sequencesInterrogees.add(idSequence);
                    return List.of(); // aucune observation : rien à corrompre, structurelle seule
                }));

        avecCris.reactiver(idPassage, sauvegarde, progres -> {});

        assertThat(sequencesInterrogees)
                .as("une interrogation par séquence à rebrancher")
                .hasSize(2);
    }

    @Test
    @DisplayName("Dossier source introuvable : refus net, rien n'est touché")
    void dossier_introuvable_refuse() throws IOException {
        archiverAvecSauvegarde(true, true);

        assertThatThrownBy(() -> service.reactiver(idPassage, dossier.resolve("absent"), progres -> {}))
                .isInstanceOf(RegleMetierException.class)
                .hasMessageContaining("Dossier introuvable");
    }

    // --- Fixture ---------------------------------------------------------------------------------

    /// Sème un passage déposé avec deux séquences, écrit leurs WAV, en capture (ou non) l'identité,
    /// place une copie dans la « sauvegarde de l'utilisateur », puis **archive** (supprime les
    /// fichiers du workspace et pose le marqueur).
    ///
    /// @param avecEmpreinte pose taille + empreinte en base (import récent) ou non (import ancien)
    /// @param sauvegardeComplete copie les deux séquences dans la sauvegarde, ou seulement la première
    private void archiverAvecSauvegarde(boolean avecEmpreinte, boolean sauvegardeComplete) throws IOException {
        idPassage = passageDao
                .insert(new Passage(
                        null,
                        1,
                        2026,
                        "2026-06-20",
                        "21:30:00",
                        "05:15:00",
                        null,
                        StatutWorkflow.DEPOSE,
                        Verdict.OK,
                        null,
                        null,
                        null,
                        idPoint,
                        SERIE))
                .id();
        Path racineSession = dossier.resolve(PREFIXE.nomDossierSession());
        idSession = sessionDao
                .insert(new SessionDEnregistrement(null, racineSession.toString(), 0L, 0L, idPassage))
                .id();
        Long idOriginal = originalDao
                .insert(new EnregistrementOriginal(
                        null,
                        "PaRec_20260620_213000.wav",
                        racineSession
                                .resolve("bruts")
                                .resolve("PaRec_20260620_213000.wav")
                                .toString(),
                        5.0,
                        384_000,
                        null,
                        idSession))
                .id();

        int index = 0;
        for (String nom : List.of(SEQ_1, SEQ_2)) {
            Path fichier = transformes.resolve(nom);
            ecrireWav(fichier, 7 + index); // contenus distincts d'une séquence à l'autre
            if (sauvegardeComplete || index == 0) {
                Files.copy(fichier, sauvegarde.resolve(nom));
            }
            sequenceDao.insert(new SequenceDEcoute(
                    null,
                    nom,
                    idOriginal,
                    index,
                    index * 5.0,
                    DUREE_REELLE_S,
                    fichier.toString(),
                    false,
                    idSession,
                    null,
                    avecEmpreinte ? Files.size(fichier) : null,
                    avecEmpreinte ? Empreintes.empreinteCourte(fichier) : null));
            Files.delete(fichier); // archivage : l'audio quitte le workspace
            index++;
        }
        sessionDao.marquerArchivee(idSession, LocalDateTime.of(2026, 7, 13, 18, 30));
    }

    /// WAV synthétique écrit comme le pipeline (en-tête à Fe/10, durée réelle [#DUREE_REELLE_S]) ; la
    /// `graine` détermine le contenu : deux graines différentes donnent deux audios différents.
    private void ecrireWav(Path fichier, int graine) throws IOException {
        int echantillons = (int) Math.round(DUREE_REELLE_S * FREQUENCE_REELLE_HZ);
        byte[] pcm = new byte[echantillons * 2];
        int valeur = graine;
        for (int n = 0; n < echantillons; n++) {
            valeur = valeur * 31 + 17;
            short amplitude = (short) (valeur % 8000);
            pcm[2 * n] = (byte) (amplitude & 0xFF);
            pcm[2 * n + 1] = (byte) ((amplitude >> 8) & 0xFF);
        }
        FichierWav.ecrire(fichier, 1, (int) (FREQUENCE_REELLE_HZ / 10), 16, pcm, 0, pcm.length);
    }
}
