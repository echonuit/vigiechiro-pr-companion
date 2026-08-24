---
type: adr
title: "Un motif de blocage se montre, il ne se lit pas"
status: stable
article: A13
chantier: "#4234, EPIC #4133"
decided_at: 2026-08-23
verification: certaine
enforced_by:
  - "ScenarioFicheSiteTest#les_boutons_disent_ce_qui_les_empeche"
verified:
  - by: machine:ci
    at: 2026-08-23
relations:
  prolonge: ["4142"]
---

# Un motif de blocage se montre, il ne se lit pas

## Contexte

L'affordance #789 du dépôt tient en deux temps : un geste impossible se **ferme**, et son motif
**nomme ce qui manque**. Le second temps est ce qui distingue un bouton gris utile d'un bouton gris
frustrant.

Trois cas de recette existaient pour faire juger précisément ce second temps. Aucun ne le montrait.

| Cas | Ce qu'il promet | Ce que le clip montrait |
|---|---|---|
| `S1-19` | « les boutons **disent** ce qui les empêche » | deux boutons ternes, aucune explication |
| `S1-16` | le geste fermé « dit ce qui manque » | un bouton grisé impeccable, muet |
| `S1-33` | idem, sur « Vérifier sur Vigie-Chiro » | idem |
| `S1-36` | « Créer » se ferme **avec son motif** | le gris seul |

Les quatre lisaient le texte de l'infobulle par `InfobulleDeBlocage.texteDe`, qui va le chercher dans
les propriétés du noeud. L'assertion passait, le film ne montrait rien, et le retour de la revue disait
« ne montre pas ce qu'il doit » (#4173, #4182).

Le défaut est celui que ce chantier traque partout : **un dispositif dont le verdict est plus large que
ce qu'il a constaté**. « Le motif est bon » y était prouvé ; « le motif est visible » ne l'était pas, et
c'est le second que le cas annonçait.

## Décision

Un cas dont l'objet est qu'un geste dit ce qui l'empêche **fait paraître** ce motif, par le survol, et
**attend** son apparition. Cette attente est une assertion : si l'infobulle ne vient pas, le test échoue
au lieu de filmer un écran muet.

`InfobulleDeBlocage.montrerEtLire(enveloppe, robot)` porte les trois gestes - survoler, attendre, lire.

## Conséquences

Un motif qu'on ne peut pas **faire venir** n'existe pas pour l'utilisateur. La règle transforme donc un
défaut invisible (une explication inatteignable) en échec de test, là où l'ancienne forme le laissait
passer sous un vert.

Le clip s'allonge d'un temps de lecture par motif. C'est le prix de ce que le cas prétend faire juger.

⚠️ `InfobulleDeBlocage.texteDe` reste, pour les tests **non filmés** : ils vérifient le texte, pas sa
venue, et leur faire payer un survol ne prouverait rien de plus.

## Alternatives écartées

**Afficher le motif en permanence à côté du bouton.** Ce serait changer le produit pour filmer le
produit. L'affordance #789 pose le motif au survol ; la recette montre ce que le produit fait, pas ce
qui serait commode à filmer.

**`Tooltip.show(...)` posé par le test.** Le motif paraîtrait sans que le geste qui le déclenche soit
joué : le clip montrerait une bulle surgie de nulle part, et le spectateur ne saurait pas qu'il faut
survoler pour l'obtenir.
