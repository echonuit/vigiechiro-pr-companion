package fr.univ_amu.iut.importation.viewmodel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import fr.univ_amu.iut.commun.model.HorlogeFigee;
import fr.univ_amu.iut.commun.model.Prefixe;
import fr.univ_amu.iut.commun.model.Protocole;
import fr.univ_amu.iut.commun.model.Reglages;
import fr.univ_amu.iut.commun.model.RegleMetierException;
import fr.univ_amu.iut.commun.viewmodel.NavigationViewModel;
import fr.univ_amu.iut.importation.model.AnalyseurLogPR;
import fr.univ_amu.iut.importation.model.EtatNommage;
import fr.univ_amu.iut.importation.model.InspecteurDossier;
import fr.univ_amu.iut.importation.model.JournalParse;
import fr.univ_amu.iut.importation.model.LigneRapport;
import fr.univ_amu.iut.importation.model.NuitAImporter;
import fr.univ_amu.iut.importation.model.Progression;
import fr.univ_amu.iut.importation.model.RapportImport;
import fr.univ_amu.iut.importation.model.RapportInspection;
import fr.univ_amu.iut.importation.model.ResultatImport;
import fr.univ_amu.iut.importation.model.ResultatImportMultiNuits;
import fr.univ_amu.iut.importation.model.ServiceImport;
import fr.univ_amu.iut.importation.model.StatutImportFichier;
import fr.univ_amu.iut.sites.model.PointDEcoute;
import fr.univ_amu.iut.sites.model.ServiceSites;
import fr.univ_amu.iut.sites.model.Site;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
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

    @TempDir
    Path racine;

    @Mock
    private ServiceImport serviceImport;

    @Mock
    private ServiceSites serviceSites;

    @Mock
    private Reglages reglages;

    private final InspecteurDossier inspecteur = new InspecteurDossier(new AnalyseurLogPR());
    private final NavigationViewModel navigation = new NavigationViewModel();
    private PreferenceConservation conservation;
    private ImportationViewModel viewModel;
    private Path sd;

    @BeforeEach
    void preparer() throws IOException {
        // Réglage « conserver les originaux » non écrit → la préférence lit le défaut (conservation activée).
        when(reglages.lireBooleen(anyString(), anyBoolean())).thenAnswer(invocation -> invocation.getArgument(1));
        conservation = new PreferenceConservation(reglages);
        viewModel = new ImportationViewModel(
                serviceImport, serviceSites, new HorlogeFigee(JOUR), ID_USER, navigation, conservation);
        sd = Files.createDirectories(racine.resolve("sd"));
        Files.writeString(sd.resolve("LogPR1925492.txt"), LOG, StandardCharsets.UTF_8);
        Files.writeString(sd.resolve("PaRecPR1925492_THLog.csv"), "Date\tHour\n", StandardCharsets.UTF_8);
        Files.writeString(sd.resolve("PaRecPR1925492_20260422_203922.wav"), "wav1");
        Files.writeString(sd.resolve("PaRecPR1925492_20260422_204326.wav"), "wav2");
    }

    // --- Étape 2 : inspection ---

    @Test
    @DisplayName("Inspecter un dossier brut expose journal, relevé, compte et état de nommage")
    void inspecter_dossier_brut() {
        when(serviceImport.inspecter(sd)).thenReturn(inspecteur.inspecter(sd));
        viewModel.inspection().dossierSourceProperty().set(sd);

        viewModel.inspecter();

        assertThat(viewModel.inspection().estInspecte()).isTrue();
        assertThat(viewModel.inspection().aUnJournalProperty().get()).isTrue();
        assertThat(viewModel.inspection().resumeJournalProperty().get()).contains("1925492");
        assertThat(viewModel.inspection().aUnReleveClimatiqueProperty().get()).isTrue();
        assertThat(viewModel.inspection().nombreOriginauxProperty().get()).isEqualTo(2);
        assertThat(viewModel.inspection().etatNommageProperty().get()).isEqualTo(EtatNommage.BRUT);
        assertThat(viewModel.messageErreurProperty().get()).isEmpty();
    }

    @Test
    @DisplayName("R20 : un dossier sans relevé climatique est signalé (aUnReleveClimatique == false)")
    void inspecter_sans_releve_climatique() throws IOException {
        Files.delete(sd.resolve("PaRecPR1925492_THLog.csv"));
        when(serviceImport.inspecter(sd)).thenReturn(inspecteur.inspecter(sd));
        viewModel.inspection().dossierSourceProperty().set(sd);

        viewModel.inspecter();

        assertThat(viewModel.inspection().estInspecte()).isTrue();
        assertThat(viewModel.inspection().aUnReleveClimatiqueProperty().get()).isFalse();
        assertThat(viewModel.inspection().nombreOriginauxProperty().get()).isEqualTo(2);
    }

    @Test
    @DisplayName("Un chemin invalide renseigne le message d'erreur et laisse inspecte à false")
    void inspecter_chemin_invalide() {
        when(serviceImport.inspecter(any())).thenThrow(new IllegalArgumentException("Le chemin n'est pas un dossier."));
        viewModel.inspection().dossierSourceProperty().set(racine.resolve("inexistant"));

        viewModel.inspecter();

        assertThat(viewModel.inspection().estInspecte()).isFalse();
        assertThat(viewModel.messageErreurProperty().get()).contains("dossier");
    }

    @Test
    @DisplayName("Sans dossier choisi, inspecter affiche un message et n'appelle pas le service")
    void inspecter_sans_dossier_choisi() {
        viewModel.inspecter();

        assertThat(viewModel.inspection().estInspecte()).isFalse();
        assertThat(viewModel.messageErreurProperty().get()).contains("dossier source");
        verifyNoInteractions(serviceImport);
    }

    @Test
    @DisplayName("Un échec après une inspection réussie réinitialise l'état (pas de rapport obsolète)")
    void echec_apres_succes_reinitialise_l_etat() {
        when(serviceImport.inspecter(sd)).thenReturn(inspecteur.inspecter(sd));
        viewModel.inspection().dossierSourceProperty().set(sd);
        viewModel.inspecter();
        assertThat(viewModel.inspection().nombreOriginauxProperty().get()).isEqualTo(2);

        Path invalide = racine.resolve("inexistant");
        when(serviceImport.inspecter(invalide)).thenThrow(new IllegalArgumentException("Chemin invalide."));
        viewModel.inspection().dossierSourceProperty().set(invalide);
        viewModel.inspecter();

        assertThat(viewModel.inspection().estInspecte()).isFalse();
        assertThat(viewModel.inspection().aUnJournalProperty().get()).isFalse();
        assertThat(viewModel.inspection().aUnReleveClimatiqueProperty().get()).isFalse();
        assertThat(viewModel.inspection().nombreOriginauxProperty().get()).isZero();
        assertThat(viewModel.inspection().etatNommageProperty().get()).isNull();
        assertThat(viewModel.inspection().resumeJournalProperty().get()).isEmpty();
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

        assertThat(viewModel.rattachement().sites()).containsExactly(a, b);
    }

    @Test
    @DisplayName("Choisir un site recharge ses points et réinitialise le point sélectionné")
    void selectionner_un_site_charge_ses_points() {
        Site site = site(1L, "640380");
        PointDEcoute point = point(10L, "A1", site.id());
        when(serviceSites.listerPoints(site.id())).thenReturn(List.of(point));

        viewModel.rattachement().siteSelectionneProperty().set(site);

        assertThat(viewModel.rattachement().points()).containsExactly(point);
        assertThat(viewModel.rattachement().pointSelectionneProperty().get()).isNull();
    }

    @Test
    @DisplayName("L'aperçu du préfixe compose le quadruplet (carré, année, n° passage, point)")
    void apercu_prefixe_compose_le_quadruplet() {
        Site site = site(1L, "640380");
        PointDEcoute point = point(10L, "A1", site.id());
        when(serviceSites.listerPoints(site.id())).thenReturn(List.of(point));

        viewModel.rattachement().siteSelectionneProperty().set(site);
        viewModel.rattachement().pointSelectionneProperty().set(point);
        viewModel.rattachement().numeroPassageProperty().set(2);

        assertThat(viewModel.rattachement().apercuPrefixeProperty().get()).startsWith("Car640380-2026-Pass2-A1-");
    }

    @Test
    @DisplayName("peutImporter exige une inspection réussie ET un rattachement complet")
    void peut_importer_exige_inspection_et_rattachement() {
        Site site = site(1L, "640380");
        PointDEcoute point = point(10L, "A1", site.id());
        when(serviceSites.listerPoints(site.id())).thenReturn(List.of(point));
        when(serviceImport.inspecter(sd)).thenReturn(inspecteur.inspecter(sd));

        assertThat(viewModel.peutImporter().get()).isFalse();

        viewModel.inspection().dossierSourceProperty().set(sd);
        viewModel.inspecter();
        assertThat(viewModel.peutImporter().get())
                .as("inspecté mais sans site/point")
                .isFalse();

        viewModel.rattachement().siteSelectionneProperty().set(site);
        viewModel.rattachement().pointSelectionneProperty().set(point);
        assertThat(viewModel.peutImporter().get()).isTrue();
    }

    @Test
    @DisplayName("Changer de dossier source après inspection invalide l'inspection (réinspection requise)")
    void changer_dossier_reinitialise_l_inspection() {
        Site site = site(1L, "640380");
        PointDEcoute point = point(10L, "A1", site.id());
        when(serviceSites.listerPoints(site.id())).thenReturn(List.of(point));
        when(serviceImport.inspecter(sd)).thenReturn(inspecteur.inspecter(sd));
        viewModel.inspection().dossierSourceProperty().set(sd);
        viewModel.inspecter();
        viewModel.rattachement().siteSelectionneProperty().set(site);
        viewModel.rattachement().pointSelectionneProperty().set(point);
        assertThat(viewModel.peutImporter().get()).isTrue();

        viewModel.inspection().dossierSourceProperty().set(racine.resolve("autre"));

        assertThat(viewModel.inspection().estInspecte()).isFalse();
        assertThat(viewModel.peutImporter().get()).isFalse();
        assertThat(viewModel.inspection().nombreOriginauxProperty().get()).isZero();
    }

    @Test
    @DisplayName("#108 : un n° de passage déjà pris pour ce point/année avertit et bloque l'import (R5)")
    void numero_passage_deja_pris_avertit_et_bloque() {
        Site site = site(1L, "640380");
        PointDEcoute point = point(10L, "A1", site.id());
        when(serviceSites.listerPoints(site.id())).thenReturn(List.of(point));
        when(serviceImport.inspecter(sd)).thenReturn(inspecteur.inspecter(sd));
        // Par défaut un n° est libre ; seul le n° 2 est déjà pris pour ce point en 2026.
        when(serviceImport.numeroPassageDejaUtilise(eq(10L), eq(2026), anyInt()))
                .thenReturn(false);
        when(serviceImport.numeroPassageDejaUtilise(10L, 2026, 2)).thenReturn(true);
        when(serviceImport.prochainNumeroPassageLibre(10L, 2026)).thenReturn(3);

        viewModel.inspection().dossierSourceProperty().set(sd);
        viewModel.inspecter();
        viewModel.rattachement().siteSelectionneProperty().set(site);
        viewModel.rattachement().pointSelectionneProperty().set(point);
        viewModel.rattachement().numeroPassageProperty().set(2);

        assertThat(viewModel.avertissementNumeroPassageProperty().get())
                .as("doublon détecté : avertissement non vide proposant le n° libre")
                .contains("existe déjà")
                .contains("3");
        assertThat(viewModel.peutImporter().get())
                .as("import bloqué tant que le n° est en doublon (R5)")
                .isFalse();

        // Forcer l'import expose le message ciblé du doublon (et non le générique « Complétez… »).
        viewModel.importer();
        assertThat(viewModel.etatProperty().get()).isEqualTo(EtatImport.PRET);
        assertThat(viewModel.messageErreurProperty().get())
                .contains("existe déjà")
                .doesNotContain("Complétez");
    }

    @Test
    @DisplayName("#108 : utiliserProchainNumeroLibre adopte le n° libre, efface l'avertissement et débloque")
    void utiliser_prochain_numero_libre_corrige_et_debloque() {
        Site site = site(1L, "640380");
        PointDEcoute point = point(10L, "A1", site.id());
        when(serviceSites.listerPoints(site.id())).thenReturn(List.of(point));
        when(serviceImport.inspecter(sd)).thenReturn(inspecteur.inspecter(sd));
        // Tout n° est libre sauf le n° 2 (déjà pris) ; le prochain libre proposé est le 3.
        when(serviceImport.numeroPassageDejaUtilise(eq(10L), eq(2026), anyInt()))
                .thenReturn(false);
        when(serviceImport.numeroPassageDejaUtilise(10L, 2026, 2)).thenReturn(true);
        when(serviceImport.prochainNumeroPassageLibre(10L, 2026)).thenReturn(3);
        viewModel.inspection().dossierSourceProperty().set(sd);
        viewModel.inspecter();
        viewModel.rattachement().siteSelectionneProperty().set(site);
        viewModel.rattachement().pointSelectionneProperty().set(point);
        viewModel.rattachement().numeroPassageProperty().set(2);
        assertThat(viewModel.peutImporter().get()).isFalse();

        viewModel.controleNumero().utiliserProchainNumeroLibre();

        assertThat(viewModel.rattachement().numeroPassageProperty().get()).isEqualTo(3);
        assertThat(viewModel.avertissementNumeroPassageProperty().get())
                .as("n° libre adopté : plus aucun avertissement")
                .isEmpty();
        assertThat(viewModel.peutImporter().get()).isTrue();
    }

    // --- Étape 4 : exécution ---

    @Test
    @DisplayName("Importer un rattachement complet expose le résultat et passe l'état à TERMINE")
    void importer_termine_avec_resultat() {
        Site site = site(1L, "640380");
        PointDEcoute point = point(10L, "A1", site.id());
        when(serviceSites.listerPoints(site.id())).thenReturn(List.of(point));
        when(serviceImport.inspecter(sd)).thenReturn(inspecteur.inspecter(sd));
        when(serviceImport.importer(eq(sd), eq(10L), any(Prefixe.class), any(), any(), anyBoolean()))
                .thenReturn(new ResultatImport(null, null, "1925492", 2, 6, List.of()));
        prepareRattachement(site, point);

        viewModel.importer();

        assertThat(viewModel.etatProperty().get()).isEqualTo(EtatImport.TERMINE);
        assertThat(viewModel.resultatProperty().get().nombreSequences()).isEqualTo(6);
        assertThat(viewModel.messageErreurProperty().get()).isEmpty();
    }

    @Test
    @DisplayName("#155 : marquerTermine expose les fichiers rejetés (« nom — raison ») du rapport")
    void marquer_termine_expose_les_rejets() {
        RapportImport rapport = new RapportImport(List.of(
                new LigneRapport("ok.wav", StatutImportFichier.IMPORTE, "3 séquence(s)"),
                new LigneRapport("casse.wav", StatutImportFichier.REJETE, "Original illisible"),
                new LigneRapport("notes.txt", StatutImportFichier.IGNORE, "fichier non pertinent")));

        viewModel.marquerTermine(new ResultatImport(null, null, "1925492", 1, 3, List.of(), rapport));

        // Seuls les rejets sont listés, formatés « nom — raison ».
        assertThat(viewModel.rejetsImport()).containsExactly("casse.wav — Original illisible");
    }

    @Test
    @DisplayName("Un refus métier passe l'état à ECHEC et expose le message (résultat null)")
    void importer_echec_expose_le_message() {
        Site site = site(1L, "640380");
        PointDEcoute point = point(10L, "A1", site.id());
        when(serviceSites.listerPoints(site.id())).thenReturn(List.of(point));
        when(serviceImport.inspecter(sd)).thenReturn(inspecteur.inspecter(sd));
        when(serviceImport.importer(eq(sd), eq(10L), any(Prefixe.class), any(), any(), anyBoolean()))
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

    @Test
    @DisplayName(
            "« Conserver les originaux » lit le réglage persisté (défaut activé) et le mémorise au lancement de l'import")
    void conserver_originaux_lit_et_persiste_le_reglage() {
        // À la construction, le défaut (conservation activée) est lu depuis Reglages.
        assertThat(conservation.conserverOriginauxProperty().get()).isTrue();

        // L'utilisateur décoche puis lance l'import : le choix est mémorisé au moment de préparer la demande.
        Site site = site(1L, "640380");
        PointDEcoute point = point(10L, "A1", site.id());
        when(serviceSites.listerPoints(site.id())).thenReturn(List.of(point));
        when(serviceImport.inspecter(sd)).thenReturn(inspecteur.inspecter(sd));
        prepareRattachement(site, point);
        conservation.conserverOriginauxProperty().set(false);

        viewModel.preparerImport();

        verify(reglages).ecrireBooleen(PreferenceConservation.CLE, false);
    }

    @Test
    @DisplayName("Découpage async : marquerEnCours, executerImport (pur), marquerTermine")
    void decoupage_async_des_etats() {
        Site site = site(1L, "640380");
        PointDEcoute point = point(10L, "A1", site.id());
        when(serviceSites.listerPoints(site.id())).thenReturn(List.of(point));
        when(serviceImport.inspecter(sd)).thenReturn(inspecteur.inspecter(sd));
        ResultatImport attendu = new ResultatImport(null, null, "1925492", 2, 6, List.of());
        when(serviceImport.importer(eq(sd), eq(10L), any(Prefixe.class), any(), any(), anyBoolean()))
                .thenReturn(attendu);
        prepareRattachement(site, point);

        viewModel.marquerEnCours();
        assertThat(viewModel.etatProperty().get()).isEqualTo(EtatImport.EN_COURS);

        ResultatImport obtenu = viewModel.executerImport(viewModel.preparerImport());
        assertThat(obtenu).isSameAs(attendu);
        assertThat(viewModel.etatProperty().get())
                .as("executerImport ne mute aucun état")
                .isEqualTo(EtatImport.EN_COURS);

        viewModel.marquerTermine(obtenu);
        assertThat(viewModel.etatProperty().get()).isEqualTo(EtatImport.TERMINE);
        assertThat(viewModel.resultatProperty().get()).isSameAs(attendu);
    }

    @Test
    @DisplayName("#33 : inspecter un dossier mélangé (≥2 enregistreurs) lève l'avertissement mélange")
    void inspecter_detecte_le_melange() {
        when(serviceImport.inspecter(sd))
                .thenReturn(new RapportInspection(
                        sd,
                        null,
                        null,
                        null,
                        List.of(Path.of("PaRecPR111_20260422_200000.wav"), Path.of("PaRecPR222_20260422_200000.wav")),
                        EtatNommage.BRUT,
                        null,
                        List.of()));
        viewModel.inspection().dossierSourceProperty().set(sd);

        viewModel.inspecter();

        assertThat(viewModel.inspection().avertissementMelangeProperty().get()).contains("plusieurs enregistreurs");
    }

    @Test
    @DisplayName("Garde-fou : inspecter des originaux déjà ralentis lève l'avertissement à l'aperçu")
    void inspecter_detecte_les_fichiers_ralentis() {
        // Fréquence d'en-tête 38400 Hz, bien sous le seuil d'un ultrason brut (mode dégradé, pas de log).
        when(serviceImport.inspecter(sd))
                .thenReturn(new RapportInspection(
                        sd,
                        null,
                        null,
                        null,
                        List.of(Path.of("PaRecPR1925492_20260422_200000.wav")),
                        EtatNommage.BRUT,
                        38400,
                        List.of()));
        viewModel.inspection().dossierSourceProperty().set(sd);

        viewModel.inspecter();

        assertThat(viewModel
                        .inspection()
                        .avertissementFichiersRalentisProperty()
                        .get())
                .contains("38400")
                .contains("rejetés");
    }

    @Test
    @DisplayName("#33 : inspecter un dossier dont le journal contredit les WAV lève l'avertissement incohérence")
    void inspecter_detecte_l_incoherence() {
        JournalParse journal = new JournalParse(
                "1925492",
                null,
                LocalDate.of(2026, 4, 22),
                null,
                null,
                null,
                null,
                null,
                true,
                null,
                List.of(),
                List.of());
        when(serviceImport.inspecter(sd))
                .thenReturn(new RapportInspection(
                        sd,
                        Path.of("LogPR1925492.txt"),
                        journal,
                        null,
                        List.of(Path.of("PaRecPR1648011_20260422_203000.wav")), // série ≠ journal
                        EtatNommage.BRUT,
                        null,
                        List.of()));
        viewModel.inspection().dossierSourceProperty().set(sd);

        viewModel.inspecter();

        assertThat(viewModel.inspection().avertissementIncoherenceProperty().get())
                .contains("la série déclarée (1925492)");
        assertThat(viewModel.inspection().avertissementMelangeProperty().get())
                .isEmpty(); // un seul enregistreur côté WAV
    }

    @Test
    @DisplayName("#33 : appliquerProgression alimente la fraction et le libellé d'étape")
    void appliquer_progression_alimente_les_proprietes() {
        viewModel.progression().appliquer(new Progression("Transformation 45/191", 0.62));

        assertThat(viewModel.progression().fractionProperty().get()).isEqualTo(0.62);
        assertThat(viewModel.progression().messageProperty().get()).isEqualTo("Transformation 45/191");
    }

    @Test
    @DisplayName("#54 : marquerEnCours verrouille la navigation, marquerTermine la déverrouille")
    void en_cours_verrouille_la_navigation_termine_la_libere() {
        assertThat(navigation.isNavigationVerrouillee()).isFalse();

        viewModel.marquerEnCours();
        assertThat(navigation.isNavigationVerrouillee()).isTrue();

        viewModel.marquerTermine(new ResultatImport(null, null, "1925492", 1, 5, List.of()));
        assertThat(navigation.isNavigationVerrouillee()).isFalse();
    }

    @Test
    @DisplayName("#54 : un import qui échoue déverrouille aussi la navigation")
    void echec_libere_la_navigation() {
        viewModel.marquerEnCours();
        assertThat(navigation.isNavigationVerrouillee()).isTrue();

        viewModel.marquerEchec("R5 : doublon");
        assertThat(navigation.isNavigationVerrouillee()).isFalse();
    }

    @Test
    @DisplayName("#139 : un dossier est utilisé tel quel ; un .zip est décompressé puis nettoyé après import")
    void extraire_si_zip_puis_nettoyage() throws IOException {
        // Un dossier est renvoyé tel quel (pas de décompression).
        assertThat(viewModel.extraireSiZip(sd)).isEqualTo(sd);

        // Un .zip est décompressé sous le workspace (disque), pas dans le tmpfs /tmp (#139) : on stubbe
        // la racine workspace que le VM interroge pour y router l'extraction.
        Path workspace = Files.createDirectories(racine.resolve("workspace"));
        when(serviceImport.racineWorkspace()).thenReturn(workspace);

        Path zip = racine.resolve("nuit.zip");
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(zip))) {
            zos.putNextEntry(new ZipEntry("LogPR1925492.txt"));
            zos.write("journal".getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }
        Path extrait = viewModel.extraireSiZip(zip);
        assertThat(extrait).isDirectory().isNotEqualTo(zip);
        assertThat(extrait)
                .as("extraction sous le workspace disque, pas dans /tmp")
                .startsWith(workspace);
        assertThat(extrait.resolve("LogPR1925492.txt")).exists();
        assertThat(zip).as("R9 : l'archive source n'est pas modifiée").exists();

        // Après import (marquerTermine), le dossier temporaire du zip est supprimé.
        viewModel.marquerTermine(new ResultatImport(null, null, "1925492", 1, 5, List.of()));
        assertThat(extrait).doesNotExist();
    }

    @Test
    @DisplayName("#230 : nettoyerAuDepart supprime le temporaire d'un .zip abandonné (jamais importé)")
    void nettoyer_au_depart_supprime_le_temporaire_zip() throws IOException {
        Path workspace = Files.createDirectories(racine.resolve("workspace"));
        when(serviceImport.racineWorkspace()).thenReturn(workspace);
        Path zip = racine.resolve("nuit.zip");
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(zip))) {
            zos.putNextEntry(new ZipEntry("LogPR1925492.txt"));
            zos.write("journal".getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }
        Path extrait = viewModel.extraireSiZip(zip);
        assertThat(extrait).isDirectory();

        // L'utilisateur abandonne et quitte l'écran sans lancer l'import (#230) : le temporaire part.
        viewModel.nettoyerAuDepart();
        assertThat(extrait).doesNotExist();
        // Idempotent : un second appel (sans temporaire) ne lève pas.
        assertThatCode(viewModel::nettoyerAuDepart).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("#146 : marquerExtractionEnCours passe en EXTRACTION et réamorce la progression")
    void marquer_extraction_en_cours() {
        viewModel.marquerExtractionEnCours();

        assertThat(viewModel.etatProperty().get()).isEqualTo(EtatImport.EXTRACTION);
        assertThat(viewModel.progression().fractionProperty().get()).isZero();
        assertThat(viewModel.progression().messageProperty().get()).containsIgnoringCase("décompression");

        // L'avancement publié pendant la décompression alimente la même barre que l'import (#33).
        viewModel.progression().appliquer(new Progression("Décompression : 2 / 4 fichiers…", 0.5));
        assertThat(viewModel.progression().fractionProperty().get()).isEqualTo(0.5);
        assertThat(viewModel.progression().messageProperty().get()).contains("2 / 4");
    }

    @Test
    @DisplayName(
            "#139 : l'extraction verrouille la navigation, l'erreur la déverrouille (pas de sortie pendant la décompression)")
    void extraction_verrouille_puis_deverrouille_la_navigation() {
        assertThat(navigation.isNavigationVerrouillee()).isFalse();

        // Pendant la décompression, on ne doit pas pouvoir quitter l'écran (le fil d'arrière-plan continue).
        viewModel.marquerExtractionEnCours();
        assertThat(navigation.isNavigationVerrouillee()).isTrue();

        // Une archive illisible (échec) lève le verrou : on peut de nouveau naviguer.
        viewModel.signalerSourceIllisible("Décompression du zip impossible");
        assertThat(navigation.isNavigationVerrouillee()).isFalse();
    }

    @Test
    @DisplayName("#146 : marquerAnnule remet l'état neutre ANNULE, efface la progression et déverrouille")
    void marquer_annule() {
        viewModel.marquerExtractionEnCours();
        assertThat(navigation.isNavigationVerrouillee()).isTrue();

        viewModel.marquerAnnule();

        assertThat(viewModel.etatProperty().get()).isEqualTo(EtatImport.ANNULE);
        assertThat(viewModel.progression().fractionProperty().get()).isZero();
        assertThat(viewModel.progression().messageProperty().get()).isEmpty();
        assertThat(navigation.isNavigationVerrouillee()).isFalse();
    }

    @Test
    @DisplayName("#146 : LibelleProgression ajoute un temps restant estimé (et rien aux bornes)")
    void eta_temps_restant() {
        // À 25 % après 10 s écoulées, l'extrapolation linéaire donne ~30 s restantes.
        assertThat(LibelleProgression.avecTempsRestant("Copie 5/20", 0.25, 10_000_000_000L))
                .isEqualTo("Copie 5/20 · ~30 s restant");
        // Aux bornes (0 %, 100 %, temps écoulé nul) : libellé inchangé, pas d'ETA absurde.
        assertThat(LibelleProgression.avecTempsRestant("Copie 0/20", 0.0, 10_000_000_000L))
                .isEqualTo("Copie 0/20");
        assertThat(LibelleProgression.avecTempsRestant("Transformation 20/20", 1.0, 10_000_000_000L))
                .isEqualTo("Transformation 20/20");
        assertThat(LibelleProgression.avecTempsRestant("Copie 5/20", 0.25, 0L)).isEqualTo("Copie 5/20");
    }

    @Test
    @DisplayName("#146 : formaterDuree en secondes puis minutes")
    void formater_duree() {
        assertThat(LibelleProgression.formaterDuree(45)).isEqualTo("~45 s");
        assertThat(LibelleProgression.formaterDuree(60)).isEqualTo("~1 min");
        assertThat(LibelleProgression.formaterDuree(90)).isEqualTo("~1 min 30 s");
    }

    // --- Import multi-nuits ---

    @Test
    @DisplayName("Multi-nuits : n° de passage auto-consécutifs depuis le prochain libre, peutImporter vrai")
    void multi_nuits_numerotation_auto() throws IOException {
        Path multi = carteMultiNuits();
        Site site = site(1L, "640380");
        PointDEcoute point = point(10L, "A1", site.id());
        when(serviceSites.listerPoints(site.id())).thenReturn(List.of(point));
        when(serviceImport.inspecter(multi)).thenReturn(inspecteur.inspecter(multi));
        when(serviceImport.prochainNumeroPassageLibre(10L, 2026)).thenReturn(4);
        when(serviceImport.numeroPassageDejaUtilise(eq(10L), eq(2026), anyInt()))
                .thenReturn(false);

        viewModel.inspection().dossierSourceProperty().set(multi);
        viewModel.inspecter();
        viewModel.rattachement().siteSelectionneProperty().set(site);
        viewModel.rattachement().pointSelectionneProperty().set(point);

        assertThat(viewModel.inspection().plusieursNuits()).isTrue();
        assertThat(viewModel.inspection().nuits())
                .extracting(NuitVM::numeroPassagePropose)
                .containsExactly(4, 5, 6);
        assertThat(viewModel.peutImporter().get()).isTrue();
    }

    @Test
    @DisplayName("Multi-nuits : les n° sont proposés même si le rattachement est complété AVANT l'inspection")
    void multi_nuits_numerotation_si_rattachement_avant_inspection() throws IOException {
        Path multi = carteMultiNuits();
        Site site = site(1L, "640380");
        PointDEcoute point = point(10L, "A1", site.id());
        when(serviceSites.listerPoints(site.id())).thenReturn(List.of(point));
        when(serviceImport.inspecter(multi)).thenReturn(inspecteur.inspecter(multi));
        when(serviceImport.prochainNumeroPassageLibre(10L, 2026)).thenReturn(4);
        when(serviceImport.numeroPassageDejaUtilise(eq(10L), eq(2026), anyInt()))
                .thenReturn(false);

        // Rattachement complété AVANT que la carte multi-nuits ne soit inspectée : `plusieursNuits` passe à
        // `true` après le `setAll` de la table, donc la numérotation doit se déclencher sur ce changement.
        viewModel.rattachement().siteSelectionneProperty().set(site);
        viewModel.rattachement().pointSelectionneProperty().set(point);
        viewModel.inspection().dossierSourceProperty().set(multi);
        viewModel.inspecter();

        assertThat(viewModel.inspection().nuits())
                .extracting(NuitVM::numeroPassagePropose)
                .containsExactly(4, 5, 6);
        assertThat(viewModel.peutImporter().get()).isTrue();
    }

    @Test
    @DisplayName("Multi-nuits : décocher une nuit la met à 0 et renumérote les incluses consécutivement")
    void multi_nuits_exclure_renumerote() throws IOException {
        Path multi = carteMultiNuits();
        Site site = site(1L, "640380");
        PointDEcoute point = point(10L, "A1", site.id());
        when(serviceSites.listerPoints(site.id())).thenReturn(List.of(point));
        when(serviceImport.inspecter(multi)).thenReturn(inspecteur.inspecter(multi));
        when(serviceImport.prochainNumeroPassageLibre(10L, 2026)).thenReturn(4);
        when(serviceImport.numeroPassageDejaUtilise(eq(10L), eq(2026), anyInt()))
                .thenReturn(false);
        viewModel.inspection().dossierSourceProperty().set(multi);
        viewModel.inspecter();
        viewModel.rattachement().siteSelectionneProperty().set(site);
        viewModel.rattachement().pointSelectionneProperty().set(point);

        viewModel.inspection().nuits().get(1).inclureProperty().set(false); // exclure la nuit du milieu

        assertThat(viewModel.inspection().nuits())
                .extracting(NuitVM::numeroPassagePropose)
                .containsExactly(4, 0, 5); // exclue → 0 ; les incluses renumérotées 4 puis 5
        assertThat(viewModel.peutImporter().get()).isTrue();
    }

    @Test
    @DisplayName("Multi-nuits : peutImporter est faux si aucune nuit n'est incluse")
    void multi_nuits_aucune_incluse_bloque() throws IOException {
        Path multi = carteMultiNuits();
        Site site = site(1L, "640380");
        PointDEcoute point = point(10L, "A1", site.id());
        when(serviceSites.listerPoints(site.id())).thenReturn(List.of(point));
        when(serviceImport.inspecter(multi)).thenReturn(inspecteur.inspecter(multi));
        when(serviceImport.prochainNumeroPassageLibre(10L, 2026)).thenReturn(4);
        when(serviceImport.numeroPassageDejaUtilise(eq(10L), eq(2026), anyInt()))
                .thenReturn(false);
        viewModel.inspection().dossierSourceProperty().set(multi);
        viewModel.inspecter();
        viewModel.rattachement().siteSelectionneProperty().set(site);
        viewModel.rattachement().pointSelectionneProperty().set(point);
        assertThat(viewModel.peutImporter().get()).isTrue();

        viewModel.inspection().nuits().forEach(nuit -> nuit.inclureProperty().set(false));

        assertThat(viewModel.peutImporter().get()).isFalse();
    }

    @Test
    @DisplayName("Multi-nuits : preparerImportNuits ne retient que les nuits incluses, avec leur n° et le point")
    void multi_nuits_preparer_demande() throws IOException {
        Path multi = carteMultiNuits();
        Site site = site(1L, "640380");
        PointDEcoute point = point(10L, "A1", site.id());
        when(serviceSites.listerPoints(site.id())).thenReturn(List.of(point));
        when(serviceImport.inspecter(multi)).thenReturn(inspecteur.inspecter(multi));
        when(serviceImport.prochainNumeroPassageLibre(10L, 2026)).thenReturn(4);
        when(serviceImport.numeroPassageDejaUtilise(eq(10L), eq(2026), anyInt()))
                .thenReturn(false);
        viewModel.inspection().dossierSourceProperty().set(multi);
        viewModel.inspecter();
        viewModel.rattachement().siteSelectionneProperty().set(site);
        viewModel.rattachement().pointSelectionneProperty().set(point);
        viewModel.inspection().nuits().get(1).inclureProperty().set(false); // exclure la nuit du milieu

        ImportationViewModel.DemandeImportNuits demande =
                viewModel.coordinationNuits().preparerDemande(multi, true);

        assertThat(demande.idPoint()).isEqualTo(10L);
        assertThat(demande.nuits()).hasSize(2);
        assertThat(demande.nuits()).extracting(NuitAImporter::numeroPassage).containsExactly(4, 5);
    }

    @Test
    @DisplayName("Multi-nuits : marquerTermineNuits expose le résultat agrégé (TERMINE, rejets cumulés)")
    void multi_nuits_marquer_termine() {
        RapportImport rapport = new RapportImport(List.of(
                new LigneRapport("ok.wav", StatutImportFichier.IMPORTE, "1 séquence(s)"),
                new LigneRapport("casse.wav", StatutImportFichier.REJETE, "Original illisible")));
        ResultatImport nuit = new ResultatImport(null, null, "1925492", 1, 1, List.of(), rapport);

        viewModel.marquerTermineNuits(new ResultatImportMultiNuits(List.of(nuit, nuit)));

        assertThat(viewModel.resultatNuitsProperty().get().nombrePassages()).isEqualTo(2);
        assertThat(viewModel.etatProperty().get()).isEqualTo(EtatImport.TERMINE);
        // Les rejets des deux nuits sont cumulés.
        assertThat(viewModel.rejetsImport())
                .containsExactly("casse.wav — Original illisible", "casse.wav — Original illisible");
    }

    private Path carteMultiNuits() throws IOException {
        Path multi = Files.createDirectories(racine.resolve("multi"));
        Files.writeString(multi.resolve("LogPR1925492.txt"), LOG, StandardCharsets.UTF_8);
        for (String jour : List.of("20260422", "20260423", "20260424")) {
            Files.writeString(multi.resolve("PaRecPR1925492_" + jour + "_203922.wav"), "wav");
            Files.writeString(multi.resolve("PaRecPR1925492_" + jour + "_204326.wav"), "wav");
        }
        return multi;
    }

    private void prepareRattachement(Site site, PointDEcoute point) {
        viewModel.inspection().dossierSourceProperty().set(sd);
        viewModel.inspecter();
        viewModel.rattachement().siteSelectionneProperty().set(site);
        viewModel.rattachement().pointSelectionneProperty().set(point);
    }

    private static Site site(Long id, String carre) {
        return new Site(id, carre, "Site " + carre, Protocole.STANDARD, null, "2026-05-31", ID_USER);
    }

    private static PointDEcoute point(Long id, String code, Long idSite) {
        return new PointDEcoute(id, code, null, null, null, idSite);
    }
}
