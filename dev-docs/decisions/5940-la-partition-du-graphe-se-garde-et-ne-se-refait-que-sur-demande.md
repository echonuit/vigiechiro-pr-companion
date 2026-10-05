---
type: adr
title: "La partition du graphe se garde, et ne se refait que sur demande"
status: stable
article: A3
chantier: "#5940 (les libellés des communautés se perdaient pour un quart), lot 11 de #5812"
decided_at: 2026-10-05
verification: certaine
enforced_by:
  - "scripts/graphify/rebuild.py"
verified:
  - by: machine:ci
    at: 2026-10-05
relations:
  complete: ["5814-une-empreinte-par-page-dit-ce-qui-est-a-reextraire"]
generated:
  by: "process:assistance-par-agents"
---

# La partition du graphe se garde, et ne se refait que sur demande

## Le contexte

L'ADR 5814 reporte les libellés des communautés d'une reconstruction à la suivante, par
recouvrement de membres. Elle écrivait que le report ne retrouve pas tout, sans en chercher la
cause : on la croyait dans la règle de report.

Elle était dessous. La reconstruction repartitionnait le graphe entier à chaque fois. Le
partitionneur du moteur est déterministe pour un graphe donné, et sensible au moindre changement
du graphe. Un libellé suit sa communauté : quand elle n'existe plus, il n'a plus où aller.

## Ce qui a été mesuré

Le 5 octobre 2026, entre deux états du graphe de référence séparés par 365 nœuds de plus sur
36 000 :

| | Partition refaite | Partition gardée |
|---|---:|---:|
| communautés d'avant retrouvées à l'identique | 388 sur 1 119 | 1 070 sur 1 119 |
| communautés d'avant dont le libellé se retrouve | non mesuré | 1 119 sur 1 119 |
| libellés distincts d'avant retrouvés | 702 sur 969 | 969 sur 969 |

Trois règles de report simulées sur la partition refaite, le recouvrement en place et un héritage
majoritaire à deux seuils, rendaient 68, 69 et 72 %. Changer la règle ne changeait rien.

La colonne de droite rejoue la fonction livrée sur la même paire d'états : 369 nœuds neufs,
tous rangés chez leurs voisins. Les 49 communautés qui ne sont plus identiques sont celles qui en
ont reçu. Avec le moteur, sur une copie du graphe et un changement plus petit, une page et une
classe modifiées : 896 libellés distincts retrouvés sur 896.

## La décision

**La reconstruction garde la communauté de chaque nœud qui existait.** Un nœud neuf va dans celle
de la majorité de ses voisins déjà rangés, le plus petit identifiant départageant. Un nœud qui
n'a aucun voisin rangé reçoit une communauté à lui.

**Les libellés d'une partition gardée se reportent par identifiant.** Le recouvrement perdrait
celui d'une communauté qui a fondu : dix membres avant, un seul aujourd'hui, c'est sous son
seuil.

**La partition entière ne se refait que sur demande**, par `--repartitionne`, et la première
fois, quand le graphe n'en porte aucune. La première ligne du journal dit laquelle des deux a eu
lieu, et combien de nœuds ont été rangés.

## Ce que cela ne couvre pas

Une partition gardée vieillit. Les nœuds neufs s'ajoutent aux communautés qui existent, et rien
ne les redécoupe quand le graphe a beaucoup bougé. Aucun seuil ne dit quand la redemander : la
ligne du journal compte les nœuds rangés à chaque fois, et c'est à qui la lit d'en juger.

Après une repartition demandée, les libellés repassent par le recouvrement, et se perdent comme
avant pour les communautés recomposées. C'est attendu : la partition est neuve.

`graphify update .` seul refait toujours la partition. C'est la commande du dépôt qui la garde.

## Les alternatives écartées

- **Changer la règle de report.** Trois essayées, aucune ne dépasse 72 %.
- **Amorcer le partitionneur par la partition d'avant.** Leiden le permet, mais il n'est pas
  installé sur le poste, et Louvain, que le moteur emploie à sa place, ne le sait pas. Ce serait
  faire dépendre le dépôt d'une dépendance du moteur.
- **Ne rien garder, et écrire que les libellés décrivent la partition du jour.** Sans coût, mais
  un libellé qui change à chaque reconstruction ne sert plus à se repérer d'une lecture à l'autre.

## Comment on le sait

L'auto-test de `scripts/graphify/rebuild.py`, lancé par `lint.yml`, joue le rangement sur des
voisinages fabriqués : la majorité, l'égalité, un nœud neuf dont le seul voisin est neuf, un
nœud sans voisin, et l'identifiant d'une communauté partie qui n'est pas repris. Il joue le
report par identifiant à côté du recouvrement, sur une communauté qui a fondu, et suit le drapeau
sur les deux chemins.

Le partitionneur lui-même est absent du runner. La mesure se rejoue à la main, avec le moteur,
sur une copie du graphe.
