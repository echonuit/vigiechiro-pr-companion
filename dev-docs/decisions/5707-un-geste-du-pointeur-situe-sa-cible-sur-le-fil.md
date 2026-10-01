---
type: adr
title: "Un geste du pointeur situe sa cible sur le fil JavaFX, ou il mesure ce qu'un autre écrit"
status: stable
article: A2
chantier: "#5697 (les bascules des bancs), lot #5707"
decided_at: 2026-10-01
verification: probable
enforced_by:
  - "scripts/adr/5707-geste-du-pointeur-hors-du-fil.py"
ratchet: 163
verified:
  - by: machine:suspects
    at: 2026-10-01
relations:
  complete: ["5068-une-dette-assumee-se-compte"]
generated:
  by: "process:assistance-par-agents"
---

# Un geste du pointeur situe sa cible sur le fil JavaFX, ou il mesure ce qu'un autre écrit

## Le contexte

`robot.moveTo("#id")` ne fait pas que bouger le pointeur : il **situe** sa cible, et il le fait sur
le fil appelant. L'octet-code de TestFX 4.0.18 dit ce que `NodeQueryUtils.isNodeVisible` lit de
chaque candidat :

```
Node.getScene -> Node.getBoundsInLocal -> Node.localToScene -> Scene.getWidth/getHeight
```

Lire des bornes recalcule la géométrie du sous-arbre, donc **itère les éléments** de tout `Path` qui
s'y trouve. Le fil JavaFX, pendant ce temps, rebâtit ces éléments quand il le doit : le caret d'un
champ de saisie en est un, reconstruit à chaque clignotement.

## Ce que cela produit

`SelecteurFichierEnFenetreTest.enregistrer_rend_le_chemin_complet` a levé en CI, **une fois sur 558
tirages** de trente jours - un minorant, le relevé ne lisant que `maven.yml` :

```
java.util.ConcurrentModificationException
  at com.sun.javafx.scene.shape.PathUtils.configShape(PathUtils.java:45)
  at org.testfx.api.FxRobot.pointOfVisibleNode(FxRobot.java:922)
  at fr.univ_amu.iut.recette.GesteVisible.cliquer(GesteVisible.java:144)
```

La pile est celle du fil **du test** : c'est lui qui itère, et le fil JavaFX qui écrit. Cette forme
n'abîme rien, contrairement aux deux que nomme `CadreVisible.lireSurLeFilFx` : elle est levée chez le
lecteur, le graphe reste intact, et le journal montre 5 603 tests pour une erreur, sans cascade.

## La décision

> Un geste du pointeur **situe sa cible sur le fil JavaFX**, et ne reçoit ensuite qu'un point.

Le point vient de `robot.point(...)`, appelé **dans** l'aller-retour de fil. Un calcul à nous serait
faux : la première version prenait le centre de `localToScreen(getBoundsInLocal())`, là où
`BoundsLocatorImpl` **intersecte** d'abord les bornes avec la scène. Un champ qui dépasse de sa
fenêtre avait donc un centre hors de la fenêtre ; le clic partait à côté, et deux bancs ont rougi sur
un champ resté à sa valeur d'origine. On importe l'instrument au lieu de le réécrire, comme pour le
prédicat de visibilité qu'`amenerDansLeCadre` partage.

## Trois voisines, et le trou entre elles

L'**ADR 5278** tient les **prédicats d'attente** qui lisent le graphe hors du fil. Son cliquet à
zéro est sincère et ne dit rien d'ici : une lecture hors de toute attente n'entre pas dans sa
population.

Le garde Java de **#4246** tient les **helpers qui lisent le graphe eux-mêmes**. Il ne pouvait pas
voir ce défaut, la lecture étant **déléguée à TestFX** - mais il a bien accusé la première version
de ce lot, qui calculait les bornes elle-même.

L'**ADR 5068** compte les `clickOn` tenant une référence résolue. Sa population est **strictement
contenue** dans celle-ci, 38 sur 38. Les deux remèdes ne se valent donc pas : passer au sélecteur
retire un site de son cliquet et le laisse dans celui-ci, `pointSurLeFil` le retire des **deux**.

## Pourquoi un cliquet, et non un banc

Deux bancs ont été écrits et jetés avant de poser cette règle.

Le premier reproduisait la course : un `Path` de deux mille éléments rebâti à chaque trame pendant
que quarante gestes résolvaient un sélecteur. **Vert sur le code fautif.** La fenêtre de collision
est la durée d'une itération, quelques dixièmes de milliseconde contre seize entre deux trames.

Le second mesurait la cause plutôt que le symptôme : `visibleProperty` liée à une liaison qui note le
fil appelant. **Vert trois fois sur trois**, et pour une raison qui condamne la famille entière - le
fil JavaFX rend à chaque trame, donc il recalcule la liaison le premier et **sert son cache** au fil
du test. Toute propriété lue par les deux fils est mise en cache par celui qui rend.

Aucun témoin Java d'API publique ne peut donc voir ce fil. La règle se tient par un cliquet, et c'est
une contrainte de l'instrument, pas une préférence.

## Les conséquences

`scripts/adr/5707-geste-du-pointeur-hors-du-fil.py` lit les gestes qui situent une cible et refuse
au-dessus de **163**. Mesure du 2026-10-01 : **169 lus, 169 fautifs** avant ce lot, **169 lus, 163
fautifs** après. La population ne bouge pas et les suspects descendent, ce qui distingue un gain d'un
ciblage manqué (ADR 4002).

Les 163 restants sont la dette déclarée des bancs, hors périmètre par arbitrage : le cliquet ne leur
demande rien, il refuse le **suivant**. Un grep sur `clickOn("` n'en voyait que 110 ; les autres
passent un identifiant, un `lookup` imbriqué, une concaténation ou un transtypage. Le compte juste
se lit à l'arbre.

## La limite, déclarée

Le garde reconnaît un **nom**, `pointSurLeFil(`, et non une propriété. Une cible située sur le fil
par une aide portant un autre nom échapperait au compte, et son `CONTRAT` le déclare.
