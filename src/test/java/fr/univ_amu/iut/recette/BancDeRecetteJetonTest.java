package fr.univ_amu.iut.recette;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.Injector;
import fr.univ_amu.iut.commun.api.FournisseurToken;
import fr.univ_amu.iut.connexion.model.StockageConnexion;
import java.io.IOException;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;

/// D'où un banc tire son jeton (#4304, lot 2 du chantier #4291).
///
/// ## La propriété, et pourquoi elle n'allait pas de soi
///
/// `ConnexionModule` lie la source du jeton ainsi :
///
/// ```java
/// () -> jetonPonctuel().or(stockage::token)
/// ```
///
/// Le **jeton ponctuel** - propriété système `vigiechiro.token`, sinon variable d'environnement
/// `VIGIECHIRO_TOKEN` - **l'emporte** sur la connexion enregistrée. C'est juste pour la CLI, à qui
/// `--token` sert précisément à passer outre. Ce ne l'est pas pour un banc.
///
/// Un scénario qui appelle [BancDeRecette#connecte] déclare vouloir une connexion **factice**. Si le
/// processus porte un jeton, le banc le sert à sa place, l'écran garde l'apparence du profil semé, et
/// les appels partent vers la plateforme réelle. Rien ne le signale.
///
/// C'est la figure de l'[ADR
/// 4134](../../../../../../dev-docs/decisions/4134-un-banc-n-emprunte-pas-l-etat-partage-il-ouvre-le-sien.md)
/// d'un cran plus haut : là c'était la fenêtre primaire de TestFX, ici c'est l'environnement du
/// processus. **Un banc n'emprunte pas l'état partagé, il ouvre le sien.**
///
/// ## Pourquoi la propriété système et non la variable d'environnement
///
/// Le défaut se produira par `VIGIECHIRO_TOKEN`, posée dans l'`env:` du pas qui filme. Mais un test
/// Java ne peut pas poser une variable d'environnement à son propre processus, et
/// [fr.univ_amu.iut.connexion.di.ConnexionModule] lit les deux par le **même** chemin, la propriété
/// d'abord. Éprouver la première branche éprouve donc la porte, et c'est la seule des deux qu'un test
/// peut ouvrir.
@ExtendWith({ApplicationExtension.class, SansExceptionAvalee.class})
class BancDeRecetteJetonTest {

    /// Trente-deux caractères en majuscules et chiffres : la forme exacte d'un jeton Vigie-Chiro, que
    /// la plateforme tire dans `A-Z0-9` sur cette longueur. Une valeur qui ressemble à ce qu'elle
    /// imite rend le cas lisible quand il rougit.
    private static final String JETON_AMBIANT = "AMBIANTQUINEDOITJAMAISSERVIR1234";

    private Injector injecteur;

    @Start
    void start(Stage stage) throws IOException {
        // Posée ICI, et non dans un `@BeforeEach` : l'extension TestFX appelle `@Start` depuis son
        // propre `beforeEach`, que JUnit exécute AVANT les `@BeforeEach` de la classe. Une propriété
        // posée là arriverait après que le banc a déjà construit son injecteur, et le cas serait vert
        // sans avoir rien éprouvé.
        System.setProperty("vigiechiro.token", JETON_AMBIANT);
        injecteur = BancDeRecette.surLeChrome()
                .executeur(BancDeRecette.Executeur.ASYNCHRONE)
                .connecte("u-banc", "chiro", "Observateur")
                .montrer(stage);
    }

    /// Le fork est partagé et réutilisé (`reuseForks`), donc une propriété laissée derrière soi
    /// servirait à toutes les classes qui passent après, dans l'ordre où la répartition les a mises.
    /// C'est le défaut même que ce cas décrit, retourné contre le reste de la suite.
    @AfterEach
    void rendreLEtatPartage() {
        System.clearProperty("vigiechiro.token");
        System.clearProperty("vigiechiro.workspace");
    }

    @Test
    @DisplayName("#4304 : un banc connecté en factice ignore le jeton ambiant du processus")
    void le_banc_ignore_le_jeton_ambiant() {
        String duBanc = injecteur.getInstance(StockageConnexion.class).token().orElseThrow();

        assertThat(injecteur.getInstance(FournisseurToken.class).token())
                .as("""
                        Le banc a enregistré sa propre connexion, et c'est elle qui doit servir.

                        Un jeton présent dans l'environnement du processus l'emporte aujourd'hui sur ce
                        que le banc a posé : un scénario qui a demandé une connexion FACTICE parlerait
                        alors à la plateforme réelle, avec un écran qui garde l'apparence du profil semé.

                        C'est ce qui arrivera au premier tournage connecté : le pas porte
                        VIGIECHIRO_TOKEN, et TOUTES les classes de son « -Dtest= » le reçoivent.""")
                .contains(duBanc);
    }
}
