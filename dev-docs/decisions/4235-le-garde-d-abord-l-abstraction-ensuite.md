---
type: adr
title: "Le garde d'abord, l'abstraction ensuite"
status: stable
article: A7
chantier: "#4235, EPIC #4133"
decided_at: 2026-08-23
verification: certaine
enforced_by:
  - "BancDesClipsTest#une_classe_filmee_neuve_declare_son_banc"
verified:
  - by: machine:ci
    at: 2026-08-23
---

# Le garde d'abord, l'abstraction ensuite

## Contexte

Monter un scénario filmé demande huit gestes toujours identiques - espace de travail jetable,
injecteur, migrations, semis, connexion, chrome, fenêtre, ouverture - et quatre décisions qui varient
vraiment : la taille, l'exécuteur, ce qu'on remplace dans l'injecteur, l'écran d'ouverture.

Mesuré à la clôture de l'EPIC #4133 : **494 lignes de préambule pour cinquante cas**, et les trois plus
lourds de **47 à 69 lignes pour un seul cas**. Écrire un cas neuf revenait à recopier le préambule du
voisin, et [une copie hérite de la dette de son modèle](3960-un-garde-dit-la-couverture-qu-il-a-et-rend-l-etat-qu-il-emprunte.md).

Le point décisif est ailleurs. **`FenetreDuBanc` existait déjà**, et faisait exactement ce qu'il fallait
(ADR 4134). **Trois classes sur onze ne l'utilisaient pas** : elles avaient copié un préambule antérieur
à sa création, et rien ne les en empêchait. Deux tailles d'écran circulaient sans raison, et le clip
d'une classe pouvait être cadré par ce que la précédente avait laissé au `Stage` partagé.

## Décision

Quand un idiome de test se met à diverger, on pose **d'abord le garde**, puis l'abstraction qui
l'absorbe.

Une abstraction que rien n'impose est contournée par le prochain copier-coller - et le copier-coller est
précisément ce que l'abstraction prétendait supprimer. `FenetreDuBanc` en est la preuve : trois classes
l'ont ignorée pendant tout le temps où seule sa commodité la recommandait.

Le garde vient donc en premier, et l'abstraction arrive dans un dépôt où plus personne ne peut s'en
passer sans rougir.

## La forme du garde

**Une liste d'attente nommée et finie** quand la migration ne peut pas se faire d'un coup - une liste,
pas un interrupteur. Le garde rougit dès qu'elle s'allonge, la dette est comptée au lieu de se fondre
dans le vert, et une classe **neuve** ne peut pas repartir d'une copie. Chaque entrée dit pourquoi elle
attend.

**Un compteur d'inspection**, qui refuse zéro. Un garde vert qui n'a rien regardé est le faux vert le
plus difficile à voir : personne ne relit un test qui passe. Cf.
[ADR 2748](2748-un-dispositif-qui-peut-ne-rien-verifier-le-dit.md).

**Et il mesure la propriété, pas un proxy.** Le premier jet cherchait la chaîne `MainView.fxml` pour
décider qu'une classe montait le chrome. Il a déclaré fautive la première classe migrée sur
`BancDeRecette` - qui monte le chrome sans le nommer, puisque le banc le nomme pour elle. Un garde qui
mesure un proxy se trompe dès que le proxy change.

## Conséquences

La migration des dix classes restantes est un travail à part, visible et compté. Elle ne bloque pas la
livraison de l'abstraction, et personne ne peut l'oublier.

## Alternatives écartées

**L'abstraction seule.** Plus court tout de suite, et c'est exactement ce qui a échoué avec
`FenetreDuBanc`.

**Le garde seul.** La divergence cesse, le coût d'écriture reste - et c'est ce coût qui produit les
copies.

**Un interrupteur d'exemption** plutôt qu'une liste nommée. Une exclusion anonyme se pose une fois et ne
se retire jamais ; une liste de fichiers se lit, se compte, et rougit quand elle grossit.
