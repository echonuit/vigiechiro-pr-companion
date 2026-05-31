package fr.univ_amu.iut.importation.viewmodel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import fr.univ_amu.iut.importation.model.AnalyseurLogPR;
import fr.univ_amu.iut.importation.model.EtatNommage;
import fr.univ_amu.iut.importation.model.InspecteurDossier;
import fr.univ_amu.iut.importation.model.ServiceImport;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/// Tests unitaires de la tranche « inspection » du [ImportationViewModel]. Le [ServiceImport] est
/// mocké (Mockito) et les [fr.univ_amu.iut.importation.model.RapportInspection] sont produits par
/// un vrai [InspecteurDossier] sur un dossier jetable (`@TempDir`) : on vérifie que le VM mappe
/// fidèlement le rapport en propriétés observables, sans dépendre d'une base de données.
@ExtendWith(MockitoExtension.class)
class ImportationViewModelTest {

  private static final String LOG =
      "22/04/26 - 16:02:20 PR1925492 Démarrage Passive Recorder numéro de série 1925492, V1.01,"
          + " CPU 600000000, T4.1\n"
          + "22/04/26 - 16:02:21 PR1925492 Sonde température/hygrométrie présente, lecture toutes"
          + " les 600s\n"
          + "22/04/26 - 16:02:21 PR1925492 Paramètres : Acquisi. 20:25-07:47, Fe384kHz, Bd. Freq."
          + " 8-120kHz\n";

  @TempDir Path racine;
  @Mock private ServiceImport service;
  private final InspecteurDossier inspecteur = new InspecteurDossier(new AnalyseurLogPR());
  private ImportationViewModel viewModel;
  private Path sd;

  @BeforeEach
  void preparer() throws IOException {
    viewModel = new ImportationViewModel(service);
    sd = Files.createDirectories(racine.resolve("sd"));
    Files.writeString(sd.resolve("LogPR1925492.txt"), LOG, StandardCharsets.UTF_8);
    Files.writeString(
        sd.resolve("PaRecPR1925492_THLog.csv"), "Date\tHour\n", StandardCharsets.UTF_8);
    Files.writeString(sd.resolve("PaRecPR1925492_20260422_203922.wav"), "wav1");
    Files.writeString(sd.resolve("PaRecPR1925492_20260422_204326.wav"), "wav2");
  }

  @Test
  @DisplayName("Inspecter un dossier brut expose journal, relevé, compte et état de nommage")
  void inspecter_dossier_brut() {
    when(service.inspecter(sd)).thenReturn(inspecteur.inspecter(sd));
    viewModel.dossierSourceProperty().set(sd);

    viewModel.inspecter();

    assertThat(viewModel.estInspecte()).isTrue();
    assertThat(viewModel.aUnJournalProperty().get()).isTrue();
    assertThat(viewModel.resumeJournalProperty().get()).contains("1925492");
    assertThat(viewModel.aUnReleveClimatiqueProperty().get()).isTrue();
    assertThat(viewModel.nombreOriginauxProperty().get()).isEqualTo(2);
    assertThat(viewModel.etatNommageProperty().get()).isEqualTo(EtatNommage.BRUT);
    assertThat(viewModel.messageErreurProperty().get()).isEmpty();
  }

  @Test
  @DisplayName("R20 : un dossier sans relevé climatique est signalé (aUnReleveClimatique == false)")
  void inspecter_sans_releve_climatique() throws IOException {
    Files.delete(sd.resolve("PaRecPR1925492_THLog.csv"));
    when(service.inspecter(sd)).thenReturn(inspecteur.inspecter(sd));
    viewModel.dossierSourceProperty().set(sd);

    viewModel.inspecter();

    assertThat(viewModel.estInspecte()).isTrue();
    assertThat(viewModel.aUnReleveClimatiqueProperty().get()).isFalse();
    assertThat(viewModel.nombreOriginauxProperty().get()).isEqualTo(2);
  }

  @Test
  @DisplayName("Un chemin invalide renseigne le message d'erreur et laisse inspecte à false")
  void inspecter_chemin_invalide() {
    when(service.inspecter(any()))
        .thenThrow(new IllegalArgumentException("Le chemin n'est pas un dossier."));
    viewModel.dossierSourceProperty().set(racine.resolve("inexistant"));

    viewModel.inspecter();

    assertThat(viewModel.estInspecte()).isFalse();
    assertThat(viewModel.messageErreurProperty().get()).contains("dossier");
  }

  @Test
  @DisplayName("Sans dossier choisi, inspecter affiche un message et n'appelle pas le service")
  void inspecter_sans_dossier_choisi() {
    viewModel.inspecter();

    assertThat(viewModel.estInspecte()).isFalse();
    assertThat(viewModel.messageErreurProperty().get()).contains("dossier source");
    verifyNoInteractions(service);
  }

  @Test
  @DisplayName(
      "Un échec après une inspection réussie réinitialise l'état (pas de rapport obsolète)")
  void echec_apres_succes_reinitialise_l_etat() {
    when(service.inspecter(sd)).thenReturn(inspecteur.inspecter(sd));
    viewModel.dossierSourceProperty().set(sd);
    viewModel.inspecter();
    assertThat(viewModel.nombreOriginauxProperty().get()).isEqualTo(2);

    Path invalide = racine.resolve("inexistant");
    when(service.inspecter(invalide)).thenThrow(new IllegalArgumentException("Chemin invalide."));
    viewModel.dossierSourceProperty().set(invalide);
    viewModel.inspecter();

    assertThat(viewModel.estInspecte()).isFalse();
    assertThat(viewModel.aUnJournalProperty().get()).isFalse();
    assertThat(viewModel.aUnReleveClimatiqueProperty().get()).isFalse();
    assertThat(viewModel.nombreOriginauxProperty().get()).isZero();
    assertThat(viewModel.etatNommageProperty().get()).isNull();
    assertThat(viewModel.resumeJournalProperty().get()).isEmpty();
    assertThat(viewModel.messageErreurProperty().get()).contains("invalide");
  }
}
