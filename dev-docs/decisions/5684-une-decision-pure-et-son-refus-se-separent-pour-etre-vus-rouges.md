---
type: adr
title: "Une décision pure et son refus se séparent pour pouvoir être vus rouges"
status: stable
article: A2
chantier: "#5684 (sas #4562)"
decided_at: 2026-09-30
verification: certaine
enforced_by:
  - ".github/scripts/verifie_cloture_consignee.py"
  - "scripts/adr/loupe-4712-lots-multi-pr.py"
verified:
  - by: machine:ci
    at: 2026-09-30
relations:
  amende: ["4967-une-seule-definition-d-un-epic-pour-les-quatre-dispositifs"]
generated:
  by: "process:assistance-par-agents"
---

# Une décision pure et son refus se séparent pour pouvoir être vus rouges

## Le contexte

L'[ADR 4967] a séparé deux choses dans la collecte des EPIC clos : `retenus_parmi`, qui **décide** si la
collecte est tronquée et rend un booléen, et le **refus** qui imprime sur `stderr` et sort en 2.

Elle a justifié cette séparation par un défaut d'outillage : `verifie_gages_joues.py` ne savait pas
nommer un module de `.github/scripts/`, donc un gage déclaré de ce côté était compté **inerte même
joué**, et la porte refusait.

**#5673 a corrigé ce défaut**, quarante minutes avant que #5672 ne fusionne. La justification de
l'ADR 4967 est donc devenue caduque le jour même de son écriture, et un lecteur en déduirait que la
séparation n'a plus de raison d'être.

## La décision

**La séparation tient, et sa raison est ailleurs : une fonction pure se joue hors ligne, un refus non.**

| Moitié | Ce qu'elle est | Comment on la voit rouge |
|---|---|---|
| `retenus_parmi` | pure : elle décide et rend `(retenus, tronquée)` | des cas hors ligne, sans leurre ni réseau |
| le refus | il imprime et sort en 2 | des cas qui le **traversent**, au plafond et sous lui |

Aucune fonction pure ne peut porter un refus : sortir en 2 n'est pas une valeur de retour. Et aucun
refus ne se joue hors ligne aussi simplement qu'un booléen, puisqu'il faut capturer `stderr` et
intercepter `SystemExit`. Les séparer est ce qui permet de voir **les deux** rouges, ce que l'article A2
exige de chaque garde.

## Pourquoi cette ADR, et non une correction de l'ADR 4967

Le dépôt amende par une **nouvelle** ADR : une ADR acceptée ne se réécrit pas, et le coût pour son
lecteur est payé par l'encart « Ce qui fait foi aujourd'hui ». C'est ce que pose
`verifie_encart_de_revision.py`, qui le vérifie dans les deux sens.

La phrase de l'ADR 4967 **n'est pas fausse** : elle était vraie le jour où elle a été écrite, et elle
dit correctement ce que ce lot n'avait pas fait. Ce qui est caduc est sa conclusion pratique. Corriger
en place aurait effacé la trace du contournement, et ce contournement est une information : il dit
qu'un défaut d'outillage a orienté une décision de conception, ce qui est rare et vaut d'être su.

Le geste a été arbitré par le porteur, après qu'une session pair a rappelé la règle et nommé un
précédent du même jour, l'[ADR 4111], dont la mesure de durée est **datée** donc pas fausse : elle
annonce « cinq minutes pour 58 clips, mesurées le 2026-08-20 », et un tournage d'aujourd'hui en met
davantage. Le porteur a décidé de laisser l'ADR et d'ouvrir une issue.

## Ce que cette ADR n'étend pas

**Elle ne pose pas que toute décision se sépare de son refus.** Un garde dont le refus n'a pas de cas
propre n'a rien à séparer ; la séparation se paie en indirection, et ne vaut que là où les deux moitiés
ont chacune leurs cas. `refus_au_plafond` existe parce que le leurre des deux cliquets de clôture
court-circuite la collecte : sans lui, la requête élargie serait du code qu'aucune épreuve ne traverse.

**Elle ne dit rien des champs vifs.** Un `ratchet:` ou un `floor:` se réécrit dans une ADR acceptée, et
le dépôt livre `resserre_cliquets.py` et `releve-les-planchers.py` pour cela. La frontière : un champ
vif est une donnée que le garde **lit** à chaque passage, la prose est une décision qu'un lecteur
**relit**. Le premier doit suivre la population sous peine de mentir ; le vieillissement de la seconde
est une information. Formulation due à `vigiechiro-pr-companion-56`.

## Comment on saurait qu'elle est rompue

Les cas de `retenus_parmi` vivent dans `epics.verifie_grammaire()`, joués des deux côtés de la
barrière, et trois d'entre eux tiennent le plafond : sous lui, à lui, au-delà. Le refus est traversé par
deux cas du garde de clôture, qui exigent le verdict **et son motif**.

La mutation le prouve : retirer la comparaison du plafond fait rougir les cas hors ligne, et retirer le
refus du chemin réel fait rougir le garde de clôture avec « attendu refus, obtenu ok ». Fondre les deux
moitiés laisserait l'une des deux sans cas.

## Ce qu'un lecteur futur pourrait défaire

**Fondre `retenus_parmi` dans `candidats_clos`**, par souci de concision, le défaut qui l'avait motivée
étant réparé. Il perdrait la moitié des cas, et c'est ce que cette ADR existe pour empêcher.

[ADR 4967]: 4967-une-seule-definition-d-un-epic-pour-les-quatre-dispositifs.md
[ADR 4111]: 4111-un-clip-montre-la-version-qu-on-valide.md
