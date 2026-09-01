---
type: adr
title: "Un garde sans unité à compter le déclare chez lui, avec sa raison"
status: stable
article: A11
chantier: "#5015, sous-chantier du compte lu (#5006)"
decided_at: 2026-09-01
verification: humaine
verification_note: "qu'une population vide soit la réussite d'un garde est un jugement sur ce que ce garde veut dire. Aucun motif ne le distingue d'un garde qui a manqué son ciblage. Ce qui se tient mécaniquement est le COMPTE des non-déclarants, qui ne remonte pas"
verified:
  - by: human:nedseb
    at: 2026-09-01
generated:
  by: "process:assistance-par-agents"
  at: 2026-09-01
---

# Un garde sans unité à compter le déclare chez lui, avec sa raison

## Contexte

Le sous-chantier #5015 a fait déclarer `lus` à 41 appels de verdict sur 45. Le compte visait zéro
non-déclarant. Il s'est arrêté à **quatre**, et ce plancher n'est pas de la dette.

**Trois sont des témoins du harnais.** Les appels de `verifie_scripts.py` aux lignes 1206, 1237 et
1344 ne sont pas des gardes qui lisent une population : ils éprouvent le contrat de `_commun`. Celui
de 1237 éprouve « une population non déclarée ne refuse pas encore », celui de 1344 « le rapport les
lit encore quand le compte n'est pas déclaré ». Leur mettre `lus=` détruirait exactement ce qu'ils
gardent, **et le ferait en rendant les tests verts**.

**Le quatrième est `compte-les-reliquats.py`.** Il signale les répertoires temporaires qu'une suite
laisse derrière elle. Aucune de ses populations n'a de zéro anormal : sur un runner vierge dont la
suite nettoie bien, ni le témoin d'avant ni les répertoires présents ne portent rien. Or `lus=0`
refuse. Déclarer l'une ou l'autre ferait rougir la réussite même du garde.

## Décision

**Un garde dont la population vide est sa réussite ne déclare pas `lus`, et écrit POURQUOI dans son
propre fichier.**

La raison vit chez le garde, non dans un registre central ni dans l'ADR seule : c'est là qu'on
l'ouvre quand on se demande pourquoi ce garde-ci ne déclare rien. L'ADR dit la règle, le fichier dit
son cas.

**Et ce que l'exception coûte s'écrit avec elle.** `compte-les-reliquats` porte ceci : si son motif
cessait d'apparier, il rendrait « aucun reliquat » en silence et rien ne le dirait. La cécité est
assumée, faute d'une population qui la révélerait.

## Conséquences

- Le plancher du compte des non-déclarants vaut **4**, et non 0. Un cliquet posé à zéro aurait
  poussé à convertir les trois témoins, ce qui les aurait cassés en vert.
- Une exception se relit : chacune est nommée, avec sa raison, à l'endroit où elle s'applique.
- Un garde qui rejoindrait ce plancher devra écrire sa raison chez lui, faute de quoi il sera
  indiscernable d'un retardataire.

## La cécité déclarée

Rien ne distingue mécaniquement « ce garde n'a pas d'unité » de « ce garde n'a pas encore été
converti ». Le compte des non-déclarants se refait à la main, par un recensement AST qui résout la
liaison de chaque nom. Poser un garde dessus demande d'abord de nommer les exceptions, ce que cette
ADR fait, et c'est le travail de **#5049**.
