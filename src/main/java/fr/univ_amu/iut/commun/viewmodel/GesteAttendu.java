package fr.univ_amu.iut.commun.viewmodel;

import fr.univ_amu.iut.commun.model.Besoin;
import fr.univ_amu.iut.commun.model.RegleMetierException;

/// Le geste que **l'application** propose pour lever un besoin (#2635) : la moitié « comment » d'un refus,
/// écrite ici parce qu'elle ne vaut que sur cette surface.
///
/// Le modèle dit ce qui manque ([Besoin]) sans nommer d'écran ; c'est l'IHM qui sait où l'on clique. La
/// ligne de commande en dit une autre (`cli.GesteAttenduCli`), et un troisième consommateur en dirait une
/// troisième - ou aucune, sans rien perdre du fait lui-même.
public final class GesteAttendu {

    private GesteAttendu() {}

    /// Le message complet d'un refus : ce qui manque, puis où le régler. Un refus sans besoin est rendu
    /// tel quel - il n'y a rien à ajouter, et inventer un geste serait pire que de se taire.
    public static String message(Throwable refus) {
        String enonce = refus.getMessage() == null ? "Le geste est impossible." : refus.getMessage();
        if (!(refus instanceof RegleMetierException metier) || metier.besoin().isEmpty()) {
            return enonce;
        }
        return enonce + " " + ou(metier.besoin().orElseThrow());
    }

    private static String ou(Besoin besoin) {
        return switch (besoin) {
            case Besoin.Connexion ignore ->
                "Connectez-vous depuis le menu principal > Se connecter à Vigie-Chiro," + " puis recommencez.";
            case Besoin.Fonctionnalite fonctionnalite ->
                "Réactivez-la depuis le menu principal > Fonctionnalités (« " + fonctionnalite.nom()
                        + " »), puis recommencez.";
        };
    }
}
