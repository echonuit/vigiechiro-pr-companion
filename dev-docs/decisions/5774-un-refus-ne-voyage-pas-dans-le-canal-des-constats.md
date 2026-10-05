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
relations:
  completee_par: ["5743-un-invariant-se-borne-par-une-liste-nommee"]
verified:
  - by: machine:ci
    at: 2026-10-04
generated:
  by: "process:assistance-par-agents"
  at: 2026-10-04
---

# Un refus ne voyage pas dans le canal des constats, sinon il hérite de leur titre

!!! warning "Ce qui fait foi aujourd'hui"
    **2026-10-05** : cette décision est **complétée** par
    [5743](5743-un-invariant-se-borne-par-une-liste-nommee.md). Ses deux attentes sont tenues, et une
    mesure la précise sur un point qui peut la faire appliquer de travers.

    **Le troisième verdict existe.** « Elle ne promet pas que la porte en tienne compte [...] et c'est
    l'issue #5780 » : ce lot est livré, et `scripts/batterie.py` sépare désormais « rouge » de
    « muet ».

    **Mais la porte classe par les MARQUES, pas par le code de sortie.** Mesuré sur
    `verdict_du_lancement` :

    ```
    code=1 AVEC « REFUS : » et « POUR REPARER : »  ->  muet
    code=2 AVEC ces marques                        ->  muet
    code=1 SANS ces marques                        ->  rouge
    code=2 SANS ces marques                        ->  rouge
    ```

    « Le code de sortie suit le canal » dit comment ÉCRIRE un garde ; il ne décrit pas comment la
    porte LIT. Un garde qui a jugé et qui emploie `refuse(..., code=1)` pour profiter de la forme
    déclarée est donc annoncé « n'a PAS pu juger », ce qui se lit « ça ne vient pas de mon diff ».
    C'est exactement le faux signal que cette décision combat, retourné.

    Vécu le 2026-10-05 sur #5743, par un premier jet poussé en demande : **28 checks verts**, parce
    qu'un garde passe tant que son invariant tient, donc le mauvais canal n'est emprunté par aucune
    exécution verte. Trouvé en relisant cette décision à la passe 0 d'une clôture, et non par un
    dispositif.

    **Donc un constat rouge sort en 1 et n'emploie ni `refuse` ni `message_de_refus`** : deux `print`
    sur `stderr` et un `SystemExit(1)`. Le cas qui tient cela regarde la SORTIE et non le code, et
    `verifie_temoins_non_decoratifs.py` en porte un.

    Le reste fait foi.

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
