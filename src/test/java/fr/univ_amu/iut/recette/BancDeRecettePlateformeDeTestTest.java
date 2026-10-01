package fr.univ_amu.iut.recette;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.inject.Injector;
import fr.univ_amu.iut.commun.api.ClientVigieChiro;
import fr.univ_amu.iut.commun.api.plateforme.PlateformeDeTest;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;

/// Vers quel serveur parle un banc qui a déclaré la **plateforme de test** (#5665).
///
/// La quatrième cible du banc, à côté du hors-ligne, de la plateforme réelle et de la connexion
/// factice. Sœur de `BancDeRecetteUrlTest`, qui tient le hors-ligne, et sur le même patron : deux prises
/// TCP comptent leurs connexions, sans serveur HTTP ni Docker. L'une est désignée par `vigiechiro.url`,
/// l'**ambiante**, qui ne doit rien recevoir ; l'autre est l'adresse de la plateforme déclarée, qui doit
/// être composée.
///
/// L'accès à la plateforme est fabriqué ici, sans la monter : ce qu'on éprouve est que la déclaration
/// produit la bonne cible, pas la plateforme elle-même, que `DepotSurLaPlateformeDeTestTest` tient.
@ExtendWith({ApplicationExtension.class, SansExceptionAvalee.class})
class BancDeRecettePlateformeDeTestTest {

    private static final String JETON_FACTICE = "JETONFACTICEDELAPLATEFORMEDETEST";

    private final AtomicInteger recuesAmbiante = new AtomicInteger();
    private final AtomicInteger recuesPlateforme = new AtomicInteger();

    private ServerSocket ambiante;
    private ServerSocket plateforme;
    private Injector injecteur;

    @Start
    void start(Stage stage) throws IOException {
        ambiante = guichet(recuesAmbiante);
        plateforme = guichet(recuesPlateforme);
        System.setProperty("vigiechiro.url", "http://127.0.0.1:" + ambiante.getLocalPort() + "/api/v1");

        injecteur = BancDeRecette.surLeChrome()
                .executeur(BancDeRecette.Executeur.SYNCHRONE)
                .surLaPlateformeDeTest(acces(), "observatrice")
                .montrer(stage);
    }

    private PlateformeDeTest.Acces acces() {
        return new PlateformeDeTest.Acces(
                "http://127.0.0.1:" + plateforme.getLocalPort() + "/api/v1",
                "https://127.0.0.1:1",
                Map.of("observatrice", JETON_FACTICE),
                Map.of());
    }

    /// Une prise TCP qui compte ce qu'elle reçoit et le referme : ce qu'on veut savoir est si le banc a
    /// composé cette adresse, pas ce qu'il y a dit.
    private static ServerSocket guichet(AtomicInteger recues) throws IOException {
        ServerSocket prise = new ServerSocket(0, 0, InetAddress.getLoopbackAddress());
        Thread accueil = new Thread(() -> {
            while (!prise.isClosed()) {
                try (Socket entrant = prise.accept()) {
                    recues.incrementAndGet();
                } catch (IOException fermeture) {
                    return;
                }
            }
        });
        accueil.setDaemon(true);
        accueil.start();
        return prise;
    }

    /// Le fork est partagé : une propriété ou un port laissés derrière soi serviraient aux classes
    /// suivantes.
    @AfterEach
    void rendreLEtatPartage() throws IOException {
        System.clearProperty("vigiechiro.url");
        System.clearProperty("vigiechiro.workspace");
        if (ambiante != null) {
            ambiante.close();
        }
        if (plateforme != null) {
            plateforme.close();
        }
    }

    @Test
    @DisplayName("#5665 : un banc qui déclare la plateforme de test parle à elle, jamais à l'ambiante")
    void le_banc_vise_la_plateforme_de_test_declaree() {
        injecteur.getInstance(ClientVigieChiro.class).moi();

        assertThat(recuesAmbiante.get()).as("""
                        L'adresse désignée par `vigiechiro.url` ne doit recevoir AUCUNE connexion : ce
                        scénario a déclaré la plateforme de test, et c'est elle qu'il vise.""").isZero();
        assertThat(recuesPlateforme.get()).as("""
                        L'adresse de la plateforme de test déclarée doit être composée. Zéro voudrait
                        dire que le banc l'a ignorée, et qu'un scénario qui croit jouer contre la vraie
                        API joue en réalité hors ligne.""").isPositive();
    }

    @Test
    @DisplayName("#5665 : la plateforme de test exclut les trois autres déclarations, dans les deux sens")
    void les_declarations_s_excluent() {
        assertThatThrownBy(() -> BancDeRecette.surLeChrome()
                        .connecte("u", "chiro", "Observateur")
                        .surLaPlateformeDeTest(acces(), "observatrice"))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> BancDeRecette.surLeChrome()
                        .surLaPlateformeDeTest(acces(), "observatrice")
                        .connecteALaPlateforme())
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() ->
                        BancDeRecette.surLeChrome().parleALaPlateforme().surLaPlateformeDeTest(acces(), "observatrice"))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> BancDeRecette.surLeChrome()
                        .surLaPlateformeDeTest(acces(), "observatrice")
                        .connecte("u", "chiro", "Observateur"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("#5665 : un utilisateur que l'état de départ ne déclare pas est refusé à la déclaration")
    void une_cle_non_declaree_est_refusee_tout_de_suite() {
        assertThatThrownBy(() -> BancDeRecette.surLeChrome().surLaPlateformeDeTest(acces(), "inconnue"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("inconnue");
    }
}
