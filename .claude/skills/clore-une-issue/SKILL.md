---
name: clore-une-issue
description: Use after the pull request is merged, to put the issue right and close it. What survives the closed tab is the issue body; this skill states why it carries truth while the comments carry the journal, and the cold-read test it must pass before being closed.
license: GPL-3.0-or-later
metadata:
  langue: fr
  origine: dev-docs/cycle-de-chantier.md
---

# Clore une issue

## Loi d'airain

```
LE CORPS PORTE LA VÉRITÉ, LES COMMENTAIRES PORTENT LE JOURNAL
```

Tout commentaire qui change la lecture de l'issue est **suivi d'une édition du corps**. Le
commentaire reste comme trace, le corps porte la conclusion.

## Annoncer

« J'utilise la compétence clore-une-issue pour mettre #N au net avant fusion. »

## Ce qui survit à la fermeture de l'onglet

Deux textes, relus dans six mois **sans le fil** : le corps de l'issue, et celui de la pull request.

| Support | Ce qu'il porte |
|---|---|
| Le corps de l'issue | l'**état courant de la vérité** : ce qu'on fait, pourquoi, ce qui a été décidé |
| Les commentaires | le **journal** : mesures, trouvailles incidentes, pistes essayées |
| Le titre de la PR | le **sujet du commit de squash**, donc la ligne du CHANGELOG |
| Le corps de la PR | ce qu'atteint quiconque remonte depuis `git log` |

## Fonction de garde

```
1. RELIRE   le corps de l issue. La premisse d ouverture est-elle toujours vraie ?
2. EDITER   le corps partout ou un commentaire l a contredit. Ne pas se contenter
            d avoir corrige en commentaire.
3. PASSER   le corps a la grille de la competence humaniser. Il est publie et non commis,
            et l article A31 le couvre depuis qu il declenche sur la publication.
4. LIRE A FROID, comme quelqu un qui n a pas suivi.
5. FERMER   l issue, et cocher son lot dans l EPIC.
```

**Le corps de la PR ne se relit pas ici** : il se met au net dans
[`ouvrir-une-pr`](../ouvrir-une-pr/SKILL.md), qui le porte avec le titre, et se corrige dans
[`clore-une-pr`](../clore-une-pr/SKILL.md) quand le travail a grossi.

Elle vient **après** [`clore-une-pr`](../clore-une-pr/SKILL.md) : la PR fusionnée, l'issue se met au
net et se ferme. Quand elle était la dernière d'un chantier,
[`clore-un-chantier`](../clore-un-chantier/SKILL.md) prend la suite.

## Si l'issue réalisait une tâche, le plan se confronte au livré

L'étape 1 demande si la prémisse d'ouverture tient encore. Quand l'issue réalisait la tâche d'un
changement OpenSpec, la même question se pose au plan : **la tâche décrit-elle ce qui a été livré ?**

Trois réponses, et une seule est le cas courant.

| Ce qu'on trouve | Ce qu'on fait |
|---|---|
| la tâche décrit le livré | elle est cochée depuis la branche, il n'y a rien à faire |
| le livré a dépassé le plan | le plan suit, par [`openspec-update-change`](../openspec-update-change/SKILL.md) |
| la tâche n'est pas cochée | la cocher maintenant, et se demander pourquoi elle ne l'a pas été |

**Ce n'est pas un quatrième déclencheur de la révision**, qui en a trois et qui viennent tous du
milieu du chantier. C'est le dernier endroit où l'on vérifie que ces trois-là ont été honorés, et le
dernier où l'écart coûte encore peu.

**Ce que coûte de ne pas regarder, mesuré.** Une révision de `emporter-une-nuit` avait écarté la
régénération d'une sélection reçue. **Trois artefacts** ont continué de la supposer, dont la tâche
5.2, et aucun garde ne les a vus : il a fallu relire. Un seul aurait suffi à faire archiver une
spécification que le code dément.

## Deux dettes réelles, laissées dans le dépôt

Une prémisse fausse, une mesure de mutation lue comme « ce code est atteignable », a été corrigée
**en commentaire**, le corps gardant la version fausse. Une mesure erronée a connu le même sort sur
une autre issue.

Qui ouvre ces issues aujourd'hui lit **d'abord l'erreur**, et la correction ensuite, s'il descend
jusque-là.

## Le test de lecture à froid

**Le corps de l'issue se lit-il correctement dans six mois, sans la discussion ?**

Ce n'est pas de la cosmétique. C'est la seule trace qui survive.

## Signaux d'alerte : on s'arrête

| Pensée | Réalité |
|---|---|
| « J'ai corrigé ça en commentaire » | Qui lit en diagonale retient la première version |
| « Le corps est un peu périmé, ce n'est pas grave » | C'est ce que lira le repreneur |
| « Le fil explique tout » | Le fil disparaît. Le corps reste |
| « C'était la dernière issue de l'EPIC, il est donc clos » | Fermer les issues n'est pas clore le chantier. Les quatorze passes portent sur le **delta**, pas sur les tickets, et 43 EPIC clos sur 64 n'en portaient aucune trace (#4659). La commande est `/clore` |
