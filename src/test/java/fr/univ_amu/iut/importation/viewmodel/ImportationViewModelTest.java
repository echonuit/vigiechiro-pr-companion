package fr.univ_amu.iut.importation.viewmodel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import fr.univ_amu.iut.commun.model.HorlogeFigee;
import fr.univ_amu.iut.commun.model.Prefixe;
import fr.univ_amu.iut.commun.model.Protocole;
import fr.univ_amu.iut.commun.model.RegleMetierException;
import fr.univ_amu.iut.importation.model.AnalyseurLogPR;
import fr.univ_amu.iut.importation.model.EtatNommage;
import fr.univ_amu.iut.importation.model.InspecteurDossier;
import fr.univ_amu.iut.importation.model.ResultatImport;
import fr.univ_amu.iut.importation.model.ServiceImport;
import fr.univ_amu.iut.sites.model.PointDEcoute;
import fr.univ_amu.iut.sites.model.ServiceSites;
import fr.univ_amu.iut.sites.model.Site;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/// Tests unitaires de l'[ImportationViewModel] (tranches « inspection » + « rattachement »).
/// [ServiceImport] et [ServiceSites] sont mockés (Mockito) ; les
/// [fr.univ_amu.iut.importation.model.RapportInspection] sont produits par un vrai
/// [InspecteurDossier] sur un dossier jetable (`@TempDir`). On vérifie ainsi le mapping du VM en
/// propriétés observables sans dépendre d'une base de données.
@ExtendWith(MockitoExtension.class)
class ImportationViewModelTest {

  private static final String ID_USER = "u-1";
  private static final LocalDate JOUR = LocalDate.of(2026, 5, 31);
  private static final String LOG =
      "22/04/26 - 16:02:20 PR1925492 Démarrage Passive Recorder numéro de série 1925492, V1.01,"
          + " CPU 600000000, T4.1\n"
          + "22/04/26 - 16:02:21 PR1925492 Sonde température/hygrométrie présente, lecture toutes"
          + " les 600s\n"
          + "22/04/26 - 16:02:21 PR1925492 Paramètres : Acquisi. 20:25-07:47, Fe384kHz, Bd. Freq."
          + " 8-120kHz\n";

  @TempDir Path racine;
  @Mock private ServiceImport serviceImport;
  @Mock private ServiceSites serviceSites;
  private final InspecteurDossier inspecteur = new InspecteurDossier(new AnalyseurLogPR());
  private ImportationViewModel viewModel;
  private Path sd;

  @BeforeEach
  void preparer() throws IOException {
    viewModel =
        new ImportationViewModel(serviceImport, serviceSites, new HorlogeFigee(JOUR), ID_USER);
    sd = Files.createDirectories(racine.resolve("sd"));
    Files.writeString(sd.resolve("LogPR1925492.txt"), LOG, StandardCharsets.UTF_8);
    Files.writeString(
        sd.resolve("PaRecPR1925492_THLog.csv"), "Date\tHour\n", StandardCharsets.UTF_8);
    Files.writeString(sd.resolve("PaRecPR1925492_20260422_203922.wav"), "wav1");
    Files.writeString(sd.resolve("PaRecPR1925492_20260422_204326.wav"), "wav2");
  }

  // --- Étape 2 : inspection ---

  @Test
  @DisplayName("Inspecter un dossier brut expose journal, relevé, compte et état de nommage")
  void inspecter_dossier_brut() {
    when(serviceImport.inspecter(sd)).thenReturn(inspecteur.inspecter(sd));
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
    when(serviceImport.inspecter(sd)).thenReturn(inspecteur.inspecter(sd));
    viewModel.dossierSourceProperty().set(sd);

    viewModel.inspecter();

    assertThat(viewModel.estInspecte()).isTrue();
    assertThat(viewModel.aUnReleveClimatiqueProperty().get()).isFalse();
    assertThat(viewModel.nombreOriginauxProperty().get()).isEqualTo(2);
  }

  @Test
  @DisplayName("Un chemin invalide renseigne le message d'erreur et laisse inspecte à false")
  void inspecter_chemin_invalide() {
    when(serviceImport.inspecter(any()))
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
    verifyNoInteractions(serviceImport);
  }

  @Test
  @DisplayName(
      "Un échec après une inspection réussie réinitialise l'état (pas de rapport obsolète)")
  void echec_apres_succes_reinitialise_l_etat() {
    when(serviceImport.inspecter(sd)).thenReturn(inspecteur.inspecter(sd));
    viewModel.dossierSourceProperty().set(sd);
    viewModel.inspecter();
    assertThat(viewModel.nombreOriginauxProperty().get()).isEqualTo(2);

    Path invalide = racine.resolve("inexistant");
    when(serviceImport.inspecter(invalide))
        .thenThrow(new IllegalArgumentException("Chemin invalide."));
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

  // --- Étape 3 : rattachement ---

  @Test
  @DisplayName("chargerSites alimente la liste des sites de l'utilisateur courant")
  void charger_sites() {
    Site a = site(1L, "640380");
    Site b = site(2L, "752204");
    when(serviceSites.listerSites(ID_USER)).thenReturn(List.of(a, b));

    viewModel.chargerSites();

    assertThat(viewModel.sites()).containsExactly(a, b);
  }

  @Test
  @DisplayName("Choisir un site recharge ses points et réinitialise le point sélectionné")
  void selectionner_un_site_charge_ses_points() {
    Site site = site(1L, "640380");
    PointDEcoute point = point(10L, "A1", site.id());
    when(serviceSites.listerPoints(site.id())).thenReturn(List.of(point));

    viewModel.siteSelectionneProperty().set(site);

    assertThat(viewModel.points()).containsExactly(point);
    assertThat(viewModel.pointSelectionneProperty().get()).isNull();
  }

  @Test
  @DisplayName("L'aperçu du préfixe compose le quadruplet (carré, année, n° passage, point)")
  void apercu_prefixe_compose_le_quadruplet() {
    Site site = site(1L, "640380");
    PointDEcoute point = point(10L, "A1", site.id());
    when(serviceSites.listerPoints(site.id())).thenReturn(List.of(point));

    viewModel.siteSelectionneProperty().set(site);
    viewModel.pointSelectionneProperty().set(point);
    viewModel.numeroPassageProperty().set(2);

    assertThat(viewModel.apercuPrefixeProperty().get()).startsWith("Car640380-2026-Pass2-A1-");
  }

  @Test
  @DisplayName("peutImporter exige une inspection réussie ET un rattachement complet")
  void peut_importer_exige_inspection_et_rattachement() {
    Site site = site(1L, "640380");
    PointDEcoute point = point(10L, "A1", site.id());
    when(serviceSites.listerPoints(site.id())).thenReturn(List.of(point));
    when(serviceImport.inspecter(sd)).thenReturn(inspecteur.inspecter(sd));

    assertThat(viewModel.peutImporter().get()).isFalse();

    viewModel.dossierSourceProperty().set(sd);
    viewModel.inspecter();
    assertThat(viewModel.peutImporter().get()).as("inspecté mais sans site/point").isFalse();

    viewModel.siteSelectionneProperty().set(site);
    viewModel.pointSelectionneProperty().set(point);
    assertThat(viewModel.peutImporter().get()).isTrue();
  }

  @Test
  @DisplayName(
      "Changer de dossier source après inspection invalide l'inspection (réinspection requise)")
  void changer_dossier_reinitialise_l_inspection() {
    Site site = site(1L, "640380");
    PointDEcoute point = point(10L, "A1", site.id());
    when(serviceSites.listerPoints(site.id())).thenReturn(List.of(point));
    when(serviceImport.inspecter(sd)).thenReturn(inspecteur.inspecter(sd));
    viewModel.dossierSourceProperty().set(sd);
    viewModel.inspecter();
    viewModel.siteSelectionneProperty().set(site);
    viewModel.pointSelectionneProperty().set(point);
    assertThat(viewModel.peutImporter().get()).isTrue();

    viewModel.dossierSourceProperty().set(racine.resolve("autre"));

    assertThat(viewModel.estInspecte()).isFalse();
    assertThat(viewModel.peutImporter().get()).isFalse();
    assertThat(viewModel.nombreOriginauxProperty().get()).isZero();
  }

  // --- Étape 4 : exécution ---

  @Test
  @DisplayName("Importer un rattachement complet expose le résultat et passe l'état à TERMINE")
  void importer_termine_avec_resultat() {
    Site site = site(1L, "640380");
    PointDEcoute point = point(10L, "A1", site.id());
    when(serviceSites.listerPoints(site.id())).thenReturn(List.of(point));
    when(serviceImport.inspecter(sd)).thenReturn(inspecteur.inspecter(sd));
    when(serviceImport.importer(eq(sd), eq(10L), any(Prefixe.class)))
        .thenReturn(new ResultatImport(null, null, "1925492", 2, 6, List.of()));
    prepareRattachement(site, point);

    viewModel.importer();

    assertThat(viewModel.etatProperty().get()).isEqualTo(EtatImport.TERMINE);
    assertThat(viewModel.resultatProperty().get().nombreSequences()).isEqualTo(6);
    assertThat(viewModel.messageErreurProperty().get()).isEmpty();
  }

  @Test
  @DisplayName("Un refus métier passe l'état à ECHEC et expose le message (résultat null)")
  void importer_echec_expose_le_message() {
    Site site = site(1L, "640380");
    PointDEcoute point = point(10L, "A1", site.id());
    when(serviceSites.listerPoints(site.id())).thenReturn(List.of(point));
    when(serviceImport.inspecter(sd)).thenReturn(inspecteur.inspecter(sd));
    when(serviceImport.importer(eq(sd), eq(10L), any(Prefixe.class)))
        .thenThrow(new RegleMetierException("R5 : un passage existe déjà pour ce point."));
    prepareRattachement(site, point);

    viewModel.importer();

    assertThat(viewModel.etatProperty().get()).isEqualTo(EtatImport.ECHEC);
    assertThat(viewModel.messageErreurProperty().get()).contains("R5");
    assertThat(viewModel.resultatProperty().get()).isNull();
  }

  @Test
  @DisplayName("Importer sans rattachement complet est refusé sans appeler le service")
  void importer_sans_rattachement_refuse() {
    viewModel.importer();

    assertThat(viewModel.etatProperty().get()).isEqualTo(EtatImport.PRET);
    assertThat(viewModel.messageErreurProperty().get()).contains("rattachement");
    verifyNoInteractions(serviceImport);
  }

  private void prepareRattachement(Site site, PointDEcoute point) {
    viewModel.dossierSourceProperty().set(sd);
    viewModel.inspecter();
    viewModel.siteSelectionneProperty().set(site);
    viewModel.pointSelectionneProperty().set(point);
  }

  private static Site site(Long id, String carre) {
    return new Site(id, carre, "Site " + carre, Protocole.STANDARD, null, "2026-05-31", ID_USER);
  }

  private static PointDEcoute point(Long id, String code, Long idSite) {
    return new PointDEcoute(id, code, null, null, null, idSite);
  }
}
