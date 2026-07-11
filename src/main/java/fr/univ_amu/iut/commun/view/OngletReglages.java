package fr.univ_amu.iut.commun.view;

import java.util.List;

/// Contrat d'un **onglet de réglages** contribué par une feature à l'écran « Réglages » du chrome.
///
/// Même mécanisme d'inversion de dépendance que [ActiviteAccueil] : le socle (`commun.view`) déclare
/// ce contrat ; chaque feature en fournit une implémentation (dans son propre `viewmodel`, comme
/// [IndicateurAccueil]) et l'enregistre dans le `Multibinder<OngletReglages>` de son module Guice.
/// L'écran de réglages injecte alors `Set<OngletReglages>` et bâtit un onglet par contribution **sans
/// dépendre d'aucune feature** : le graphe de slices reste acyclique (cf. `ArchitectureTest`), et
/// ajouter des réglages revient à écrire une implémentation + une ligne de binding.
///
/// Les réglages sont **déclaratifs** : la feature ne manipule que des [DescripteurReglage] (données
/// pures, sans JavaFX), et c'est le socle qui en dérive les contrôles d'IHM. Pour le cas rare d'un
/// réglage qui ne se décrit pas ainsi, voir l'échappatoire [OngletReglagesPersonnalise].
public interface OngletReglages {

    /// Identité **stable** de la feature (`"importation"`, `"audio"`…) : sert à dédupliquer, ordonner
    /// et, à terme, désactiver une contribution. Distincte du [#titre()] affiché, qui peut changer.
    String idFeature();

    /// Rang d'affichage de l'onglet parmi les autres (ordre croissant : les plus petits en premier).
    int ordre();

    /// Titre court de l'onglet (ex. « Import », « Général »).
    String titre();

    /// Code d'icône [Ikonli](https://kordamp.org/ikonli/) FontAwesome 5 affiché sur l'onglet (ex.
    /// `"fas-file-import"`). Chaîne vide si aucune icône : c'est le socle qui construit le `FontIcon`,
    /// la feature reste libre de toute dépendance JavaFX/Ikonli.
    default String iconeLiteral() {
        return "";
    }

    /// Réglages déclarés par la feature, rendus par le socle **dans l'ordre de la liste**. Une liste
    /// vide masque l'onglet (sauf échappatoire [OngletReglagesPersonnalise]).
    List<DescripteurReglage> reglages();
}
