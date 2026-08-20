package fr.univ_amu.iut.recette;

import org.testfx.api.FxRobot;

/// Les temps d'arrêt d'un scénario filmé, **nommés par le moment qu'ils tiennent**.
///
/// ## Pourquoi cette classe existe
///
/// Quatre scénarios perceptifs définissaient chacun ses propres `AVANT_MS` et `APRES_MS`, et les
/// quatre valeurs avaient dérivé : 600 ici, 700 là, 1 200 d'un côté, 3 000 de l'autre. Aucune n'était
/// fausse, et c'est le problème - personne ne pouvait dire laquelle était la bonne.
///
/// ## Pourquoi elles ont été allongées
///
/// Retour de la revue des clips publiés : « elles sont parfois un peu rapides et ne permettent pas
/// forcément de comprendre ce que l'on voit ». Mesuré sur la première publication : hors carton, les
/// clips perceptifs duraient de **2,9 s à 7,6 s**, et les plus courts tenaient un enchaînement entier
/// - un état de repos, un geste, un état d'arrivée - en moins de trois secondes.
///
/// Le banc filme à **dix images par seconde**. Une seconde et demie fait quinze images : de quoi
/// enregistrer un état. Une seconde en fait dix, ce qui suffit à un oeil qui sait déjà quoi chercher,
/// et pas à un relecteur qui découvre.
///
/// ⚠️ Ces arrêts ne coûtent qu'à une **séance filmée**. Hors tournage, [Seance#filmee()] est faux et
/// rien ne dort : la suite ordinaire ne paie pas la lisibilité des films.
public final class Respiration {

    /// L'écran au repos, avant le geste. C'est la **référence** de qui compare : sans elle, on ne
    /// peut pas dire si quelque chose a bougé, seulement à quoi ça ressemble à la fin.
    private static final long AVANT_MS = 1_500;

    /// Après le geste : le temps de **lire** ce qui a changé, pas seulement de le voir passer.
    private static final long APRES_MS = 2_500;

    /// Le moment que le cas existe pour montrer. Il tient plus longtemps que les autres parce que
    /// c'est celui-là qu'on est venu juger, et qu'un jugement demande de regarder deux fois.
    private static final long MOMENT_CLE_MS = 3_000;

    /// Entre deux gestes d'un même enchaînement : assez pour que l'oeil suive, pas assez pour
    /// laisser croire que l'application attend quelque chose.
    private static final long ENTRE_MS = 2_000;

    /// Une liste déroulée, un menu ouvert : on vient y lire des **valeurs**, une par une.
    private static final long LECTURE_MS = 2_500;

    private Respiration() {}

    public static void avantLeGeste(FxRobot robot) {
        tenir(robot, AVANT_MS);
    }

    public static void apresLeGeste(FxRobot robot) {
        tenir(robot, APRES_MS);
    }

    public static void surLeMomentCle(FxRobot robot) {
        tenir(robot, MOMENT_CLE_MS);
    }

    public static void entreDeuxGestes(FxRobot robot) {
        tenir(robot, ENTRE_MS);
    }

    public static void leTempsDeLire(FxRobot robot) {
        tenir(robot, LECTURE_MS);
    }

    /// Ne s'arrête que si l'on filme : hors séance filmée, ces arrêts n'allongeraient le build que
    /// pour personne.
    private static void tenir(FxRobot robot, long millis) {
        if (Seance.filmee()) {
            robot.sleep(millis);
        }
    }
}
