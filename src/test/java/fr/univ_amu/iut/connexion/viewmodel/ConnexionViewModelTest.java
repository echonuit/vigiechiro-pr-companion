package fr.univ_amu.iut.connexion.viewmodel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import fr.univ_amu.iut.commun.api.ClientVigieChiro;
import fr.univ_amu.iut.commun.api.ProfilVigieChiro;
import fr.univ_amu.iut.commun.api.RapportSynchro;
import fr.univ_amu.iut.commun.api.RapprochementVigieChiro;
import fr.univ_amu.iut.commun.api.RapprochementVigieChiro.Phase;
import fr.univ_amu.iut.commun.api.ReponseApi;
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
import org.mockito.InOrder;
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
        when(client.moi()).thenReturn(ReponseApi.succes(PROFIL));
        when(rapprocheur.synchroniser(client)).thenReturn(Optional.of(new RapportSynchro("taxons", 385)));

        assertThat(viewModel.connecter("TOK123")).isEqualTo(ReponseApi.succes(PROFIL));

        assertThat(stockage.profil()).as("persisté").contains(PROFIL);
        verify(rapprocheur).synchroniser(client);
        assertThat(viewModel.resumeSynchro()).isEqualTo("385 taxons");
        viewModel.rafraichir();
        assertThat(viewModel.connecteProperty().get()).isTrue();
        assertThat(viewModel.identiteProperty().get()).contains("Sébastien").contains("Observateur");
    }

    @Test
    @DisplayName("#1776 : la structure (sites) est rapprochée AVANT les dépendants (passages), pas l'ordre du Set")
    void connecter_amorce_la_structure_avant_les_dependants() {
        RapprochementVigieChiro sites = mock(RapprochementVigieChiro.class);
        RapprochementVigieChiro passages = mock(RapprochementVigieChiro.class);
        when(sites.phase()).thenReturn(Phase.STRUCTURE);
        when(passages.phase()).thenReturn(Phase.DEPENDANTE);
        when(client.moi()).thenReturn(ReponseApi.succes(PROFIL));
        when(sites.synchroniser(client)).thenReturn(Optional.of(new RapportSynchro("site(s)", 3)));
        when(passages.synchroniser(client)).thenReturn(Optional.of(new RapportSynchro("passage(s) rapatrié(s)", 2)));
        // Le passage est donné AVANT le site dans le Set : l'ordre ne doit venir que des phases, pas de l'entrée.
        ConnexionViewModel avecDeux = new ConnexionViewModel(stockage, client, Set.of(passages, sites));

        avecDeux.connecter("TOK123");

        InOrder ordre = inOrder(sites, passages);
        ordre.verify(sites).synchroniser(client);
        ordre.verify(passages)
                .synchroniser(client); // les passages, dépendant des points locaux, passent après les sites
    }

    @Test
    @DisplayName("connecter avec un token invalide : connexion effacée, état « non connecté »")
    void connecter_invalide() {
        when(client.moi()).thenReturn(ReponseApi.refuse(401, "token invalide"));

        assertThat(viewModel.connecter("MAUVAIS")).isEqualTo(ReponseApi.refuse(401, "token invalide"));

        verifyNoInteractions(rapprocheur);
        assertThat(stockage.token()).as("effacé").isEmpty();
        viewModel.rafraichir();
        assertThat(viewModel.connecteProperty().get()).isFalse();
        assertThat(viewModel.identiteProperty().get()).isEqualTo("Non connecté");
    }

    @Test
    @DisplayName("#1369 : plateforme injoignable → le jeton est CONSERVÉ, non vérifié, revérifiable")
    void injoignable_conserve_le_jeton() {
        when(client.moi()).thenReturn(ReponseApi.injoignable("délai d'attente dépassé"));

        assertThat(viewModel.connecter("TOK123")).isEqualTo(ReponseApi.injoignable("délai d'attente dépassé"));

        // Avant #1369, le jeton (peut-être parfaitement valide) était jeté à cause du réseau.
        assertThat(stockage.token()).as("jeton conservé").contains("TOK123");
        assertThat(stockage.profil()).as("mais rien de vérifié").isEmpty();
        assertThat(viewModel.jetonAVerifier())
                .as("à revérifier à la prochaine occasion")
                .contains("TOK123");
        verifyNoInteractions(rapprocheur);

        viewModel.rafraichir();
        assertThat(viewModel.connecteProperty().get())
                .as("pas vérifié = pas « connecté » (la saisie reste possible)")
                .isFalse();
        assertThat(viewModel.jetonEnregistreProperty().get())
                .as("mais la déconnexion doit rester possible")
                .isTrue();
        assertThat(viewModel.identiteProperty().get()).contains("non vérifié");
    }

    @Test
    @DisplayName("#1369 : un jeton vérifié n'est pas à revérifier")
    void jeton_verifie_na_rien_a_reverifier() {
        when(client.moi()).thenReturn(ReponseApi.succes(PROFIL));
        when(rapprocheur.synchroniser(client)).thenReturn(Optional.empty());

        viewModel.connecter("TOK123");

        assertThat(viewModel.jetonAVerifier()).isEmpty();
    }

    @Test
    @DisplayName("connecter avec un token vide : vide, sans toucher au réseau ni au stockage")
    void connecter_vide() {
        assertThat(viewModel.connecter("   ")).isInstanceOf(ReponseApi.NonConnecte.class);

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
