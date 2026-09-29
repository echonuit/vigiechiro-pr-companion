---
type: adr
title: "Les trois index répondent, et aucun ne devient un garde"
status: stable
article: A3
chantier: "#5553 (deux tiers de la frontière de #5464 restent sans index)"
decided_at: 2026-09-29
verification: certaine
enforced_by:
  - "scripts/qualite/appelants.py"
  - "scripts/qualite/implemente.py"
  - "scripts/qualite/lecteurs.py"
verified:
  - by: machine:ci
    at: 2026-09-29
relations:
  prolonge: ["5532-un-index-d-appels-repond-il-ne-refuse-pas"]
generated:
  by: "process:assistance-par-agents"
---

# Les trois index répondent, et aucun ne devient un garde

## Le contexte

L'ADR 5532 a tranché pour **un** index : celui des appels répond, il ne refuse pas. Elle est écrite au
singulier, avec un seul applicateur, parce qu'un seul existait.

Le chantier #5553 en a livré deux autres, sur le même modèle Spoon : les implémentations (#5564) et
les lectures de champ (#5565). La règle se transpose, mais sa **raison** ne se transpose pas d'elle
même, et c'est le geste qu'on saute.

## La mesure qui décide

Au 2026-09-29, pour chaque index, la part du corpus qui n'a aucun voisin :

| index | sans relation externe | part |
|---|---:|---:|
| appels | 11 531 méthodes | 69 % |
| implémentations | 27 contrats | 22 % |
| champs | 8 196 champs | **92 %** |

**Le troisième est le plus tentant, et de loin.** Un lecteur qui découvre 8 196 champs sans lecteur
hors de leur classe voit une liste de code mort. Elle n'en est pas une : un état privé lu par les
méthodes de sa propre classe est exactement cela, et c'est la forme normale d'une classe.

## La décision

**La règle de l'ADR 5532 vaut pour les trois index, et le taux le plus raide est celui qui l'exige le
plus.** Aucun des trois ne compte de suspects, n'a de cliquet, ni ne fait rougir une demande. Leur
seul code non nul est l'absence de leur index.

Ce qui change par rapport à 5532 n'est pas la règle, c'est ce qui la rend nécessaire : à 69 %, un
garde bâti sur « sans appelant externe » serait bruyant ; à 92 %, il signalerait presque tout le
corpus, donc rien.

## Ce que cela n'interdit pas

Un garde sur un **sous-ensemble motivé** reste possible : PMD juge déjà `UnusedPrivateField` dans la
classe qui déclare le champ, et rien ici ne l'en empêche. Ce que la décision refuse est le garde bâti
sur la liste **entière** que l'index rend.

## Les alternatives écartées

- **Un cliquet sur les champs sans lecteur externe.** Il partirait à 8 196 et ne descendrait jamais :
  un cliquet qui ne peut pas bouger n'est pas un cliquet, c'est un chiffre.
- **Étendre 5532 plutôt qu'écrire ici.** 5532 est acceptée et son titre dit « un index d'appels » ; un
  lecteur venu du troisième index ne l'y chercherait pas. Une ADR se prolonge, elle ne se réécrit pas.
- **Ne rien écrire, la docstring suffit.** Elle suffit à qui ouvre `lecteurs.py`. Elle ne dit rien à
  qui ouvre l'index lui-même et compte ses entrées vides.

## Comment on le sait

Les trois outils portent un cas qui l'éprouve par leur point d'entrée entier : sur un index
**fabriqué**, la réponse sort en **0** même quand la liste rendue est vide ; sur un index **absent**,
et sur cela seul, ils sortent en **1**. Le contraste est ce qui juge : sans le cas positif, rien ne
distinguerait un outil qui refuse l'absence d'un outil qui refuse toujours.

Vingt-six mutations jouées à la main sur le chantier, vingt-cinq tuées. La vingt-sixième est déclarée
survivante et sa mesure est portée sur #5192 : court-circuiter l'`--auto-test` d'un outil est
indétectable de l'intérieur, et le dispositif qui l'attraperait est un banc dont `scripts/qualite` est
exclu.
