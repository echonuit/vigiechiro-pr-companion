package fr.univ_amu.iut.recette;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import javafx.application.Platform;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.testfx.util.WaitForAsyncUtils;

/// Fait rougir un cas quand une exception meurt dans un gestionnaire d evenement JavaFX (#4409).
///
/// ## Ce qu il ferme
///
/// Une exception levee dans un `setOnMouseClicked`, un `setOnAction` ou un ecouteur de propriete ne
/// remonte a personne : JavaFX la donne au gestionnaire d exception non capturee du fil, qui par
/// defaut l imprime sur la sortie d erreur. Le geste n a pas lieu, et le test ne peut rapporter que
/// l ABSENCE d effet.
///
/// C est ce qui a rendu #4408 indechiffrable. Le cas disait « la carte a-t-elle ouvert quoi que ce
/// soit ? L accueil est encore la » - tout ce qu il savait dire. Or la chaine derriere ce clic a
/// trois maillons qui peuvent lever : une requete en base, un `FXMLLoader.load()`, et la
/// construction Guice du controleur. Le symptome d une exception y est IDENTIQUE a celui d une
/// lenteur, et une occurrence n a donc rien appris de sa cause.
///
/// C est l article A12 applique au banc : aucun echec silencieux.
///
/// ## Ce qu il ne ferme pas
///
/// - **Une exception avalee par un `catch` du produit.** Elle n arrive jamais au fil, et c est le
///   propos d un autre garde. Celui-ci voit ce qui SORT, pas ce qu on retient.
/// - **Une exception levee hors du cas**, entre deux classes ou pendant l arret du toolkit. Le
///   gestionnaire est pose au debut du cas et retire a sa fin, precisement pour ne pas faire porter
///   a une classe ce qu une autre a laisse.
/// - **Le fil du test lui-meme.** Une assertion qui echoue s occupe deja d elle-meme.
///
/// ## Pourquoi il se pose et se retire a chaque cas
///
/// TestFX REUTILISE le Stage primaire d une classe a l autre dans un meme fork, et le fil JavaFX est
/// donc partage. Un gestionnaire laisse en place ferait rougir la classe SUIVANTE pour l exception
/// de la precedente - le defaut exact que le job `ordre-alternatif` existe pour attraper. Il est
/// donc pose avant chaque cas et l ancien est rendu apres.
public final class SansExceptionAvalee implements BeforeEachCallback, AfterEachCallback {

    /// Ce que le fil JavaFX a laisse tomber pendant le cas courant.
    ///
    /// Une liste et non un seul rejet : un geste peut en produire plusieurs, et n en garder qu un
    /// ferait choisir au hasard lequel le lecteur verra.
    private final List<Throwable> rejets = new CopyOnWriteArrayList<>();

    private Thread.UncaughtExceptionHandler precedent;
    private boolean pose;

    @Override
    public void beforeEach(ExtensionContext contexte) {
        rejets.clear();
        pose = false;
        if (!Platform.isFxApplicationThread()) {
            // Le fil JavaFX tourne deja - c est ApplicationExtension qui l a demarre. On lui pose le
            // gestionnaire depuis son propre fil, seul endroit ou `Thread.currentThread()` le designe.
            surLeFilFx(() -> {
                precedent = Thread.currentThread().getUncaughtExceptionHandler();
                Thread.currentThread().setUncaughtExceptionHandler((fil, rejet) -> rejets.add(rejet));
                pose = true;
            });
        }
    }

    /// Execute `action` SUR le fil de JavaFX et attend qu elle soit faite.
    ///
    /// Attendre n est pas une precaution : `Thread.currentThread()` ne designe le fil JavaFX que
    /// depuis lui-meme, et rendre la main avant que l action soit passee poserait le gestionnaire
    /// apres le premier geste du cas.
    private static void surLeFilFx(Runnable action) {
        try {
            WaitForAsyncUtils.asyncFx(action).get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError("interrompu en posant le garde sur le fil JavaFX", e);
        } catch (ExecutionException e) {
            throw new AssertionError("le garde n a pas pu s installer sur le fil JavaFX", e);
        }
    }

    @Override
    public void afterEach(ExtensionContext contexte) {
        if (pose) {
            surLeFilFx(() -> Thread.currentThread().setUncaughtExceptionHandler(precedent));
        }
        if (rejets.isEmpty()) {
            return;
        }
        // Le PREMIER rejet porte la cause ; les suivants en decoulent le plus souvent. On le remonte
        // comme cause pour que la trace soit lisible, et on dit combien il y en a eu.
        Throwable premier = rejets.get(0);
        String combien = rejets.size() == 1 ? "" : " (et " + (rejets.size() - 1) + " autre(s))";
        throw new AssertionError(
                "une exception est morte dans un gestionnaire du fil JavaFX" + combien
                        + " : le geste n a pas eu lieu, et sans ce garde le cas n aurait pu rapporter"
                        + " que l absence d effet",
                premier);
    }
}
