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

Cette compétence est distincte de `trier-les-issues` : le triage décide s'il y a lieu d'ouvrir, celle-ci dit
ce qu'il faut avoir posé avant de commencer, et refuse de démarrer tant que ce n'est pas fait.

## Annoncer

« J'utilise la compétence ouvrir-une-issue pour cadrer #N avant d'y toucher. »

## Fonction de garde

```
1. ENONCER  ce qu il y a a faire, en UNE phrase, dans les termes du PROBLEME
            et non de la solution.
2. DIRE     pourquoi maintenant : ce qui la rend traitable (un prerequis fusionne,
            une mesure qui vient de tomber) ou urgente (elle bloque autre chose).
3. SITUER   dans quelle continuite elle s inscrit : quelle issue elle suit, ce
            qu elle rend possible ensuite.
4. NOMMER   le CHANTIER auquel elle appartient, par son numero. Pas celui d ou elle
            vient : celui qui traite sa CAUSE. S il n existe pas, l ouvrir.
5. DEPOSER  ces quatre elements EN COMMENTAIRE sur l issue, avec le remede envisage.
6. RELIRE   a la grille de la competence humaniser ce qui part sur la forge. Le corps de l issue
            est de la prose publiee, et l article A31 le couvre depuis qu il ne
            declenche plus sur le commit.
7. ASSIGNER l issue a qui la prend.
```

Sauter l'étape 3, c'est produire un correctif isolé dont personne ne saura s'il a été fini. C'est
celle qu'on saute, et la seule qui ne se retrouve pas après coup.

## Le chantier s'écrit, il ne se sous-entend pas

**Venir d'un chantier n'est pas lui appartenir.** L'étape 4 demande un numéro parce qu'une phrase de
continuité se satisfait de trop peu : « elle sort du sas » est vrai, ne coûte rien, et laisse l'issue
sans rattachement.

Le [cycle de chantier](../../../dev-docs/cycle-de-chantier.md) tranche déjà où une issue **appartient**,
et cette page ne le redit pas : elle appartient au chantier qui traite sa cause, pas à celui qui a
remarqué son symptôme.

**Le sas des suites n'est pas un chantier.** On y **consigne** une trouvaille au moment où on la fait,
et c'est le bon geste. On n'y **prend** rien : son corps dit que rien ne s'y traite directement, et la
passe 9 le vide. Consigner et prendre sont deux moments, et le sas ne sert qu'au premier.

**Une issue de suite n'a donc pas de chantier par construction**, et c'est exactement le cas qui piège :
il faut en ouvrir un. Une suite qu'on prend cesse d'être une suite ; elle devient le premier lot de
quelque chose, et ce quelque chose se nomme avant d'écrire du code.

Mesuré le 27 août 2026 : trois sessions ont pris des issues sans chantier le même jour, et il a fallu
les reprendre une par une. Les blocs de #4571, #4554 et #4617 portaient tous `Pris par : sas des suites
#4562`. Ces trois issues formaient un chantier - un dispositif vert sans avoir jugé, sous trois formes -
qui n'a été ouvert qu'après coup, en #4650. Trois sessions au même endroit le même jour ne sont pas
trois inattentions.

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
lâche exactement quand ça va vite, c'est-à-dire quand les collisions arrivent. Il complète `trier-les-issues`,
il ne le remplace pas.

## Signaux d'alerte : on s'arrête

| Pensée | Réalité |
|---|---|
| « C'est évident, je commence » | Évident pour vous aujourd'hui, opaque pour qui relit dans trois semaines |
| « J'écrirai le pourquoi dans la PR » | La PR dit ce qui a été fait, pas ce qui rendait la chose traitable |
| « Je m'assigne, ça suffit » | L'assignation ne dit pas quel remède vous allez écrire |
| « Personne d'autre ne travaille dessus » | L'assignation est muette et le compte est partagé |
| « Je reprendrai plus tard, je garde l'assignation » | Une revendication oubliée est pire que rien |
