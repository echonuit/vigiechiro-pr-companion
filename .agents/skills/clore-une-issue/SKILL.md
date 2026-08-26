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
4. LANCER   .github/scripts/verifie-titre-pr.sh "<le titre>" AVANT gh pr create.
            Le titre devient le sujet du squash, et le garde de CI ne mord
            qu une fois la PR ouverte.
5. PASSER   les deux corps a la grille de la competence humaniser. Ils sont publies et non commis,
            et l article A31 les couvre depuis qu il declenche sur la publication.
6. LIRE A FROID les deux, comme quelqu un qui n a pas suivi.
```

## Deux dettes réelles, laissées dans le dépôt

Une prémisse fausse, une mesure de mutation lue comme « ce code est atteignable », a été corrigée
**en commentaire**, le corps gardant la version fausse. Une mesure erronée a connu le même sort sur
une autre issue.

Qui ouvre ces issues aujourd'hui lit **d'abord l'erreur**, et la correction ensuite, s'il descend
jusque-là.

## Le titre se vérifie avant l'ouverture, pas après

`titre-pr.yml` refuse un titre non conforme, et il le fait bien : les 100 dernières PR fusionnées
suivent toutes la convention. Il ne peut mordre qu'une fois la PR ouverte, et cela coûte une PR à
ré-éditer puis une vérification à relancer. Le même script tourne en local, sur la chaîne que vous
vous apprêtez à taper :

```bash
./.github/scripts/verifie-titre-pr.sh "fix(passage): le pivot se relit"
```

Le défaut n'entre pas au commit. Les quatre PR rouges du 2026-08-26, #4570, #4588, #4589 et #4591,
partaient toutes d'une branche aux sujets de commit conformes, et 286 des 297 sujets de branche
hors `main` le sont aussi. Trois de ces quatre titres sont le sujet du commit retapé avec ses
accents, le quatrième est le titre de l'issue #4574 recopié tel quel. Le défaut entre à la frappe
du titre : en écrivant du français correct, la main applique la typographie française, et l'espace
avant le deux-points que Conventional Commits interdit vient avec.

La règle est dans `CONTRIBUTING.md` depuis longtemps, avec sa raison et son coût. Les quatre PR
l'ont manquée le même jour. Le script, lui, rend un verdict.

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
| « Je relis le titre, ça suffit » | Quatre titres relus ont rougi le même jour. Le script rend un verdict, la relecture rend un avis |
| « La typographie du corps, ça n'engage rien » | `corps-pr.yml` refuse le cadratin, l'apostrophe courbe et l'élision sans apostrophe. Le reste de la grille est à vous, et ce corps est publié dès qu'il part |
