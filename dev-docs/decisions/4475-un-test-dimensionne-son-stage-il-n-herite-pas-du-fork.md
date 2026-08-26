---
type: adr
title: "Un test dimensionne son stage, il n'hérite pas de celui du fork"
status: stable
article: A9
chantier: "#4475 (report d outillage, chantier #4462)"
decided_at: 2026-08-25
verification: probable
enforced_by:
  - "scripts/adr/4475-stage-non-dimensionne.py"
ratchet: 0
verified:
  - by: humain:mutation
    at: 2026-08-25
generated:
  by: "process:assistance-par-agents"
  at: 2026-08-25
---

# Un test dimensionne son stage, il n'hérite pas de celui du fork

## Contexte

`LotVueIntegrationTest` et `LotDepotConnecteViewTest` échouaient sur un `clickOn` par intermittence,
sur un nœud pourtant `visible=true`. Le défaut a résisté à **deux diagnostics faux** avant d'être
reproduit.

Ce qui l'a résolu n'est pas un raisonnement : c'est le rapport que `AttenteAvantClic` rend à
l'expiration.

```
« #btnDeposer » n'est pas devenu cliquable en 5 s.
  - Button visible=true bornes y=774..809 x=35..189 | scène 900x600 : HORS CADRE
```

**`scène 900x600`.** Le `@Start` demande pourtant `new Scene(vue, 980, 980)`, et l'écran headless
mesure 1 000 × 1 000 : la place était là.

900 et 600 sont `TailleOuverture.LARGEUR_MINIMALE` et `HAUTEUR_MINIMALE`.

## Le mécanisme

Le stage primaire est **partagé entre les classes d'un même fork surefire**. La classe qui a exercé
le dimensionnement de l'application le laisse dimensionné, et **`setScene` sur un stage déjà
dimensionné ne le redimensionne pas** : la scène demandée à 980 × 980 est relue à 900 × 600, et tout
ce qui vit sous 600 px tombe hors du rectangle que le clic exige.

L'intermittence est structurelle : `surefire.forkCount=1C` redistribue les classes entre les forks à
chaque passe, donc la contamination se produit ou non selon la composition du fork. Un test lancé
seul est toujours vert - ce qui a fait conclure deux fois de suite qu'il n'y avait rien.

## Décision

**Un `@Start` qui affirme une taille de scène la fait tenir par sa fenêtre, sans la figer.**

```java
FenetreAjustable.poser(stage, vue, 980, 980);
FenetreAjustable.afficher(stage); // show(), puis sizeToScene()
```

**La première rédaction prescrivait `setWidth` / `setHeight`, et c'était l'inverse** (#4582). Ces
deux appels font passer un Stage en dimensionnement explicite : il cesse de s'ajuster aux scènes
suivantes, donc il corrige la classe courante en contaminant celles d'après.
`ConventionsDEcritureTest.aucun_stage_recu_n_est_fige` (#4134) le refuse, et depuis avant cette ADR.
Appliqué aux 80 classes, le remède prescrit ici aurait fait rougir ce test 80 fois ; personne ne
l'ayant appliqué en bloc, la contradiction n'a jamais rougi.

**Attendre ne pouvait rien**, et c'est ce qui rend cette ADR nécessaire : un nœud à `y=774` dans une
scène de 600 n'y entrera jamais. La première tentative de correctif - attendre le prédicat exact du
clic - ne corrigeait donc rien. Elle a servi à autre chose, et c'est ce qui compte ici : **elle a
rendu la cause lisible**. Les appels `attendreCliquable` sont conservés à ce titre.

## La dette, et qui la tient

**Zéro** (#4582). Les 80 classes qui posaient une scène dimensionnée sur le stage reçu passent par
`FenetreAjustable`, et le cliquet est devenu un **refus** : l'article A9 veut qu'une zone au plancher
soit gardée par un refus, faute de quoi le zéro ne reste pas zéro.

Deux suspects n'en étaient pas. `ModalesTest` et `ModalesCentrageTest` posent leur scène sur un
`new Stage()` à elles, que rien ne partage - le cas que `ConventionsDEcritureTest` écarte déjà en
disant qu'une fenêtre à soi n'est plus reçue. Le détecteur les exempte, et deux témoins tiennent
l'exemption, dont celui qui la rend sûre : une pose privée ne masque pas la pose partagée qui la
suit.

Ce que le cliquet comptait est une **forme** : une taille affirmée sans que la fenêtre la tienne.
Avant ce lot, la suite entière en fork unique et ordre inverse rendait déjà 5 099 tests et 0 échec
(#4545) - le mécanisme est réel, deux mutations le reproduisent, aucune classe ne le déclenchait.
Corriger la forme n'a donc pas réparé un défaut vivant : cela aligne le dépôt sur sa référence, qui
compte zéro, et ferme la porte à la classe suivante.

## Conséquences

Le niveau est `probable` : le cliquet voit qu'une taille est affirmée sans être tenue, il ne voit
pas si l'écran déborde. À zéro il ne trie plus rien - il refuse - et un cas légitime se traite en
nommant son repreneur dans cette ADR, pas en relevant le compteur.

**Le garde a eu le défaut qu'il cherche.** Sa première version cherchait `stage.setWidth` : la
moitié des `@Start` du dépôt renomment leur stage - `modale`, `fenetre` - et il les déclarait verts
sans les avoir lus. Il lit désormais le receveur du `setScene`, et un témoin le tient.

## Mutation

Le stage forcé à 900 × 600 dans le `@Start` reproduit l'échec **au caractère près** - deux échecs,
`scène 900x600`, `bornes y=774..809` - et le rétablissement à 980 le rend vert. C'est la première
reproduction déterministe de ce défaut.
