package fr.univ_amu.iut.commun.view;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import javafx.scene.chart.NumberAxis;
import javafx.util.StringConverter;

/// Axe des abscisses gradué en **minutes depuis une origine**, mais **étiqueté en heures**.
///
/// Deux écrans tracent une courbe dans le temps d'une nuit — le climat du [Diagnostic] et l'activité par
/// espèce — et tous deux se heurtent au même obstacle : un `NumberAxis` ne sait pas parler d'heures. On
/// place donc les points à leur distance en minutes d'une origine, et on **reconstruit** l'heure du jour
/// à l'étiquette. Sans cela, l'axe afficherait des minutes brutes (0, 60, 120…) que personne ne lit.
///
/// **L'étiquette se déduit du pas**, elle ne se choisit pas : la minute n'apparaît que si le pas n'est
/// pas une heure pleine. Un axe gradué toutes les 45 minutes a besoin de `22:00` puis `22:45`, sans quoi
/// deux repères porteraient « 22 » ; un axe gradué à l'heure n'a que faire d'un `:00` répété quatorze
/// fois. Une seule règle sert donc les deux écrans, là où deux formats codés en dur laissaient le
/// Diagnostic afficher `22:00` même sur un pas horaire.
///
/// Reste chez chaque écran la **politique de bornes**, qui relève de son intention : le Diagnostic cale
/// son axe sur l'étendue de ses mesures, l'Activité impose un cadre fixe de 18 h à 8 h pour que deux
/// nuits se comparent.
public final class AxeHoraire {

    /// Graduation à l'heure pleine : la minute serait un `:00` répété.
    private static final DateTimeFormatter HEURE = DateTimeFormatter.ofPattern("HH");

    /// Graduation intercalaire (quart d'heure, demie, trois quarts) : sans la minute, deux repères
    /// voisins porteraient la même étiquette.
    private static final DateTimeFormatter HEURE_MINUTE = DateTimeFormatter.ofPattern("HH:mm");

    private AxeHoraire() {}

    /// Gradue `axe` de `0` à `etendueMinutes`, un repère tous les `pasMinutes`, chaque graduation portant
    /// l'heure atteinte depuis `origine`.
    ///
    /// L'axe cesse de s'auto-cadrer : les bornes viennent de l'appelant, qui seul sait ce que son écran
    /// doit montrer. L'heure **repasse par minuit** sans rien de spécial à faire ([LocalTime] boucle), ce
    /// qui est exactement ce qu'on veut d'une nuit.
    ///
    /// @param axe l'axe des abscisses à graduer
    /// @param origine l'heure que représente l'abscisse `0`
    /// @param etendueMinutes largeur de l'axe, en minutes
    /// @param pasMinutes intervalle entre deux graduations, en minutes ; il décide aussi de l'étiquette
    public static void graduerEnHeures(NumberAxis axe, LocalTime origine, double etendueMinutes, double pasMinutes) {
        axe.setAutoRanging(false);
        axe.setLowerBound(0);
        axe.setUpperBound(etendueMinutes);
        axe.setTickUnit(pasMinutes);
        axe.setMinorTickCount(0);
        axe.setTickLabelFormatter(etiquettesHeure(origine, pasMinutes));
    }

    /// Le convertisseur seul, pour un axe dont l'appelant règle lui-même les bornes.
    ///
    /// `fromString` n'est jamais appelé (une étiquette d'axe ne se saisit pas) : il rend `0` plutôt que de
    /// lever, pour qu'un appel inattendu de JavaFX ne fasse pas tomber un écran.
    public static StringConverter<Number> etiquettesHeure(LocalTime origine, double pasMinutes) {
        DateTimeFormatter format = pasMinutes % 60 == 0 ? HEURE : HEURE_MINUTE;
        return new StringConverter<>() {
            @Override
            public String toString(Number minutes) {
                return origine.plusMinutes(minutes.longValue()).format(format);
            }

            @Override
            public Number fromString(String texte) {
                return 0;
            }
        };
    }
}
