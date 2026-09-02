package fr.univ_amu.iut.importation.viewmodel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import fr.univ_amu.iut.commun.viewmodel.CompteRendu.Constat;
import fr.univ_amu.iut.commun.viewmodel.CompteRendu.Detail;
import fr.univ_amu.iut.fixture.JournalDeCapteur;
import fr.univ_amu.iut.importation.model.AnalyseurLogPR;
import fr.univ_amu.iut.importation.model.InspecteurDossier;
import fr.univ_amu.iut.importation.model.PassageExistant;
import fr.univ_amu.iut.importation.model.ServiceImport;
import java.io.IOException;
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

/// Tests unitaires du sous-VM [InspectionImportViewModel] (étapes 1-2 de M-Import), extrait de
/// [ImportationViewModel] (#183). [ServiceImport] est mocké ; les `RapportInspection` viennent d'un
/// vrai [InspecteurDossier] sur un dossier jetable (`@TempDir`). Aucune base de données.
@ExtendWith(MockitoExtension.class)
class InspectionImportViewModelTest {

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
        JournalDeCapteur.ecrire(sd, "1925492", LocalDate.of(2026, 4, 22));
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
        JournalDeCapteur.ecrire(multi, "1925492", LocalDate.of(2026, 4, 22));
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
        JournalDeCapteur.ecrire(multi, "1925492", LocalDate.of(2026, 4, 22));
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

    @Test
    @DisplayName("#5091 : un support en lecture seule remonte jusqu'au compte rendu de l'inspection")
    void un_support_en_lecture_seule_atteint_le_compte_rendu() {
        // Le fil que #5091 n'avait pas éprouvé : la sonde avait ses cas, le bandeau les siens, et rien
        // ne traversait de l'une à l'autre. Même trou que celui de #4990 sur les indices du journal, et
        // c'est la couture posée pour l'aperçu qui le rend visible.
        when(serviceImport.inspecter(sd)).thenReturn(inspecteur.inspecter(sd));
        vm.definirSondeDuSupport(chemin -> true);
        vm.dossierSourceProperty().set(sd);

        vm.inspecter();

        assertThat(vm.avertissementsProperty().get().constats())
                .as("le quatrième constat atteint le compte rendu, il ne s'arrête pas au modèle")
                .anySatisfy(constat -> assertThat(constat.fait()).contains("lecture seule"));
    }

    @Test
    @DisplayName("#5091 : un support inscriptible ne fait apparaître aucun constat")
    void un_support_inscriptible_n_ajoute_rien() {
        // Contrôle négatif. Sans lui, une sonde branchée en dur sur `true` passerait le cas d'au-dessus.
        when(serviceImport.inspecter(sd)).thenReturn(inspecteur.inspecter(sd));
        vm.definirSondeDuSupport(chemin -> false);
        vm.dossierSourceProperty().set(sd);

        vm.inspecter();

        assertThat(vm.avertissementsProperty().get().constats())
                .noneSatisfy(constat -> assertThat(constat.fait()).contains("lecture seule"));
    }
}
