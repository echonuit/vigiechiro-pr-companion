package fr.univ_amu.iut.recette;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.testfx.api.FxRobot;
import org.testfx.util.WaitForAsyncUtils;

/// Choisir une entrée dans un menu, **de façon qu'on voie choisir** (#4177).
///
/// ## Pourquoi ce geste est partagé
///
/// Un clip qui montre le menu s'ouvrir puis l'écran changer laisse le spectateur deviner **quelle**
/// entrée a été prise. Retour de la revue, mot pour mot : « on ne voit pas la souris aller sur le
/// menuitem réglage ».
///
/// La première correction avait remplacé `MenuItem.fire()` par un vrai clic, ce qui était nécessaire et
/// **pas suffisant** : `clickOn(libellé)` téléporte le pointeur et clique dans la foulée, et le menu se
/// referme aussitôt. Mesuré en extrayant les images autour du clic - à 56 % comme à 64 % du clip, le
/// curseur était encore sur le bouton du menu, et l'instant où il repose sur l'entrée n'existait sur
/// **aucune** trame.
///
/// ⚠️ Ce geste n'est pas neuf : `ScenarioPerceptifConnexionTest` l'avait inventé pour lui seul, après
/// un retour de revue - « on a l'impression que la modale apparaît par magie ». Il vit ici parce que
/// deux implémentations d'une même doctrine finissent par diverger, et que la seconde n'aurait pas
/// hérité de ce qu'a coûté la première.
///
/// D'où les trois temps : le menu s'ouvre, le pointeur **va** sur l'entrée et **s'y arrête**, puis il
/// clique. Le temps d'arrêt ne coûte qu'à une séance filmée.
public final class GesteDeMenu {

    private GesteDeMenu() {}

    /// Ouvre `idDuMenu`, amène le pointeur sur l'entrée `libelle`, l'y laisse voir, puis clique.
    ///
    /// @throws TimeoutException si l'entrée ne paraît pas - un menu qui ne s'ouvre pas rendrait un clip
    ///     immobile que personne ne signalerait
    public static void choisir(FxRobot robot, String idDuMenu, String libelle) throws TimeoutException {
        robot.clickOn(idDuMenu);
        WaitForAsyncUtils.waitForFxEvents();
        WaitForAsyncUtils.waitFor(
                5, TimeUnit.SECONDS, () -> robot.lookup(libelle).tryQuery().isPresent());
        Respiration.leTempsDeLire(robot);

        // ⚠️ Le pointeur VA sur l'entrée, et s'y arrête, AVANT de cliquer. Sans cet arrêt, le clic et la
        // fermeture du menu tombent sur la même trame : on voit le menu, puis l'écran d'après, et jamais
        // le choix.
        robot.moveTo(libelle);
        WaitForAsyncUtils.waitForFxEvents();
        Respiration.entreDeuxGestes(robot);

        robot.clickOn(libelle);
        WaitForAsyncUtils.waitForFxEvents();
    }
}
