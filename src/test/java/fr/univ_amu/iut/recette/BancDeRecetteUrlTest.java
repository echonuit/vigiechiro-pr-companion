package fr.univ_amu.iut.recette;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.Injector;
import fr.univ_amu.iut.commun.api.ClientVigieChiro;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.atomic.AtomicInteger;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;

/// Vers quel serveur un banc parle (#4332).
///
/// ## Le jumeau du défaut que #4304 avait fermé
///
/// #4304 a posé que le banc **lie sa propre source de jeton** au lieu d'emprunter celle du processus,
/// parce qu'un scénario déclaré factice parlerait sinon à la plateforme réelle. Il ne l'avait pas fait
/// pour l'**URL** : `ConnexionModule#fournirClient` la lit dans `vigiechiro.url`, sinon
/// `VIGIECHIRO_URL`, et le banc ne surchargeait pas `ClientVigieChiro`.
///
/// C'est la même figure un champ plus loin, et c'est
/// l'[ADR 4134](../../../../../../dev-docs/decisions/4134-un-banc-n-emprunte-pas-l-etat-partage-il-ouvre-le-sien.md) :
/// **un banc n'emprunte pas l'état partagé, il ouvre le sien.**
///
/// Ce n'était pas théorique. `cli-reseau.bats` exporte `VIGIECHIRO_URL` pour pointer sur son
/// bouchon ; une session shell qui l'a gardée faisait parler **tous** les scénarios de banc dessus.
/// Mesuré à l'écriture de ce cas : sur les treize classes qui montent le banc, **huit** ne remplacent
/// pas leur client, donc huit suivaient l'ambiante.
///
/// ## Pourquoi neutraliser plutôt que refuser
///
/// Refuser - s'arrêter quand une URL traîne dans l'environnement - aurait été plus bruyant et plus
/// symétrique du refus qui garde `connecteALaPlateforme()`. Mais il aurait cassé ces huit classes sur
/// tout poste ayant lancé `cli-reseau.bats`, pour une exposition qui n'est que latente. Le banc pose
/// donc **sa** valeur, et `http://localhost:1` est l'idiome hors-ligne déjà employé par les outils de
/// capture : les réponses deviennent `Injoignable`, ce qui est le comportement juste d'un scénario qui
/// n'a pas déclaré vouloir un serveur.
///
/// ## Ce que ce cas mesure, et pourquoi pas la chaîne
///
/// `ClientVigieChiro` n'expose pas son URL, et l'exposer pour un test serait ouvrir la production pour
/// la commodité de l'éprouver. Le cas monte une prise TCP sur un port éphémère, la désigne
/// par `vigiechiro.url`, et compte les connexions reçues. **Zéro connexion** est la propriété ; une
/// connexion dirait que le banc a composé l'ambiante.
@ExtendWith({ApplicationExtension.class, SansExceptionAvalee.class})
class BancDeRecetteUrlTest {

    private final AtomicInteger recues = new AtomicInteger();

    private ServerSocket guichet;

    private Thread accueil;

    private Injector injecteur;

    @Start
    void start(Stage stage) throws IOException {
        // Monté et désigné AVANT le banc, comme la propriété du jeton : `@Start` s'exécute avant les
        // `@BeforeEach`, et une URL posée plus tard arriverait après la construction de l'injecteur.
        // Une simple prise TCP, et non un serveur HTTP : `com.sun.net.httpserver` vit dans le module
        // `jdk.httpserver`, que le `module-info.java` de production ne lit pas. L'y ajouter pour un test
        // élargirait la surface du produit pour la commodité de l'éprouver. Or compter les CONNEXIONS
        // suffit : ce qu'on veut savoir est si le banc a composé cette adresse, pas ce qu'il y a dit.
        guichet = new ServerSocket(0, 0, InetAddress.getLoopbackAddress());
        accueil = new Thread(() -> {
            while (!guichet.isClosed()) {
                try (Socket entrant = guichet.accept()) {
                    recues.incrementAndGet();
                } catch (IOException fermeture) {
                    return;
                }
            }
        });
        accueil.setDaemon(true);
        accueil.start();
        System.setProperty("vigiechiro.url", "http://127.0.0.1:" + guichet.getLocalPort() + "/api/v1");

        injecteur = BancDeRecette.surLeChrome()
                .executeur(BancDeRecette.Executeur.SYNCHRONE)
                // Une connexion FACTICE, et elle est indispensable au cas : sans jeton,
                // `TransportVigieChiro.lire` rend `nonConnecte` SANS appeler - « pas de jeton : l appel
                // n a pas lieu ». Le cas serait alors vert quelle que soit l URL, c est-a-dire vide.
                .connecte("u-banc", "chiro", "Observateur")
                .montrer(stage);
    }

    /// Le fork est partagé et réutilisé : une propriété laissée derrière soi servirait à toutes les
    /// classes qui passent après, dans l'ordre où la répartition les a mises. C'est le défaut même que
    /// ce cas décrit, retourné contre le reste de la suite.
    @AfterEach
    void rendreLEtatPartage() {
        System.clearProperty("vigiechiro.url");
        System.clearProperty("vigiechiro.workspace");
        fermer();
    }

    private void fermer() {
        try {
            if (guichet != null) {
                guichet.close();
            }
        } catch (IOException fermeture) {
            // Rien à réparer : le guichet n'existe que le temps du cas.
        }
    }

    @Test
    @DisplayName("#4332 : un banc qui n'a pas déclaré de serveur ne parle pas à celui de l'environnement")
    void le_banc_ignore_l_url_ambiante() {
        injecteur.getInstance(ClientVigieChiro.class).moi();

        assertThat(recues.get()).as("""
                        L'adresse désignée par `vigiechiro.url` ne doit recevoir AUCUNE connexion.

                        Ce scénario n'a rien déclaré : ni client remplacé, ni `connecteALaPlateforme()`.
                        Son client doit donc pointer là où le banc l'a posé, pas là où l'environnement
                        du processus le dit.

                        Une connexion reçue veut dire que le banc a composé l'ambiante - et qu'un
                        scénario bouchonné parlerait au serveur qu'une autre commande a laissé derrière
                        elle, ou à la production.""").isZero();
    }
}
