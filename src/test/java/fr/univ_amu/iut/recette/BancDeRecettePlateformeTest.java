package fr.univ_amu.iut.recette;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.inject.Injector;
import fr.univ_amu.iut.connexion.di.ConnexionModule;
import fr.univ_amu.iut.connexion.model.StockageConnexion;
import fr.univ_amu.iut.connexion.viewmodel.ConnexionViewModel;
import java.io.IOException;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;

/// Le mode **connecté à la plateforme réelle** du banc (#4304, lot 2 du chantier #4291).
///
/// ## Ce que ce mode doit rendre possible
///
/// Les six premières cases de `S8` filment la connexion **devenue une opération longue** : la barre,
/// son libellé, l'estimation, le bouton **Annuler** atteignable, « Fermer » grisé. Elles n'existent
/// que si la plateforme répond vraiment.
///
/// Or le champ du jeton est un `TextField` et non un `PasswordField` : coller un jeton devant la caméra
/// le grave dans un clip qui part en artefact public. Ce mode passe donc par la porte de #1369 :
/// [ConnexionViewModel#jetonAVerifier] rend le jeton enregistré **tant que le profil est vide**, et la
/// modale le revérifie à son ouverture **sans geste**. Le jeton ne traverse jamais l'écran.
///
/// ## Et il refuse plutôt que de se dégrader
///
/// Sans jeton, ce banc filmerait un écran hors ligne parfaitement convaincant. Le clip ne serait pas
/// faux, il serait **muet sur son propre objet** (ADR 4142), ce qui est pire parce qu'on le regarde en
/// croyant savoir.
@ExtendWith({ApplicationExtension.class, SansExceptionAvalee.class})
class BancDeRecettePlateformeTest {

    /// La forme exacte d'un jeton Vigie-Chiro : trente-deux caractères tirés dans `A-Z0-9`.
    private static final String JETON_REEL = "PLATEFORMEREELLEPOURLEBANC123456";

    private Stage scene;
    private Injector injecteur;

    @Start
    void start(Stage stage) throws IOException {
        // Posée ici pour la même raison que dans `BancDeRecetteJetonTest` : `@Start` s'exécute avant
        // les `@BeforeEach` de la classe.
        System.setProperty("vigiechiro.token", JETON_REEL);
        this.scene = stage;
        this.injecteur = BancDeRecette.surLeChrome()
                .executeur(BancDeRecette.Executeur.ASYNCHRONE)
                .connecteALaPlateforme()
                .montrer(stage);
    }

    @AfterEach
    void rendreLEtatPartage() {
        System.clearProperty("vigiechiro.token");
        System.clearProperty("vigiechiro.workspace");
    }

    @Test
    @DisplayName("#4304 : le jeton réel est enregistré SANS profil, donc la modale le revérifiera seule")
    void le_jeton_est_depose_sans_profil() {
        StockageConnexion reserve = injecteur.getInstance(StockageConnexion.class);

        assertThat(reserve.token())
                .as("le banc dépose bien le jeton de l'environnement dans sa propre réserve")
                .contains(JETON_REEL);
        assertThat(reserve.profil()).as("""
                        Le profil doit rester VIDE. C'est lui qui décide : `jetonAVerifier()` ne rend le
                        jeton que tant qu'aucune identité n'est en cache, et c'est cette absence qui fait
                        revérifier la modale à son ouverture, sans geste et avec sa progression.

                        Un profil posé ici ferait paraître un écran déjà connecté, et les six premières
                        cases de S8 n'auraient plus rien à montrer.""").isEmpty();
        assertThat(injecteur.getInstance(ConnexionViewModel.class).jetonAVerifier())
                .as("la porte de #1369 est ouverte : le jeton attend d'être revérifié")
                .contains(JETON_REEL);
    }

    @Test
    @DisplayName("#4304 : un banc est factice ou réel, jamais les deux")
    void les_deux_modes_s_excluent() {
        assertThatThrownBy(() ->
                        BancDeRecette.surLeChrome().connecteALaPlateforme().connecte("u-banc", "chiro", "Observateur"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("jamais les deux");

        assertThatThrownBy(() -> BancDeRecette.surLeChrome()
                        .connecte("u-banc", "chiro", "Observateur")
                        .connecteALaPlateforme())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("jamais les deux");
    }

    @Test
    @DisplayName("#4304 : sans jeton, le banc refuse de monter et dit quoi poser")
    void sans_jeton_il_refuse() {
        System.clearProperty("vigiechiro.token");
        // Contrôle de la condition AVANT de conclure. Si le poste qui lance ce cas porte lui-même
        // `VIGIECHIRO_TOKEN`, le banc trouvera un jeton et ce cas passerait au vert sans avoir éprouvé
        // le refus. On le dit et on rougit, plutôt que de sauter en silence : un cas sauté ressemble
        // trop à un cas réussi.
        assertThat(ConnexionModule.jetonPonctuel()).as("""
                        Ce cas éprouve le refus du banc quand AUCUN jeton n'est disponible, et votre
                        environnement en porte un. Retirer `VIGIECHIRO_TOKEN` avant de le relancer.

                        Ce n'est pas ce cas qui est en cause : c'est la condition qu'il suppose.""").isEmpty();

        assertThatThrownBy(() -> BancDeRecette.surLeChrome()
                        .executeur(BancDeRecette.Executeur.ASYNCHRONE)
                        .connecteALaPlateforme()
                        .montrer(scene))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("VIGIECHIRO_TOKEN")
                .hasMessageContaining("du PAS qui filme");
    }
}
