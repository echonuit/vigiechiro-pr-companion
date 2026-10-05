---
type: adr
title: "Un libellé ne se partage pas, et une passe à plusieurs lots dit ce qu'elle perd"
status: stable
article: A3
chantier: "#5936 (une passe à plusieurs lots perdait un énoncé et des arêtes sans le dire), lot 9 de #5812"
decided_at: 2026-10-05
verification: certaine
enforced_by:
  - "scripts/graphify/couche_semantique.py"
verified:
  - by: machine:ci
    at: 2026-10-05
relations:
  complete: ["5904-une-hyperarete-se-declare-et-les-ponts-parcourent-les-pages-du-graphe"]
generated:
  by: "process:assistance-par-agents"
---

# Un libellé ne se partage pas, et une passe à plusieurs lots dit ce qu'elle perd

## Le contexte

L'ADR 5813 dit qu'une mise à jour déclare l'identifiant sémantique qu'elle lâche, et l'ADR 5904
l'a étendu aux hyperarêtes. Les deux ont été éprouvées par des passes d'un seul lot.

La clôture du chantier #5812 a fait la première réextraction à plusieurs lecteurs depuis que
l'outillage est au dépôt : 37 pages, 4 lots. Les quatre rendus passaient l'audit, et la fusion
rendait « perdus du fait des lots=0 ». Deux pertes n'y figuraient pas.

## Ce qui a été mesuré

Le 5 octobre 2026, en comparant le graphe de référence avant et après cette fusion :

| | |
|---|---:|
| énoncés émis par les quatre lots | 918 |
| énoncés d'avant absents après, sans qu'aucun lecteur les ait lâchés | 1 |
| arêtes d'avant portées par les fiches | 1 023 |
| dont une extrémité que l'audit refusait | 13, sur 6 pages |
| lecteurs qui ont rencontré ce refus | 4 sur 4 |

L'énoncé perdu portait le même libellé qu'un énoncé d'une autre page, dans un autre lot. Le
moteur dédoublonne par libellé : il a fondu les deux nœuds, et gardé l'identifiant de l'un.

Les treize arêtes aboutissaient sur une page que la passe ne relisait pas. Leur cible n'était ni
dans les lots, ni dans les index.

## La décision

**Un libellé ne se partage pas entre deux énoncés. L'audit le refuse, entre deux lots comme dans
un seul, en nommant les deux énoncés.** C'est là que le lecteur peut encore dire ce que sa page
en dit, ou relier les deux.

**La fusion compte les énoncés qu'elle attendait, et nomme celui que le moteur a fondu, avec
celui qui reste. Elle ne refuse pas.** Un libellé déjà dans le graphe, sur une page hors de la
passe, échappe à l'audit, qui ne voit pas le graphe. Deux pages peuvent dire la même chose, et
bloquer une passe pour une page qu'elle ne touche pas coûterait plus que de le dire.

**La fiche range sous `ailleurs` l'extrémité d'une arête qui vit sur une page hors de la passe, et
l'audit l'admet.** C'est la règle de l'ADR 5904 pour le membre d'une hyperarête, étendue aux
arêtes. Une extrémité sur une page de la même passe n'y est pas rangée : son lecteur la réémet
ou la déclare, et c'est par ce que les autres lots émettent qu'elle est admise.

**L'audit écrit une ligne pour un lot sain.** Tant que d'autres lots manquaient, son lecteur ne
distinguait pas « mon lot passe » de « mon lot n'a pas été lu ».

## Ce que cela ne couvre pas

Deux libellés proches sans être égaux. Le moteur en fond aussi, par ressemblance, et l'audit ne
compare que l'égalité. La fusion les nomme quand même : elle compte ce qui manque, quelle qu'en
soit la cause.

Une arête d'avant dont la cible, sur une page de la même passe, est lâchée par son lecteur.
Elle part avec elle.

## Les alternatives écartées

- **Ne jamais refuser, toujours dire.** La couche d'une des deux pages perdrait un nœud à chaque
  passe, alors que son lecteur pouvait l'éviter.
- **Refuser aussi à la fusion.** Une passe serait bloquée par le libellé d'une page qu'elle ne
  relit pas, et que personne dans la passe ne peut corriger.
- **Admettre toute extrémité que la fiche porte.** Une cible sur une page de la même passe peut
  avoir été lâchée par son lecteur : l'arête pointerait sur rien.

## Comment on le sait

L'auto-test de `scripts/graphify/couche_semantique.py`, lancé par `lint.yml`, joue l'audit sur
deux lots : il refuse les deux quand ils partagent un libellé, et écrit une ligne pour chacun
quand ils sont sains. Il joue `fusionne` avec un faux moteur qui fond un énoncé, et lit la ligne
qui le nomme. Il joue la découpe sur un dépôt témoin, pour une cible hors de la passe puis dans
la passe.

Avec le moteur, absent du runner, la mesure se rejoue à la main sur une copie du graphe : les
quatre rendus de la clôture y font refuser les deux lots en cause, et le lot seul y fait nommer
l'énoncé fondu.
