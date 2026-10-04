---
type: adr
title: "Un refus ne voyage pas dans le canal des constats, sinon il hérite de leur titre"
status: stable
article: A3
chantier: "#5774, lot du sous-chantier #5762 (huit corps de demande sur deux sessions)"
decided_at: 2026-10-04
verification: certaine
enforced_by:
  - "scripts/methode/verifie-sous-commandes-openspec.py"
  - "scripts/methode/verifie-specs-valides.py"
verified:
  - by: machine:ci
    at: 2026-10-04
generated:
  by: "process:assistance-par-agents"
  at: 2026-10-04
---

# Un refus ne voyage pas dans le canal des constats, sinon il hérite de leur titre

## Contexte

`verifie-sous-commandes-openspec.py` rendait une liste de constats, et son appelant l'imprimait sous
un titre : « Invocations d'OpenSpec qui n'existent pas ». Ce qui l'empêchait de **juger** passait par
la même liste, donc portait ce titre-là. Un garde qui ne pouvait pas conclure accusait le diff.

Son voisin, `verifie-specs-valides.py`, avait la forme inverse du même défaut : il gardait la marque
`REFUS :` mais perdait son champ `POUR REPARER :`, et `lit_le_refus` rend `None` quand l'un des deux
manque. Son refus n'était donc **pas lu** comme déclaré, et la porte retombait sur son repli.

**Ce que cela a coûté, mesuré** : huit corps de demande sur deux sessions ont qualifié ces refus
d'« environnementaux, étrangers à ce diff », chacun après avoir vérifié qu'ils rougissaient aussi sur
`main`. La vérification était juste, et c'est elle qui a dispensé de chercher.

## Décision

**Ce qui empêche de juger et ce qui a été jugé voyagent par deux canaux distincts.** Une fonction rend
les constats ; une autre rend `(cause, geste)` ou `None` ; l'appelant demande la seconde **avant** la
première.

La conséquence qui compte est qu'une liste vide reprend un sens : elle dit « j'ai jugé et c'est
vert », jamais « je n'ai pas pu juger ». Un canal unique les confond, et c'est irréparable en aval :
l'appelant ne peut pas deviner laquelle des deux choses il tient.

**Et le code de sortie suit le canal.** Un refus sort en `2`, un constat rouge en `1`. La convention
est mesurée depuis #5485 - vingt-huit sites en `2` dans seize gardes - et `_commun.refuse` en fait son
défaut.

## Pourquoi un message correct ne suffisait pas

C'est la tentation qu'il faut écarter, parce qu'elle est moins chère : rallonger la phrase du refus.
Elle échoue sur la forme, pas sur le fond. Le titre des constats est imprimé par l'appelant, **avant**
la liste, donc aucune formulation de l'élément ne peut le contredire. Tant que le refus est un
élément de cette liste, il accuse le diff par construction.

## Ce que cette décision ne dit pas

**Elle n'oblige pas à employer `refuse`.** Un garde qui rend `(code, message)` à son appelant garde ce
flot et emploie `message_de_refus`, qui porte les mêmes marques sans sortir. La docstring de cette
fonction le dit déjà : imposer `refuse` changerait un flot de contrôle pour une question de forme.

**Et elle ne promet pas que la porte en tienne compte.** `verdict_du_lancement` lit le refus déclaré,
puis rend « rouge » sur tout code non nul. Un garde qui n'a pas pu juger est donc encore compté rouge,
et c'est l'issue #5780. Cette décision rend ce lot traitable en donnant aux gardes une forme déclarée
dans toutes leurs situations ; elle ne le remplace pas.

## Conséquences

**Le harnais d'un garde refuse aussi.** Sans `node`, l'auto-test de
`verifie-sous-commandes-openspec.py` rendait « Le garde ne tient pas : le témoin rougit » : il
accusait son garde de ce qui tenait au poste, donc il produisait chez lui le faux signal que la
décision supprime ailleurs. Il refuse désormais en `2`, en nommant le prérequis.

**Un auto-test qui n'assertait qu'un code ne voyait rien de tout cela.** Les cas du garde comparaient
son code de sortie, et il sortait bien non nul : c'est son **texte** qui accusait le diff. Les cas
lisent maintenant la sortie, et le contrôle qui les a éprouvés est la **version d'avant** plutôt
qu'une mutation choisie - cinq mutations qui la restaurent tuent de un à six cas chacune.

**La distinction peut se perdre au seuil de la sortie.** `verifie-specs-valides.py` séparait le refus
de l'écart en interne, sa docstring le disant depuis toujours, et son `sys.exit(0 if code == 0 else 1)`
écrasait les deux. Un compte juste jeté sur le pas de la porte est la forme la plus discrète de ce
défaut, et le dépôt l'a trouvée **trois fois** : ici, et dans
`prepare-l-environnement.py` (ADR 5775).
