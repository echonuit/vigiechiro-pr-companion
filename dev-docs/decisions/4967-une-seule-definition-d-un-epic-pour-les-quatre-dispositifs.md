---
type: adr
title: "Une seule définition d'un EPIC, parce que la divergence était le défaut"
status: stable
article: A3
chantier: "#4967, chantier #5584"
decided_at: 2026-09-30
verification: certaine
enforced_by:
  - "scripts/adr/loupe-4712-lots-multi-pr.py"
  - ".github/scripts/verifie_cloture_consignee.py"
verified:
  - by: machine:ci
    at: 2026-09-30
relations:
  complete: ["4659-une-cloture-sans-trace-ne-se-distingue-pas-d-une-cloture-absente"]
generated:
  by: "process:assistance-par-agents"
---

# Une seule définition d'un EPIC, parce que la divergence était le défaut

## Le contexte

Le dépôt désigne un EPIC de deux façons : le **label** `epic`, et le **préfixe de titre** `[epic]` ou
`[chantier]`. Aucune des deux populations ne contient l'autre, et quatre dispositifs lisaient cette
notion. Un seul la lisait juste.

| Dispositif | Sa définition | Sa surface |
|---|---|---|
| `loupe-4992-lots-sans-critere.py` | l'union | prédicat local |
| `loupe-4712-lots-multi-pr.py` | le label seul | prédicat local |
| `verifie_cloture_consignee.py` | le label seul | **la requête** |
| `verifie_specification_consignee.py` | le label seul | **la requête** |

La divergence était consignée depuis le 2026-08-31, en commentaire de #4948, sous la mention
« consigné, pas traité ». Aucune issue ouverte ne la portait un mois plus tard.

## Ce qu'elle coûtait, mesuré

Les deux derniers filtraient `--label epic` **à la requête** : aucun prédicat local ne pouvait les
élargir. Sur les 1 737 issues closes, 96 portaient le label et 154 étaient des EPIC. Le cliquet
d'[ADR 4659], qui juge « les EPIC clos sans trace de clôture », rendait donc son verdict sans avoir
jamais regardé **58 d'entre eux**, dont **23 sans aucune trace**.

C'est l'article A3 exactement : un dispositif qui conclut sur ce qu'il n'a pas lu, sans le dire.

## La décision

**La définition vit dans `scripts/_commun/epics.py`, et les quatre l'importent.** Elle prend l'union
du label et des deux préfixes.

C'est le critère que le dépôt s'était donné en #5565 : *ce qui gagne à être commun est ce dont la
divergence est le défaut ; ce qui doit rester local est ce dont la panne est silencieuse*. Ici la
divergence **était** le défaut, et elle valait 68 issues.

**L'union vaut dans les deux sens.** Deux EPIC ouverts portaient le label sans le préfixe, et dix le
préfixe sans le label : une définition fondée sur le seul titre serait fausse elle aussi. C'est le
sens qu'on oublie en corrigeant, et le chiffre a d'abord été mesuré faux, par une soustraction
`label − (label ∪ titre)` vide **par construction** dont le zéro se lisait comme un constat.

## Un dépassement déclaré

`.github/scripts/_forge.py` pose de ne pas verser « de logique de forge » dans `scripts/_commun/`, sur
la mesure de #5216. `epics.py` la frôle et s'en distingue : il ne lance rien, ne lit aucun réseau, ne
connaît pas `gh`, et répond sur un dictionnaire déjà lu. Ce qui restait
interdit reste chez `_forge.py`. La direction de l'import existait déjà : deux gardes de CI
importent `scripts/_commun`.

## Le remède révèle, il ne cause pas

Élargir la population fait monter deux cliquets : 42 vers **65** pour [ADR 4659], 1 vers **2** pour
[ADR 4922]. Aucune clôture n'a régressé ; la mesure commence à dire vrai.

Le contrôle qui l'établit est une **fermeture arithmétique** : compter les traces manquantes parmi les
96 que le garde lisait rend exactement 42, le chiffre que son en-tête portait. La divergence était
donc dans la population, et non dans la détection.

## Comment on saurait qu'elle est rompue

`verifie_grammaire()` porte **dix-sept cas**, joués des **deux côtés de la barrière** : par
l'auto-test de `loupe-4712-lots-multi-pr.py` et par celui de `verifie_cloture_consignee.py`, un de
chaque paquet.

Ce qu'il fallait prouver est que la **même** définition les traverse tous, et la ressemblance n'y
suffit pas. Muter `est_epic` en son défaut d'origine fait rougir **trois** harnais indépendants, ceux
des deux loupes et du garde de clôture, tous verts après restauration.

La **décision** de tronquer vit dans `retenus_parmi`, pure, donc jouable hors ligne. Le **refus**
lui-même ne peut pas y vivre : il est traversé par deux cas du garde de clôture, au plafond et sous
lui. Cette séparation n'est pas un choix d'élégance, c'est que `verifie_gages_joues.py` ne résout pas
un module hors de `scripts/` et comptait le gage inerte alors qu'il était joué.

**Dix mutations à la main, dix tuées, zéro survivante** : le retour au label seul, la perte de
`[chantier]`, l'union devenue intersection, le plafond comparé en `==`, la collecte qui ne filtre
plus, le refus retiré du chemin réel, chacune des deux sources d'enfants prise seule, la sous-issue
qui cesse d'être un lot, et le dédoublonnage de la forme mixte.

## Ce qu'un lecteur futur pourrait défaire

**Le plafond de collecte.** Il paraît une précaution de pagination et n'en est pas une : un corpus
tronqué rend moins d'EPIC, donc moins de manques, donc un cliquet qui **passe**. C'est la seule façon
dont ces gardes puissent se tromper en silence et dans le sens rassurant.

**L'union, réduite au titre** par souci de cohérence le jour où tous les EPIC porteront le préfixe.
Deux le portent aujourd'hui sans le label ; l'inverse existe aussi.

[ADR 4659]: 4659-une-cloture-sans-trace-ne-se-distingue-pas-d-une-cloture-absente.md
[ADR 4922]: 4922-l-adoption-d-une-specification-se-tient-par-un-cliquet.md
