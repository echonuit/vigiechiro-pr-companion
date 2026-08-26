---
type: adr
title: "Une dette qu'on migre au fil de l'eau se tient par un cliquet"
status: stable
article: A9
chantier: "#2867 (axe 5 du chantier #1771)"
decided_at: 2026-07-30
verification: certaine
enforced_by:
  - "PatronDuCliquetTest#tout_cliquet_passe_par_le_patron"
verified:
  - by: machine:ci
    at: 2026-07-30
---

# Une dette qu'on migre au fil de l'eau se tient par un cliquet

## Contexte

Le dépôt migre rarement en une fois. Une brique canonique apparaît (une fixture, un writer, un harnais),
et les copies existantes basculent **quand on les retouche**. C'est le bon rythme : une conversion
mécanique en masse est risquée, et un test converti trop vite est un test que plus personne ne relit.

Mais une migration opportuniste sans compteur est une migration qu'on oublie. Le chantier #1771 en donne
la démonstration chiffrée : à son ouverture, sur quatre dettes de fixtures identifiées, **la seule qui
avait reculé depuis l'audit initial était la seule équipée d'un compteur**. Les trois autres avaient
grossi.

> Ce qui n'est pas compté grandit.

Le dépôt possédait déjà plusieurs dispositifs qui font ce travail sous des noms différents :
`CliquetFixturePassageTest`, [`DocumentationAJourTest`](2385-la-doc-chiffree-est-adossee-au-code.md), le
cliquet typographique de l'[ADR 2843](2843-typographie-cliquet-plutot-que-nettoyage.md), les cliquets
d'ADR de l'[ADR 2465](2465-une-adr-declare-comment-elle-est-verifiee.md). Ils partagent une forme, des
pièges et un message ; rien ne le disait.

## Décision

**Une dette qu'on migre au fil de l'eau est épinglée dans une liste explicite, et cette liste ne peut que
rétrécir.** Le patron s'appelle un **cliquet**, il vit dans `fr.univ_amu.iut.cliquet.Cliquet`, et tout
dispositif de ce genre passe par lui.

Quatre règles en découlent.

### 1. Les deux sens de variation sont rouges, et le message les distingue

La liste **s'allonge** : une copie de plus vient d'apparaître, le message renvoie vers la brique
canonique. C'est le cas qui compte le plus, car sans lui la dette repousse aussi vite qu'on la coupe.

La liste **raccourcit** : deux causes possibles qui ne se valent pas. Soit une migration a abouti, et
retirer le nom est le geste qui rend le progrès visible. Soit le **détecteur** a changé, et la liste
raccourcit sans qu'une ligne de code ait bougé : ce n'est pas un progrès, c'est une correction de la
mesure, et elle se dit comme telle. Un message qui ne distingue pas les deux fait passer l'un pour
l'autre.

Corollaire pratique : le nombre restant est **toujours exact**, et personne n'a besoin de s'en souvenir.
Un commentaire qui annonce un chiffre est déjà faux (celui du premier cliquet annonçait 64 pour 50).

### 2. La destination de la migration est exclue, et l'exclusion est écrite

La brique canonique fait, par métier, exactement ce que le détecteur cherche : la fixture sème, le harnais
capture, le journal partagé écrit un journal. La compter serait absurde.

**L'exclusion manque toujours au début**, et pour une raison structurelle : au moment où le cliquet est
posé, la brique **n'existe pas encore**. Rien ne rappelle d'y penser le jour où elle naît. C'est arrivé
trois fois de suite (#2714, #2904, #2909), la troisième fois en connaissant les deux précédentes.

La règle est donc : **le paquet de destination est exclu dès la pose du cliquet**, même quand la brique
n'existe pas, et l'exclusion est écrite plutôt que laissée à un effet de bord. Une exclusion qui tient
« parce que le fichier n'a pas de `@BeforeEach` » ne tient qu'à un cheveu.

### 3. Pas de court-circuit : un objet compte tant qu'il lui reste une occurrence

Dès qu'un détecteur cesse de regarder un objet parce qu'il le croit « déjà traité », il devient aveugle
exactement sur ce qui est **en cours** de migration, c'est-à-dire là où il devrait parler. Son silence se
lit alors comme un accord.

Le premier cliquet s'arrêtait au premier `JeuDeDonneesPassage` rencontré : un fichier **partiellement**
migré sortait de la liste, avec le semis à la main qui y restait (#2714).

### 4. Usage et mention ne sont pas la même chose

Citer une brique ne prouve rien. Le même cliquet comptait `new PassageDao(source).findAll()` comme un
semis : une **lecture** comptée comme une écriture, et onze fichiers maintenus dans une liste où ils
n'avaient plus leur place. Un cliquet qui surcompte se décrédibilise aussi sûrement qu'un qui sous-compte.

Le pendant du détecteur trop large est le détecteur trop **littéral** : celui des captures cherchait la
chaîne `new ByteArrayOutputStream` et ne voyait pas `new java.io.ByteArrayOutputStream(...)`. Il a été
trouvé par une sonde qui vérifiait qu'il voyait bien apparaître une copie ; il ne la voyait pas.

## Conséquences

- **La valeur par défaut ne ment pas quand on l'oublie** (#2833) : un objet est **hors** du dispositif
  tant qu'on ne l'y met pas, jamais l'inverse. Un cliquet qui compte par défaut se remplit tout seul de
  faux positifs et finit désarmé ; un dispositif qui **exclut** par défaut se tait sur ce qu'il ignore.
- **Un cliquet se vérifie comme un test se vérifie** : en le voyant rouge. Un détecteur qu'on n'a jamais
  vu rougir sur une mutation n'est pas un garde-fou, c'est une décoration. Cette ADR a été écrite après
  qu'un « les cinq cliquets sont verts » a été annoncé sur une commande dont la branche d'échec
  n'affichait rien.
- **Le patron ne prétend pas mutualiser du code.** Les détecteurs inspectent des choses trop
  différentes : ce qui est partagé, c'est le balayage, le message et les règles ci-dessus. Le
  reste - « qu'est-ce qui compte ? » - est propre à chaque dette et doit rester lisible.
- **Le cliquet borne, il ne prouve pas.** Il dit qu'une dette ne grossit pas ; il ne dit pas qu'elle est
  petite. Un cliquet dont la liste ne bouge plus depuis six mois est un chantier abandonné qui se croit
  tenu.
- Le niveau `probable` de l'[ADR 2465](2465-une-adr-declare-comment-elle-est-verifiee.md) est
  **exactement ce patron** appliqué aux suspects d'un script : « le signal utile n'est pas zéro mais
  aucun nouveau ». Cette ADR nomme ce que 2465 pratiquait déjà.

## Alternatives écartées

**Interdire d'emblée la forme dépréciée** (échec du build sur toute occurrence). Ce serait exact et
inapplicable : la dette existe déjà, à cinquante ou cent exemplaires. Un garde-fou qu'on doit désarmer
pour travailler ne survit pas à sa première semaine.

**Compter sans épingler** (un seuil numérique : « pas plus de 24 »). Le nombre passe, mais rien ne dit
**lesquels**, et un fichier migré peut être remplacé par un fichier neuf sans que le compteur bouge. La
liste nominative est ce qui rend la substitution visible.

**Un rapport hebdomadaire non bloquant.** Déjà tenté ailleurs dans le dépôt : ce qui ne bloque pas ne se
lit pas.
