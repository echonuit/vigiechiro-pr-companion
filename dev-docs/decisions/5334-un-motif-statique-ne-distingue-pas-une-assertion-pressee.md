---
type: adr
title: "Un motif statique ne distingue pas une assertion pressée"
status: stable
article: A2
chantier: "#5357 (les suites de la convergence des bancs filmés), lot #5334"
decided_at: 2026-09-06
verification: certaine
enforced_by:
  - "ExecuteurTacheDiffereTest#le_travail_est_retarde"
  - "ExecuteurTacheDiffereTest#une_lecture_immediate_ne_voit_rien"
verified:
  - by: machine:ci
    at: 2026-09-06
generated:
  by: "process:assistance-par-agents"
---

# Un motif statique ne distingue pas une assertion pressée

## Le défaut qu'on cherche

Une assertion posée juste après un geste **asynchrone** lit un port que le travail n'a pas encore
écrit. Elle passe presque toujours - le travail a le plus souvent fini avant - et quand elle échoue,
elle échoue dans un autre test, sans motif lisible. On la classe « flake ».

Le dépôt a payé ce diagnostic une fois, sur `ScenarioPerceptifRefusDepotTest` (#5152).

## Ce qui ne le distingue pas, et c'est mesuré

L'issue partait d'une forme de surface : une `Respiration` suivie d'une assertion. Trois critères
statiques ont été essayés sur les **64** sites qui la portent.

| Critère | Ce qu'il désigne | Verdict |
|---|---:|---|
| le banc déclare un exécuteur asynchrone | **48** sur 64 | trois quarts : ne discrimine pas |
| aucune attente devant, aides du **fichier** suivies | **16** sur 64 | faux positifs |
| aucune attente devant, aides **partagées** suivies | **0** sur 64 | ne désigne rien |

Les seize du deuxième essai s'expliquent : l'attente vit dans un **autre fichier**.
`GesteVisible.choisir` fait `waitForFxEvents` puis `Attente.queSurLeFil` avant de rendre la main.

**Une attente devant ne protège que si elle attend la bonne chose.** Dans #5152, une `Respiration`
était là, et ne tenait rien - elle ne s'arrête que si l'on filme.

Et la forme confond deux gestes : la `Respiration` est souvent posée **après** une assertion, où elle
sépare deux blocs de cas et rythme le clip.

## La décision

**Le défaut se cherche par MUTATION, pas par motif.** `ExecuteurTacheDiffere` retarde le travail
confié à l'exécuteur ; une assertion qui court après lui tombe alors dans le trou à tous les coups,
et une assertion qui attend vraiment reste verte.

C'est le jumeau inverse d'`ExecuteurTacheRalenti`, qui freine le **relais de progression** pour qu'un
transitoire dure assez longtemps pour être filmé. Le retard a lieu dans le `Supplier` confié au
délégué, donc sur le fil de travail et jamais sur celui de JavaFX, que le freiner gèlerait.

## Ce que la mesure a rendu, et pourquoi c'est un résultat

Neuf sites désignés, **zéro défaut**. Chacun classé sur preuve : `ouvrirSite()` appelle son port
directement sur le fil FX, `ActionsEmport` n'utilise pas d'exécuteur, et `MainViewTest` lit le graphe
de scène plutôt qu'un port. Les sept sites d'emport ont été rejoués sous le différé : verts.

**Le dispositif a établi qu'ils tiennent en NE rougissant PAS** là où le motif accusait. Le défaut de
#5152 était isolé.

## Conséquences

**Le retard déborde de sa cible s'il est trop grand.** À 400 ms, les scénarios d'emport rougissent
dans leur préambule d'import, qui devient trop lent - pas sur les sites visés. À 120 ms ils passent,
alors qu'un trou de 150 ms suffit à révéler une lecture pressée. Le retard est donc réglable, et sa
valeur par défaut porte sa raison.

**Le toolkit JavaFX doit être monté pour l'éprouver.** `ExecuteurTacheAsynchrone` rend son résultat
par `Platform.runLater`, qui n'a nulle part où le poster sans lui : les rappels se perdent sur le fil
virtuel, et les cas expirent en accusant le retard là où il n'y a qu'un toolkit absent.

**Le dispositif reste au dépôt**, sans site à corriger. C'est délibéré : le prochain « flake » de
cette famille aura de quoi être reproduit, et c'est ce qui manquait à #5152.
