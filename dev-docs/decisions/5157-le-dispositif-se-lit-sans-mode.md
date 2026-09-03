---
type: adr
title: "Le dispositif déclare ce que l'outil fait quand on l'appelle sans mode"
status: stable
article: A3
chantier: "#5157 (sous-chantier #5154)"
decided_at: 2026-09-03
verification: certaine
enforced_by:
  - "scripts/adr/verifie_contrats_tiennent.py"
verified:
  - by: machine:suspects
    at: 2026-09-03
generated:
  by: "process:assistance-par-agents"
  at: 2026-09-03
---

# Le dispositif déclare ce que l'outil fait quand on l'appelle sans mode

## Contexte

L'[ADR 5125] a clos le vocabulaire des dispositifs à sept valeurs. Elle ne dit pas **laquelle
choisir** quand un outil en fait plusieurs.

Le cas n'est pas marginal. `matrice-constitution.py` **écrit** la matrice de la constitution, et
déclare pourtant `dispositif: invariant`. `couverture-relecture.py` marque des fichiers relus, et
refuse quand la couverture a bougé. Neuf outils de `scripts/methode` portent un mode `--verifie` à
côté d'un mode qui produit.

**Une première règle a été écrite au découpage du sous-chantier C, et elle était fausse** : « le
dispositif déclare ce que l'outil fait quand la **CI** l'appelle ». Elle tient pour
`matrice-constitution.py`, dont la CI lance `--verifie`. Elle ne dit rien de trois outils sur
vingt-quatre : `releve-les-planchers.py` refuse un plancher périmé mais n'est appelé qu'en
`--auto-test`, et `convertit-adr-okf.py` comme `releve-des-secrets.py` ne figurent dans **aucun
atelier**.

## Décision

**Le dispositif déclare ce que l'outil fait quand on l'appelle sans mode**, c'est-à-dire son
comportement par défaut.

`matrice-constitution.py` sans argument régénère, mais son dispositif reste `invariant` parce que
son travail est de refuser une matrice périmée, et que la régénération est le service rendu à qui la
corrige. `convertit-adr-okf.py` sans argument rend un aperçu et ne juge rien : `generateur`.

## Pourquoi celle-là, et non l'autre

**Une règle indexée sur la CI est muette là où la CI est muette.** Trois outils sur vingt-quatre ne
sont dans aucun atelier, et le dépôt en aura d'autres : un outil qu'on lance à la main garde un
dispositif, et le lui refuser reviendrait à dire qu'il n'est rien.

**Et elle enseigne le mauvais critère.** Un outil dont on ajouterait demain une étape de CI en mode
producteur changerait de dispositif sans avoir changé de nature. Le contrat déclare ce qu'un point
d'entrée **est**, jamais ce qu'un atelier en fait.

## Conséquences

**Le champ garde une réponse pour tout point d'entrée**, y compris ceux qu'aucun atelier ne lance.
Sur les 67 porteurs du dépôt, deux sont dans ce cas et déclarent quand même.

**Un mot dont la raison ne se lit pas dans une aide s'écrit à côté du contrat**, en commentaire. Les
huit `invariant` de `scripts/methode` n'en portent pas : ils refusent, et cela se lit dans ce qu'ils
rendent. Les cinq autres, trois `rapport`, une `loupe` et un `generateur`, disent pourquoi aucun
autre mot ne convenait.

**Ce que cette règle ne tranche pas** : ce qu'un outil devrait déclarer s'il jugeait dans un mode et
produisait dans un autre **sans qu'aucun des deux ne soit le défaut**. Le corpus n'en porte aucun, et
inventer la réponse avant le cas produirait une règle que rien n'aurait éprouvée.

[ADR 5125]: https://companion-dev.echonuit.fr/decisions/5125-un-point-d-entree-qui-ne-juge-pas-dit-ce-qu-il-est/
