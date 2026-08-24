---
name: tdd
description: Use when implementing any feature or fix, before writing implementation code. The red-green-refactor loop at the right granularity, why the step must be small, the signals that it was too big, and where the loop's refactor stops and the closure harmonisation pass begins.
license: GPL-3.0-or-later
metadata:
  langue: fr
  origine: dev-docs/cycle-de-chantier.md
---

# TDD

## Loi d'airain

```
UNE ISSUE = N TOURS. PAS UN TOUR AVEC N ASSERTIONS DEDANS.
```

Rouge, vert, refactor n'est pas une liste qu'on parcourt une fois par issue. C'est un **tour**, et
une issue en compte autant que de petits pas : quelques minutes chacun, souvent plusieurs dizaines
avant que le comportement soit complet.

## Annoncer

« J'utilise la compétence tdd pour <le comportement>, pas à pas. »

## Ce qu'est un petit pas

**Le plus petit comportement observable** qu'on puisse rendre rouge puis vert. Pas « la
fonctionnalité », pas « la classe », pas « la méthode ».

- le refus quand la date manque ;
- la conversion d'une borne quand le point est connu ;
- le cas où la liste est vide.

## Fonction de garde, à chaque tour

```
1. ECRIRE   le test du pas courant, et LE VOIR ROUGE.
2. ECRIRE   le MINIMUM qui le rend vert.
3. REGARDER ce qu il y a a retravailler. Systematiquement, meme si on n applique rien.
4. RECOMMENCER avec le pas suivant.
```

Sauter l'étape 1, c'est écrire un test qui décrit ce que le code fait déjà. Sauter l'étape 3
« parce qu'on y reviendra » est la façon habituelle de ne jamais y revenir.

## Pourquoi le pas doit être petit

- **Le diagnostic est gratuit.** Quand un pas casse, la cause est dans les trois lignes qu'on vient
  d'écrire. Sur un grand pas, elle est quelque part dans une heure de travail.
- **Le refactor devient possible.** On ne retravaille sereinement qu'un code couvert : chaque tour
  élargit le filet sous les pas suivants. Un refactoring tenté après coup, sur du code écrit d'un
  bloc, se fait sans filet, c'est-à-dire qu'il ne se fait pas.
- **Le pas suivant se choisit en connaissance de cause.** Le vert précédent apprend quelque chose
  sur le domaine, et il arrive qu'il démente le plan.

## Les deux signaux que le pas était mal taillé

| Signal | Ce qu'il dit | Geste |
|---|---|---|
| **Le rouge dure.** Plus de quelques minutes, ou plusieurs classes à écrire pour revenir au vert | le pas était trop gros | revenir au dernier vert, couper le pas en deux |
| **Le vert du premier coup**, sans rien écrire | le test ne testait rien de neuf | choisir un vrai pas suivant |

Rester longtemps en rouge fait perdre ce que la boucle apporte : on retombe dans « j'écris tout, je
teste après », avec un test écrit avant en guise d'alibi.

## Le rouge d'abord, parce qu'il ne coûte rien à ce moment-là

Le dépôt tient déjà la moitié de cette règle sous un autre nom : un garde-fou se vérifie en le
voyant rouge. Le rouge du TDD est cette même exigence, déplacée **avant** le code, là où elle est
gratuite.

Après coup, elle se paie. Une règle d'architecture a dû être vérifiée en **réintroduisant le défaut
à la main**, en relançant, puis en restaurant : trois gestes, un risque d'oublier le dernier, et une
confiance qui repose sur le fait qu'on ait bien tout remis en place. Écrite d'abord, elle était
rouge sans cérémonie.

## Un rouge inattendu est une trouvaille

Un test qui échoue **pour une autre raison que celle qu'on attendait** vient de dire quelque chose.
Le réflexe est de corriger jusqu'au vert ; le bon geste est de **lire le message avant de
corriger**.

Vécu : un test qui figeait trois états capturés annonçait « ~10 s » là où on attendait « ~17 s ». La
cause n'était pas dans le correctif mais dans la **monotonie d'une fraction** : une fraction plus
basse posée après une plus haute ne redescend pas, et l'estimation se calcule donc sur l'ancienne.
La capture était juste par accident de séquence, et rien ne le disait.

## REFACTOR : à chaque tour, et à la bonne échelle

Ce qui se regarde à ce moment-là est **petit** : un nom qui ne dit pas ce que fait la méthode, une
duplication apparue entre le pas précédent et celui-ci, une condition qui gagnerait à être nommée.
Ce qui déborde du pas courant se note et revient à l'harmonisation de clôture.

| | REFACTOR du tour | Passe d'harmonisation |
|---|---|---|
| Portée | le code qu'on vient de toucher | l'application entière |
| Moment | à chaque barre verte | à la clôture du chantier |
| Filet | le test qu'on vient de rendre vert | la suite complète |
| Décision | seul | discutée |

Sans cette frontière, l'une des deux règles cède à l'autre.

**Ce que le tour attrape et que l'harmonisation attrape mal** : un plafond PMD qui mord *pendant*
l'issue force une extraction qui porte un **concept nommé**, parce qu'elle est écrite par quelqu'un
qui a encore la raison en tête. L'harmonisation aurait produit la même extraction, au bon endroit,
sous un nom quelconque.

## Signaux d'alerte : on s'arrête

| Pensée | Réalité |
|---|---|
| « J'écris la fonctionnalité, puis les tests » | C'est l'ordre inverse, et le test devient un alibi |
| « Un seul test avec toutes les assertions » | Un tour par comportement observable |
| « Je suis en rouge depuis vingt minutes » | Le pas était trop gros. Revenir au dernier vert |
| « Le test est passé du premier coup » | Il ne testait rien de neuf |
| « Je refactorerai à la fin » | Le moment le moins cher dure un tour |
| « Ce rouge n'est pas celui que j'attendais, je corrige » | Lisez le message. Il vient de dire quelque chose |
