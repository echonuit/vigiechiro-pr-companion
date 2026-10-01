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

`SelecteurFichierEnFenetreTest.enregistrer_rend_le_chemin_complet` a levé en CI :

```
java.util.ConcurrentModificationException
  at com.sun.javafx.scene.shape.PathUtils.configShape(PathUtils.java:45)
  ...
  at org.testfx.api.FxRobot.pointOfVisibleNode(FxRobot.java:922)
  at fr.univ_amu.iut.recette.GesteVisible.cliquer(GesteVisible.java:144)
```

**Une fois sur 558 tirages** de trente jours, et c'est un minorant : le relevé des bancs instables ne
lit que `maven.yml`. La pile est celle du fil **du test** - c'est lui qui itère, et le fil JavaFX qui
écrit. La `ConcurrentModificationException` est une **troisième** forme de ce que la javadoc de
`CadreVisible.lireSurLeFilFx` nomme, après l'index de -1 et le tableau de segments nul, et la seule
qui n'abîme rien : elle est levée chez le lecteur, et le graphe reste intact. Le journal le confirme,
5 603 tests et une erreur, sans cascade.

## La décision

> Un geste du pointeur **situe sa cible sur le fil JavaFX**, et ne reçoit ensuite qu'un point
> d'écran.

Le repère est celui de l'**écran**, parce que c'est ce que `moveTo(Point2D)` attend. Rendre un point
de scène donnerait un geste décalé de la position de la fenêtre, et **rien ne refuserait** : le clic
partirait simplement à côté.

Le refus de TestFX est conservé plutôt que perdu. `GesteVisible.exigerVisible` emploie
`NodeQueryUtils.isVisible()`, le prédicat même de TestFX, importé et non réécrit : une seconde façon
de juger la visibilité divergerait de celle sur laquelle `amenerDansLeCadre` s'appuie pour savoir
quand défiler.

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

Les 163 restants sont la **dette déclarée des bancs**, hors périmètre de ce lot par arbitrage : ils
résolvent leur sélecteur directement, sans passer par `GesteVisible`. Le cliquet ne leur demande
rien ; il refuse le **suivant**.

Un grep sur `clickOn("` n'en voyait que 110 : les 59 autres passent un identifiant, un `lookup`
imbriqué, une concaténation ou un transtypage. Le compte juste se lit à l'arbre.

## La limite, déclarée

Le garde reconnaît un **nom**, `pointSurLeFil(`, et non une propriété. Une cible située sur le fil par
une aide portant un autre nom échapperait au compte. C'est le prix d'un détecteur statique, et il est
écrit dans son `CONTRAT` plutôt que découvert.
