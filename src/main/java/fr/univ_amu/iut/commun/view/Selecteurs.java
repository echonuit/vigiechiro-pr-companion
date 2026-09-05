package fr.univ_amu.iut.commun.view;

import java.util.function.Supplier;
import javafx.stage.Window;

/// Le **seul** endroit où le dispositif de désignation d'un fichier se construit.
///
/// ## Pourquoi une fabrique pour une seule ligne
///
/// Elle ne fait rien d'autre aujourd'hui que ce que douze écrans écrivaient eux-mêmes, et c'est
/// précisément le problème qu'elle résout : le choix du dispositif était écrit **douze fois**, donc
/// il n'existait aucun endroit où le changer. Introduire un second dispositif sans elle aurait
/// dupliqué le choix douze fois de plus.
///
/// Le changement `selecteur-de-repli` ajoutera un dialogue dessiné par l'application et un réglage
/// qui tranche entre les deux. **Ici**, et nulle part ailleurs.
///
/// ## Une fabrique, et non une liaison dans l'injecteur
///
/// Chaque écran fournit sa fenêtre, résolue **au moment de choisir** : l'action est bâtie avant que
/// l'écran ne soit attaché, et l'injecteur ne connaît pas cette fenêtre.
///
/// Le choix n'est pas encore un paramètre : rien n'est à choisir tant que le second dispositif
/// n'existe pas, et un paramètre à une seule valeur serait du décor. Le lot du réglage l'ajoutera
/// ici. Voir l'[ADR
/// 5307](../../../../../../../dev-docs/decisions/5307-le-dispositif-de-designation-se-choisit-en-un-endroit.md).
public final class Selecteurs {

    private Selecteurs() {}

    /// Le porteur de désignation d'un écran, remplaçable par un double en test.
    ///
    /// Rend un [SelecteurFichierModifiable] et non un [SelecteurFichier] nu, parce que c'est ce que
    /// les douze appelants gardent en champ `final` et exposent à leurs tests. Le porteur est la
    /// couture de testabilité ; celle-ci est la couture de **configuration**, et les deux se
    /// superposent sans se remplacer.
    ///
    /// @param fenetre la fenêtre propriétaire, évaluée au moment de choisir ; peut rendre `null`
    public static SelecteurFichierModifiable pour(Supplier<Window> fenetre) {
        return new SelecteurFichierModifiable(new SelecteurFichierJavaFx(fenetre));
    }
}
