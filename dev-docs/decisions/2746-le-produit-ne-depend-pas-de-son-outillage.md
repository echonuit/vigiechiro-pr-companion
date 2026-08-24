---
type: adr
title: "Le produit ne dépend pas de son outillage"
status: stable
article: A20
chantier: "#2746, lot #2724 du chantier #2720"
decided_at: 2026-08-06
verification: certaine
enforced_by:
  - "ArchitectureTest#produit_sans_outillage"
verified:
  - by: machine:ci
    at: 2026-08-06
---

# Le produit ne dépend pas de son outillage

## Contexte

Ce dépôt bâtit son outillage **avec** le produit et **dans** le même module : 41 outils de capture de
documentation, des bancs de mesure, des générateurs de cartes SD. Ils vivent sous `**/outils/**` et
`perf/**`, sont déjà exclus de la couverture, et sont **construits en même temps** que l'application.

Ils partaient aussi chez l'utilisateur. Mesuré sur le jar distribué : **80 classes d'outillage** et
**47 points d'entrée `main`** en plus des trois du produit. Un naturaliste recevait donc cinquante
manières de démarrer autre chose que son application.

⚠️ **Le motif n'est pas le poids**, et l'issue le croyait d'abord. L'outillage pèse 600 Ko
décompressés quand `org/sqlite` en pèse 21,6 Mo : le jar ne maigrit pas visiblement. Ce qui se réduit
est la **surface** livrée.

## La dépendance qui interdisait de trancher

Une classe de production franchissait la frontière dans le mauvais sens : `ExportGraphe` appelait
`ApercuFx.enregistrerPng`, c'est-à-dire du code d'outillage, pour l'export d'image offert à
l'utilisateur (courbe d'activité #2352, courbe climatique du diagnostic #2618) - deux fonctions
derrière un bouton visible et documentées.

Exclure `outils` du jar **aurait donc cassé une fonctionnalité**, et à l'exécution seulement : la CI
teste depuis `target/classes`, où tout est présent. La règle ArchUnit ne pouvait pas exister non
plus : elle serait née rouge.

## Décision

**Ce qui est offert à l'utilisateur vit dans le produit ; l'outillage dépend du produit, jamais
l'inverse.** Trois pièces, dans cet ordre, parce que chacune rend la suivante sûre :

1. `commun/view/RenduPng` reçoit le geste dont la production a besoin - **une** méthode sur onze,
   pas la classe entière ;
2. `ArchitectureTest#produit_sans_outillage` interdit à la production de dépendre de `..outils..` et
   `..perf..` ;
3. le greffon `shade` retire `fr/univ_amu/iut/**/outils/**` et `fr/univ_amu/iut/perf/**` du jar
   distribué.

## Conséquences

- **0 classe d'outillage** dans le binaire, contre 80, et **3 points d'entrée** au lieu de 50 ;
- l'outillage continue de fonctionner : `capture-screenshots.sh` lance ses `main` par
  `exec-maven-plugin` sur le **classpath Maven** (`target/classes`), jamais par ce jar. Vérifié en
  rejouant une capture, qui se régénère ;
- les 43 noms de classes en dur du script de captures restent valides : **aucune source n'est
  déplacée**.

### ⚠️ Le contrôle de lisibilité ne suit pas dans `RenduPng`, et ce n'est pas un oubli

`ApercuFx.enregistrerPng` refuse une image dont un libellé est tronqué
([`LisibiliteCapture#refuserToutTexteIllisible`]). C'est juste pour une **capture de documentation** :
une image fausse ne doit pas partir dans la doc.

Ce n'est pas juste pour l'**export utilisateur**. `ExportGraphe` redessine le graphe dans une scène
transitoire hors écran ([ADR 2348](2348-un-export-d-image-se-redessine-il-ne-se-capture-pas.md)) : une troncature s'y
produirait dans une mise en page que l'utilisateur ne voit jamais et ne peut pas corriger. Faire
échouer son export là-dessus le laisserait **sans recours**. Le souci que l'ADR 2348 nomme - l'export
qui échoue en silence - est réglé par le redessin lui-même.

Conséquence assumée : depuis cette ADR, l'export d'image de l'utilisateur ne peut plus échouer sur un
texte tronqué. L'outillage de documentation, lui, garde le contrôle.

### L'ordre des deux PR n'était pas cosmétique

Sans la règle ArchUnit, l'exclusion du jar serait une **panne à retardement** : une future dépendance
de production vers `outils` rétablirait le franchissement, l'application échouerait **chez
l'utilisateur** sur une `NoClassDefFoundError`, et la CI resterait verte puisqu'elle ne teste jamais
depuis le jar amputé. La règle a été vue rougir sur le franchissement réintroduit, et elle nomme
l'appel exact.
