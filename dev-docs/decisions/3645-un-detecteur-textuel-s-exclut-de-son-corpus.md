---
type: adr
title: "Un détecteur textuel s'exclut de son propre corpus"
status: stable
article: A2
chantier: "#3645, suites du chantier #3536"
decided_at: 2026-08-16
verification: certaine
enforced_by:
  - "src/test/java/fr/univ_amu/iut/architecture/AnnonceDesMutationsTest.java"
verified:
  - by: machine:ci
    at: 2026-08-16
---

# Un détecteur textuel s'exclut de son propre corpus

## Contexte

Le lot 1 du chantier #3536 a posé l'invariant « tu écris, tu signales »
([ADR 3537](3537-un-signal-se-pose-a-l-ecriture.md)) : toute écriture structurelle validée appelle
`JournalMutations.mutationStructurelleValidee()`.

Pour empêcher qu'une nouvelle écriture entre sans annonce, #3645 a livré un **cliquet de dette**
([ADR 2843](2843-typographie-cliquet-plutot-que-nettoyage.md)) : cinq classes annonçaient sans qu'un test
compte leur annonce, et ce nombre devait descendre, jamais monter. Le cliquet tient un **compteur**, pas
une liste, l'[ADR 3575](3575-le-journal-fait-exception-et-le-cliquet-ne-recopie-rien.md) ayant montré
qu'une liste de noms ne protège qu'elle-même. Mais son doc-comment **nommait** les cinq débiteurs, comme trace de départ.

Sa règle : une classe est gardée si un fichier de `src/test/java` cite son nom **et** contient le mot
`JournalMutations`.

## Le défaut

Le fichier du détecteur est lui-même dans `src/test/java`. Il contient le mot `JournalMutations` par
construction, puisque c'est ce qu'il cherche. Et sa documentation nomme les cinq classes en dette.

Il les certifiait donc gardées, toutes les cinq, en se lisant lui-même.

Mesuré en remplaçant `hasSizeLessThanOrEqualTo(5)` par `hasSize(5)` :

```
Expected size: 5 but was: 0
En dette : []
```

Le cliquet n'a jamais nommé personne. Il ne pouvait pas descendre, puisqu'il était déjà à zéro ; il ne
pouvait pas rougir, puisque toute classe qu'il aurait accusée serait, du fait même de l'accusation
écrite dans son en-tête, aussitôt absoute. Un vert qui existerait à l'identique sur un dépôt cassé, ce
que l'[ADR 3624](3624-un-fait-que-rien-ne-peut-faire-rougir-s-ancre-autrement.md) refuse.

La forme est particulière et mérite d'être nommée : ce n'est pas un garde trop faible, c'est un garde
dont le **corpus contient le juge**. Plus il documente ce qu'il cherche, moins il le trouve.

## Décision

**Un dispositif qui cherche un motif textuel dans un corpus de fichiers exclut son propre fichier de ce
corpus.**

L'exclusion est nommée et documentée sur place, comme `SOCLE` dans `LibelleDeNavigationTest`, qui écarte
`Navigateur.java` pour la raison symétrique : la classe qui **déclare** un geste n'est pas celle qui le
**commet**.

Corollaire, pour cette famille de gardes : **un garde nomme le port qu'il vérifie**. Un
`() -> compteur[0]++` anonyme compte l'annonce sans qu'aucun détecteur textuel puisse le voir ;
`BaseNeuveTest` gardait ainsi son annonce depuis le lot 1, et se faisait accuser d'une dette qu'elle
n'avait pas. Le message d'échec l'écrit désormais en toutes lettres.

## Conséquences

Le détecteur s'exclut, les cinq gardes sont écrites - dont quatre nouvelles, la cinquième n'étant qu'un
lambda à typer - et le plafond de dette a laissé place à une **invariante** : la liste des classes sans
garde doit être vide. Un cliquet arrivé à zéro n'a plus de raison d'être un plafond.

Le doc-comment disait aussi que l'approximation « ne peut pas sous-estimer » la couverture. C'était faux,
et de la moitié dangereuse : une dette surévaluée donne du mou. Il dit maintenant qu'elle se trompe dans
les deux sens, et lesquels.

Ce qu'aucun de ces dispositifs ne prouve reste écrit en tête du fichier : que les **bonnes** opérations
annoncent. L'ADR 3537 autorise une seule annonce par rafale, donc comparer deux comptes rougirait en
permanence sur un dépôt correct.
