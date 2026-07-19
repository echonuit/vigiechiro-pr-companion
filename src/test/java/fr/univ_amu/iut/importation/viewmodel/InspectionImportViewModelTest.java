package fr.univ_amu.iut.importation.viewmodel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import fr.univ_amu.iut.commun.viewmodel.CompteRendu.Constat;
import fr.univ_amu.iut.commun.viewmodel.CompteRendu.Detail;
import fr.univ_amu.iut.importation.model.AnalyseurLogPR;
import fr.univ_amu.iut.importation.model.InspecteurDossier;
import fr.univ_amu.iut.importation.model.PassageExistant;
import fr.univ_amu.iut.importation.model.ServiceImport;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/// Tests unitaires du sous-VM [InspectionImportViewModel] (étapes 1-2 de M-Import), extrait de
/// [ImportationViewModel] (#183). [ServiceImport] est mocké ; les `RapportInspection` viennent d'un
/// vrai [InspecteurDossier] sur un dossier jetable (`@TempDir`). Aucune base de données.
@ExtendWith(MockitoExtension.class)
class InspectionImportViewModelTest {

    private static final String LOG = "22/04/26 - 16:02:20 PR1925492 Démarrage Passive Recorder numéro de série"
            + " 1925492, V1.01, CPU 600000000, T4.1\n";

    @TempDir
    Path racine;

    @Mock
    private ServiceImport serviceImport;

    private final InspecteurDossier inspecteur = new InspecteurDossier(new AnalyseurLogPR());
    private InspectionImportViewModel vm;
    private Path sd;

    @BeforeEach
    void preparer() throws IOException {
        vm = new InspectionImportViewModel(serviceImport);
        sd = Files.createDirectories(racine.resolve("sd"));
        Files.writeString(sd.resolve("LogPR1925492.txt"), LOG, StandardCharsets.UTF_8);
        Files.writeString(sd.resolve("PaRecPR1925492_20260422_203922.wav"), "wav1");
        Files.writeString(sd.resolve("PaRecPR1925492_20260422_204326.wav"), "wav2");
    }

    @Test
    @DisplayName("Sans dossier choisi, inspecter publie son message d'erreur et reste non inspecté")
    void inspecter_sans_dossier() {
        vm.inspecter();

        assertThat(vm.messageErreurProperty().get()).contains("dossier");
        assertThat(vm.estInspecte()).isFalse();
        assertThat(vm.rapport()).isNull();
    }

    @Test
    @DisplayName("Inspecter un dossier valide vide le message et expose journal, compte et rapport")
    void inspecter_succes() {
        when(serviceImport.inspecter(sd)).thenReturn(inspecteur.inspecter(sd));
        vm.dossierSourceProperty().set(sd);

        vm.inspecter();

        assertThat(vm.messageErreurProperty().get()).isEmpty();
        assertThat(vm.estInspecte()).isTrue();
        assertThat(vm.aUnJournalProperty().get()).isTrue();
        assertThat(vm.nombreOriginauxProperty().get()).isEqualTo(2);
        assertThat(vm.rapport()).isNotNull();
        assertThat(vm.dossier()).isEqualTo(sd);
    }

    @Test
    @DisplayName("Une inspection qui échoue publie le message et remet l'état à zéro")
    void inspecter_echec() {
        when(serviceImport.inspecter(sd)).thenThrow(new RuntimeException("Chemin illisible"));
        vm.dossierSourceProperty().set(sd);

        vm.inspecter();

        assertThat(vm.messageErreurProperty().get()).isEqualTo("Chemin illisible");
        assertThat(vm.estInspecte()).isFalse();
        assertThat(vm.rapport()).isNull();
    }

    @Test
    @DisplayName("#147 : inspecter une nuit déjà en base lève l'avertissement « nuit déjà importée »")
    void inspecter_nuit_deja_importee() {
        when(serviceImport.inspecter(sd)).thenReturn(inspecteur.inspecter(sd));
        when(serviceImport.nuitDejaImportee("1925492", "2026-04-22"))
                .thenReturn(List.of(new PassageExistant(2, 2026, "640380", "Z1")));
        vm.dossierSourceProperty().set(sd);

        vm.inspecter();

        Constat constat = vm.avertissementsProperty().get().constats().getFirst();
        assertThat(constat.fait()).contains("déjà été importée");
        assertThat(constat.details())
                .extracting(Detail::sujet)
                .containsExactly("n° 2 (2026) au carré 640380, point Z1");
    }

    @Test
    @DisplayName("#147/#107 : sans journal, la détection de doublon utilise l'identité déduite des WAV")
    void inspecter_nuit_deja_importee_sans_journal() throws IOException {
        Files.delete(sd.resolve("LogPR1925492.txt")); // mode dégradé : pas de journal
        when(serviceImport.inspecter(sd)).thenReturn(inspecteur.inspecter(sd));
        when(serviceImport.nuitDejaImportee("1925492", "2026-04-22"))
                .thenReturn(List.of(new PassageExistant(2, 2026, "640380", "Z1")));
        vm.dossierSourceProperty().set(sd);

        vm.inspecter();

        assertThat(vm.aUnJournalProperty().get()).isFalse();
        assertThat(vm.avertissementsProperty().get().constats())
                .as("le doublon est détecté même sans journal (identité reconstituée des noms de WAV)")
                .anySatisfy(constat -> {
                    assertThat(constat.fait()).contains("déjà été importée");
                    assertThat(constat.details())
                            .extracting(Detail::sujet)
                            .anySatisfy(sujet -> assertThat(sujet).contains("n° 2"));
                });
    }

    @Test
    @DisplayName("#147 : une nuit absente de la base ne lève aucun avertissement « nuit déjà importée »")
    void inspecter_nuit_inedite() {
        when(serviceImport.inspecter(sd)).thenReturn(inspecteur.inspecter(sd));
        when(serviceImport.nuitDejaImportee("1925492", "2026-04-22")).thenReturn(List.of());
        vm.dossierSourceProperty().set(sd);

        vm.inspecter();

        assertThat(vm.avertissementsProperty().get().estVide())
                .as("nuit inédite : rien à signaler")
                .isTrue();
    }

    @Test
    @DisplayName("reinitialiser remet l'inspection (et son message) à zéro après un succès")
    void reinitialiser_remet_a_zero() {
        when(serviceImport.inspecter(sd)).thenReturn(inspecteur.inspecter(sd));
        vm.dossierSourceProperty().set(sd);
        vm.inspecter();
        assertThat(vm.estInspecte()).isTrue();

        vm.reinitialiser();

        assertThat(vm.estInspecte()).isFalse();
        assertThat(vm.aUnJournalProperty().get()).isFalse();
        assertThat(vm.nombreOriginauxProperty().get()).isZero();
        assertThat(vm.rapport()).isNull();
        assertThat(vm.messageErreurProperty().get()).isEmpty();
        assertThat(vm.nuits()).isEmpty();
        assertThat(vm.plusieursNuits()).isFalse();
    }

    @Test
    @DisplayName("Une seule nuit : la table a une ligne, plusieursNuits est faux")
    void inspecter_une_seule_nuit() {
        when(serviceImport.inspecter(sd)).thenReturn(inspecteur.inspecter(sd));
        vm.dossierSourceProperty().set(sd);

        vm.inspecter();

        assertThat(vm.plusieursNuits()).isFalse();
        assertThat(vm.nuits()).hasSize(1);
        assertThat(vm.nuits().getFirst().date()).hasToString("2026-04-22");
        assertThat(vm.nuits().getFirst().nombreFichiers()).isEqualTo(2);
        assertThat(vm.nuits().getFirst().estIncluse()).isTrue();
    }

    @Test
    @DisplayName("Plusieurs dates : une ligne de nuit par nuit (triées), plusieursNuits vrai")
    void inspecter_plusieurs_nuits() throws IOException {
        Path multi = Files.createDirectories(racine.resolve("multi"));
        Files.writeString(multi.resolve("LogPR1925492.txt"), LOG, StandardCharsets.UTF_8);
        for (String jour : List.of("20260422", "20260423", "20260424")) {
            Files.writeString(multi.resolve("PaRecPR1925492_" + jour + "_203922.wav"), "wav");
            Files.writeString(multi.resolve("PaRecPR1925492_" + jour + "_204326.wav"), "wav");
        }
        when(serviceImport.inspecter(multi)).thenReturn(inspecteur.inspecter(multi));
        vm.dossierSourceProperty().set(multi);

        vm.inspecter();

        assertThat(vm.plusieursNuits()).isTrue();
        assertThat(vm.nuits()).hasSize(3);
        assertThat(vm.nuits())
                .extracting(nuit -> nuit.date().toString())
                .containsExactly("2026-04-22", "2026-04-23", "2026-04-24");
        assertThat(vm.nuits()).allSatisfy(nuit -> {
            assertThat(nuit.nombreFichiers()).isEqualTo(2);
            assertThat(nuit.estIncluse()).isTrue();
        });
    }

    @Test
    @DisplayName("#147 par nuit : seule la nuit déjà en base porte le badge « déjà importée »")
    void nuit_deja_importee_par_ligne() throws IOException {
        Path multi = Files.createDirectories(racine.resolve("multi2"));
        Files.writeString(multi.resolve("LogPR1925492.txt"), LOG, StandardCharsets.UTF_8);
        for (String jour : List.of("20260422", "20260423")) {
            Files.writeString(multi.resolve("PaRecPR1925492_" + jour + "_203922.wav"), "wav");
        }
        when(serviceImport.inspecter(multi)).thenReturn(inspecteur.inspecter(multi));
        when(serviceImport.nuitDejaImportee("1925492", "2026-04-22")).thenReturn(List.of());
        when(serviceImport.nuitDejaImportee("1925492", "2026-04-23"))
                .thenReturn(List.of(new PassageExistant(5, 2026, "640380", "Z1")));
        vm.dossierSourceProperty().set(multi);

        vm.inspecter();

        assertThat(vm.nuits()).hasSize(2);
        assertThat(vm.nuits().get(0).statutDejaImporteeProperty().get()).isEmpty();
        assertThat(vm.nuits().get(1).statutDejaImporteeProperty().get()).contains("déjà importée");
    }
}
