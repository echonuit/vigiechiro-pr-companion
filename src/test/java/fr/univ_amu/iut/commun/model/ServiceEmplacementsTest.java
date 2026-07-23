package fr.univ_amu.iut.commun.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import fr.univ_amu.iut.commun.model.ServiceEmplacements.Accessibilite;
import fr.univ_amu.iut.commun.model.ServiceEmplacements.Emplacements;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/// Le service qui lit et écrit où vivent l'espace de travail et la base (#1038, ADR 1038).
///
/// Les tests posent `vigiechiro.config` sur un dossier jetable et **effacent** `vigiechiro.workspace`,
/// pour que la configuration écrite pilote bien `Workspace.resolu()` - la même couture qu'en
/// production, où le service et le workspace lisent le même dossier.
class ServiceEmplacementsTest {

    private static final String PROP_CONFIG = "vigiechiro.config";
    private static final String PROP_WORKSPACE = "vigiechiro.workspace";

    private final ServiceEmplacements service = new ServiceEmplacements();

    @Test
    @DisplayName("Sans configuration : emplacements par défaut, non personnalisés")
    void sans_configuration_defaut(@TempDir Path config) throws Exception {
        surConfig(config, () -> {
            Emplacements e = service.emplacementsCourants();
            assertThat(e.personnalise()).isFalse();
            assertThat(e.espaceDeTravail()).isEqualTo(e.espaceDeTravailParDefaut());
            assertThat(e.base()).isEqualTo(e.baseParDefaut());
        });
    }

    @Test
    @DisplayName("Après enregistrement : les emplacements effectifs suivent le choix, marqués personnalisés")
    void apres_enregistrement_les_emplacements_suivent(@TempDir Path config, @TempDir Path racine) throws Exception {
        Path travail = racine.resolve("nuits");
        Path coffre = racine.resolve("coffre");

        surConfig(config, () -> {
            service.enregistrer(travail, coffre);
            Emplacements e = service.emplacementsCourants();

            assertThat(e.personnalise()).isTrue();
            assertThat(e.espaceDeTravail()).isEqualTo(travail.toAbsolutePath());
            assertThat(e.base())
                    .as("la base est nommée automatiquement dans le dossier choisi")
                    .isEqualTo(coffre.resolve("vigiechiro.db").toAbsolutePath());
            assertThat(e.espaceDeTravailParDefaut())
                    .as("le défaut reste rappelé, distinct du choix")
                    .isNotEqualTo(e.espaceDeTravail());
        });
    }

    @Test
    @DisplayName("reinitialiser : retour aux emplacements par défaut, non personnalisés")
    void reinitialiser_efface_le_choix(@TempDir Path config, @TempDir Path racine) throws Exception {
        surConfig(config, () -> {
            service.enregistrer(racine.resolve("nuits"), racine.resolve("coffre"));
            assertThat(service.emplacementsCourants().personnalise()).isTrue();

            service.reinitialiser();

            Emplacements e = service.emplacementsCourants();
            assertThat(e.personnalise()).isFalse();
            assertThat(e.espaceDeTravail()).isEqualTo(e.espaceDeTravailParDefaut());
        });
    }

    @Test
    @DisplayName("Sonde : un dossier inscriptible est ACCESSIBLE, et un dossier manquant est créé")
    void sonde_dossier_inscriptible(@TempDir Path racine) {
        assertThat(service.sonder(racine)).isEqualTo(Accessibilite.ACCESSIBLE);

        Path aCreer = racine.resolve("sous").resolve("dossier");
        assertThat(service.sonder(aCreer))
                .as("désigner un dossier encore inexistant est un usage normal")
                .isEqualTo(Accessibilite.ACCESSIBLE);
        assertThat(aCreer).isDirectory();
    }

    @Test
    @DisplayName("Sonde : le fichier témoin est effacé, elle ne jonche pas le dossier de l'utilisateur")
    void sonde_ne_laisse_pas_de_temoin(@TempDir Path racine) throws IOException {
        assertThat(service.sonder(racine)).isEqualTo(Accessibilite.ACCESSIBLE);

        try (var contenu = Files.list(racine)) {
            assertThat(contenu)
                    .as("la sonde écrit un témoin pour éprouver l'écriture : elle doit l'effacer ensuite")
                    .isEmpty();
        }
    }

    @Test
    @DisplayName("Sonde : un fichier n'est PAS un dossier")
    void sonde_fichier(@TempDir Path racine) throws IOException {
        Path fichier = Files.createFile(racine.resolve("un-fichier"));
        assertThat(service.sonder(fichier)).isEqualTo(Accessibilite.PAS_UN_DOSSIER);
    }

    @Test
    @DisplayName("Sonde : un dossier impossible à créer (sous un fichier) est INEXISTANT_NON_CREABLE")
    void sonde_non_creable(@TempDir Path racine) throws IOException {
        Path fichier = Files.createFile(racine.resolve("obstacle"));
        assertThat(service.sonder(fichier.resolve("dessous")))
                .as("créer un dossier SOUS un fichier régulier échoue")
                .isEqualTo(Accessibilite.INEXISTANT_NON_CREABLE);
    }

    @Test
    @DisplayName("Sonde : un dossier en lecture seule est NON_INSCRIPTIBLE")
    void sonde_lecture_seule(@TempDir Path racine) throws IOException {
        assumeTrue(FileSystems.getDefault().supportedFileAttributeViews().contains("posix"), "POSIX requis");
        Path lecture = Files.createDirectory(racine.resolve("lecture-seule"));
        Files.setPosixFilePermissions(lecture, PosixFilePermissions.fromString("r-xr-xr-x"));
        try {
            // Auto-garde : si l'on peut écrire malgré r-x (exécution en root), le test ne prouve rien.
            try {
                Files.delete(Files.createTempFile(lecture, "root", ".tmp"));
                assumeTrue(false, "écriture possible malgré r-x (probablement root) : test sans objet");
            } catch (IOException attendu) {
                // C'est le comportement voulu : on ne peut pas écrire.
            }
            assertThat(service.sonder(lecture)).isEqualTo(Accessibilite.NON_INSCRIPTIBLE);
        } finally {
            Files.setPosixFilePermissions(lecture, PosixFilePermissions.fromString("rwxr-xr-x"));
        }
    }

    /// Corps de test susceptible de lever.
    @FunctionalInterface
    private interface CorpsTest {
        void executer() throws Exception;
    }

    /// Exécute `corps` avec `vigiechiro.config` posé sur `config` et `vigiechiro.workspace` **effacé**
    /// (pour que la configuration écrite pilote `Workspace.resolu()`), puis restaure les deux.
    private static void surConfig(Path config, CorpsTest corps) throws Exception {
        String configAvant = System.getProperty(PROP_CONFIG);
        String workspaceAvant = System.getProperty(PROP_WORKSPACE);
        try {
            System.setProperty(PROP_CONFIG, config.toString());
            System.clearProperty(PROP_WORKSPACE);
            corps.executer();
        } finally {
            restaurer(PROP_CONFIG, configAvant);
            restaurer(PROP_WORKSPACE, workspaceAvant);
        }
    }

    private static void restaurer(String cle, String valeur) {
        if (valeur == null) {
            System.clearProperty(cle);
        } else {
            System.setProperty(cle, valeur);
        }
    }
}
