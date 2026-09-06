package fr.univ_amu.iut.commun.view;

import com.google.inject.Inject;
import fr.univ_amu.iut.commun.model.PreferenceDesignation;
import java.util.Objects;
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
/// **Injectable, sa fenêtre restant un argument de méthode** : c'est ce qui lui permet de lire le
/// réglage sans qu'aucun écran n'ait à connaître [Reglages]. Elle ne le lit pas ici pour autant -
/// [SelecteurSelonLaPreference] le lit au moment de désigner. Voir l'[ADR
/// 5310](../../../../../../../dev-docs/decisions/5310-la-fabrique-est-injectable-la-fenetre-reste-un-argument.md).
public final class Selecteurs {

    private final PreferenceDesignation preference;

    @Inject
    public Selecteurs(PreferenceDesignation preference) {
        this.preference = Objects.requireNonNull(preference, "preference");
    }

    /// Le porteur de désignation d'un écran, remplaçable par un double en test.
    ///
    /// Rend un [SelecteurFichierModifiable] et non un [SelecteurFichier] nu, parce que c'est ce que
    /// les douze appelants gardent en champ `final` et exposent à leurs tests. Le porteur est la
    /// couture de testabilité ; celle-ci est la couture de **configuration**, et les deux se
    /// superposent sans se remplacer.
    ///
    /// @param fenetre la fenêtre propriétaire, évaluée au moment de choisir ; peut rendre `null`
    public SelecteurFichierModifiable pour(Supplier<Window> fenetre) {
        return new SelecteurFichierModifiable(new SelecteurSelonLaPreference(preference, fenetre));
    }
}
