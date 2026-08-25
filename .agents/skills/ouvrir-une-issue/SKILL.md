---
name: ouvrir-une-issue
description: Use before writing the first line of code on an issue. Establishes what has to be measured, understood and announced first: what the issue does in problem terms, why now, what continuity it belongs to, and the public claim that prevents two people building two branches for the same defect.
license: GPL-3.0-or-later
metadata:
  langue: fr
  origine: dev-docs/cycle-de-chantier.md
---

# Ouvrir une issue

## Loi d'airain

```
PAS UNE LIGNE DE CODE AVANT QUE LE POURQUOI SOIT ÉCRIT SUR L'ISSUE
```

Cette compétence est distincte de `triage` : le triage décide s'il y a lieu d'ouvrir, celle-ci dit
ce qu'il faut avoir posé avant de commencer, et refuse de démarrer tant que ce n'est pas fait.

## Annoncer

« J'utilise la compétence ouvrir-une-issue pour cadrer #N avant d'y toucher. »

## Fonction de garde

```
1. ENONCER  ce qu il y a a faire, en UNE phrase, dans les termes du PROBLEME
            et non de la solution.
2. DIRE     pourquoi maintenant : ce qui la rend traitable (un prerequis fusionne,
            une mesure qui vient de tomber) ou urgente (elle bloque autre chose).
3. SITUER   dans quelle continuite elle s inscrit : de quel chantier elle vient,
            quelle issue elle suit, ce qu elle rend possible ensuite.
4. DEPOSER  ces trois phrases EN COMMENTAIRE sur l issue, avec le remede envisage.
5. RELIRE   a la grille humaniseur ce qui part sur la forge. Le corps de l issue
            est de la prose publiee, et l article A31 le couvre depuis qu il ne
            declenche plus sur le commit.
6. ASSIGNER l issue a qui la prend.
```

Sauter l'étape 3, c'est produire un correctif isolé dont personne ne saura s'il a été fini. C'est
celle qu'on saute, et la seule qui ne se retrouve pas après coup.

## Pourquoi le commentaire **et** l'assignation, pas l'un ou l'autre

| Dispositif | Ce qu'il porte | Ce qu'il ne dit pas |
|---|---|---|
| L'assignation | le signal machine : `gh issue list --assignee "*"` répond en une commande | ni le chantier, ni la branche, ni le remède |
| Le commentaire | le chantier, la branche, et **le remède envisagé** | il oblige à ouvrir chaque issue pour savoir |

Le remède annoncé est le vrai gain, et il dépasse la réservation. Deux personnes peuvent voir le
même défaut et imaginer deux corrections dont l'une est meilleure. Annoncées, le désaccord se règle
**avant** le code. Sinon il se règle au moment de choisir laquelle des deux branches on jette.

## Relâcher son signalement

Quand on s'arrête, reporté, bloqué ou abandonné : **on retire l'assignation et on le dit**.

Une revendication oubliée depuis trois semaines est **pire que rien** : elle fait passer une issue
libre pour prise, et personne ne la reprendra.

## Ce que le signalement ne couvre pas

Il répond à « cette issue est-elle prise ? ». Il ne répond **pas** à « cette issue est-elle la même
que celle-là, sous d'autres mots ? ». C'est cette seconde question qui a produit le doublon le plus
coûteux du dépôt : deux issues sur le même sujet, écrites depuis deux angles, ne se ressemblent pas,
et aucune n'était assignée.

Le signalement est un **filet**, pas une garantie. Il repose sur la discipline, et la discipline
lâche exactement quand ça va vite, c'est-à-dire quand les collisions arrivent. Il complète `triage`,
il ne le remplace pas.

## Signaux d'alerte : on s'arrête

| Pensée | Réalité |
|---|---|
| « C'est évident, je commence » | Évident pour vous aujourd'hui, opaque pour qui relit dans trois semaines |
| « J'écrirai le pourquoi dans la PR » | La PR dit ce qui a été fait, pas ce qui rendait la chose traitable |
| « Je m'assigne, ça suffit » | L'assignation ne dit pas quel remède vous allez écrire |
| « Personne d'autre ne travaille dessus » | L'assignation est muette et le compte est partagé |
| « Je reprendrai plus tard, je garde l'assignation » | Une revendication oubliée est pire que rien |
