package fr.univ_amu.iut.analyse.model;

import java.util.Arrays;
import java.util.Optional;

/// Largeur d'une **tranche horaire** pour la courbe d'activité (#2352, lot 2 du chantier #2348) : les
/// trois pas au choix de l'observateur. Un pas fin dessine le détail d'un pic, un pas large lisse la forme
/// générale de la nuit ; le choix reste à l'utilisateur, aucune largeur n'est « la bonne ».
///
/// L'énumération borne les valeurs acceptables (15, 30 ou 60 minutes) : une largeur arbitraire n'a pas de
/// sens pour un axe qu'on veut comparer d'une nuit à l'autre.
public enum LargeurTranche {

    /// Quart d'heure : le pas le plus fin, pour lire le détail d'un pic.
    QUART_HEURE(15),

    /// Demi-heure : compromis par défaut entre détail et lisibilité.
    DEMI_HEURE(30),

    /// Heure pleine : le pas le plus large, pour la forme d'ensemble.
    HEURE(60);

    private final int minutes;

    LargeurTranche(int minutes) {
        this.minutes = minutes;
    }

    /// Nombre de minutes couvertes par une tranche.
    public int minutes() {
        return minutes;
    }

    /// La tranche dont la largeur vaut exactement `minutes`, ou **vide** si aucune ne correspond : sert à
    /// interpréter l'option `--tranche` du CLI (`exporter-activite`), qui parle en minutes comme l'IHM, et à
    /// refuser proprement une valeur hors des trois pas bornés.
    public static Optional<LargeurTranche> deMinutes(int minutes) {
        return Arrays.stream(values())
                .filter(tranche -> tranche.minutes == minutes)
                .findFirst();
    }
}
