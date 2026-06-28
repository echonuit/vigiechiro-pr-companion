package fr.univ_amu.iut.multisite.view;

import fr.univ_amu.iut.commun.view.carte.CarteSites;
import fr.univ_amu.iut.commun.view.carte.FournisseurEmpriseCarre;
import java.util.List;

/// **Focalisation de la carte multi-sites** depuis un autre écran (« voir sur la carte »), extraite du
/// [MultisiteController] pour le garder petit. Surligne le carré concerné, recentre la carte (sur le
/// carré, ou plus finement sur un point), puis **dégage la carte** (repli du tableau, #338) via le rappel
/// fourni par le controller.
final class FocalisationCarte {

    /// Zoom de mise au point serrée sur un **point** précis (« voir sur la carte » d'un GPS, #154).
    private static final int ZOOM_POINT = 15;

    private final CarteSites carte;
    private final Runnable degagerCarte;

    FocalisationCarte(CarteSites carte, Runnable degagerCarte) {
        this.carte = carte;
        this.degagerCarte = degagerCarte;
    }

    /// Focalise sur un **carré** : surbrillance + recentrage sur son emprise officielle (#338). Sans effet
    /// si le numéro est vide ou hors carroyage.
    void surCarre(String numeroCarre) {
        if (numeroCarre == null || numeroCarre.isBlank()) {
            return;
        }
        carte.surbrillanceCarre(numeroCarre);
        FournisseurEmpriseCarre.parDefaut().emprise(numeroCarre, List.of()).ifPresent(carte::centrerSurCarre);
        degagerCarte.run();
    }

    /// Focalise sur un **point précis** : surbrillance de son carré + recentrage serré sur ses
    /// coordonnées (#154), pour le voir (et, en mode édition, le corriger). Sans effet si le carré est vide.
    void surPoint(String numeroCarre, double latitude, double longitude) {
        if (numeroCarre == null || numeroCarre.isBlank()) {
            return;
        }
        carte.surbrillanceCarre(numeroCarre);
        carte.centrerSur(latitude, longitude, ZOOM_POINT);
        degagerCarte.run();
    }
}
