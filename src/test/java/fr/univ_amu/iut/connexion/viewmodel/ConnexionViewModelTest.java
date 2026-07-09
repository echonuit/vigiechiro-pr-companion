package fr.univ_amu.iut.connexion.viewmodel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import fr.univ_amu.iut.commun.api.ClientVigieChiro;
import fr.univ_amu.iut.commun.api.ProfilVigieChiro;
import fr.univ_amu.iut.commun.api.RapportSynchro;
import fr.univ_amu.iut.commun.api.RapprochementVigieChiro;
import fr.univ_amu.iut.commun.model.Horloge;
import fr.univ_amu.iut.commun.model.Workspace;
import fr.univ_amu.iut.connexion.model.StockageConnexion;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/// Tests du ViewModel de connexion (#727) : vérification d'un token via `GET /moi` (client mocké),
/// persistance sur succès / effacement sur échec, et reflet de l'état (identité, connecté).
@ExtendWith(MockitoExtension.class)
class ConnexionViewModelTest {

    private static final ProfilVigieChiro PROFIL = new ProfilVigieChiro("6a1b", "Sébastien", "Observateur");

    @TempDir
    Path workspace;

    @Mock
    private ClientVigieChiro client;

    @Mock
    private RapprochementVigieChiro rapprocheur;

    private StockageConnexion stockage;
    private ConnexionViewModel viewModel;

    @BeforeEach
    void preparer() {
        stockage = new StockageConnexion(new Workspace(workspace), Horloge.figeeAu(LocalDate.of(2026, 1, 1)));
        viewModel = new ConnexionViewModel(stockage, client, Set.of(rapprocheur));
    }

    @Test
    @DisplayName("connecter avec un token valide : identité persistée, état « connecté », résumé de synchro")
    void connecter_valide() {
        when(client.moi()).thenReturn(Optional.of(PROFIL));
        when(rapprocheur.synchroniser(client)).thenReturn(Optional.of(new RapportSynchro("taxons", 385)));

        assertThat(viewModel.connecter("TOK123")).contains(PROFIL);

        assertThat(stockage.profil()).as("persisté").contains(PROFIL);
        verify(rapprocheur).synchroniser(client);
        assertThat(viewModel.resumeSynchro()).isEqualTo("385 taxons");
        viewModel.rafraichir();
        assertThat(viewModel.connecteProperty().get()).isTrue();
        assertThat(viewModel.identiteProperty().get()).contains("Sébastien").contains("Observateur");
    }

    @Test
    @DisplayName("connecter avec un token invalide : connexion effacée, état « non connecté »")
    void connecter_invalide() {
        when(client.moi()).thenReturn(Optional.empty());

        assertThat(viewModel.connecter("MAUVAIS")).isEmpty();

        verifyNoInteractions(rapprocheur);
        assertThat(stockage.token()).as("effacé").isEmpty();
        viewModel.rafraichir();
        assertThat(viewModel.connecteProperty().get()).isFalse();
        assertThat(viewModel.identiteProperty().get()).isEqualTo("Non connecté");
    }

    @Test
    @DisplayName("connecter avec un token vide : vide, sans toucher au réseau ni au stockage")
    void connecter_vide() {
        assertThat(viewModel.connecter("   ")).isEmpty();

        verifyNoInteractions(client, rapprocheur);
        assertThat(stockage.token()).isEmpty();
    }

    @Test
    @DisplayName("deconnecter efface la connexion locale")
    void deconnecter() {
        stockage.enregistrer("TOK", PROFIL);

        viewModel.deconnecter();

        viewModel.rafraichir();
        assertThat(viewModel.connecteProperty().get()).isFalse();
        assertThat(stockage.token()).isEmpty();
    }
}
