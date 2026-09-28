---
type: adr
title: "Un index d'appels répond, il ne refuse pas"
status: stable
article: A3
chantier: "#5532, chantier #5464"
decided_at: 2026-09-28
verification: certaine
enforced_by:
  - "scripts/qualite/appelants.py"
verified:
  - by: machine:ci
    at: 2026-09-28
relations:
  prolonge: ["5402"]
generated:
  by: "process:assistance-par-agents"
  at: 2026-09-28
---

# Un index d'appels répond, il ne refuse pas

## Contexte

Le lot 3 de #5464 promettait un garde : rendre les six copies d'un helper `executer` que #4656 nomme.
Deux faits l'ont rendu intenable. #4656 est un chantier **terminé**, et #4657 a retiré ces copies. Et
le repli évident ne tient pas non plus.

`sans_appelant_externe()` rend **10 376 méthodes sur 14 816**, soit **70 % du corpus** :

| ce que contient ce 70 % | combien |
|---|---:|
| cas de test en `snake_case`, appelés par JUnit par réflexion | 5 014 |
| `main`, `initialize`, `start`, `stop`, `init` | 226 |
| le reste, dominé par des aides appelées **dans leur propre fichier** et des `configure` / `fournir*` de Guice | 5 132 |

Aucune n'est morte. Le témoin le plus net est `nettoyer` : **77 déclarations, zéro appelant externe,
toutes vivantes.**

## Décision

**Cet index sert à RÉPONDRE, pas à refuser.** « Aucun appelant hors de son fichier » est l'état
**normal** d'une aide privée, pas un défaut, et un garde bâti là-dessus serait une machine à faux
positifs. Son consommateur `scripts/qualite/appelants.py` ne porte donc ni cliquet ni population de
suspects.

**Le seul cas où il sort non nul est l'absence de l'index.** Un index manquant rendrait « aucun
appelant » pour TOUTE méthode, ce qui se lirait exactement comme du code mort : c'est le faux négatif
le plus coûteux que ce lecteur puisse produire, et le seul que rien d'autre ne rattraperait.

**Et il ne juge aucun compte.** La population dérive - 14 790 méthodes le 2026-09-09, 14 816 le
2026-09-28 - donc un compte attendu se périmerait en silence et rougirait chez l'innocent qui ajoute
une méthode.

## Conséquences

**Le consommateur est un relevé, donc il ne déclare pas de `CONTRAT`.** Le vocabulaire des dispositifs
est clos (ADR 5125) et aucune de ses sept valeurs ne convient à un garde qui ne juge rien ; une
`loupe` surface une revue pour une ADR `humaine`, ce qui n'est pas son objet. Lui coller une ADR pour
qu'il ait droit au nom aurait été écrire une décision pour justifier un outil.

**Et cela le sort des bancs de mutation, ce qui se dit plutôt que de se découvrir.** Il **entre** dans
la dérivation brute du banc de méthode, que `lint.yml` alimente, et c'est le filtre
`declare_un_contrat` qui l'en retire. Lui donner un `CONTRAT` le ferait donc entrer au banc **sans un
mot**. Ses six mutations ont été jouées à la main, et sa docstring porte cet avertissement.

**Ce qu'aucun dispositif ne gage**, et c'est déclaré ici plutôt que supposé : rien ne refuse
mécaniquement l'ajout d'un cliquet à ce consommateur. Son auto-test gage ce qu'il fait - la réponse et
le refus sur index absent - pas l'absence de ce qu'il ne doit pas faire. Le gage nommé peut rougir, et
il l'a fait sur chacune des six mutations.

## Alternatives écartées

**Un garde sur les méthodes publiques sans appelant externe.** Écarté par la mesure : le reste après
retrait des formes réflexives est dominé par des aides intra-fichier, toutes vivantes. Un garde sans
positif vivant ne se livre pas.

**Les trois règles `Unused*` que #4656 a laissées** - 33 `UnusedLocalVariable`, 19
`UnusedPrivateField`, 4 `UnusedFormalParameter`. Écarté parce que PMD les rend déjà : l'index n'y
ajoute rien.
