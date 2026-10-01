package fr.univ_amu.iut.recette;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.Injector;
import fr.univ_amu.iut.commun.api.ClientVigieChiro;
import java.io.IOException;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;

/// Vers quel serveur un banc parle (#4332).
///
/// #4304 a posé que le banc **lie sa propre source de jeton** au lieu d'emprunter celle du processus. Il
/// ne l'avait pas fait pour l'**URL** : `ConnexionModule#fournirClient` la lit dans `vigiechiro.url`,
/// sinon `VIGIECHIRO_URL`, et le banc ne remplaçait pas `ClientVigieChiro`. Même figure un champ plus
/// loin, et même
/// [ADR 4134](../../../../../../dev-docs/decisions/4134-un-banc-n-emprunte-pas-l-etat-partage-il-ouvre-le-sien.md) :
/// **un banc n'emprunte pas l'état partagé, il ouvre le sien.**
///
/// `cli-reseau.bats` exporte `VIGIECHIRO_URL` pour pointer sur son bouchon, et une session shell qui l'a
/// gardée faisait parler tous les scénarios dessus : sur les treize classes qui montent le banc, huit ne
/// remplacent pas leur client, donc huit suivaient l'ambiante.
///
/// **Neutraliser plutôt que refuser**, un refus cassant ces huit classes sur tout poste ayant lancé
/// `cli-reseau.bats` pour une exposition latente. Le banc pose sa valeur, `http://localhost:1`, l'idiome
/// hors-ligne des outils de capture : les réponses deviennent `Injoignable`.
///
/// `ClientVigieChiro` n'expose pas son URL, et l'exposer pour un test serait ouvrir la production pour
/// la commodité de l'éprouver. Le cas monte une prise TCP sur un port éphémère, la désigne par
/// `vigiechiro.url`, et compte les connexions : **zéro** est la propriété.
@ExtendWith({ApplicationExtension.class, SansExceptionAvalee.class})
class BancDeRecetteUrlTest {

    private GuichetQuiCompte guichet;

    private Injector injecteur;

    @Start
    void start(Stage stage) throws IOException {
        // Monté et désigné AVANT le banc, comme la propriété du jeton : `@Start` s'exécute avant les
        // `@BeforeEach`, et une URL posée plus tard arriverait après la construction de l'injecteur.
        guichet = GuichetQuiCompte.ouvrir();
        System.setProperty("vigiechiro.url", guichet.urlApi());

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
    void rendreLEtatPartage() throws IOException {
        System.clearProperty("vigiechiro.url");
        System.clearProperty("vigiechiro.workspace");
        fermer();
    }

    /// La fermeture REMONTE : un guichet qui refuse de se fermer laisse un port pris, et le cas
    /// suivant du fork échouerait sur une cause qui n'est pas la sienne.
    private void fermer() throws IOException {
        if (guichet != null) {
            guichet.close();
        }
    }

    @Test
    @DisplayName("#4332 : un banc qui n'a pas déclaré de serveur ne parle pas à celui de l'environnement")
    void le_banc_ignore_l_url_ambiante() {
        injecteur.getInstance(ClientVigieChiro.class).moi();

        assertThat(guichet.connexionsRecues()).as("""
                        L'adresse désignée par `vigiechiro.url` ne doit recevoir AUCUNE connexion.

                        Ce scénario n'a rien déclaré : ni client remplacé, ni `connecteALaPlateforme()`.
                        Son client doit donc pointer là où le banc l'a posé, pas là où l'environnement
                        du processus le dit.

                        Une connexion reçue veut dire que le banc a composé l'ambiante - et qu'un
                        scénario bouchonné parlerait au serveur qu'une autre commande a laissé derrière
                        elle, ou à la production.""").isZero();
    }
}
