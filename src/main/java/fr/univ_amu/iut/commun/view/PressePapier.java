package fr.univ_amu.iut.commun.view;

import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;

/// Copie de texte vers le **presse-papier système** (#1798). Un `null` ou une valeur vide copie une chaîne
/// vide (jamais d'exception : un « Copier » ne doit pas casser un parcours).
public final class PressePapier {

    private PressePapier() {}

    public static void copier(String texte) {
        ClipboardContent contenu = new ClipboardContent();
        contenu.putString(texte == null ? "" : texte);
        Clipboard.getSystemClipboard().setContent(contenu);
    }
}
