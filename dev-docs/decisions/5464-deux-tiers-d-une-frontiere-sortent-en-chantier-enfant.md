---
type: adr
title: "Deux tiers d'une frontière sortent en chantier enfant plutôt que de disparaître"
status: stable
article: A5
chantier: "EPIC #5464"
decided_at: 2026-09-28
verification: humaine
loupe: "scripts/adr/loupe-4712-lots-multi-pr.py"
verified:
  - by: human:porteur
    at: 2026-09-28
relations:
  applique: ["4712"]
generated:
  by: "process:assistance-par-agents"
  at: 2026-09-28
---

# Deux tiers d'une frontière sortent en chantier enfant plutôt que de disparaître

## Contexte

#5464 écrivait sa frontière en **trois** questions, et ajoutait « c'est là, et seulement là, que
l'index se justifie » :

```
ni arbre.py, ni attache.py, ni PMD ne savent dire
  1. qui appelle une methode publique a travers le corpus
  2. quel type implemente un contrat
  3. si un champ est lu ailleurs
```

Ses trois lots ont livré **la première**. Sa passe 0 de clôture a constaté que rien ne répond aux deux
autres : aucun lecteur de `scripts/_commun/`, et `UnusedPrivateField` est hors du jeu de règles de
l'ADR 4617 (vrai à cette date ; la règle y est entrée le 7 octobre 2026, par #6115). Le pluriel de la promesse les attendait pourtant, puisqu'elle écrivait que le lecteur
« lit `target/index-*.json` ». Il n'y en avait qu'un.

**La cause n'est pas un renoncement, c'est un découpage.** Les trois lots, tels qu'ils ont été coupés,
ne nomment ni les implémentations ni les champs. Personne ne les a écartés ; ils ont disparu entre la
frontière et la liste des lots.

## Décision

**Les deux questions restantes sortent en chantier enfant, #5553, et la clôture reprend.** C'est une
décision de ne pas livrer **ici**, non de ne pas livrer.

Elle applique l'ADR 4712 : chacune des deux demande un extracteur, un lecteur, un consommateur et
leur mutation, donc plus d'une demande de fusion chacune, donc un sous-chantier et non une case à
cocher. C'est ainsi que #5464 lui-même est sorti de #5402, à sa clôture du 2026-09-07.

La population du reste est mesurée plutôt qu'estimée, par `scripts/_commun/arbre.py` sur les deux
racines que l'extracteur balaie déjà : **117 interfaces** pour la question 2, **6 982 champs** pour la
question 3. Les deux ordres de grandeur diffèrent d'un facteur soixante, ce qui est un argument
supplémentaire pour deux lots distincts.

## Conséquences

**Ce qui est déjà payé ne se repaie pas.** L'extracteur Spoon, sa production en CI, le patron du
lecteur avec son refus et celui du consommateur existent. Le reste est d'ajouter deux sorties au même
extracteur et deux lecteurs sur le même modèle.

**Le coût en CI se mesure et ne se suppose pas.** L'extraction actuelle coûte 25 s et le job `methode`
tient à 3,95 min sous un butoir de 15, soit 11 min de marge. Deux index de plus ne sortent pas de
cette marge, et c'est le premier contrôle de chaque lot de #5553 plutôt qu'une hypothèse.

**Une troisième voie était disponible et elle n'est pas permise** : clore en laissant la promesse à
moitié tenue sans que personne ne l'ait décidé. Elle aurait produit un bilan juste - trois lots
livrés, trois lots fermés - et une clôture fausse. C'est pourquoi cette décision s'écrit, alors
qu'elle ne laisse aucun code derrière elle.

## Pourquoi sa vérification est humaine

Aucun dispositif ne sait confronter la frontière écrite en prose d'un EPIC à ce que ses lots ont
livré : la promesse est un paragraphe, pas une donnée. `loupe-4712-lots-multi-pr.py` surface les EPIC
dont les lots pèsent plusieurs demandes, ce qui est le terrain de cette décision, et la confrontation
reste la passe 0 de chaque clôture.

Nommer un gage mécanique ici aurait prétendu plus que le dépôt ne tient, ce que l'ADR 5483 refuse en
toutes lettres.
