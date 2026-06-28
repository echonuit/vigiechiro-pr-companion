package fr.univ_amu.iut.commun.view;

/// Contrat de navigation inter-feature : ouvrir la **vue multi-sites** en la **focalisant** sur un carré
/// précis (« voir sur la carte »). Permet à d'autres écrans (fiche site, M-Passage…) de renvoyer vers
/// LA carte de référence — centrée et surlignée sur l'élément concerné — au lieu d'embarquer une carte.
///
/// Défini dans le socle (`commun.view`) pour ne pas dépendre du `view` de la feature `multisite` (règle
/// ArchUnit `pas_de_dependance_inter_feature_vers_la_vue`). La feature `multisite` en fournit
/// l'implémentation (`NavigationMultisite`, bindée par `MultisiteModule`). Même esprit que [OuvrirSite].
@FunctionalInterface
public interface OuvrirMultisite {

    /// Ouvre la vue multi-sites et **centre/surligne** la carte sur le carré `numeroCarre` (sans effet
    /// si le numéro est nul/vide ou introuvable).
    void ouvrirSurCarre(String numeroCarre);

    /// Ouvre la vue multi-sites **centrée sur un point précis** : surbrillance de son carré + recentrage
    /// serré sur ses coordonnées (#154), pour le voir (et, en mode édition, le corriger) sur LA carte de
    /// référence. Par défaut, se rabat sur le carré ([#ouvrirSurCarre]) ; l'implémentation réelle centre
    /// plus finement. Ce défaut garde le contrat **fonctionnel** (implémentable par une lambda côté tests).
    default void ouvrirSurPoint(String numeroCarre, double latitude, double longitude) {
        ouvrirSurCarre(numeroCarre);
    }
}
