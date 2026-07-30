package fr.univ_amu.iut.validation.model;

import java.util.Set;

/// Sait dire si un taxon est une **espèce à enjeu de conservation** (#2353) : une des espèces que le Plan
/// National d'Actions Chiroptères désigne comme prioritaires.
///
/// Lu **une fois**, à la construction, et jamais rafraîchi : c'est une donnée de **référence**, posée par
/// la migration au démarrage. L'application ne l'écrit pas, rien ne peut la changer en cours de session :
/// la relire à chaque chargement d'écran laisserait croire le contraire.
///
/// Clefé sur le **code du taxon**, et non sur une ligne : les trois écrans qui s'en servent filtrent des
/// choses différentes : une observation audio, une espèce agrégée, un contact horaire, mais posent tous
/// la même question. À chacun d'extraire le taxon qu'il **retient**.
///
/// L'ensemble est ensuite consulté une fois par ligne affichée : une requête par ligne n'y résisterait pas.
public final class MarqueurEspecesAEnjeu {

    /// La mention **textuelle** du repère, pour les surfaces qui ne peuvent pas porter d'icône : une
    /// ligne de détail, un CSV, une sortie de terminal. Le pendant graphique vit dans
    /// `commun.view.RepereEspeceAEnjeu`, qui dépend de JavaFX et n'a donc pas sa place ici.
    ///
    /// Elle **nomme le plan** plutôt que d'asséner « à enjeu » : sans sa source, la mention se lirait
    /// comme un jugement du produit. Le mot « espèce » en est absent, car les surfaces qui l'emploient
    /// désignent déjà une espèce : le répéter allongerait sans rien apprendre, dans une ligne déjà
    /// dense. L'écran vers lequel le résultat mène porte, lui, l'infobulle complète.
    public static final String MENTION = "prioritaire (PNA)";

    private final Set<String> codes;

    public MarqueurEspecesAEnjeu(EspecesPrioritaires referentiel) {
        this.codes = Set.copyOf(referentiel.codes());
    }

    /// Ce taxon est-il prioritaire ? `false` pour `null` : une séquence non identifiée n'est pas une
    /// espèce, et l'immense majorité des taxons détectés (oiseaux, orthoptères, micromammifères) ne
    /// relèvent tout simplement pas de ce plan.
    public boolean aEnjeu(String codeTaxon) {
        return codeTaxon != null && codes.contains(codeTaxon);
    }
}
