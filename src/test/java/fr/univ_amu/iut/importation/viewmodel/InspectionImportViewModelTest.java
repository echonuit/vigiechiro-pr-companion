package fr.univ_amu.iut.importation.viewmodel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import fr.univ_amu.iut.importation.model.AnalyseurLogPR;
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
    @DisplayName("Sans dossier choisi, inspecter renvoie un message et reste non inspecté")
    void inspecter_sans_dossier() {
        String erreur = vm.inspecter();

        assertThat(erreur).contains("dossier");
        assertThat(vm.estInspecte()).isFalse();
        assertThat(vm.rapport()).isNull();
    }

    @Test
    @DisplayName("Inspecter un dossier valide renvoie null et expose journal, compte et rapport")
    void inspecter_succes() {
        when(serviceImport.inspecter(sd)).thenReturn(inspecteur.inspecter(sd));
        vm.dossierSourceProperty().set(sd);

        String erreur = vm.inspecter();

        assertThat(erreur).isNull();
        assertThat(vm.estInspecte()).isTrue();
        assertThat(vm.aUnJournalProperty().get()).isTrue();
        assertThat(vm.nombreOriginauxProperty().get()).isEqualTo(2);
        assertThat(vm.rapport()).isNotNull();
        assertThat(vm.dossier()).isEqualTo(sd);
    }

    @Test
    @DisplayName("Une inspection qui échoue renvoie le message et remet l'état à zéro")
    void inspecter_echec() {
        when(serviceImport.inspecter(sd)).thenThrow(new RuntimeException("Chemin illisible"));
        vm.dossierSourceProperty().set(sd);

        String erreur = vm.inspecter();

        assertThat(erreur).isEqualTo("Chemin illisible");
        assertThat(vm.estInspecte()).isFalse();
        assertThat(vm.rapport()).isNull();
    }

    @Test
    @DisplayName("reinitialiser remet l'inspection à zéro après un succès")
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
    }
}
