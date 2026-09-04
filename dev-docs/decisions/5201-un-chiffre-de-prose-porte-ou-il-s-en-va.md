---
type: adr
title: "Un chiffre de prose porte quelque chose, ou il s'en va"
status: stable
article: A5
chantier: "#5201 (chantier #5202)"
decided_at: 2026-09-04
verification: humaine
verification_note: "distinguer un inventaire d'un exemple, d'un ordre de grandeur ou d'une mesure d'incident demande de lire la phrase et de juger ce que le chiffre y fait. Sur 52 candidats relevés, 39 ne sont pas des inventaires, et aucun motif ne les écarte : le tri s'est fait en les ouvrant"
verified:
  - by: human:nedseb
    at: 2026-09-04
generated:
  by: "process:assistance-par-agents"
  at: 2026-09-04
---

# Un chiffre de prose porte quelque chose, ou il s'en va

## Contexte

Quatre affirmations chiffrées du dépôt sont devenues fausses en dix jours, et **aucune n'a été
trouvée par un garde** : « 41 points d'entrée » pour 68, « 42 scripts shell » pour 50, « les trois
porteurs » pour 68, « les 194 ADR » pour 278. Toutes les quatre par une passe de clôture, c'est-à-dire
par quelqu'un qui relisait et qui a eu l'idée de recompter.

**Un nombre plus petit que la réalité ne cloche pas dans une phrase.** C'est ce qui rend cette dérive
silencieuse, et c'est pourquoi elle se répète.

Le réflexe est d'ancrer : le dépôt sait le faire depuis l'ADR 5169, et porte 51 balises sur 28 clés.
Ce chantier a mesuré ce que l'ancrage devrait couvrir, et la mesure dit autre chose.

## Ce que la mesure a rendu

52 affirmations chiffrées candidates, hors compétences, spikes, ADR et CHANGELOG - dont les chiffres
**datent un fait** et dont la fixité est ce qu'on veut. Lues une par une :

| | n |
|---|---:|
| pas une affirmation sur une population : exemple d'IHM, ordre de grandeur, réglage | **23** |
| mesure d'**incident**, datée, qui justifie une règle | **16** |
| population **vivante**, recomptable, que rien ne tient | **13** |

Et sur ces treize, **un seul est vérifiable sans deviner** : « 50 scripts », dont la définition vit
dans le cliquet `5188-corpus-shell.py`. Les douze autres ne disent pas ce qu'ils comptent.
« Les 283 classes de vue » : quelles classes, sous quel chemin, tests compris ? Recompter demande de
supposer une définition, et croire cette supposition serait l'erreur même que ce chantier poursuit.

## Décision

**Trois questions, dans cet ordre, quand un chiffre s'écrit.**

1. **Porte-t-il quelque chose ?** Si l'argument tient sans lui, il s'en va. C'est le geste le moins
   cher, et le seul qui ne laisse aucune dette. Souvent la **magnitude** porte et l'**exactitude**
   ne porte pas : « plus de mille lignes » dit ce qu'il faut et ne dérive jamais.
2. **Sa définition est-elle écrite quelque part ?** Un chiffre ne s'ancre que si l'on sait ce qu'il
   compte. Une balise posée sans définition ancre un nombre dont personne ne sait ce qu'il mesure,
   et donne une garantie que rien ne tient. C'est déjà l'exigence de l'ADR 5169, formulée là pour
   les documents qui font autorité sur leur population.
3. **Alors seulement, s'ancre-t-il ?** Par une balise `inv:` que
   `DocumentationAJourTest#chaque_chiffre_balise_egale_l_inventaire_reel` recompte, ou par un
   cliquet dont l'`inv_key` en tient lieu.

**Un chiffre qui date un fait ne s'ancre pas.** Une mesure d'incident, un ordre de grandeur, un
exemple d'écran : les recompter détruirait ce qu'ils disent. Les compétences, les spikes, les ADR et
le CHANGELOG en sont faits, et sortent de cette règle par nature.

## Ce que cette décision ne livre pas, et pourquoi

**Aucun dispositif.** Treize candidats, dont un déjà tenu et douze sans définition écrite, ne valent
pas un garde. Un garde sur les chiffres de prose devrait distinguer un inventaire d'un exemple, ce
qu'aucun motif ne fait : le tri de ce chantier a demandé de les ouvrir, et deux relevés successifs
ont sur-signalé des deux tiers avant qu'on les lise.

Écrire un garde ici reviendrait à mesurer une ressemblance là où il faut une lecture, ce que
l'[ADR 5102] interdit.

## Ce qu'elle ne tranche pas

**Les douze sans définition.** Ils ne sont ni corrigés ni ancrés : les corriger demanderait d'inventer
leur définition, ce qui figerait une supposition. Ils se traiteront un par un, quand quelqu'un aura
besoin du chiffre et écrira ce qu'il compte.

[ADR 5102]: https://companion-dev.echonuit.fr/decisions/5102-une-capacite-se-demande-jamais-se-reconnait/
