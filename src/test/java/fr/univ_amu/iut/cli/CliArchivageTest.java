package fr.univ_amu.iut.cli;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.Injector;
import fr.univ_amu.iut.commun.model.Empreintes;
import fr.univ_amu.iut.commun.model.FichierWav;
import fr.univ_amu.iut.commun.model.Prefixe;
import fr.univ_amu.iut.commun.model.Protocole;
import fr.univ_amu.iut.commun.model.StatutWorkflow;
import fr.univ_amu.iut.commun.model.Utilisateur;
import fr.univ_amu.iut.commun.model.Verdict;
import fr.univ_amu.iut.commun.model.dao.UtilisateurDao;
import fr.univ_amu.iut.commun.persistence.MigrationSchema;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.importation.model.SequenceProduite;
import fr.univ_amu.iut.importation.model.TransformationAudio;
import fr.univ_amu.iut.importation.model.TransformationOriginal;
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
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/// Parité CLI ↔ IHM du chantier « passage archivé » (#1304, EPIC #1297) : les commandes `archiver` et
/// `reactiver` exposent les mêmes capacités que M-Passage, avec les mêmes règles (archivage réservé aux
/// passages déposés, réactivation **vérifiée**) et des codes de sortie exploitables en script.
///
/// Bootstrap des autres tests CLI : workspace surchargé vers un `@TempDir`, graphe semé via les DAO de
/// l'injecteur applicatif. Les séquences sont de **vrais WAV** : la réactivation les vérifie réellement.
class CliArchivageTest {

    private static final String SERIE = "1925492";
    private static final Prefixe PREFIXE = new Prefixe("040962", 2026, 1, "A1");
    private static final String ORIGINAL = "Car040962-2026-Pass1-A1-PaRec_20260620_213000.wav";
    private static final String SEQ = "Car040962-2026-Pass1-A1-PaRec_20260620_213000_000.wav";
    private static final int FREQUENCE_SORTIE_HZ = 38_400;
    private static final int ECHANTILLONS = 19_200; // 0,5 s réelle (Fe 384 kHz), 0,5 s en-tête ×10

    /// Passage reconstruit (EPIC #1653) : brut de carte SD (non préfixé) + Fe du log, en-tête à Fe/10.
    private static final String NOM_SD_BRUT = "PaRec_20260620_213000.wav";
    private static final String NOM_R6_BRUT = "Car040962-2026-Pass1-A1-PaRec_20260620_213000.wav";
    private static final int FREQUENCE_ACQUISITION_HZ = 40_000;
    private static final double DUREE_BRUT_S = 8.0; // 8 s réelles → 2 tranches (5 s + 3 s)

    @TempDir
    Path workspace;

    private Injector injecteur;
    private Cli cli;
    private ByteArrayOutputStream tamponSortie;
    private PrintStream sortie;
    private PrintStream erreur;
    private SourceDeDonnees source;
    private Path transformes;
    private Path sauvegarde;
    private Long idPassage;

    @BeforeEach
    void preparer() throws IOException {
        System.setProperty("vigiechiro.workspace", workspace.toString());
        injecteur = Cli.injecteurApplicatif();
        cli = new Cli(injecteur);
        injecteur.getInstance(MigrationSchema.class).migrer();
        tamponSortie = new ByteArrayOutputStream();
        sortie = new PrintStream(tamponSortie, true, StandardCharsets.UTF_8);
        erreur = new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8);
        source = injecteur.getInstance(SourceDeDonnees.class);
        transformes = Files.createDirectories(
                workspace.resolve(PREFIXE.nomDossierSession()).resolve("transformes"));
        sauvegarde = Files.createDirectories(workspace.resolve("sauvegarde"));
    }

    @AfterEach
    void nettoyer() {
        System.clearProperty("vigiechiro.workspace");
    }

    private String texte() {
        return tamponSortie.toString(StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("archiver sans --confirmer : annonce l'espace récupérable, ne supprime rien, sort en 2")
    void archiver_sans_confirmer_annonce_seulement() throws IOException {
        semerPassage(StatutWorkflow.DEPOSE);

        int code = cli.executer(new String[] {"archiver", "--passage", idPassage.toString()}, sortie, erreur);

        assertThat(code).isEqualTo(Cli.CODE_ERREUR_ARGUMENTS);
        assertThat(texte()).contains("seraient libérés").contains("--confirmer");
        assertThat(transformes.resolve(SEQ)).as("rien n'a été supprimé").exists();
    }

    @Test
    @DisplayName("archiver --confirmer : purge l'audio, capture les empreintes, sort en 0")
    void archiver_confirme_purge() throws IOException {
        semerPassage(StatutWorkflow.DEPOSE);

        int code = cli.executer(
                new String[] {"archiver", "--passage", idPassage.toString(), "--confirmer"}, sortie, erreur);

        assertThat(code).isZero();
        assertThat(texte()).contains("archivé").contains("empreinte(s) capturée(s)");
        assertThat(transformes.resolve(SEQ)).doesNotExist();
    }

    @Test
    @DisplayName("archiver un passage non déposé : refusé (code 1), rien n'est supprimé")
    void archiver_passage_non_depose_refuse() throws IOException {
        semerPassage(StatutWorkflow.VERIFIE);

        int code = cli.executer(
                new String[] {"archiver", "--passage", idPassage.toString(), "--confirmer"}, sortie, erreur);

        assertThat(code).isEqualTo(Cli.CODE_ERREUR_EXECUTION);
        assertThat(transformes.resolve(SEQ)).exists();
    }

    @Test
    @DisplayName("reactiver : les bons fichiers rebranchent, l'audio redevient COMPLETE, sortie 0")
    void reactiver_rebranche_les_bons_fichiers() throws IOException {
        semerPassage(StatutWorkflow.DEPOSE);
        Files.copy(transformes.resolve(SEQ), sauvegarde.resolve(SEQ));
        cli.executer(new String[] {"archiver", "--passage", idPassage.toString(), "--confirmer"}, sortie, erreur);
        tamponSortie.reset();

        int code = cli.executer(
                new String[] {"reactiver", "--passage", idPassage.toString(), "--source", sauvegarde.toString()},
                sortie,
                erreur);

        assertThat(code).isZero();
        assertThat(texte()).contains("1 séquence(s) réactivée(s)").contains("COMPLETE");
        assertThat(transformes.resolve(SEQ)).exists();
    }

    @Test
    @DisplayName("reactiver avec un fichier homonyme d'un autre audio : refusé, motivé, sortie 1")
    void reactiver_homonyme_different_refuse() throws IOException {
        semerPassage(StatutWorkflow.DEPOSE);
        cli.executer(new String[] {"archiver", "--passage", idPassage.toString(), "--confirmer"}, sortie, erreur);
        tamponSortie.reset();
        ecrireWav(sauvegarde.resolve(SEQ), 99); // même nom, autre contenu

        int code = cli.executer(
                new String[] {
                    "reactiver", "--passage", idPassage.toString(), "--source", sauvegarde.toString(), "--json"
                },
                sortie,
                erreur);

        assertThat(code).isEqualTo(Cli.CODE_ERREUR_EXECUTION);
        assertThat(texte())
                .contains("\"reactivees\": 0")
                .contains("\"divergentes\": 1")
                .contains("\"disponibiliteAudio\": \"ABSENTE\"")
                .contains("\"motif\"");
        assertThat(transformes.resolve(SEQ))
                .as("aucun fichier douteux n'est rebranché en silence")
                .doesNotExist();
    }

    @Test
    @DisplayName("reactiver hydrate un passage reconstruit depuis ses bruts + log : séquences régénérées, sortie 0")
    void reactiver_hydrate_un_passage_reconstruit() throws IOException {
        Path carteSd = semerReconstruit();

        int code = cli.executer(
                new String[] {"reactiver", "--passage", idPassage.toString(), "--source", carteSd.toString()},
                sortie,
                erreur);

        assertThat(code).as("l'audio est intégralement revenu").isZero();
        assertThat(texte())
                .contains("régénérées")
                .contains("séquence(s) réactivée(s)")
                .contains("COMPLETE");
        // #1651 : le placeholder a laissé place aux vrais originaux (avec fréquence), déclarés purgés.
        SessionDEnregistrement session =
                new SessionDao(source).trouverParPassage(idPassage).orElseThrow();
        assertThat(new EnregistrementOriginalDao(source).findBySession(session.id()))
                .isNotEmpty()
                .allSatisfy(original ->
                        assertThat(original.frequenceEchantillonnageHz()).isNotNull());
        assertThat(session.originauxPurges()).isTrue();
    }

    @Test
    @DisplayName("statut-passage : la disponibilité de l'audio est affichée (texte et JSON)")
    void statut_passage_porte_la_disponibilite() throws IOException {
        semerPassage(StatutWorkflow.DEPOSE);
        cli.executer(new String[] {"archiver", "--passage", idPassage.toString(), "--confirmer"}, sortie, erreur);
        tamponSortie.reset();

        cli.executer(new String[] {"statut-passage", "--passage", idPassage.toString()}, sortie, erreur);
        assertThat(texte()).contains("Audio").contains("ABSENTE").contains("0/1");

        tamponSortie.reset();
        cli.executer(new String[] {"statut-passage", "--passage", idPassage.toString(), "--json"}, sortie, erreur);
        assertThat(texte()).contains("\"disponibiliteAudio\": \"ABSENTE\"").contains("\"sequencesPresentes\": 0");
    }

    @Test
    @DisplayName("audit-coherence : un passage archivé est informatif (code 0) et porte le décompte")
    void audit_coherence_passage_archive_informatif() throws IOException {
        semerPassage(StatutWorkflow.DEPOSE);
        cli.executer(new String[] {"archiver", "--passage", idPassage.toString(), "--confirmer"}, sortie, erreur);
        tamponSortie.reset();

        int code = cli.executer(new String[] {"audit-coherence", "--passage", idPassage.toString()}, sortie, erreur);

        assertThat(code).as("archivé volontairement n'est pas corrompu").isZero();
        assertThat(texte()).contains("AUDIO_ARCHIVE").contains("0/1 séquence(s)");
    }

    // --- Fixture ---------------------------------------------------------------------------------

    /// Sème un passage au statut donné, avec une session, un original et **une séquence** dont le WAV
    /// existe sous `transformes/` (taille et empreinte posées, comme un import récent).
    private void semerPassage(StatutWorkflow statut) throws IOException {
        new UtilisateurDao(source).insert(new Utilisateur("u-1", "Testeur"));
        Site site = new SiteDao(source)
                .insert(new Site(null, "040962", null, Protocole.STANDARD, null, "2026-05-01", "u-1"));
        Long idPoint = new PointDao(source)
                .insert(new PointDEcoute(null, "A1", null, null, null, site.id()))
                .id();
        new EnregistreurDao(source).insert(new Enregistreur(SERIE, null, null));
        idPassage = new PassageDao(source)
                .insert(new Passage(
                        null,
                        1,
                        2026,
                        "2026-06-20",
                        "21:30:00",
                        "05:15:00",
                        null,
                        statut,
                        Verdict.OK,
                        null,
                        null,
                        null,
                        idPoint,
                        SERIE))
                .id();
        Path racineSession = workspace.resolve(PREFIXE.nomDossierSession());
        Long idSession = new SessionDao(source)
                .insert(new SessionDEnregistrement(null, racineSession.toString(), 0L, 4096L, idPassage))
                .id();
        Long idOriginal = new EnregistrementOriginalDao(source)
                .insert(new EnregistrementOriginal(
                        null,
                        ORIGINAL,
                        racineSession.resolve("bruts").resolve(ORIGINAL).toString(),
                        5.0,
                        384_000,
                        null,
                        idSession))
                .id();
        Path fichier = transformes.resolve(SEQ);
        ecrireWav(fichier, 7);
        new SequenceDao(source)
                .insert(new SequenceDEcoute(
                        null,
                        SEQ,
                        idOriginal,
                        0,
                        0.0,
                        0.5,
                        fichier.toString(),
                        false,
                        idSession,
                        null,
                        Files.size(fichier),
                        Empreintes.empreinteCourte(fichier)));
    }

    /// WAV synthétique écrit comme le pipeline (en-tête à Fe/10) ; la graine détermine le contenu.
    private void ecrireWav(Path fichier, int graine) throws IOException {
        byte[] pcm = new byte[ECHANTILLONS * 2];
        int valeur = graine;
        for (int n = 0; n < ECHANTILLONS; n++) {
            valeur = valeur * 31 + 17;
            short amplitude = (short) (valeur % 8000);
            pcm[2 * n] = (byte) (amplitude & 0xFF);
            pcm[2 * n + 1] = (byte) ((amplitude >> 8) & 0xFF);
        }
        FichierWav.ecrire(fichier, 1, FREQUENCE_SORTIE_HZ, 16, pcm, 0, pcm.length);
    }

    /// Sème un passage **reconstruit** (EPIC #1653) : un placeholder à la place des originaux, des
    /// séquences nommées comme les tranches d'un brut (produites par la vraie transformation, puis
    /// effacées : passage archivé). Dépose le brut (nom de carte SD) et son log dans un dossier « carte SD »
    /// et renvoie ce dossier, cible du `--source` de `reactiver`.
    private Path semerReconstruit() throws IOException {
        new UtilisateurDao(source).insert(new Utilisateur("u-1", "Testeur"));
        Site site = new SiteDao(source)
                .insert(new Site(null, "040962", null, Protocole.STANDARD, null, "2026-05-01", "u-1"));
        Long idPoint = new PointDao(source)
                .insert(new PointDEcoute(null, "A1", null, null, null, site.id()))
                .id();
        new EnregistreurDao(source).insert(new Enregistreur(SERIE, null, null));
        idPassage = new PassageDao(source)
                .insert(new Passage(
                        null,
                        1,
                        2026,
                        "2026-06-20",
                        "21:30:00",
                        "05:15:00",
                        null,
                        StatutWorkflow.DEPOSE,
                        null,
                        null,
                        null,
                        null,
                        idPoint,
                        SERIE))
                .id();
        Path racineSession = workspace.resolve(PREFIXE.nomDossierSession());
        SessionDao sessionDao = new SessionDao(source);
        Long idSession = sessionDao
                .insert(new SessionDEnregistrement(null, racineSession.toString(), 0L, 0L, idPassage))
                .id();
        EnregistrementOriginalDao originalDao = new EnregistrementOriginalDao(source);
        Long idPlaceholder = originalDao
                .insert(new EnregistrementOriginal(
                        null, PREFIXE.prefixeFichier() + "reconstruit.wav", "", null, null, null, idSession))
                .id();

        Path brut = workspace.resolve("origine").resolve(NOM_SD_BRUT);
        ecrireBrut(brut);
        TransformationOriginal transformation = new TransformationAudio()
                .transformer(brut, NOM_R6_BRUT, transformes, PREFIXE, FREQUENCE_ACQUISITION_HZ);

        SequenceDao sequenceDao = new SequenceDao(source);
        int index = 0;
        for (SequenceProduite produite : transformation.sequences()) {
            sequenceDao.insert(new SequenceDEcoute(
                    null,
                    produite.nomFichier(),
                    idPlaceholder,
                    index,
                    null,
                    null,
                    transformes.resolve(produite.nomFichier()).toString(),
                    false,
                    idSession,
                    null,
                    null,
                    null));
            Files.delete(produite.chemin());
            index++;
        }
        sessionDao.marquerArchivee(idSession, LocalDateTime.of(2026, 7, 16, 18, 30));

        Path carteSd = Files.createDirectories(workspace.resolve("carte-sd"));
        Files.copy(brut, carteSd.resolve(NOM_SD_BRUT));
        ecrireLog(carteSd.resolve("LogPR" + SERIE + ".txt"), FREQUENCE_ACQUISITION_HZ / 1000);
        Files.delete(brut);
        return carteSd;
    }

    /// Brut synthétique de [#DUREE_BRUT_S] secondes réelles, en-tête à Fe/10 (comme l'écrit l'enregistreur).
    private void ecrireBrut(Path fichier) throws IOException {
        int echantillons = (int) Math.round(DUREE_BRUT_S * FREQUENCE_ACQUISITION_HZ);
        byte[] pcm = new byte[echantillons * 2];
        int valeur = 42;
        for (int n = 0; n < echantillons; n++) {
            valeur = valeur * 31 + 17;
            short amplitude = (short) (valeur % 8000);
            pcm[2 * n] = (byte) (amplitude & 0xFF);
            pcm[2 * n + 1] = (byte) ((amplitude >> 8) & 0xFF);
        }
        Files.createDirectories(fichier.getParent());
        FichierWav.ecrire(fichier, 1, FREQUENCE_ACQUISITION_HZ / 10, 16, pcm, 0, pcm.length);
    }

    /// Journal minimal de l'enregistreur : une ligne « Paramètres » porte la fréquence `Fe…kHz`.
    private void ecrireLog(Path fichier, int frequenceKhz) throws IOException {
        Files.write(
                fichier,
                List.of(
                        "20/06/26 - 21:30:00 PR" + SERIE + " Démarrage v1.0",
                        "20/06/26 - 21:30:01 PR" + SERIE + " Paramètres : Acquisi. 21:30-05:15, Fe" + frequenceKhz
                                + "kHz, S. R. Med, Bd. Freq. 8-120kHz"),
                StandardCharsets.UTF_8);
    }
}
