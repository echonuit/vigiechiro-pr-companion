---
type: adr
title: "Un verbe de relation se normalise, il ne s'énumère pas"
status: stable
article: A3
chantier: "#5579, chantier #5584"
decided_at: 2026-09-30
verification: certaine
enforced_by:
  - "scripts/adr/verifie_encart_de_revision.py"
  - "scripts/adr/verifie_okf.py"
verified:
  - by: machine:ci
    at: 2026-09-30
relations:
  prolonge: ["4967-une-seule-definition-d-un-epic-pour-les-quatre-dispositifs"]
generated:
  by: "process:assistance-par-agents"
---

# Un verbe de relation se normalise, il ne s'énumère pas

## Le contexte

Deux dispositifs lisaient le bloc `relations:` d'une ADR, et n'en lisaient pas la même chose :
`verifie_okf.lit_entete`, qui lit tout par `partition(":")`, et
`verifie_encart_de_revision.relations()`, dont le motif `[a-zé_]+` ratait deux formes.

Sur les **101** ADR qui portent ce bloc, **neuf** étaient lues différemment : sept écrivent `complète`
avec un accent grave, deux écrivaient `fait évoluer` avec une espace.

## Ce que l'issue annonçait, et ce que la mesure a démenti

Le corps de #5579 plaçait le défaut dans le **motif**. Il était dans la **comparaison** :

```
verbe écrit           relations() le voit   fautes() refuse
amendee_par                          True              True
amendée_par                          True             False  <- ECHAPPE
```

Le motif **voyait** `amendée_par`. C'est `SUBIES`, un tuple littéral, qui ne le reconnaissait pas. Le
garde d'encart sortait donc **vert** sur une ADR amendée qui n'annonçait rien sous son titre, ce qui est
exactement ce qu'il existe pour empêcher. Corriger le motif seul n'aurait rien corrigé.

Trois autres comparaisons littérales vivaient dans `verifie_okf.py` et avaient la même fragilité :
`DEPASSEMENT`, et une paire `("remplacee_par", "renversee_par")` écrite en dur.

## La décision

**La règle vit dans `scripts/_commun/relations.py`, et elle NORMALISE au lieu d'énumérer.** Accents et
casse sont repliés avant toute comparaison ; le verbe reste rendu tel que l'auteur l'a écrit, pour
qu'un refus puisse le citer.

**Le garde s'adapte à la prose, pas la prose au garde.** `complète` avec un accent est du français, pas
une faute de frappe, et normaliser le corpus aurait demandé aux auteurs d'écrire sans accents pour
plaire à un tuple littéral. Formulation due à `vigiechiro-pr-companion-56`, meilleure que la mienne,
qui n'opposait que deux coûts.

**Une exception, et elle est nommée : un verbe est une CLÉ, pas une phrase.** Un verbe portant une
espace est **refusé**. Les deux `fait évoluer` du corpus sont devenus `amende`, le verbe qui disait ce
qu'ils faisaient : #0047 change l'identité de distribution que #0045 déclarait constante, et #2213
porte une section entière sur ce qu'elle change dans #3501.

**Voir n'est pas admettre.** Un verbe refusé est d'abord **lu**, puis signalé. Ne pas le lire était le
défaut d'origine : une clé invisible ne se signale pas, elle disparaît.

## Ce que la mesure a corrigé au passage

`verifie_okf.py` déclarait **deux fois** `RENVOI`, avec **deux motifs différents**, la seconde
déclaration masquant la première. Le premier exigeait un chiffre en tête, donc qui lisait le haut du
fichier croyait appliquer une règle plus stricte que celle en vigueur. Mesure avant retrait : les deux
trouvent les **mêmes 605 renvois**, aucun fichier ne diffère. Le retrait ne change donc aucun
comportement, et c'était une fausse lecture qu'il fallait retirer, pas un bug.

## Comment on saurait qu'elle est rompue

`verifie_grammaire()` porte **vingt-trois cas**, joués par l'auto-test du garde d'encart. Le garde OKF
en porte trois de plus, dont le refus d'un verbe à espace et une ADR `deprecated` dont le successeur
est accentué.

**Six mutations à la main, six tuées, zéro survivante** : la comparaison redevenue littérale, la
normalisation annulée, le motif retombé à `[a-z_]+`, le refus des espaces retiré, et les deux
vocabulaires `DEPASSEMENT` et `SUCCESSEURS` redevenus littéraux.

La sixième a d'abord **survécu** : `depasse` n'avait aucun témoin, et sa mutation ne faisait rougir
personne. Le remède a été de lui en donner un, non de retirer le contrôle.

## Ce qu'un lecteur futur pourrait défaire

**`SUBIES` et `SUCCESSEURS` sont deux listes qui se recoupent** sur `remplacee_par` et divergent
ailleurs : `renversee_par` n'est que dans l'une, `amendee_par` que dans l'autre. Les unifier par souci
de cohérence changerait ce que deux gardes exigent. La divergence est consignée ici, pas tranchée :
dire laquelle a raison est une question de vocabulaire que personne n'a posée.

**Le refus des espaces** paraît une rigidité gratuite. Il ne l'est pas : les deux relations écrites en
deux mots étaient **invisibles** au garde d'encart, donc leurs obligations ne s'appliquaient pas.
