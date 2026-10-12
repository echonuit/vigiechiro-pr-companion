package fr.univ_amu.iut.perf.outils;

import java.util.Locale;
import javafx.stage.Window;

/// L'échelle à laquelle le banc de l'IHM a dessiné, lue dans le processus sur sa fenêtre (#6177).
///
/// Le banc dessine en rendu logiciel (`prism.order=sw`, posé par le pom). Sur un écran que le système
/// double, chaque image compte quatre fois plus de pixels : le 2026-10-12, sur un même poste, les écarts
/// entre deux images pendant l'import passaient de 99-114 ms à l'échelle 1 à 224-338 ms à l'échelle
/// par défaut d'un écran annoncé à 192 points par pouce, et la navigation de moins de 0,4 s à 1,3 s.
/// Un verdict de seuil dépendait donc d'un réglage que le relevé ne portait pas.
///
/// D'où deux règles. Le relevé porte toujours cette échelle ([#ligne()]). Et le banc refuse de mesurer
/// à une autre échelle que 1 ([#refus()]), sauf dérogation posée par `-Dperf.ihm.echelle=ecran`, que le
/// relevé écrit alors à côté de ses chiffres : on peut vouloir mesurer ce qu'un écran dense coûte au
/// dessin logiciel, mais jamais sans le dire.
///
/// @param renduX l'échelle de rendu de la fenêtre (`Window#getRenderScaleX`), celle du dessin
/// @param renduY la même, verticale
/// @param ecranX l'échelle que l'écran demande à la fenêtre (`Window#getOutputScaleX`)
/// @param ecranY la même, verticale
/// @param reglages les réglages d'affichage de la JVM, tels qu'ils ont été posés
/// @param derogation vrai quand le tireur a accepté l'échelle de l'écran
record EchelleDuRendu(double renduX, double renduY, double ecranX, double ecranY, String reglages, boolean derogation) {

    /// La propriété qui accepte une autre échelle que 1, et la valeur qui l'accepte.
    static final String PROPRIETE_DEROGATION = "perf.ihm.echelle";

    static final String DEROGATION = "ecran";

    private static final double TOLERANCE = 1e-9;

    /// Lit l'échelle de `fenetre` et les réglages d'affichage de la JVM. Sur le fil JavaFX, la fenêtre
    /// affichée : avant `show()`, l'écran n'a encore rien demandé.
    static EchelleDuRendu lire(Window fenetre) {
        String reglages =
                String.join(" · ", reglage("glass.platform"), reglage("prism.order"), reglage("glass.gtk.uiScale"));
        return new EchelleDuRendu(
                fenetre.getRenderScaleX(),
                fenetre.getRenderScaleY(),
                fenetre.getOutputScaleX(),
                fenetre.getOutputScaleY(),
                reglages,
                DEROGATION.equals(System.getProperty(PROPRIETE_DEROGATION)));
    }

    private static String reglage(String cle) {
        return cle + "=" + System.getProperty(cle, "non posée");
    }

    /// La ligne que le relevé porte, en sortie et dans `releve.tsv`.
    String ligne() {
        String ligne = String.format(
                Locale.ROOT, "rendu %.2f × %.2f · écran %.2f × %.2f · %s", renduX, renduY, ecranX, ecranY, reglages);
        return derogation
                ? ligne + " · dérogation : échelle de l'écran acceptée (-D" + PROPRIETE_DEROGATION + "=" + DEROGATION
                        + ")"
                : ligne;
    }

    /// Arrête le banc sur [#refus()], avant toute mesure.
    void verifier() {
        String refus = refus();
        if (refus != null) {
            throw new AssertionError("le banc s'arrête : " + refus);
        }
    }

    /// Ce qui doit arrêter le banc, ou `null` : une échelle de rendu autre que 1 sans dérogation.
    String refus() {
        if (derogation || (unite(renduX) && unite(renduY))) {
            return null;
        }
        return String.format(
                Locale.ROOT,
                "échelle de rendu %.2f × %.2f, le protocole demande 1. Relancer avec -Dglass.gtk.uiScale=1"
                        + " (par JAVA_TOOL_OPTIONS), ou poser -D%s=%s pour mesurer à l'échelle de l'écran,"
                        + " ce que le relevé dira",
                renduX,
                renduY,
                PROPRIETE_DEROGATION,
                DEROGATION);
    }

    private static boolean unite(double echelle) {
        return Math.abs(echelle - 1) < TOLERANCE;
    }
}
