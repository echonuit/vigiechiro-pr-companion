---
type: adr
title: "Une version qu'aucun check de demande n'exerce se déclare, elle ne se devine pas"
status: stable
article: A3
chantier: "#5087, chantier #5584"
decided_at: 2026-09-30
verification: certaine
enforced_by:
  - "scripts/adr/5087-versions-hors-des-checks.py"
verified:
  - by: machine:ci
    at: 2026-09-30
relations:
  complete: ["4571-une-fusion-sans-verdict-se-refuse"]
generated:
  by: "process:assistance-par-agents"
---

# Une version qu'aucun check de demande n'exerce se déclare, elle ne se devine pas

## Le contexte

La demande #5078 bumpait `pitest-maven` de 1.25.8 à 1.30.0. **Ses dix-neuf checks sont passés au
vert, et aucun n'avait exécuté pitest.** Dependabot l'a bumpé trois fois - #61, #2269, #5078 - et
aucune des trois n'a pu être jugée.

La règle de fusion du dépôt demande de se poser la question « quel check aurait rougi si mon
changement était faux ». Rien ne rappelle qu'il faut se la poser. Sur #5078 elle ne l'a été qu'à cause
d'un rouge de runner sans rapport, qui avait fait ouvrir le journal.

## La mesure qui décide

Au 2026-09-30, la carte **dérivée** du `pom.xml` et des huit flux :

| profil | activé par | versions portées |
|---|---|---|
| `ecj` | `-P`, dans `maven.yml` qui déclenche sur `pull_request` | 2, donc exercées |
| `mutation` | `-P`, dans `mutation-*.yml`, `schedule` et `dispatch` seulement | **2, exposées** |
| les sept autres | `-P` ou `<os>` | aucune |

La population exposée tient en **deux propriétés** : `pitest.version` et `pitest.junit5.version`. Et
ce sont des propriétés, non des littéraux - la valeur vit dans `<properties>`, à une indirection de la
déclaration du plugin.

## La décision

**Une version exposée se déclare dans un manifeste, avec ce qui l'atteste, et un garde confronte le
`pom.xml` à ce manifeste.** Une divergence refuse, en nommant le profil et ce qui l'exercerait.

Le manifeste est l'attestation, sur le patron de `relus.txt` : **la valeur est la marque**. Si le pom
bouge et que la ligne ne suit pas, le garde refuse. Personne n'a à se souvenir de la question, puisque
changer la chose invalide l'attestation.

**Et c'est un invariant, pas un cliquet.** Une divergence n'est jamais une dette qui descend : elle est
fausse ou elle n'est pas. Déclarer un cliquet à zéro dirait qu'il pourrait monter.

## Ce que cela ne fait pas

Le garde ne teste pas pitest et ne dit pas si un bump fonctionne. **Il refuse le silence.** Le filet
qui répond est nocturne, dans `mutation-model.yml` et `mutation-ihm.yml`, dont l'en-tête refuse
explicitement de juger une demande : PIT est lent, jusqu'à 2 h 35 sur un paquet, et un survivant est
une question posée à un humain.

## Les alternatives écartées

- **Une fumée dans un job de demande**, jouant `mutation` sur une cible minuscule. Elle répondrait au
  lieu de poser la question, mais coûte des minutes à **chaque** demande pour un risque réalisé zéro
  fois sur trois bumps - et une classe pure ne suffit pas : il faut une classe de vue pour voir le
  minion headless cassé, ce qui est la part chère.
- **Ne rien faire, et l'écrire.** Le filet nocturne existe et ses seize dernières exécutions sont
  vertes. Ce qu'on assumerait est qu'il tombe **après** la fusion, sur `main`, sur un job programmé que
  personne n'est tenu de regarder.
- **Un garde qui lit le DIFF de la demande.** Deux mesures l'ont refusé. `corps-pr.yml` prend la ref de
  fusion en profondeur 1, donc il n'y a pas de base à comparer. Et un garde qui refuse « une demande
  qui touche une version » refuserait **tous** les bumps à jamais, puisque rien dans un diff ne dit
  qu'on a vérifié : il lui faut de toute façon une attestation.

## Comment on le sait

Le contraste, dans les deux sens, et c'est ce qui distingue ce garde d'un garde absent. Il **refuse**
sur un bump de chacune des deux propriétés exposées et sur une ligne de manifeste retirée ; il **se
tait** sur `ecj`, dont `maven.yml` exerce les deux versions en demande de fusion. Mesuré : `ecj` n'entre
pas dans la population exposée.

Deux pièges de dérivation sont tenus par des cas plutôt que par de la prudence. Un relevé libre des
`-P` rend **deux flux sur huit** à tort - `-Process` et `-PassThru` sont des drapeaux PowerShell,
`multi-PR` un mot composé - d'où la résolution contre les noms que le pom déclare. Et **trois profils
sur neuf** s'activent par `<os>` et non par `-P` : ils sont nommés dans la sortie plutôt que tus, sans
quoi le garde laisserait croire sa carte complète.
