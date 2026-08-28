---
type: adr
title: "Le paquet fige la sélection d'écoute, parce que deux tirages ne se comparent pas"
status: stable
article: A5
chantier: "#4627, chantier #4511 (mise en service d'OpenSpec), pour l'EPIC produit #3848"
decided_at: 2026-08-28
verification: humaine
verification_note: "aucun garde ne tient une décision d'échantillonnage ; le refus de régénérer sur une nuit venue d'un paquet est décrit dans la delta spec du changement `emporter-une-nuit` et sera couvert par le lot qui la réalise"
relations:
  amende: ["4517-un-avis-de-relecteur-se-range-a-cote"]
verified:
  - by: human:nedseb
    at: 2026-08-28
generated:
  by: "process:assistance-par-agents"
  at: 2026-08-28
---

# Le paquet fige la sélection d'écoute, parce que deux tirages ne se comparent pas

## Contexte

L'[ADR 4517](4517-un-avis-de-relecteur-se-range-a-cote.md) a posé que l'avis d'un relecteur se range à
côté du nôtre, dans deux colonnes de `selection_sequence`. Elle en tirait une conséquence sur le
contenu du paquet : il emporte **toutes** les séquences transformées de la nuit, pour que le relecteur
puisse régénérer sa propre sélection d'écoute plutôt que de subir celle de l'expéditeur.

Cette conséquence ne tient pas, et c'est une arithmétique qui le montre.

## La mesure

La sélection d'écoute tire `t` séquences réparties sur une nuit qui en porte `N`, avec `t` entre dix
et trente. Deux tirages indépendants se recouvrent en espérance sur `t × t / N` séquences.

| Séquences dans la nuit | Jugées par les deux, sur un tirage de 30 |
|---|---|
| 100 | environ 9 |
| 500 | environ **2** |
| 1000 | environ **1** |

`N` n'est pas mesuré dans ce dépôt, et ce tableau ne prétend pas le fixer. Il suffit à établir le
sens de la variation : dès que la nuit est grande devant le tirage, les deux échantillons sont
**quasi disjoints**.

## Décision

**Le paquet emporte les séquences de la sélection, et la sélection est figée.** Le relecteur juge le
même échantillon que l'expéditeur ; la régénération est refusée sur une nuit venue d'un paquet, avec
un motif qui dit pourquoi.

### Ce que cela répare

Deux avis sur des échantillons disjoints ne se confrontent pas. Les deux colonnes de l'ADR 4517
supposent que les deux verdicts tombent sur la **même ligne** : sans sélection commune, elles ne
serviraient qu'aux une ou deux séquences tirées par hasard des deux côtés, et le reste demanderait
une réconciliation que personne ne veut écrire.

Figer la sélection est donc ce qui rend la décision 4517 opérante, et non une restriction qu'on lui
ajoute.

### Ce que cela coûte, et qui est assumé

Le relecteur ne peut plus **contester l'échantillonnage**. S'il juge le tirage mal réparti, il le dit
hors de l'outil. C'était l'argument qui avait fait retenir « toutes les séquences » ; il pesait moins
que l'incomparabilité qu'il produisait.

*Écarté : retirer le `UNIQUE` sur `listening_selection.passage_id`* pour porter deux sélections par
passage. Ce serait la voie qui préserve la régénération, et l'ADR 4517 l'a déjà écartée sur la
mesure : vingt-trois classes lisent le verdict d'un passage comme une notion à valeur unique.

*Écarté : garder l'avis revenu comme un document à côté*, hors de la sélection. Un avis qui vit dans
un document se lit mais ne se manipule pas : il ne s'affiche pas près de la séquence qu'il juge, ne
se filtre pas, ne s'agrège pas.

## Conséquences

- **Le paquet maigrit d'un ordre de grandeur.** Il porte dix à trente séquences au lieu de toutes
  celles de la nuit. Le plan annonce quand même son volume avant d'écrire : une clé pleine reste une
  clé pleine.
- **Le code de `PlanDePaquet` et de `EcrivainPaquet` ne change pas.** Le plan reçoit la liste des
  séquences à emporter ; c'est l'appelant qui la choisit. Une décision de conception qui ne coûte
  aucune ligne est assez rare pour être notée.
- **Le refus de régénérer doit dire sa cause.** « La sélection est celle de l'expéditeur » se
  comprend ; un bouton grisé sans motif ne se comprend pas.
- La question du contenu du paquet, que l'EPIC #3848 posait comme « les séquences transformées
  suffisent-elles, ou faut-il les bruts ? », se referme sans que les bruts aient jamais été en jeu :
  ce qui décidait était la comparabilité des avis, pas la réactivabilité de la nuit.
