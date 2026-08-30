---
type: adr
title: "Une passe se range où son effet est juste, et le corpus immuable se renumérote par règle"
status: stable
article: A3
chantier: "#4846 (EPIC #4511)"
decided_at: 2026-08-31
verification: probable
enforced_by:
  - "scripts/methode/passes-citees-existent.py"
verified:
  - by: machine:ci
    at: 2026-08-31
generated:
  by: "process:assistance-par-agents"
  at: 2026-08-31
---

# Une passe se range où son effet est juste, et le corpus immuable se renumérote par règle

## L'incident

`AGENTS.md` portait une règle, et elle était motivée :

> Les passes **1 à 9 gardent leur numéro** : 35 des 42 citations de passes du dépôt vivent dans des
> ADR **immuables**. Les deux passes ajoutées sont donc aux extrémités.

#4518 l'a rompue. La passe d'archivage OpenSpec s'insère en **10**, et les deux dernières glissent
d'un cran. La phrase a été réécrite dans le même lot, mais la **raison** du renversement ne vivait
que dans le corps d'une demande de fusion.

## Pourquoi l'archivage ne pouvait aller à aucune extrémité

Il doit **précéder** l'écriture des ADR : celle-ci balaie tout ce qui la précède, et doit lire une
spécification déjà fusionnée plutôt que la devancer.

Il ne peut pas non plus aller en queue, ce qui le mettrait **après le bilan**, c'est-à-dire après la
clôture qu'il conditionne.

Aucune extrémité ne convient, et ce n'est pas une propriété de cette passe-là : c'est ce qui arrive
dès qu'une passe agit sur ce que d'autres consomment.

## La décision

**Une passe se range où son effet est juste.** La règle des extrémités optimisait le coût de la
renumérotation ; elle revient à ranger une étape là où elle dérange le moins plutôt que là où elle
agit. Une passe mal placée est une passe qui ne produit pas son effet, et ce qu'on économise en
citations se perd en cycle.

Le dépôt l'avait déjà mesuré à l'envers : à la clôture de #4573, la passe d'archivage rejouée **avant**
les passes qu'elle balaie a rendu deux ADR sur quatre, et cela ressemblait à un succès.

## Comment se renumérote un corpus dont une part est immuable

Le renversement a coûté **29 citations dans 18 fichiers**, dont une quinzaine d'ADR. Le tri s'est fait
sur une règle qui vaut d'être écrite, faute de quoi le prochain la réinventera :

| Ce que la citation fait | Ce qu'elle devient |
|---|---|
| désigner une passe **par son numéro** | se renumérote, sinon le renvoi tombe sur la mauvaise passe |
| raconter un **fait daté** | garde son compte |

La seconde moitié est la moins évidente et la plus importante. L'EPIC #4671 a bien été clos par
**douze** passes le 28 août 2026 ; l'écrire « quatorze » aujourd'hui falsifierait le dossier au nom de
la cohérence. Une ADR immuable n'est pas un texte qu'on ne touche pas, c'est un texte dont on ne
récrit pas l'histoire.

## Ce qui la vérifie, et ce qui ne se vérifie pas

`scripts/methode/passes-citees-existent.py` refuse une citation qui désigne une passe inexistante :
il dérive les bornes des titres du cycle, lit **les deux extrémités** d'un intervalle, et s'exclut
lui-même de son corpus.

Il ne voit pas la première moitié de cette décision. Qu'une passe soit **bien placée** ne se mesure
pas ; cela se discute à l'insertion, et c'est la raison du niveau `probable`.

## Ce qu'un lecteur futur pourrait défaire

Rétablir la règle des extrémités, au motif qu'une renumérotation coûte 29 citations. Ce serait une
décision, et elle demanderait de dire où l'archivage se range alors.

Ou renuméroter les faits datés par souci d'uniformité, ce qui est le geste le plus naturel et le seul
qui abîme quelque chose d'irréparable.
