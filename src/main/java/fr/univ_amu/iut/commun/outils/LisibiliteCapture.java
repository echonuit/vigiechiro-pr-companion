package fr.univ_amu.iut.commun.outils;

import java.util.ArrayList;
import java.util.List;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Labeled;
import javafx.scene.control.TextInputControl;
import javafx.scene.text.Text;

/// Refuse une capture dont un texte est **illisible**, plutot que d'ecrire une image qui ment.
///
/// Extrait d'[ApercuFx] quand ce dernier a appris a voir les invites de saisie (#3170) : rendre une
/// scene en PNG et juger sa lisibilite sont deux preoccupations, et la seconde a maintenant sa propre
/// taille. `ApercuFx` rend, cette classe refuse.
///
/// Trois modes de troncature, decouverts dans cet ordre et par une revue **a l'oeil**, jamais par un
/// test : le libelle enroulable **comprime** en hauteur (#2049), le libelle **ellipse** en largeur
/// (#1641, #1701, #1873, #1579, #2012), et l'**invite** d'un champ de saisie coupee **sans ellipse**
/// (#3170) - le plus trompeur des trois, puisque l'ellipse est justement l'aveu qu'on cherche.
public final class LisibiliteCapture {

    private LisibiliteCapture() {}

    /// Fin de chaque constat : « … manque N px) ». Ecrit une fois, PMD refusant sa triple copie.
    private static final String SUFFIXE_PX = " px)";

    /// Tolerance de comparaison, en pixels : la mise en page produit des ecarts d'arrondi qui ne sont
    /// pas des elisions.
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
            if (manque > TOLERANCE_PX) {
                comprimes.add(resumer(libelle) + " (manque " + Math.round(manque) + SUFFIXE_PX);
            }
        }
        if (noeud instanceof Labeled large && estTronqueEnLargeur(large)) {
            comprimes.add(resumer(large) + " (tronque, manque " + Math.round(largeurManquante(large)) + SUFFIXE_PX);
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

    /// Classe CSS par laquelle un FXML **assume** qu'un libelle se raccourcisse quand la place manque.
    ///
    /// Le deficit d'une barre doit bien tomber quelque part : figer tous ses controles ne le supprime pas,
    /// il le deplace. Cette classe designe celui qui le porte - typiquement un selecteur, dont la valeur
    /// se relit au deroule, plutot qu'un libelle d'action, qui ne se relit nulle part.
    ///
    /// Elle vit dans le FXML et non dans une liste tenue ici : l'exception se lit a l'endroit ou elle
    /// s'applique, par celui qui modifie la vue.
    public static final String ABREGEABLE = "abregeable";

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
                && libelle.getWidth() > 0
                && libelle.getText() != null
                && !libelle.getText().isBlank()
                && !libelle.getStyleClass().contains(ABREGEABLE)
                && !dansUnParentAbregeable(libelle)
                && largeurManquante(libelle) > TOLERANCE_PX;
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
