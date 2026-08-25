---
name: clore-une-issue
description: Use before merging, when finishing an issue. What survives the closed tab is the issue body and the pull request body; this skill states which of the two carries truth, which carries the journal, and the cold-read test that gates the merge.
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
3. RELIRE   le corps de la PR : dit-il ce qui a ete fait et pourquoi, sans retracer
            les hesitations ?
4. VERIFIER le titre de la PR : il devient le sujet du squash.
5. PASSER   les deux corps a la grille humaniseur. Ils sont publies et non commis,
            et l article A31 les couvre depuis qu il declenche sur la publication.
6. LIRE A FROID les deux, comme quelqu un qui n a pas suivi.
```

## Deux dettes réelles, laissées dans le dépôt

Une prémisse fausse, une mesure de mutation lue comme « ce code est atteignable », a été corrigée
**en commentaire**, le corps gardant la version fausse. Une mesure erronée a connu le même sort sur
une autre issue.

Qui ouvre ces issues aujourd'hui lit **d'abord l'erreur**, et la correction ensuite, s'il descend
jusque-là.

## Le test de lecture à froid

**Le corps de la PR et celui de l'issue se lisent-ils correctement dans six mois, sans la
discussion ?**

Ce n'est pas de la cosmétique. C'est la seule trace qui survive.

## Signaux d'alerte : on s'arrête

| Pensée | Réalité |
|---|---|
| « J'ai corrigé ça en commentaire » | Qui lit en diagonale retient la première version |
| « Le corps est un peu périmé, ce n'est pas grave » | C'est ce que lira le repreneur |
| « Le fil explique tout » | Le fil disparaît. Le corps reste |
| « Le titre de la PR, on s'en fiche » | Il devient la ligne du CHANGELOG, que la typographie ne rattrape pas après coup |
| « La typographie du corps, ça n'engage rien » | `corps-pr.yml` refuse le cadratin, l'apostrophe courbe et l'élision sans apostrophe. Le reste de la grille est à vous, et ce corps est publié dès qu'il part |
