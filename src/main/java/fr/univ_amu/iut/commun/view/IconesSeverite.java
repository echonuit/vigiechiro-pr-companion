package fr.univ_amu.iut.commun.view;

import fr.univ_amu.iut.commun.viewmodel.RetourOperation.Severite;
import java.util.Map;
import org.kordamp.ikonli.Ikon;
import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;
import org.kordamp.ikonli.javafx.FontIcon;

/// La **forme** d'une sévérité : un glyphe Ikonli par niveau, source unique pour toutes les surfaces.
///
/// L'application dit une sévérité **deux fois**, en couleur et en forme, « pour qui distingue mal les
/// couleurs comme pour qui lit vite ». Cette promesse n'a de valeur que si les deux canaux ne peuvent
/// pas se contredire - donc si la table est écrite **une seule fois**.
///
/// Elle vivait dans [BandeauRetour] seul. Quand [VueCompteRendu] a eu besoin des mêmes icônes (clôture
/// de #2004), la recopier aurait créé deux tables libres de diverger - exactement le motif que #1974 a
/// passé une PR à corriger sur les classes CSS.
///
/// L'erreur porte le **cercle barré** et non le triangle : celui-ci est le glyphe usuel de
/// l'avertissement, et deux niveaux distincts qui partagent une forme ne se distinguent plus quand la
/// couleur manque (#2052).
public final class IconesSeverite {

    private static final Map<Severite, Ikon> GLYPHE = Map.of(
            Severite.SUCCES, FontAwesomeSolid.CHECK_CIRCLE,
            Severite.INFO, FontAwesomeSolid.INFO_CIRCLE,
            Severite.AVERTISSEMENT, FontAwesomeSolid.EXCLAMATION_TRIANGLE,
            Severite.ERREUR, FontAwesomeSolid.TIMES_CIRCLE);

    private IconesSeverite() {}

    /// L'icône Ikonli d'une sévérité, comme **valeur typée**.
    ///
    /// C'est la forme qu'attend [IconeSelonEtat], qui lie un glyphe par `Bindings.when` plutôt que par
    /// un écouteur. Les deux composants partagent donc la même table, ce qui n'était pas le cas quand
    /// celle-ci ne parlait qu'en littéraux : `DetailInspection` avait dû poser ses icônes à la main.
    public static Ikon ikon(Severite severite) {
        return GLYPHE.get(severite);
    }

    /// Le littéral du glyphe, pour les surfaces qui construisent leur icône elles-mêmes.
    public static String glyphe(Severite severite) {
        return ikon(severite).getDescription();
    }

    /// Une icône dont la couleur vient du **conteneur** : dans un encart, la règle
    /// `.encart-<severite> .ikonli-font-icon` s'en charge, donc l'icône n'a pas de classe propre.
    public static FontIcon icone(Severite severite) {
        return new FontIcon(glyphe(severite));
    }

    /// Une icône neuve pour cette sévérité, portant les `classes` demandées.
    ///
    /// L'appelant lui donne **la classe de sévérité du texte qu'elle accompagne** : la feuille de style
    /// y pose `-fx-icon-color` à côté de `-fx-text-fill`, donc les deux se colorent depuis la même règle
    /// et ne peuvent pas diverger. Un `FontIcon` ne suit pas `-fx-text-fill` : sans classe, il prend
    /// celle du conteneur ou reste au défaut, et peut alors contredire le texte au lieu de le confirmer.
    public static FontIcon icone(Severite severite, String... classes) {
        FontIcon icone = new FontIcon(ikon(severite));
        icone.getStyleClass().addAll(classes);
        return icone;
    }
}
