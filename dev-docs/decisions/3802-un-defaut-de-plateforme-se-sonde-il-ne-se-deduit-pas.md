---
type: adr
title: "Un défaut de plateforme se sonde, il ne se déduit pas"
status: stable
article: A4
chantier: "#3802, suites du chantier #3518"
decided_at: 2026-08-16
verification: probable
enforced_by:
  - "scripts/adr/5437-fixture-suppose-la-plateforme.py"
ratchet: 0
verified:
  - by: machine:suspects
    at: 2026-09-07
relations:
  prolonge: ["3664"]
  completee_par: ["5437-une-fixture-qui-suppose-la-plateforme-se-refuse-localement"]
---

# Un défaut de plateforme se sonde, il ne se déduit pas

!!! warning "Ce qui fait foi aujourd'hui"
    **Complétée le 2026-09-07** par
    [ADR 5437](5437-une-fixture-qui-suppose-la-plateforme-se-refuse-localement.md) : un garde local
    refuse la fixture qui suppose la plateforme, au lieu de la détecter le mardi suivant.

## Ce qui tient cette décision

**`probable`, et non `certaine`.** La règle est de méthode : rien ne peut prouver qu'une sonde a
précédé un test, et l'ADR 2465 réserve `certaine` à un invariant qui échoue en CI.

La moitié mécanisable, elle, existe : `scripts/adr/5437-fixture-suppose-la-plateforme.py` liste les
fixtures qui appellent une API POSIX sans déclarer qu'elles l'exigent, cliquet à zéro, sur chaque
demande. C'est la définition de `probable`.

L'`enforced_by` nommait `suite-sous-windows-et-macos.yml`, qui ne se déclenche ni sur une demande ni
sur une poussée. Ce détecteur hebdomadaire reste en place ; il n'était pas ce que `certaine`
promettait. Le geste suit l'[ADR 5414](5414-une-regle-que-rien-ne-peut-garder-se-declare.md) : ce que
rien ne peut garder se déclare. Mesuré par #5448.

## Contexte

L'ADR 3664 avait établi qu'un relevé qui n'a pas ouvert les fichiers est une hypothèse. Cette campagne
en a rencontré une forme plus dure : **des défauts qu'aucune mesure locale ne peut juger**, quelle que
soit la rigueur du relevé.

Quatre fois, sur cinq lots, le poste de développement était **aveugle par construction** :

| Défaut | Pourquoi le poste ne peut pas juger |
|---|---|
| la police d'un test (#3773) | `Noto Sans` est une police **système** sous Linux : elle est là, installée ou non |
| `ATOMIC_MOVE` sur cible ouverte (#3777) | sous POSIX, le déplacement **réussit** quoi qu'on tienne ouvert |
| la protection du jeton (#3778) | la vue **ACL** n'existe pas sur un système de fichiers POSIX |
| le repli de lecture du verrou (#3714) | PIT tourne sous Linux, où la branche est **inatteignable** |

Dans les quatre cas, une suite locale **verte** ne disait rien. Et dans un cas, une expérience
d'isolement menée sur le poste a rendu vert et a **failli servir de preuve**.

## Décision

**Quand un défaut dépend de la plateforme, on le sonde là où il vit, avant d'écrire l'assertion.**

Une **sonde** est un test jetable qui **rapporte** au lieu de juger : elle énumère les cas et imprime
ce que le système en fait. On la dispatche sur la plateforme concernée (#3754 : trois minutes en ciblé,
contre quarante-huit en complet), on lit son tableau, **puis** on écrit le test.

### Pourquoi rapporter plutôt qu'affirmer

Parce que l'hypothèse est fausse plus souvent qu'on ne le croit. Sur les deux sondes de cette campagne,
**les deux ont contredit l'issue qui les demandait** :

- #3777 redoutait un cas **irreproductible** - les quatre façons de tenir la cible le reproduisent,
  un simple `Files.newInputStream` suffit ;
- #3778 soupçonnait une protection **absente** - elle est réelle, trois entrées `ALLOW`, l'équivalent
  de `600`. La doc disait vrai.

Une sonde qui aurait *asserté* l'hypothèse serait passée au rouge sans qu'on sache pourquoi, ou au vert
en la confirmant par hasard.

### Ce que la sonde ne remplace pas

**Le test qui reste** doit être éprouvable **partout**, donc passer par une couture d'injection -
`GestesFichiers`, `TailleFichier`, `CouleurCli`, `Deplacement`, `ProtectionFichier`. La sonde établit
**quel** comportement câbler ; l'injection le rend rejouable.

Les deux sont nécessaires, et l'un sans l'autre laisse la moitié du remède non jugée : le passage
hebdomadaire éprouve le **câblage**, la couture éprouve la **borne**.

## Conséquences

- **La sonde est jetable, son résultat ne l'est pas.** Il vit dans le doc-comment du remède, avec le
  numéro du run - pour qu'on n'ait pas à la refaire.
- **Une limite doit être écrite quand elle existe.** Trois remèdes de cette campagne portent une
  branche inatteignable sous Linux, et leurs ADR le disent : sans cela, on croirait la couverture
  complète en lisant un rapport PIT.
- **Le vert d'un run ne vaut que si son tri conclut.** Deux fois dans cette campagne, un run marqué
  `success` portait un tableau qui disait l'inverse - dont une sonde dont le verdict aurait fait écrire
  qu'un mécanisme démontré était réfuté. Lire le **tableau**, jamais la pastille.

## Alternatives écartées

- **Déduire depuis la documentation de la plateforme.** C'est ce qui a produit l'hypothèse fausse de
  #3777 : la Javadoc de Java sur les drapeaux de partage était exacte, et la conclusion qu'on en tirait
  ne l'était pas.
- **Faire tourner la suite complète pour chaque question.** Quarante-huit minutes par passage : la
  boucle rapide de #3754 existe précisément pour que sonder reste bon marché.
- **Renoncer et documenter le doute.** C'est l'état d'où vient cette campagne : deux affirmations de
  sécurité écrites et jamais vérifiées, dont l'une était le motif fondateur d'un chantier.
