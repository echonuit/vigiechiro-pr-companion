package fr.univ_amu.iut.commun.view;

import java.util.List;

/// Contrat **optionnel** d'un écran (son controller) qui déclare son **emplacement** dans la hiérarchie
/// de navigation : la liste ordonnée de ses ancêtres puis lui-même, rendue en fil d'Ariane par le chrome.
///
/// Le fil d'Ariane est **hiérarchique** (emplacement), distinct de l'historique de retour porté par le
/// [Navigateur] (cf. la décision « sémantique hybride » : « remonter » dans le fil ≠ « ← Retour »). C'est
/// ce qui permet, par exemple, qu'un passage atteint depuis « Vue multi-sites » affiche tout de même son
/// site dans le fil (`Accueil › Mes sites › Carré N › Passage`).
///
/// Les segments retournés **n'incluent pas** le « Accueil » de tête (ajouté par le chrome). Le dernier
/// segment est l'écran courant (non cliquable, cf. [Lieu#courant]). Un écran qui n'implémente pas ce
/// contrat retombe sur un fil construit à partir de l'historique.
public interface EmplacementNavigation {

    /// Segments de l'emplacement, du plus haut au plus bas, **sans** « Accueil ». Le dernier = l'écran
    /// courant.
    List<Lieu> emplacement();
}
