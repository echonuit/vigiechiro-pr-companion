---
type: adr
title: "Un cas joué n'est pas un cas prouvé : la recette a trois états, pas deux"
status: stable
article: A4
chantier: "#3764, tranche (a) de l'EPIC #3667"
decided_at: 2026-08-16
verification: certaine
enforced_by:
  - "CorrespondanceRecetteTest#le_script_et_le_code_s_accordent_sur_le_juge"
verified:
  - by: machine:ci
    at: 2026-08-16
---

# Un cas joué n'est pas un cas prouvé : la recette a trois états, pas deux

## Contexte

Le garde de correspondance (#3728) relie les scripts de recette au code qui les couvre. Il ne
connaissait que **deux** situations : un cas est **couvert**, ou il ne l'est pas.

Cette dichotomie allait produire un faux vert, et on savait exactement lequel. Les cas perceptifs -
« la modale s'ouvre **sans saut** », « rien ne se redimensionne pendant la récupération » - devaient
être joués par des scénarios filmés. Or un scénario **cite** son cas : c'est le seul lien vers le
script. Le garde l'aurait donc compté parmi les couverts, et le rapport aurait annoncé deux cas
prouvés **que personne n'avait regardés**.

Le défaut n'aurait pas eu de symptôme. Le compteur aurait monté, la CI serait restée verte, et la
seule trace du problème aurait été un chiffre légèrement trop flatteur.

## Décision

**1. Trois états, et le mot « couvert » reste réservé à ce que la CI prouve.**

| État | Qui tranche | Comment il se déclare |
|---|---|---|
| **Asserté** | une assertion, et elle rougit | un test porte `@CasDeRecette("S1-02")` |
| **Perceptif** | un humain, en regardant | la case porte la marque `*perceptif*` |
| **Non couvert** | personne | ni l'une, ni l'autre |

**2. L'état est une propriété du CAS, pas du test.** Il se décide en passe 6, quand on constate
qu'aucune assertion ne le tranchera - donc **avant** qu'un test existe. C'est pourquoi il vit dans le
script, sous forme de marque, et non dans l'annotation.

L'annotation, elle, porte ce que le **test** prétend prouver : `Jugement.AUTOMATIQUE` par défaut,
`HUMAIN` pour un scénario qui joue sans asserter. `AUTOMATIQUE` est le défaut non parce qu'il serait
le plus fréquent, mais pour qu'un **oubli se voie** : un test muet sur son juge est réputé asserter,
et fait donc rougir s'il porte sur un cas perceptif.

**3. Les deux sources se confrontent.** Le script dit ce qu'un cas *demande*, le code ce qu'un test
*prouve*. Le garde rougit quand elles se contredisent, dans les deux sens. C'est ce recoupement qui
empêche la marque de dériver comme la prose avait dérivé avant #3728 - le script disait
« perceptifs » en toutes lettres, et aucune machine ne le lisait.

**4. Un cas marqué perceptif ne rejoint JAMAIS les assertés**, quoi qu'en dise le code. La
contradiction se signale ; elle ne se résout pas en silence dans le sens qui arrange le compteur.

## Conséquences

- Le rapport rend trois colonnes. Les cas perceptifs quittent la file des tests à écrire, où il n'y
  avait rien à écrire.
- La tranche (c) a pu filmer `S1-26` et `S1-27` sans les faire compter comme prouvés. **L'ordre était
  contraint** : dans l'autre sens, la première annotation d'un cas perceptif aurait menti avant qu'on
  ait le moyen de la corriger.
- Le mécanisme a été **adopté hors du chantier** en quelques heures : une session parallèle a marqué
  `S1-37`. Changer la syntaxe de la marque ou le nom de l'attribut coûte désormais plus qu'au premier
  jour.
- Le tri vit dans `RepartitionDesCas`, hors du test qui l'utilise. Un calcul alimenté par un balayage
  du classpath ne peut recevoir aucun jeu fabriqué : sorti, il s'éprouve sur les situations que le
  dépôt ne contient pas - et l'on a vu rouge, en rétablissant l'ancienne règle, les deux tests qui
  portent la règle neuve.

## Alternatives écartées

- **Une annotation sœur** (`@CasFilme`) plutôt qu'un attribut. Deux annotations laisseraient un test
  en porter deux à la fois, et le garde devrait arbitrer un cas qui n'a pas de sens. Un attribut à
  valeur par défaut rend l'état **total** : chaque citation en a un, exactement un.
- **Nommer le troisième état « filmé »**, comme il l'était dans le plan initial. Faux au moment où
  l'annotation apparaît : aucun film n'existe encore, l'index par cas étant la tranche suivante. Un
  rapport annonçant « filmé » aurait désigné une vidéo qu'on ne savait pas produire. L'état dit **qui
  tranche**, pas par quel moyen on regarde.
- **Laisser le code seul décider**, sans marque dans le script. C'était la conception initiale, et
  elle était incohérente : elle prévoyait d'annoter deux cas perceptifs alors qu'aucune méthode
  n'existait encore pour les porter.
