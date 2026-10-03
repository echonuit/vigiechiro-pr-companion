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
ratchet: 162
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
est levée chez le lecteur et laisse le graphe intact - 5 603 tests pour une erreur, sans cascade - là
où les deux que nomme `CadreVisible.lireSurLeFilFx` abîment la JVM du fork.

**Révision du 2026-10-01** : ce minorant a une **seconde** raison, trouvée par #5738 - le relevé ne
lit que les tirages **rejoués**, 21 sur les 59 qui portent un rouge. La décision ne change pas : elle
tient sur le mécanisme, pas sur le taux.

## La décision

> Un geste du pointeur **situe sa cible sur le fil JavaFX**, et ne reçoit ensuite qu'un point.

Le point vient de `robot.point(...)`, appelé **dans** l'aller-retour de fil. Un calcul à nous serait
faux : la première version prenait le centre de `localToScreen(getBoundsInLocal())`, là où
`BoundsLocatorImpl` **intersecte** d'abord les bornes avec la scène. Un champ qui dépasse de sa
fenêtre avait donc un centre hors de la fenêtre ; le clic partait à côté, et deux bancs ont rougi sur
un champ resté à sa valeur d'origine. On importe l'instrument au lieu de le réécrire, comme pour le
prédicat de visibilité qu'`amenerDansLeCadre` partage.

## Trois voisines, et le trou entre elles

L'**ADR 5278** tient les **prédicats d'attente** qui lisent le graphe hors du fil : son cliquet à
zéro ne dit rien d'ici, une lecture hors de toute attente n'entrant pas dans sa population. Le garde
Java de **#4246** tient les helpers qui lisent le graphe **eux-mêmes**, et ne pouvait pas voir une
lecture déléguée à TestFX - mais il a bien accusé la première version de ce lot, qui calculait les
bornes elle-même.

L'**ADR 5068** compte les `clickOn` tenant une référence résolue, et sa population est **strictement
contenue** dans celle-ci, 38 sur 38. Les deux remèdes ne se valent donc pas : passer au sélecteur
retire un site de son cliquet et le laisse dans celui-ci, `pointSurLeFil` le retire des **deux**.

## Pourquoi un cliquet, et non un banc

Deux bancs ont été écrits et jetés. Le premier reproduisait la course et sortait **vert sur le code
fautif** : la fenêtre de collision est la durée d'une itération contre seize millisecondes entre deux
trames.

Le second mesurait la cause - `visibleProperty` liée à une liaison qui note le fil appelant - et
sortait **vert trois fois sur trois**, pour une raison qui condamne la famille entière : le fil
JavaFX rend à chaque trame, recalcule la liaison le premier, et **sert son cache** au fil du test.
Toute propriété lue par les deux fils est mise en cache par celui qui rend.

Aucun témoin Java d'API publique ne peut donc voir ce fil. La règle se tient par un cliquet, et c'est
une contrainte de l'instrument, pas une préférence.

## Les conséquences

`scripts/adr/5707-geste-du-pointeur-hors-du-fil.py` lit les gestes qui situent une cible et refuse
au-dessus de **162**. Mesure du 2026-10-01 : **169 lus**, 169 fautifs avant ce lot, 163 après ; #5734
l'a resserré à 162 en convertissant un site. La population ne bouge pas et les suspects descendent,
ce qui distingue un gain d'un ciblage manqué (ADR 4002).

Les restants sont la dette déclarée des bancs : le cliquet refuse le **suivant**, pas eux. Un grep
sur `clickOn("` n'en voyait que 110, les autres passant un identifiant ou un `lookup` imbriqué : le
compte juste se lit à l'arbre.

## Les limites, déclarées

Depuis #5767 le garde lit la **structure** de l'aide : l'argument doit nommer une aide du fichier
dont le corps route vers le fil. Il lisait le **nom**, et une aide gardant le nom sans son routage
passait - trouvé par mutation. Restent deux limites : une aide d'un **autre fichier** échappe, comme
chez l'ADR 5278 ; et la présence de l'appel suffit, donc router **puis** lire hors du fil passe
encore - la propriété plus faible de #4246.
