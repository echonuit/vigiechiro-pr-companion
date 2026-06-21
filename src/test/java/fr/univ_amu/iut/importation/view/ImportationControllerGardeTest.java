package fr.univ_amu.iut.importation.view;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import fr.univ_amu.iut.commun.model.Horloge;
import fr.univ_amu.iut.commun.viewmodel.NavigationViewModel;
import fr.univ_amu.iut.importation.model.ServiceImport;
import fr.univ_amu.iut.importation.viewmodel.ImportationViewModel;
import fr.univ_amu.iut.sites.model.ServiceSites;
import java.nio.file.Path;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/// Garde de saisie de M-Import : quitter l'écran avec un **import préparé non lancé** (dossier choisi,
/// état PRET) doit déclencher la confirmation du socle. On vérifie le prédicat
/// [ImportationController#aSaisieNonEnregistree()] (lecture du ViewModel, sans FXML). L'import EN COURS
/// est, lui, déjà bloqué par le verrou de navigation (#54).
@ExtendWith(MockitoExtension.class)
class ImportationControllerGardeTest {

    @Mock
    private ServiceImport serviceImport;

    @Mock
    private ServiceSites serviceSites;

    @Mock
    private Horloge horloge;

    @Test
    @DisplayName("aSaisieNonEnregistree : faux sans dossier, vrai dès qu'un dossier est choisi (état PRET)")
    void garde_reflete_l_import_prepare() {
        when(horloge.aujourdhui()).thenReturn(LocalDate.of(2026, 6, 21));
        ImportationViewModel viewModel =
                new ImportationViewModel(serviceImport, serviceSites, horloge, "u-1", new NavigationViewModel());
        ImportationController controller = new ImportationController(viewModel);

        assertThat(controller.aSaisieNonEnregistree()).isFalse();

        viewModel.dossierSourceProperty().set(Path.of("/tmp/nuit-sd"));

        assertThat(controller.aSaisieNonEnregistree()).isTrue();
    }
}
