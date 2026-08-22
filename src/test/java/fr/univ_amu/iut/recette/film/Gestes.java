package fr.univ_amu.iut.recette.film;

import java.util.Optional;
import javafx.scene.Scene;
import javafx.scene.control.TextInputControl;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;

/// Ce que le clip doit montrer du geste, et que le graphe de scène ne contient pas.
///
/// ## Le manque
///
/// Un clip du banc montre l'**effet** d'un geste, jamais le geste. Le pointeur n'est pas un nœud de
/// la scène, donc `Scene.snapshot` ne le voit pas ; et une frappe au clavier ne laisse aucune trace,
/// puisqu'à dix images par seconde l'appui tient sur une image où rien ne bouge.
///
/// C'était la dernière ligne où le banc bash gardait un avantage : `ffmpeg -f x11grab` dessine le
/// pointeur du système parce qu'il filme un bureau.
///
/// ## Ce qui est dessiné, et ce que ce n'est pas
///
/// ⚠️ Ce qui est reconstruit ici est ce que **JavaFX a reçu**, et non ce que le système a émis. La
/// nuance est à l'avantage du procédé : un pointeur reconstruit ne peut pas se désynchroniser de ce
/// que l'application a effectivement traité, là où un pointeur filmé du dehors montre parfois un
/// clic qui n'est jamais arrivé - c'est exactement le défaut que le banc bash a payé (#3696). Mais
/// un banc qui dessine quelque chose doit dire ce qu'il dessine.
///
/// ## Pourquoi la fenêtre est retenue AVEC la position
///
/// Une position de scène ne veut rien dire sans la scène à laquelle elle se rapporte. Un point pris
/// sur un menu, dessiné dans le repère de la fenêtre principale, tomberait ailleurs. On ne lit
/// jamais de coordonnée d'écran : sous Monocle l'absolu ment, et c'est la leçon que
/// [CameraDeScene#decalageRelatif] a déjà coûtée.
final class Gestes {

    /// Le dernier endroit où le pointeur a été vu, et dans quelle fenêtre.
    ///
    /// La fenêtre est un `Object` parce que ce suivi n'a pas besoin de savoir ce qu'est une
    /// fenêtre : il lui suffit de la rendre telle quelle à qui saura la situer sur la toile. C'est
    /// aussi ce qui permet de l'éprouver sans monter la moindre scène.
    record Vu(Object fenetre, double x, double y) {}

    /// Marque qu'aucun appui n'a encore eu lieu. Un `long` ordinaire ne pourrait pas la distinguer
    /// d'un appui très ancien sans introduire un second champ.
    private static final long JAMAIS = Long.MIN_VALUE;

    private volatile Vu pointeur;
    private volatile long dernierAppui = JAMAIS;
    private volatile String dernierBadge;
    private volatile long instantDuBadge = JAMAIS;

    // ---------------------------------------------------------------------------- les décisions

    /// Faut-il montrer cette frappe ?
    ///
    /// ⚠️ La distinction n'est pas la touche, c'est ce qu'elle fait : on **tape**, ou on
    /// **commande**. Les raccourcis de l'écran d'écoute sont des lettres nues - `R` référence, `D`
    /// douteux, `N` suivant, `1/2/3` certitude - si bien qu'une règle « seulement les touches
    /// modifiées ou nommées » raterait précisément ce que la session S3 vient juger. À l'inverse,
    /// tout montrer rendrait les dix-sept caractères d'un jeton en autant de badges, pour rien : le
    /// champ montre déjà ce qu'on y tape.
    static boolean aAfficher(boolean avecModificateur, boolean focusEnSaisie) {
        return avecModificateur || !focusEnSaisie;
    }

    /// Le libellé d'un raccourci, modificateurs d'abord.
    ///
    /// L'ordre est fixe pour que deux clips ne montrent jamais deux libellés du même geste.
    static String libelle(String touche, boolean ctrl, boolean maj, boolean alt) {
        StringBuilder lu = new StringBuilder();
        if (ctrl) {
            lu.append("Ctrl + ");
        }
        if (maj) {
            lu.append("Maj + ");
        }
        if (alt) {
            lu.append("Alt + ");
        }
        return lu.append(touche).toString();
    }

    /// Ce qu'il reste du halo, de 1 à l'appui jusqu'à 0 au bout de sa durée.
    ///
    /// ⚠️ Bornée des DEUX côtés. Au-delà de la durée, un clip long rallumerait un halo éteint ; en
    /// deçà de zéro - l'horloge peut rendre un instant antérieur entre deux images - le halo
    /// grossirait au-delà de lui-même.
    static double resorption(long ageMs, long dureeMs) {
        if (ageMs >= dureeMs) {
            return 0;
        }
        if (ageMs <= 0) {
            return 1;
        }
        return 1.0 - (double) ageMs / dureeMs;
    }

    // ------------------------------------------------------------------------------- le suivi

    void noterPointeur(Object fenetre, double x, double y) {
        pointeur = new Vu(fenetre, x, y);
    }

    void noterAppui(long instantMs) {
        dernierAppui = instantMs;
    }

    void noterTouche(String libelle, long instantMs) {
        dernierBadge = libelle;
        instantDuBadge = instantMs;
    }

    Optional<Vu> pointeur() {
        return Optional.ofNullable(pointeur);
    }

    /// Ce qu'il reste du halo du dernier appui, ou zéro s'il n'y en a pas eu.
    double halo(long maintenantMs, long dureeMs) {
        return dernierAppui == JAMAIS ? 0 : resorption(maintenantMs - dernierAppui, dureeMs);
    }

    /// Le badge à afficher, tant qu'il n'a pas fait son temps.
    Optional<String> badge(long maintenantMs, long dureeMs) {
        if (dernierBadge == null || instantDuBadge == JAMAIS || maintenantMs - instantDuBadge > dureeMs) {
            return Optional.empty();
        }
        return Optional.of(dernierBadge);
    }

    // --------------------------------------------------------------------------- le branchement

    /// Pose les filtres sur une scène, une fois pour toutes.
    ///
    /// ⚠️ Des FILTRES, et rien n'y est consommé : un banc qui change ce que le test reçoit ne filme
    /// plus le produit, il filme sa propre présence.
    void observer(Scene scene) {
        scene.addEventFilter(MouseEvent.ANY, evenement -> {
            noterPointeur(scene.getWindow(), evenement.getSceneX(), evenement.getSceneY());
            if (evenement.getEventType() == MouseEvent.MOUSE_PRESSED) {
                noterAppui(System.currentTimeMillis());
            }
        });
        scene.addEventFilter(KeyEvent.KEY_PRESSED, evenement -> {
            boolean modificateur = evenement.isShortcutDown() || evenement.isAltDown() || evenement.isShiftDown();
            if (!aAfficher(modificateur, scene.getFocusOwner() instanceof TextInputControl)) {
                return;
            }
            noterTouche(
                    libelle(
                            nomLisible(evenement),
                            evenement.isShortcutDown(),
                            evenement.isShiftDown(),
                            evenement.isAltDown()),
                    System.currentTimeMillis());
        });
    }

    /// Le nom de la touche tel qu'on l'écrirait dans une notice.
    ///
    /// `KeyCode.getName()` rend déjà « Enter », « Tab », « A » : on ne réécrit que ce que le lecteur
    /// francophone d'un clip de recette ne reconnaîtrait pas.
    private static String nomLisible(KeyEvent evenement) {
        return switch (evenement.getCode()) {
            case ENTER -> "Entrée";
            case ESCAPE -> "Échap";
            case SPACE -> "Espace";
            case BACK_SPACE -> "Retour";
            case DELETE -> "Suppr";
            case TAB -> "Tab";
            case UP -> "↑";
            case DOWN -> "↓";
            case LEFT -> "←";
            case RIGHT -> "→";
            default -> evenement.getCode().getName();
        };
    }
}
