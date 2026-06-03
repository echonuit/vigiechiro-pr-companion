package fr.univ_amu.iut.bibliotheque;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import fr.univ_amu.iut.bibliotheque.model.EntreeBiblio;
import fr.univ_amu.iut.bibliotheque.model.ExportBiblioSons;
import fr.univ_amu.iut.bibliotheque.model.ServiceBibliotheque;
import fr.univ_amu.iut.bibliotheque.viewmodel.BibliothequeViewModel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/// Tests unitaires du [BibliothequeViewModel] : chargement de la table depuis le service mocké,
/// résumé, chemin audio suivant la sélection, et délégation de l'export vers le dossier choisi. Pas
/// de base de données ni de JavaFX UI (le VM n'expose que des `javafx.beans`/`javafx.collections`).
@ExtendWith(MockitoExtension.class)
class BibliothequeViewModelTest {

    @Mock
    private ServiceBibliotheque service;

    private static EntreeBiblio entree(String taxon, String chemin) {
        return new EntreeBiblio(taxon, "seq.wav", chemin, 45000, null);
    }

    @Test
    @DisplayName("charger() peuple la table et résume le nombre de sons de référence")
    void charger_peuple_et_resume() {
        when(service.exporterBibliotheque())
                .thenReturn(
                        new ExportBiblioSons(List.of(entree("NYCNOC", "/ws/n.wav"), entree("PIPPIP", "/ws/p.wav"))));
        BibliothequeViewModel vm = new BibliothequeViewModel(service);

        vm.charger();

        assertThat(vm.entrees()).hasSize(2);
        assertThat(vm.biblioNonVideProperty().get()).isTrue();
        assertThat(vm.resumeProperty().get()).contains("2 son");
    }

    @Test
    @DisplayName("Bibliothèque vide : résumé d'invite et export désactivé")
    void biblio_vide() {
        when(service.exporterBibliotheque()).thenReturn(new ExportBiblioSons(List.of()));
        BibliothequeViewModel vm = new BibliothequeViewModel(service);

        vm.charger();

        assertThat(vm.entrees()).isEmpty();
        assertThat(vm.biblioNonVideProperty().get()).isFalse();
        assertThat(vm.resumeProperty().get()).contains("Aucun son");
    }

    @Test
    @DisplayName("Le chemin audio suit la sélection courante")
    void chemin_audio_suit_la_selection() {
        when(service.exporterBibliotheque()).thenReturn(new ExportBiblioSons(List.of(entree("NYCNOC", "/ws/n.wav"))));
        BibliothequeViewModel vm = new BibliothequeViewModel(service);
        vm.charger();

        assertThat(vm.cheminAudioCourantProperty().get()).isNull();
        vm.selectionProperty().set(vm.entrees().get(0));
        assertThat(vm.cheminAudioCourantProperty().get()).hasToString("/ws/n.wav");

        vm.selectionProperty().set(null);
        assertThat(vm.cheminAudioCourantProperty().get()).isNull();
    }

    @Test
    @DisplayName("exporter() écrit le CSV et copie les sons dans le dossier choisi")
    void exporter_materialise_dans_le_dossier(@TempDir Path source, @TempDir Path dest) throws Exception {
        Path wav = Files.writeString(source.resolve("ref.wav"), "RIFF");
        when(service.exporterBibliotheque())
                .thenReturn(new ExportBiblioSons(List.of(entree("NYCNOC", wav.toString()))));
        BibliothequeViewModel vm = new BibliothequeViewModel(service);
        vm.charger();

        boolean ok = vm.exporter(dest);

        assertThat(ok).isTrue();
        assertThat(dest.resolve(ExportBiblioSons.NOM_CSV)).exists();
        assertThat(dest.resolve("ref.wav")).hasContent("RIFF");
        assertThat(vm.messageProperty().get()).contains("1 fichier(s) son");
    }

    @Test
    @DisplayName("exporter() sans chargement préalable est ignoré (aucun export en mémoire)")
    void exporter_sans_chargement_ignore(@TempDir Path dest) {
        BibliothequeViewModel vm = new BibliothequeViewModel(service);
        assertThat(vm.exporter(dest)).isFalse();
    }
}
