package fr.univ_amu.iut.multisite.viewmodel;

import fr.univ_amu.iut.commun.model.Severite;
import fr.univ_amu.iut.commun.model.SuiviTraitement.BilanReleveGroupe;
import fr.univ_amu.iut.commun.viewmodel.CompteRenduChiffre;
import fr.univ_amu.iut.commun.viewmodel.CompteRenduChiffre.Action;
import fr.univ_amu.iut.commun.viewmodel.CompteRenduChiffre.Avertissement;
import fr.univ_amu.iut.commun.viewmodel.CompteRenduChiffre.Segment;
import fr.univ_amu.iut.commun.viewmodel.CompteRenduChiffre.Teinte;
import fr.univ_amu.iut.commun.viewmodel.CompteRenduChiffre.Ventilation;
import java.util.ArrayList;
import java.util.List;

/// Traduit un **relevé groupé** en compte rendu chiffré (#2757), celui que rend
/// [fr.univ_amu.iut.commun.view.PanneauCompteRendu].
///
/// ## Pourquoi il remplace une phrase
///
/// Le relevé composait son compte rendu à la main, dans un [fr.univ_amu.iut.commun.viewmodel.RetourOperation] :
/// *« État relevé pour 9 nuit(s) sur 12 : 3 injoignable(s), leur dernier état connu reste affiché. »*
///
/// Deux choses clochaient. D'abord la **proportion ne se voyait pas** : « 9 sur 12 » se lit, une barre
/// dont le tiers droit est ocre se **voit**. Ensuite un retour est **borné** par définition, c'est ce qui
/// le sépare d'un compte rendu ([ADR
/// 0031](../../../../../../../dev-docs/decisions/0031-un-retour-n-est-pas-un-compte-rendu.md)) ;
/// or ce texte grandissait avec le nombre d'états à mentionner. Il était dans le mauvais véhicule.
///
/// Le bilan s'y prêtait déjà : [BilanReleveGroupe#total()] vaut `rafraichis + echecs`, donc deux parts
/// d'une **même unité** qui couvrent le tout. C'est exactement la forme que le constructeur de
/// [Ventilation] sait exiger, et refuser quand elle manque.
///
/// ## Pourquoi les injoignables ne sont pas en rouge
///
/// Une nuit qu'on n'a pas pu joindre a bien échoué techniquement, mais **rien n'est perdu** : son dernier
/// état connu reste affiché, et un nouveau clic la relèvera. En faire une part [Teinte#REFUSE] contredirait
/// la sévérité que ce compte rendu porte déjà - ni un succès (ce serait mentir sur la fraîcheur obtenue),
/// ni une erreur (l'écran continue de montrer des données justes, seulement plus anciennes).
///
/// D'où [Teinte#ECARTE] : « écarté sans que ce soit un échec » décrit précisément ce qui s'est passé.
public final class CompteRenduChiffreReleve {

    private CompteRenduChiffreReleve() {}

    /// Le compte rendu chiffré d'un relevé groupé terminé.
    ///
    /// @param bilan les nuits rafraîchies et celles qu'on n'a pas pu joindre
    /// @param actions ce que l'écran propose ensuite ; c'est lui qui sait où mènent ses boutons
    public static CompteRenduChiffre de(BilanReleveGroupe bilan, List<Action> actions) {
        return new CompteRenduChiffre(
                titre(bilan),
                resultat(bilan),
                severite(bilan),
                List.of(),
                ventilation(bilan),
                List.of(),
                avertissements(bilan),
                actions);
    }

    /// Le titre dit la fin qu'on a eue, avant que les barres ne l'expliquent.
    private static String titre(BilanReleveGroupe bilan) {
        return bilan.echecs() == 0 ? "État des analyses à jour" : "Relevé partiel";
    }

    /// « 12 relevées », ou « 9 / 12 relevées » dès qu'il en manque.
    private static String resultat(BilanReleveGroupe bilan) {
        return bilan.echecs() == 0
                ? bilan.total() + " relevées"
                : bilan.rafraichis() + " / " + bilan.total() + " relevées";
    }

    /// Jamais [Severite#ERREUR] : voir la Javadoc de la classe. Un relevé partiel reste une information.
    private static Severite severite(BilanReleveGroupe bilan) {
        return bilan.echecs() == 0 ? Severite.SUCCES : Severite.INFO;
    }

    /// Deux parts, une unité, exhaustives : le constructeur de [Ventilation] refuserait le contraire.
    private static Ventilation ventilation(BilanReleveGroupe bilan) {
        List<Segment> segments = new ArrayList<>();
        segments.add(new Segment("Relevées", bilan.rafraichis(), bilan.rafraichis() + " nuit(s)", Teinte.RETENU));
        if (bilan.echecs() > 0) {
            segments.add(new Segment("Injoignables", bilan.echecs(), bilan.echecs() + " nuit(s)", Teinte.ECARTE));
        }
        return new Ventilation("nuits déposées", bilan.total(), segments);
    }

    /// Ce qui reste vrai à la fin. La part injoignable a besoin d'une phrase que la barre ne porte pas :
    /// que rien n'est perdu, et qu'un nouveau clic suffira.
    ///
    /// **Un avertissement, alors que le compte rendu est une information ?** Oui, et ce n'est pas une
    /// contradiction : l'issue du relevé est informative (rien n'a cassé), mais le fait que trois nuits
    /// n'aient pas été jointes est bien « ce sur quoi il faut revenir ». C'est l'usage que fait déjà la
    /// bande d'import pour ses lignes ignorées - un fait à reprendre, suivi de la conduite à tenir. Le
    /// piège à éviter serait d'alerter sur la **bonne** nouvelle, ce que [#avertissements] ne fait pas :
    /// un relevé complet n'en produit aucun.
    private static List<Avertissement> avertissements(BilanReleveGroupe bilan) {
        if (bilan.echecs() == 0) {
            return List.of();
        }
        return List.of(Avertissement.de(bilan.echecs()
                + " nuit(s) n'ont pas pu être relevées : leur dernier état connu reste affiché, et un"
                + " nouveau relevé les reprendra."));
    }
}
