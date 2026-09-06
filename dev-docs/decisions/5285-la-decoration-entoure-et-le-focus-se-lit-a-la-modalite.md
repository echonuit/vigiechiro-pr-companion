---
type: adr
title: "La décoration entoure sans remplacer, et le focus se lit à la modalité"
status: stable
article: A9
chantier: "#5282 (convergence des deux bancs filmés), lot #5285"
decided_at: 2026-09-06
verification: certaine
enforced_by:
  - "DecorationDeFenetreTest#la_decoration_n_empiete_pas_sur_la_scene"
  - "DecorationDeFenetreTest#le_focus_change_la_couleur_de_la_barre"
  - "CameraDeSceneTest#une_modale_d_application_bloque_tout"
verified:
  - by: machine:ci
    at: 2026-09-06
generated:
  by: "process:assistance-par-agents"
---

# La décoration entoure sans remplacer, et le focus se lit à la modalité

## Pourquoi une décoration DESSINÉE est légitime

Le banc de documentation tournait sous un gestionnaire de fenêtres, qui donnait aux clips un cadre :
barre de titre, bordure, pastilles. Le banc de recette n'en a pas, et ses clips montrent une scène nue.
Un lecteur de la documentation a besoin de savoir qu'il regarde **une fenêtre d'application**.

L'objection est l'[ADR 3788](3788-un-banc-qui-maximise-tout-ne-montre-pas-ce-qu-on-livre.md) : un banc
qui maximise tout ne montre pas ce qu'on livre. Elle **ne s'applique pas ici**, et le spike l'a établi
en relisant ce qu'elle refuse : le mensonge de `matchbox` portait sur la **mise en page** - une modale
rendue en 1280 × 900 au lieu de sa taille réelle, « contenu tassé en haut et grand vide en dessous ».

Une décoration dessinée **autour** d'une scène rendue à sa taille réelle ne déplace rien de ce qu'un
humain juge. Ce qui est simulé entoure ; il ne remplace pas.

**Une décoration neutre n'existe pas, et ce n'est pas un obstacle.** Les pastilles sont à droite ;
macOS les met à gauche. Ce qui compte est d'**avoir** une décoration, pas laquelle : le lecteur doit
savoir qu'il regarde une fenêtre, pas croire que c'est la sienne.

## Le focus ne se lit PAS avec `isFocused()`

Une barre de titre dit qui a la main. La lecture évidente est `Window#isFocused()`, et elle est
**fausse sous Monocle headless** : toutes les fenêtres s'y déclarent focalisées, si bien que les deux
barres sortaient bleues, y compris celle du fond derrière une modale.

Le focus se lit donc à la **modalité** : une fenêtre est bloquée par toute modale visible qui la
possède, et par toute modale d'application, qui bloque tout le monde.

```java
static boolean bloqueeParUneModale(Object fenetre, List<AutreFenetre> autres) {
    return autres.stream()
            .anyMatch(autre -> autre.visible() && autre.modale()
                    && (autre.applicative() || autre.proprietaire() == fenetre));
}
```

**C'est la ligne la plus facile à « simplifier » de ce chantier.** Un lecteur qui la trouve pensera
qu'un `isFocused()` dirait la même chose en plus court. Cette ADR existe pour qu'il sache pourquoi
non.

La règle prend un `record AutreFenetre` et non des `Window`, pour se jouer **sans toolkit JavaFX** :
une règle qu'aucun cas ne garde serait une règle décrite, pas gardée.

## Ce que la décoration n'entoure pas

Les `PopupWindow` ne sont pas décorées : une infobulle ou un menu déroulant n'est pas une fenêtre au
sens où l'utilisateur l'entend, et lui dessiner une barre de titre inventerait ce que le produit ne
montre pas.

Les **cartons** non plus. Ils ne sont pas une fenêtre du tout, et les décorer avait produit une image
étrange que le porteur du produit a relevée du premier coup d'œil.

## Conséquences

**Les constantes sont MESURÉES, pas choisies.** Barre de 20 px, bord de 1 px, dégradés relevés pixel
par pixel sur `parcours-declarer-un-carre.mp4`, le clip que le banc bash produit. C'est la comparaison
qui décide de la ressemblance, et non le goût : la première mesure disait 12 px, faute d'avoir vu que
la barre de la fenêtre principale est coupée par le bord de l'écran.

**La bordure est un anneau, pas un rectangle plein.** Un rectangle rempli passait **sous** la scène et
disparaissait ; le refus est venu de `DecorationDeFenetreTest`, pas de la relecture.

**La caméra centre la boîte DÉCORÉE**, pas la scène : sinon le cadre déborde d'un côté.
