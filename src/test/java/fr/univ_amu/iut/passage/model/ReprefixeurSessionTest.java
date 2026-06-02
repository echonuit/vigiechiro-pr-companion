package fr.univ_amu.iut.passage.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import fr.univ_amu.iut.commun.model.Prefixe;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/// Tests de [ReprefixeurSession] : re-renommage physique du dossier de session et de ses fichiers
/// préfixés, sur des dossiers temporaires (aucune base de données).
class ReprefixeurSessionTest {

  private static final Prefixe ANCIEN = new Prefixe("040962", 2026, 1, "A1");
  private static final Prefixe NOUVEAU = new Prefixe("040962", 2026, 2, "A1");

  private final ReprefixeurSession reprefixeur = new ReprefixeurSession();

  private Path creerSession(Path parent) throws IOException {
    Path racine = parent.resolve("Car040962-2026-Pass1-A1");
    Files.createDirectories(racine.resolve("bruts"));
    Files.createDirectories(racine.resolve("transformes"));
    Files.writeString(racine.resolve("bruts").resolve("Car040962-2026-Pass1-A1-PaRec.wav"), "o");
    Files.writeString(
        racine.resolve("transformes").resolve("Car040962-2026-Pass1-A1-PaRec_000.wav"), "s");
    Files.writeString(racine.resolve("journal.txt"), "j"); // non préfixé : nom à conserver
    return racine;
  }

  @Test
  @DisplayName("Déplace le dossier et re-préfixe les fichiers ; les non-préfixés gardent leur nom")
  void reprefixe_dossier_et_fichiers(@TempDir Path tmp) throws IOException {
    Path racine = creerSession(tmp);

    Path nouvelle = reprefixeur.reprefixer(racine, ANCIEN, NOUVEAU);

    assertThat(nouvelle.getFileName()).hasToString("Car040962-2026-Pass2-A1");
    assertThat(Files.exists(racine)).isFalse();
    assertThat(nouvelle.resolve("bruts").resolve("Car040962-2026-Pass2-A1-PaRec.wav")).exists();
    assertThat(nouvelle.resolve("transformes").resolve("Car040962-2026-Pass2-A1-PaRec_000.wav"))
        .exists();
    assertThat(nouvelle.resolve("journal.txt")).exists(); // déplacé, nom conservé
  }

  @Test
  @DisplayName("Refuse si le dossier cible existe déjà et laisse la source intacte")
  void refuse_si_cible_existe(@TempDir Path tmp) throws IOException {
    Path racine = creerSession(tmp);
    Files.createDirectories(tmp.resolve("Car040962-2026-Pass2-A1")); // cible occupée

    assertThatThrownBy(() -> reprefixeur.reprefixer(racine, ANCIEN, NOUVEAU))
        .isInstanceOf(IllegalStateException.class);
    assertThat(racine.resolve("bruts").resolve("Car040962-2026-Pass1-A1-PaRec.wav")).exists();
  }

  // --- cheminApres : recalcul des chemins persistés (pur, sans disque) ---

  @Test
  @DisplayName("cheminApres relocalise sous la session et re-préfixe le nom des fichiers préfixés")
  void cheminApres_relocalise_sous_la_session() {
    Path ancienne = Path.of("/ws/Car040962-2026-Pass1-A1");
    Path nouvelle = Path.of("/ws/Car040962-2026-Pass2-A1");

    assertThat(
            ReprefixeurSession.cheminApres(
                "/ws/Car040962-2026-Pass1-A1/bruts/Car040962-2026-Pass1-A1-x.wav",
                ancienne,
                nouvelle,
                ANCIEN.prefixeFichier(),
                NOUVEAU.prefixeFichier()))
        .isEqualTo("/ws/Car040962-2026-Pass2-A1/bruts/Car040962-2026-Pass2-A1-x.wav");

    // fichier non préfixé (journal) : relocalisé, nom conservé
    assertThat(
            ReprefixeurSession.cheminApres(
                "/ws/Car040962-2026-Pass1-A1/foo_LogPR.txt",
                ancienne,
                nouvelle,
                ANCIEN.prefixeFichier(),
                NOUVEAU.prefixeFichier()))
        .isEqualTo("/ws/Car040962-2026-Pass2-A1/foo_LogPR.txt");
  }

  @Test
  @DisplayName("cheminApres laisse inchangé un chemin externe, même un frère au nom préfixe")
  void cheminApres_laisse_un_chemin_externe_inchange() {
    Path ancienne = Path.of("/ws/Car040962-2026-Pass1-A1");
    Path nouvelle = Path.of("/ws/Car040962-2026-Pass2-A1");
    String externe = "/autre/Car040962-2026-Pass1-A1-export.csv";
    String frere = "/ws/Car040962-2026-Pass1-A1-export/x.csv"; // frère dont le nom commence pareil

    assertThat(
            ReprefixeurSession.cheminApres(
                externe, ancienne, nouvelle, ANCIEN.prefixeFichier(), NOUVEAU.prefixeFichier()))
        .isEqualTo(externe);
    assertThat(
            ReprefixeurSession.cheminApres(
                frere, ancienne, nouvelle, ANCIEN.prefixeFichier(), NOUVEAU.prefixeFichier()))
        .isEqualTo(frere);
  }

  @Test
  @DisplayName("cheminApres préserve un parent contenant par coïncidence le nom de dossier")
  void cheminApres_preserve_le_parent() {
    Path ancienne = Path.of("/tmp/Car040962-2026-Pass1-A1-arch/Car040962-2026-Pass1-A1");
    Path nouvelle = Path.of("/tmp/Car040962-2026-Pass1-A1-arch/Car040962-2026-Pass2-A1");

    assertThat(
            ReprefixeurSession.cheminApres(
                "/tmp/Car040962-2026-Pass1-A1-arch/Car040962-2026-Pass1-A1/bruts/"
                    + "Car040962-2026-Pass1-A1-x.wav",
                ancienne,
                nouvelle,
                ANCIEN.prefixeFichier(),
                NOUVEAU.prefixeFichier()))
        .isEqualTo(
            "/tmp/Car040962-2026-Pass1-A1-arch/Car040962-2026-Pass2-A1/bruts/"
                + "Car040962-2026-Pass2-A1-x.wav");
  }
}
