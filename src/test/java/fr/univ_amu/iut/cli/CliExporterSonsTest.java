package fr.univ_amu.iut.cli;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.Injector;
import fr.univ_amu.iut.commun.model.ModeValidation;
import fr.univ_amu.iut.commun.persistence.MigrationSchema;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.fixture.JeuDeDonneesPassage;
import fr.univ_amu.iut.fixture.SortieCapturee;
import fr.univ_amu.iut.passage.model.SequenceDEcoute;
import fr.univ_amu.iut.passage.model.dao.SequenceDao;
import fr.univ_amu.iut.validation.model.Observation;
import fr.univ_amu.iut.validation.model.dao.ObservationDao;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/// Test d'intégration de la commande `exporter-sons` (#2795) : base réelle, fichiers réels, archive
/// **relue** via [ZipFile] - le CSV et les sons doivent y être, pas seulement un fichier non vide. Les
/// refus (portées simultanées, passage inconnu, invocation vide) sortent en code 2 avant toute
/// écriture. La structure d'archive et les cas limites du service (dédoublonnage, introuvables,
/// homonymes) sont couverts, eux, par `ExportObservationsEtSonsTest`.
class CliExporterSonsTest {

    private static final String DOSSIER_SESSION = "Car640380-2026-Pass1-Z1";

    @TempDir
    Path workspace;

    private Cli cli;
    private Injector injecteur;
    private long idPassage;
    private final SortieCapturee capture = new SortieCapturee();

    @BeforeEach
    void preparer() throws IOException {
        System.setProperty("vigiechiro.workspace", workspace.toString());
        injecteur = Cli.injecteurApplicatif();
        cli = new Cli(injecteur);
        injecteur.getInstance(MigrationSchema.class).migrer();

        // Un passage complet (fabrique partagée, cliquet #1771), une séquence dont le fichier existe
        // réellement, une observation Tadarida dessus. L'utilisateur semé (« u-1 », premier de la
        // table) est celui que résout @Named("idUtilisateurCourant") pour la portée --espece.
        SourceDeDonnees source = injecteur.getInstance(SourceDeDonnees.class);
        Path racineSession = workspace.resolve(DOSSIER_SESSION);
        JeuDeDonneesPassage jeu = JeuDeDonneesPassage.dans(source)
                .carre("640380")
                .nomSite("Étang")
                .point("Z1")
                .cheminSession(racineSession.toString())
                .nuit(1, 2026, "2026-06-20")
                .semer();
        idPassage = jeu.idPassage();
        jeu.ajouterResultats();
        Path transformes = Files.createDirectories(racineSession.resolve("transformes"));
        Path fichier = transformes.resolve("a_000.wav");
        Files.write(fichier, new byte[] {1, 2, 3});
        long idSequence = injecteur
                .getInstance(SequenceDao.class)
                .insert(new SequenceDEcoute(
                        null, "a_000.wav", jeu.idOriginal(), 0, 0.0, 5.0, fichier.toString(), false, jeu.idSession()))
                .id();
        injecteur
                .getInstance(ObservationDao.class)
                .insert(new Observation(
                        null,
                        idSequence,
                        0.5,
                        2.5,
                        45,
                        "Rhifer",
                        0.98,
                        null,
                        null,
                        null,
                        null,
                        false,
                        ModeValidation.NON_VALIDE,
                        null,
                        false,
                        null,
                        null,
                        null,
                        null,
                        null));
    }

    @AfterEach
    void nettoyer() {
        System.clearProperty("vigiechiro.workspace");
    }

    private int executer(String... args) {
        return cli.executer(args, capture.sortie(), capture.erreur());
    }

    @Test
    @DisplayName("exporter-sons --passage : archive relue (CSV + son présents), bilan chiffré, code 0")
    void exporter_par_passage_ecrit_une_archive_relue() throws IOException {
        Path sortie = workspace.resolve("sons.zip");
        int code = executer("exporter-sons", "--passage", String.valueOf(idPassage), "--sortie", sortie.toString());

        assertThat(code).isEqualTo(Cli.CODE_SUCCES);
        assertThat(capture.texte())
                .contains("Archive écrite : 1 observation(s), 1 son(s)")
                .contains(sortie.toAbsolutePath().toString());
        try (ZipFile zip = new ZipFile(sortie.toFile())) {
            assertThat(zip.stream().map(ZipEntry::getName))
                    .containsExactly("observations.csv", "sons/" + DOSSIER_SESSION + "/a_000.wav");
        }
    }

    @Test
    @DisplayName("exporter-sons --espece : l'espèce à travers les passages de l'utilisateur, code 0")
    void exporter_par_espece_couvre_les_passages_de_l_utilisateur() throws IOException {
        Path sortie = workspace.resolve("rhifer.zip");
        int code = executer("exporter-sons", "--espece", "Rhifer", "--sortie", sortie.toString());

        assertThat(code).isEqualTo(Cli.CODE_SUCCES);
        try (ZipFile zip = new ZipFile(sortie.toFile())) {
            assertThat(zip.stream().map(ZipEntry::getName))
                    .as("l'observation Rhifer du passage semé arrive avec son fichier son")
                    .containsExactly("observations.csv", "sons/" + DOSSIER_SESSION + "/a_000.wav");
        }
    }

    @Test
    @DisplayName("exporter-sons : une espèce sans observation produit une archive au CSV d'en-têtes seuls, code 0")
    void exporter_une_espece_sans_observation_reste_valide() throws IOException {
        Path sortie = workspace.resolve("vide.zip");
        int code = executer("exporter-sons", "--espece", "Pippip", "--sortie", sortie.toString());

        assertThat(code).isEqualTo(Cli.CODE_SUCCES);
        assertThat(capture.texte()).contains("0 observation(s), 0 son(s)");
        try (ZipFile zip = new ZipFile(sortie.toFile())) {
            assertThat(zip.stream().map(ZipEntry::getName)).containsExactly("observations.csv");
        }
    }

    @Test
    @DisplayName("exporter-sons : --passage et --espece s'excluent, code 2")
    void refuse_les_deux_portees() {
        int code = executer(
                "exporter-sons",
                "--passage",
                String.valueOf(idPassage),
                "--espece",
                "Rhifer",
                "--sortie",
                workspace.resolve("a.zip").toString());

        assertThat(code)
                .as("une portée ambiguë se refuse plutôt que de se trancher en silence")
                .isEqualTo(Cli.CODE_ERREUR_ARGUMENTS);
    }

    @Test
    @DisplayName("exporter-sons : passage inconnu refusé avant toute écriture, code 2")
    void refuse_un_passage_inconnu() {
        Path sortie = workspace.resolve("inconnu.zip");
        int code = executer("exporter-sons", "--passage", "999", "--sortie", sortie.toString());

        assertThat(code)
                .as("un passage inconnu est une faute de frappe, pas un export vide")
                .isEqualTo(Cli.CODE_ERREUR_ARGUMENTS);
        assertThat(capture.texteErreur()).contains("Passage introuvable");
        assertThat(Files.exists(sortie)).isFalse();
    }

    @Test
    @DisplayName("exporter-sons sans argument : refus picocli, code 2")
    void refuse_une_invocation_vide() {
        int code = executer("exporter-sons");

        assertThat(code).isEqualTo(Cli.CODE_ERREUR_ARGUMENTS);
    }
}
