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
| « C'était la dernière issue de l'EPIC, il est donc clos » | Fermer les issues n'est pas clore le chantier. Les treize passes portent sur le **delta**, pas sur les tickets, et 43 EPIC clos sur 64 n'en portaient aucune trace (#4659). La commande est `/clore` |
