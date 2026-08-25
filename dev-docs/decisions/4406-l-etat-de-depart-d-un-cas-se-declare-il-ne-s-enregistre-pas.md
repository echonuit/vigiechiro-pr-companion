---
type: adr
title: "L'état de départ d'un cas se déclare, il ne s'enregistre pas"
status: stable
article: A4
chantier: "#4406 (EPIC #4386)"
decided_at: 2026-08-25
verification: humaine
verified:
  - by: humain
    at: 2026-08-25
relations:
  prolonge: ["4142", "4291"]
---

# L'état de départ d'un cas se déclare, il ne s'enregistre pas

## Contexte

La session de recette `S8` compte trente-huit cas. Dix-sept d'entre eux ne décrivent pas une
récupération : ils décrivent **l'écran d'après**. Une pastille violette plutôt que bleue, un bouton
grisé et le motif qu'il affiche, une case remplie sur Ma saison, une vue qui range une nuit avec les
autres plutôt qu'après elles.

Aucun de ces dix-sept n'interroge la plateforme. Elle n'est nécessaire qu'une fois, pour amener la
base locale dans l'état « une nuit a été rapatriée ».

On a d'abord cru qu'un seul préambule les porterait tous. La mesure dit le contraire :

```java
public Injector montrer(Stage stage) throws IOException {
    Path espace = Files.createTempDirectory("vc-banc");
```

`BancDeRecette#montrer` est appelé **une fois par scénario**, et crée un espace de travail neuf à
chaque fois. Il n'existe donc aucun préambule partagé : les dix-sept paieraient dix-sept
récupérations réelles, chacune sur des données vivantes, chacune consommant un tir et un jeton que la
révocation de fin de tournage oblige à reposer à la main.

## Décision

**L'état de départ d'un cas filmé se décrit dans un fichier lisible, et un générateur déterministe le
matérialise. Il ne se capture pas depuis une exécution réelle.**

Ce n'est pas une invention : c'est la décision que ce dépôt a déjà prise pour les cartes SD, et pour
les mêmes raisons. `dev-docs/recette/fixtures.md` :

> Les cartes SD de recette pesaient plusieurs centaines de méga-octets et étaient **faites à la
> main** : impossibles à committer et **non rejouables**. On les décrit désormais par des **specs
> déclaratives** qu'un générateur déterministe matérialise. La spec est la source de vérité ; l'arbre
> SD n'en est qu'un artefact reconstructible **octet pour octet**.

Un enregistrement de base est exactement ce que ce dépôt a abandonné, un cran plus loin dans la
chaîne.

## La tension avec l'article A4, qu'il faut regarder en face

A4 dit : *« Ce qui se voit se contrôle sur ce que le **produit** rend, pas sur ce que le test a
reconstruit. »* Une base engendrée **est** reconstruite par le test. On peut donc lire cette décision
comme une entorse à l'article qu'elle invoque, et quelqu'un le fera.

La distinction est celle de l'**entrée** et de la **sortie**.

A4 interdit de contrôler ce qui se voit sur une reconstruction : asserter contre un modèle que le
test a bâti en parallèle du produit, c'est se répondre à soi-même. Il n'interdit pas de **préparer une
entrée**. Une carte SD engendrée est une entrée : ce que la recette regarde ensuite est ce que
`ServiceImport` en fait, et personne ne prétend qu'une carte engendrée prouve le comportement d'un
enregistreur.

Une base de départ engendrée occupe la même place. Ce que les dix-sept cas regardent reste **rendu par
le produit** : la fiche, ses boutons, leurs motifs, les vues qui la rangent. Rien n'est reconstruit du
côté de l'observation.

La frontière n'est pas négociable dans l'autre sens : le jour où un cas asserterait sur le contenu
de la spec plutôt que sur l'écran, il tomberait sous A4 et devrait être refusé.

## Et l'ADR 4142 n'y fait pas obstacle

[ADR 4142](4142-un-cas-dit-ou-se-lit-son-verdict.md) condamne un clip tourné contre
un bouchon comme **preuve du comportement de la plateforme** : le bouchon invente la réponse, et le
clip donne l'illusion d'avoir éprouvé un contrat qu'il n'a pas touché.

Ces dix-sept cas ne prouvent rien de la plateforme, et ne le prétendent pas. Ils prouvent ce que
l'écran local montre une fois la nuit là. C'est pourquoi ils ne demandent pas de tournage connecté,
alors que les sept cas de l'avancement - la barre qui progresse, l'estimation, le bouton **Annuler**
atteignable pendant l'opération - en demandent un : ceux-là regardent une opération **qui tourne**, pas
son résultat.

## Ce que la décision fait disparaître

La question qui avait ouvert l'issue - *comment remarque-t-on qu'un enregistrement a vieilli ?* -
n'a plus d'objet. Elle était propre à l'enregistrement : un fichier binaire figé s'éloigne en silence
de ce que le produit fabrique, et rien ne le dit.

Une spec ne vieillit pas de la même façon. Elle se lit, elle se versionne, et surtout la
correspondance avec la réalité ne se vérifie pas en la comparant à la plateforme : c'est le **code de
récupération** qu'on fait tourner contre elle, sur le patron du garde des cartes SD.

> Le bloc `attendu` de chaque spec est un **contrat** vérifié contre le **code réel**, pas contre une
> liste tenue à la main.

Le jour où la plateforme change de forme, ce n'est pas la spec qui doit s'en apercevoir : c'est le
contrat live d'`api-live.yml`, qui existe et tourne chaque semaine.

## Conséquences

- Les dix-sept cas cessent de dépendre d'un jeton, du réseau et de l'état vivant d'un compte. Leurs
  clips deviennent **déterministes**, donc comparables comme les autres - ce que
  [ADR 4291](4291-un-clip-tourne-contre-la-plateforme-ne-se-range-pas-avec-les-autres.md) refuse
  précisément aux clips connectés.
- Trois cas de plus - le rattrapage par « Compléter une nuit récupérée » - n'attendaient déjà rien :
  une nuit « sans contenu » est un **squelette local**, `ServiceReconstructionPassages#aReconstruire`
  le dit, et le banc l'atteint seul.
- Il reste à écrire le générateur et sa spec. C'est le travail de #4325, pas de cette décision.

## Vérification

`humaine`, et le motif est daté : **le générateur n'existe pas encore**. Une décision se prend quand
la question se pose, pas quand l'outil est prêt, et déclarer `certaine` en nommant un test à écrire
serait exactement l'affirmation sans preuve que l'article A1 refuse.

Elle deviendra `certaine` le jour où le générateur portera son cliquet, sur le modèle de
`GenerationCartesSDCliquetTest` : pour chaque spec, engendrer la base puis vérifier que le **code réel**
y constate l'état déclaré.

Ce qu'aucun garde ne tiendra, et qui restera une affaire de relecture : la frontière entrée/sortie
énoncée plus haut. Un cas qui asserterait sur la spec au lieu de l'écran passerait au vert, et seul un
lecteur peut le refuser.
