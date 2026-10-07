package fr.univ_amu.iut.commun.outils;

import fr.univ_amu.iut.commun.view.ColonneAbregeable;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Labeled;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumnBase;
import javafx.scene.control.TextInputControl;
import javafx.scene.text.Text;

/// Refuse une capture dont un texte est **illisible**, plutot que d'ecrire une image qui ment.
///
/// Extrait d'[ApercuFx] quand ce dernier a appris a voir les invites de saisie (#3170) : rendre une
/// scene en PNG et juger sa lisibilite sont deux preoccupations, et la seconde a maintenant sa propre
/// taille. `ApercuFx` rend, cette classe refuse.
///
/// Quatre modes de troncature, decouverts dans cet ordre et par une revue **a l'oeil**, jamais par un
/// test : le libelle enroulable **comprime** en hauteur (#2049), le libelle **ellipse** en largeur
/// (#1641, #1701, #1873, #1579, #2012), l'**invite** d'un champ de saisie coupee **sans ellipse**
/// (#3170) - le plus trompeur, puisque l'ellipse est justement l'aveu qu'on cherche - et la **cellule
/// de table** coupee (#5113), que le critere de largeur ne pouvait pas voir : une cellule demande la
/// largeur de sa colonne, jamais celle de son texte.
public final class LisibiliteCapture {

    private LisibiliteCapture() {}

    /// Fin de chaque constat : « … manque N px) ». Ecrit une fois, PMD refusant sa triple copie.
    private static final String SUFFIXE_PX = " px)";

    /// Tolerance de comparaison, en pixels : la mise en page produit des ecarts d'arrondi qui ne sont
    /// pas des elisions. Pour la **hauteur** d'un libelle enroulable elle est une borne **atteinte** :
    /// un pixel entier qui manque elide deja (#5899).
    private static final double TOLERANCE_PX = 1.0;

    /// Refuse la capture si un libelle enroulable y a ete **comprime**, plutot que d'ecrire une image
    /// qui ment.
    ///
    /// L'application monte ses vues dans un `ScrollPane` permanent : ce qui deborde **defile**. La
    /// capture n'a pas ce recours - elle rend une scene de taille fixe, et ce qui deborde se
    /// **comprime**. Un `Label` en `wrapText` se rabat alors sur une ligne et se termine par une
    /// ellipse. Rien ne le signalait : la capture etait produite, elle avait l'air normale, et elle
    /// mentait (#2049).
    ///
    /// Le critere porte sur **le libelle**, pas sur la scene : un libelle comprime occupe moins de
    /// hauteur que celle qu'il demanderait pour la largeur dont il dispose. Comparer plutot la hauteur
    /// du contenu a celle de la scene ne marcherait pas - mesure sur Diagnostic, cet ecart vaut 1,6 sur
    /// un ecran ou **rien** n'est elide, ses conteneurs extensibles absorbant la place sans rien perdre.
    /// Point d'entree : leve si la scene porte un texte illisible.
    public static void refuserToutTexteIllisible(Scene scene) {
        List<String> comprimes = new ArrayList<>();
        collecterComprimes(scene.getRoot(), comprimes);
        if (!comprimes.isEmpty()) {
            throw new IllegalStateException("Capture tronquee : " + comprimes.size()
                    + " libelle(s) rendu(s) avec une ellipse, donc illisibles. « manque N px » = la scene"
                    + " est trop courte pour un libelle enroulable ; « tronque » = le controle est trop"
                    + " etroit pour son texte (le figer par minWidth=\"-Infinity\", elargir la colonne, ou"
                    + " assumer l'abregement par la classe CSS « " + ABREGEABLE + " »). En cause : "
                    + String.join(" | ", comprimes));
        }
    }

    private static void collecterComprimes(Node noeud, List<String> comprimes) {
        // Un noeud masque a une hauteur nulle tout en gardant une hauteur preferee : sans ce filtre, tout
        // libelle conditionnel passe pour comprime. C'est le premier faux positif rencontre - le repere GPS
        // du Diagnostic, absent quand le passage est introuvable.
        if (!noeud.isVisible()) {
            return;
        }
        if (noeud instanceof Labeled libelle && libelle.isWrapText() && libelle.getWidth() > 0) {
            double manque = libelle.prefHeight(libelle.getWidth()) - libelle.getHeight();
            // Inclusif (#5899) : il suffit d'un pixel a JavaFX pour rabattre deux lignes sur une et finir
            // par une ellipse. La comparaison stricte laissait passer cet ecart-la exactement, et deux
            // apercus sont partis avec des consignes coupees. La tolerance n'ecarte que les arrondis,
            // qui restent sous le pixel.
            if (manque >= TOLERANCE_PX) {
                comprimes.add(resumer(libelle) + " (manque " + Math.round(manque) + SUFFIXE_PX);
            }
        }
        if (noeud instanceof Labeled large && estTronqueEnLargeur(large)) {
            comprimes.add(resumer(large) + " (tronque, manque " + Math.round(largeurManquante(large)) + SUFFIXE_PX);
        }
        if (noeud instanceof TableCell<?, ?> cellule && estCelluleCoupee(cellule)) {
            comprimes.add(resumer(cellule) + " (cellule de table coupee : « "
                    + texteDessine(cellule).orElse("")
                    + " » dessine" + motifDuRefus(cellule) + ", colonne « " + titreDeLaColonne(cellule) + " » de "
                    + Math.round(cellule.getWidth()) + SUFFIXE_PX);
        }
        if (noeud instanceof TextInputControl champ && estInviteTronquee(champ)) {
            comprimes.add("invite « " + champ.getPromptText() + " » (tronquee, manque "
                    + Math.round(largeurInviteManquante(champ)) + SUFFIXE_PX);
        }
        if (noeud instanceof Parent parent) {
            parent.getChildrenUnmodifiable().forEach(enfant -> collecterComprimes(enfant, comprimes));
        }
    }

    /// Vrai si l'**invite** d'un champ de saisie ne tient pas dans sa largeur (#3170).
    ///
    /// Angle mort du garde jusqu'ici : il ne visitait que les [Labeled]. Or JavaFX rogne une invite de
    /// `TextInputControl` **sans poser d'ellipse** - le texte s'arrete net, au milieu d'un mot, et rien
    /// ne signale qu'il manque quelque chose. C'est le mode de troncature le plus trompeur, parce que
    /// l'ellipse est justement l'aveu qu'on cherche a l'oeil.
    ///
    /// Mesuree seulement quand le champ est **vide** : des qu'il porte une saisie, l'invite ne s'affiche
    /// plus et sa largeur n'apprend rien.
    private static boolean estInviteTronquee(TextInputControl champ) {
        return largeurInviteManquante(champ) > TOLERANCE_PX;
    }

    private static double largeurInviteManquante(TextInputControl champ) {
        String invite = champ.getPromptText();
        if (invite == null || invite.isBlank() || !champ.getText().isEmpty() || champ.getWidth() <= 0) {
            return 0;
        }
        Text mesure = new Text(invite);
        mesure.setFont(champ.getFont());
        double disponible = champ.getWidth()
                - champ.getInsets().getLeft()
                - champ.getInsets().getRight();
        return mesure.getLayoutBounds().getWidth() - disponible;
    }

    /// Classe CSS par laquelle une vue **assume** qu'un libelle se raccourcisse quand la place manque.
    ///
    /// Le deficit d'une barre doit tomber quelque part : cette classe designe le controle qui le porte,
    /// un selecteur dont la valeur se relit au deroule plutot qu'un libelle d'action. Elle vit dans la
    /// vue, la ou l'exception s'applique, et non dans une liste tenue ici.
    ///
    /// Pour une **colonne de table**, elle ne vaut qu'avec une infobulle qui rend le texte entier
    /// (#5113) : les deux se posent ensemble par [ColonneAbregeable#assumer].
    public static final String ABREGEABLE = ColonneAbregeable.MARQUE;

    /// Vrai si le texte de `libelle` ne tient pas dans sa largeur, donc s'affiche avec une ellipse.
    ///
    /// Pendant longtemps rien ne l'a signale : c'est le mecanisme derriere cinq issues nees d'une revue a
    /// l'oeil (#1641, #1701, #1873, #1579, #2012). Un test verifie qu'un bouton **fait** ce qu'il doit ;
    /// il ne verifie pas qu'on puisse **lire** ce qu'il dit.
    private static boolean estTronqueEnLargeur(Labeled libelle) {
        // Un libelle enroulable ne s'ellipse pas horizontalement : il passe a la ligne - JavaFX coupe meme
        // un mot insecable caractere par caractere - et c'est la compression VERTICALE qui le guette, deja
        // couverte plus haut.
        //
        // LIMITE CONNUE (#2265). Cette mesure verticale peut mentir dans un cas : rendu HORS d'une fenetre
        // montree (le snapshot d'un `DialogPane`), un libelle enroulable dont la largeur est contrainte
        // sous ce qu'il faudrait peut rester haut d'une SEULE ligne, `prefHeight` retombant sur cette meme
        // hauteur - l'ecart mesure vaut alors zero et la troncature passe inapercue (#2243).
        //
        // Aucun controle geometrique ne referme ce trou de facon fiable : toute construction reproductible
        // s'enroule correctement, ou declenche deja la mesure verticale. Un controle de plus serait donc du
        // code qu'aucun test ne peut voir echouer. La parade est A LA SOURCE - pre-enrouler les textes
        // d'une capture, cf. `CaptureConfirmationsImport#enrouler(CompteRendu)`.
        return !libelle.isWrapText()
                && porteUnTexteMisEnPage(libelle)
                && !assumeDEtreAbrege(libelle)
                && largeurManquante(libelle) > TOLERANCE_PX;
    }

    /// Vrai si `libelle` a ete mis en page et porte un texte : sans cela il n'y a rien a lire.
    private static boolean porteUnTexteMisEnPage(Labeled libelle) {
        return libelle.getWidth() > 0
                && libelle.getText() != null
                && !libelle.getText().isBlank();
    }

    /// Vrai si la vue assume que `libelle` se raccourcisse, par sa propre classe ou par celle d'un
    /// parent. Commun aux deux criteres qui lisent un [Labeled].
    private static boolean assumeDEtreAbrege(Labeled libelle) {
        return libelle.getStyleClass().contains(ABREGEABLE) || dansUnParentAbregeable(libelle);
    }

    /// Vrai si une cellule de table **dessine** autre chose que le texte qu'elle a recu (#5113).
    ///
    /// Le critere de largeur y est aveugle : une `TableCell` demande la largeur de sa **colonne**, jamais
    /// celle de son texte, et `prefWidth(-1) - getWidth()` vaut zero. On compare donc le texte dessine,
    /// ellipse comprise, a `getText()`, qui reste entier.
    ///
    /// Une cellule coupee n'est exemptee que si sa colonne ou sa table porte [#ABREGEABLE] **et** si son
    /// infobulle rend le texte entier. Ce qui n'est pas juge est dans `dev-docs/captures.md`.
    private static boolean estCelluleCoupee(TableCell<?, ?> cellule) {
        return porteUnTexteMisEnPage(cellule)
                && texteDessine(cellule)
                        .filter(dessine -> !dessine.equals(cellule.getText()))
                        .isPresent()
                && !(assumeDEtreAbrege(cellule) && seRelitAuSurvol(cellule));
    }

    /// Vrai si l'infobulle de `cellule` contient le texte que la cellule a recu.
    private static boolean seRelitAuSurvol(TableCell<?, ?> cellule) {
        return cellule.getTooltip() != null
                && cellule.getTooltip().getText() != null
                && cellule.getTooltip().getText().contains(cellule.getText());
    }

    /// Ce qu'il faut dire d'une cellule coupee qui porte la marque : c'est l'infobulle qui lui manque.
    private static String motifDuRefus(TableCell<?, ?> cellule) {
        return assumeDEtreAbrege(cellule)
                ? ", marquee « " + ABREGEABLE + " » sans infobulle qui rende le texte entier"
                : "";
    }

    /// Le texte que l'habillage de `libelle` a pose a l'ecran, ellipse comprise ; vide tant que
    /// l'habillage n'est pas monte. C'est l'enfant direct de classe `text`, ce qui ecarte le texte
    /// d'un graphique porte par le meme controle.
    private static Optional<String> texteDessine(Labeled libelle) {
        return libelle.getChildrenUnmodifiable().stream()
                .filter(enfant ->
                        enfant instanceof Text && enfant.getStyleClass().contains("text"))
                .map(enfant -> ((Text) enfant).getText())
                .findFirst();
    }

    private static String titreDeLaColonne(TableCell<?, ?> cellule) {
        return Optional.ofNullable(cellule.getTableColumn())
                .map(TableColumnBase::getText)
                .orElse("");
    }

    /// Un controle compose (`ComboBox`, `MenuButton`) rend son texte dans un libelle **interne**, que le
    /// FXML ne peut pas marquer. La tolerance posee sur le controle vaut donc pour sa doublure.
    private static boolean dansUnParentAbregeable(Labeled libelle) {
        for (Node parent = libelle.getParent(); parent != null; parent = parent.getParent()) {
            if (parent.getStyleClass().contains(ABREGEABLE)) {
                return true;
            }
        }
        return false;
    }

    private static double largeurManquante(Labeled libelle) {
        return libelle.prefWidth(-1) - libelle.getWidth();
    }

    /// De quoi retrouver le libelle fautif : son identifiant s'il en a un, sinon le debut de son texte.
    private static String resumer(Labeled libelle) {
        if (libelle.getId() != null && !libelle.getId().isBlank()) {
            return "#" + libelle.getId();
        }
        String texte = libelle.getText() == null ? "" : libelle.getText();
        return "« " + (texte.length() > 40 ? texte.substring(0, 40) + "…" : texte) + " »";
    }
}
