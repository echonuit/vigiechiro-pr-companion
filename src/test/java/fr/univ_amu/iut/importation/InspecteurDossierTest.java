package fr.univ_amu.iut.importation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import fr.univ_amu.iut.importation.model.AnalyseurLogPR;
import fr.univ_amu.iut.importation.model.EtatNommage;
import fr.univ_amu.iut.importation.model.InspecteurDossier;
import fr.univ_amu.iut.importation.model.RapportInspection;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests de l'inspection en lecture seule d'un dossier de carte SD (P2, étape 1). Vérifie la
 * détection du journal, des originaux, du relevé climatique, de l'état de nommage, et surtout que
 * l'inspection <b>n'écrit rien</b> sur la source (R9).
 */
class InspecteurDossierTest {

  private static final String LOG =
      "22/04/26 - 16:02:20 PR1925492 Démarrage Passive Recorder numéro de série 1925492, V1.01,"
          + " CPU 600000000, T4.1\n"
          + "22/04/26 - 16:02:21 PR1925492 Sonde température/hygrométrie présente, lecture toutes"
          + " les 600s\n"
          + "22/04/26 - 16:02:21 PR1925492 Paramètres : Acquisi. 20:25-07:47, Fe384kHz, Bd. Freq."
          + " 8-120kHz\n";

  @TempDir Path racine;

  private final InspecteurDossier inspecteur = new InspecteurDossier(new AnalyseurLogPR());
  private Path sd;

  @BeforeEach
  void preparer() throws IOException {
    sd = Files.createDirectories(racine.resolve("sd"));
    Files.writeString(sd.resolve("LogPR1925492.txt"), LOG, StandardCharsets.UTF_8);
    Files.writeString(
        sd.resolve("PaRecPR1925492_THLog.csv"), "Date\tHour\n", StandardCharsets.UTF_8);
    Files.writeString(sd.resolve("PaRecPR1925492_20260422_203922.wav"), "wav1");
    Files.writeString(sd.resolve("PaRecPR1925492_20260422_204326.wav"), "wav2");
  }

  @Test
  @DisplayName("Détecte le journal, les originaux bruts et le relevé climatique")
  void inspecte_un_dossier_brut() {
    RapportInspection rapport = inspecteur.inspecter(sd);

    assertThat(rapport.aUnJournal()).isTrue();
    assertThat(rapport.journal().numeroSerie()).isEqualTo("1925492");
    assertThat(rapport.nombreOriginaux()).isEqualTo(2);
    assertThat(rapport.aUnReleveClimatique()).isTrue();
    assertThat(rapport.etatNommage()).isEqualTo(EtatNommage.BRUT);
  }

  @Test
  @DisplayName("R9 : l'inspection ne modifie pas la source (mêmes fichiers avant et après)")
  void inspection_en_lecture_seule() throws IOException {
    List<String> avant = listerNoms(sd);

    inspecteur.inspecter(sd);

    assertThat(listerNoms(sd)).isEqualTo(avant);
  }

  @Test
  @DisplayName("Des originaux déjà préfixés sont reconnus comme PREFIXE")
  void detecte_etat_prefixe() throws IOException {
    Files.delete(sd.resolve("PaRecPR1925492_20260422_203922.wav"));
    Files.delete(sd.resolve("PaRecPR1925492_20260422_204326.wav"));
    Files.writeString(
        sd.resolve("Car640380-2026-Pass2-Z1-PaRecPR1925492_20260422_203922.wav"), "x");

    RapportInspection rapport = inspecteur.inspecter(sd);

    assertThat(rapport.etatNommage()).isEqualTo(EtatNommage.PREFIXE);
  }

  @Test
  @DisplayName("R20 : un dossier sans relevé climatique est signalé (aUnReleveClimatique == false)")
  void releve_climatique_absent() throws IOException {
    Files.delete(sd.resolve("PaRecPR1925492_THLog.csv"));

    RapportInspection rapport = inspecteur.inspecter(sd);

    assertThat(rapport.aUnReleveClimatique()).isFalse();
    assertThat(rapport.cheminReleveClimatique()).isNull();
  }

  @Test
  @DisplayName("Les originaux rangés dans un sous-dossier bruts/ sont détectés")
  void detecte_les_originaux_dans_bruts() throws IOException {
    Path source = Files.createDirectories(racine.resolve("session"));
    Files.writeString(source.resolve("LogPR1925492.txt"), LOG, StandardCharsets.UTF_8);
    Path bruts = Files.createDirectories(source.resolve("bruts"));
    Files.writeString(bruts.resolve("PaRecPR1925492_20260422_203922.wav"), "wav");

    RapportInspection rapport = inspecteur.inspecter(source);

    assertThat(rapport.nombreOriginaux()).isEqualTo(1);
  }

  @Test
  @DisplayName("Un chemin qui n'est pas un dossier est refusé")
  void chemin_invalide_refuse() {
    assertThatThrownBy(() -> inspecteur.inspecter(racine.resolve("inexistant")))
        .isInstanceOf(IllegalArgumentException.class);
  }

  private static List<String> listerNoms(Path dossier) throws IOException {
    try (var flux = Files.list(dossier)) {
      return flux.map(p -> p.getFileName().toString()).sorted().toList();
    }
  }
}
