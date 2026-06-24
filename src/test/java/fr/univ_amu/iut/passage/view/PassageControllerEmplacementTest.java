package fr.univ_amu.iut.passage.view;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import fr.univ_amu.iut.commun.model.StatutWorkflow;
import fr.univ_amu.iut.commun.model.Verdict;
import fr.univ_amu.iut.commun.view.Lieu;
import fr.univ_amu.iut.commun.view.OuvrirSite;
import fr.univ_amu.iut.commun.viewmodel.ContexteSite;
import fr.univ_amu.iut.passage.model.DetailPassage;
import fr.univ_amu.iut.passage.model.ServicePassage;
import fr.univ_amu.iut.passage.viewmodel.PassageViewModel;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/// Test unitaire de l'**emplacement** (fil d'Ariane) déclaré par [PassageController] : le passage
/// affiche son site (« Carré N ») et son numéro **quel que soit le chemin d'arrivée** (M-Sites ou
/// M-Multisite), et les ancêtres sont cliquables via le contrat socle [OuvrirSite]. Aucun FXML/toolkit
/// requis : `emplacement()` ne lit que le ViewModel et le contexte.
@ExtendWith(MockitoExtension.class)
class PassageControllerEmplacementTest {

    @Mock
    private ServicePassage service;

    @Mock
    private NavigationPassage navigation;

    private PassageController controller(PassageViewModel vm, OuvrirSite ouvrirSite) {
        return new PassageController(vm, idp -> {}, idp -> {}, idp -> {}, idp -> {}, navigation, ouvrirSite);
    }

    @Test
    @DisplayName("L'emplacement = Mes sites › Carré N › Détails du passage N° X (site connu même via multisite)")
    void emplacement_inclut_le_site_et_le_numero() {
        when(service.detailPassage(1L))
                .thenReturn(new DetailPassage(
                        2,
                        2026,
                        "2026-06-22",
                        "20:25:00",
                        "07:47:00",
                        "1925492",
                        StatutWorkflow.TRANSFORME,
                        Verdict.OK,
                        null,
                        4096L,
                        1024L,
                        30,
                        150.0,
                        null));
        PassageViewModel vm = new PassageViewModel(service);
        List<String> ouvertures = new ArrayList<>();
        OuvrirSite ouvrirSite = new OuvrirSite() {
            @Override
            public void ouvrirListe() {
                ouvertures.add("liste");
            }

            @Override
            public void ouvrirDetail(String numeroCarre) {
                ouvertures.add("detail:" + numeroCarre);
            }
        };
        PassageController controller = controller(vm, ouvrirSite);
        // Contexte arrivé depuis multisite (nomSite null) : le carré suffit à situer le passage.
        controller.ouvrirSur(1L, new ContexteSite("640380", "A1", null));

        List<Lieu> fil = controller.emplacement();

        assertThat(fil)
                .extracting(Lieu::libelle)
                .containsExactly("Mes sites", "Carré 640380", "Détails du passage N° 2");
        assertThat(fil.get(2).estCliquable()).isFalse();

        fil.get(0).ouvrir().run();
        fil.get(1).ouvrir().run();
        assertThat(ouvertures).containsExactly("liste", "detail:640380");
    }

    @Test
    @DisplayName("Sans contexte (passage non ouvert), l'emplacement se limite au segment courant")
    void emplacement_sans_contexte() {
        PassageViewModel vm = new PassageViewModel(service);
        OuvrirSite ouvrirSite = new OuvrirSite() {
            @Override
            public void ouvrirListe() {}

            @Override
            public void ouvrirDetail(String numeroCarre) {}
        };

        List<Lieu> fil = controller(vm, ouvrirSite).emplacement();

        assertThat(fil).extracting(Lieu::libelle).containsExactly("Détails du passage");
    }
}
