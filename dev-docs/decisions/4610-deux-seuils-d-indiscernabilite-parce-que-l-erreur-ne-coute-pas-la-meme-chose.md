---
type: adr
title: "Deux seuils d'indiscernabilité, parce que l'erreur ne coûte pas la même chose"
status: stable
article: A17
chantier: "#4610 (seuil du contrôle), tranché en passe 7 de la clôture de #4671"
decided_at: 2026-08-28
verification: certaine
enforced_by:
  - "BandeDesIndiscernablesTest#les_deux_seuils_different"
  - "BandeDesIndiscernablesTest#chaque_seuil_est_encadre"
  - "ConfrontationCarreTest#sous_le_seuil_le_second_concorde"
  - "ConfrontationCarreTest#au_dessus_du_seuil_le_second_diverge"
verified:
  - by: machine:ci
    at: 2026-08-28
generated:
  by: "process:assistance-par-agents"
  at: 2026-08-28
---

# Deux seuils d'indiscernabilité, parce que l'erreur ne coûte pas la même chose

## Contexte

Sur une frontière, plusieurs carrés sont à distance **strictement égale** d'un point : deux au milieu
d'un côté commun, quatre à un coin. L'application décide qui est « trop proche pour être départagé » au
moyen d'un écart de distance.

Deux classes posaient ce seuil, avec deux valeurs, et **écrivaient deux fois la même justification** :
la dérivation géométrique, la note sur le mutant équivalent, et jusqu'à la raison de leur écart.

| Classe | Seuil | Question posée |
|---|---|---|
| `PropositionCarre` | 50 m | quel numéro **écrire** dans le champ ? |
| `ConfrontationCarre` | 100 m | le numéro **déjà écrit** est-il plausible ? |

## Décision

**Les deux seuils restent, et un seul type les porte** - `BandeDesIndiscernables` - où chaque constante
se lit à côté de l'autre, avec ce que coûte l'erreur de son côté.

Les deux se dérivent de la même géométrie : pour un point à `x` mètres d'un bord, l'écart entre les
deux distances aux centres vaut environ `2x`. Le seuil se lit donc en **doublant la marge visée** :
50 m pour 25 m de marge, 100 m pour 50 m.

## Pourquoi ils ne s'unifient pas

Parce que l'erreur n'a pas le même prix des deux côtés.

**Proposer** écrit un numéro dans un champ que l'observateur validera **sans le relire**. Un numéro
faux et plausible contamine ensuite le préfixe R6 de tous ses fichiers, et ne se voit qu'au dépôt. La
proposition doit donc se taire tôt, et demander plutôt que deviner.

**Contrôler** commente un numéro que l'observateur a déjà écrit. Se tromper d'excès de prudence ne
coûte qu'un contrôle en moins ; se tromper d'excès de zèle, en revanche, c'est accuser à tort quelqu'un
qui avait raison - et un avertissement toujours faux finit par ne plus être lu du tout.

Un seuil unique ferait donc payer un usage pour l'autre, dans un sens ou dans l'autre.

## Pourquoi un type plutôt que deux javadoc

C'est la leçon de l'[ADR 4578](4578-deux-precisions-de-conversion-coexistent-parce-qu-elles-ne-servent-pas-a-la-meme-chose.md),
et elle s'applique ici mot pour mot : **une divergence documentée dans une seule des classes n'avertit
que qui ouvre celle-là**. Le premier lecteur pressé les aurait unifiées, à 50 ou à 100 selon la porte
par laquelle il serait entré.

La deuxième fois que ce dépôt rencontre cette forme, la réponse est la même. Elle commence à valoir
règle, et `dev-docs/patterns.md` la porte.

## Comment on saurait qu'elle est rompue

`BandeDesIndiscernablesTest#les_deux_seuils_different` **exige que les deux valeurs divergent**, et
n'a pas d'autre raison d'être. `#chaque_seuil_est_encadre` tient chaque valeur, sans quoi l'un pourrait
dériver jusqu'à rejoindre l'autre en gardant l'inégalité.

Les deux cas de `ConfrontationCarreTest` encadrent le seuil du contrôle par une paire, à 60 m et
140 m : la borne stricte elle-même n'est pas testable - un écart de 100,0 m exacts n'est pas
atteignable sur des distances calculées depuis des degrés - donc c'est la **valeur** qu'on tient.
