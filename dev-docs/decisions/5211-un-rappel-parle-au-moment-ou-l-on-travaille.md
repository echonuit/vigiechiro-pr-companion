---
type: adr
title: "Un rappel parle au moment où l'on travaille, et un silence assumé sur une fausse raison est pire qu'un silence nu"
status: stable
article: A3
chantier: "#5211 (deux lots sur trois de #5202 avaient leur critère en commentaire), lot de l'EPIC #5584"
decided_at: 2026-10-01
verification: humaine
verification_note: "rien ne refuse, et c'est la décision de l'ADR 4992 que celle-ci garde : l'arbitrage de #4961 a écarté le rouge à l'unanimité de trois auditeurs. Ce qui change est le MOMENT du signalement, et deux loupes le portent. Aucune ne juge la qualité d'un critère, que l'ADR 5335 déclare non mécanisable"
loupe:
  - ".github/scripts/rappelle_le_critere_de_fin.py"
relations:
  amende: ["4992-le-critere-de-fin-se-rappelle-et-se-mesure-il-ne-se-refuse-pas"]
verified:
  - by: human:nedseb
    at: 2026-10-01
generated:
  by: "process:assistance-par-agents"
  at: 2026-10-01
---

# Un rappel parle au moment où l'on travaille, et un silence assumé sur une fausse raison est pire qu'un silence nu

## Contexte

L'[ADR 4992](4992-le-critere-de-fin-se-rappelle-et-se-mesure-il-ne-se-refuse-pas.md) pose deux
dispositifs pour que chaque lot dise dans son corps comment on saura qu'il est fini : un rappel à
l'ouverture de l'issue, et une loupe hebdomadaire. Aucun ne refuse, et trois auditeurs ont écarté le
rouge à l'unanimité.

Le rappel assume de se taire à tort ainsi, et c'est écrit dans son code :

> Signaler à tort coûte un commentaire inutile ; **se taire à tort ne coûte rien de plus**, la loupe
> hebdomadaire balayant le stock.

**La loupe ne balaie pas le stock.** Elle ne lit que les sous-issues `state == "OPEN"` et ne passe que
le lundi à 6 h UTC. Mesure du 2026-10-01, population complète, 441 issues closes lues sur 441
annoncées :

```
  lots clos nés depuis la règle          : 386
  dont la loupe a pu en voir UN passage  : 49  (13 %)
  dont elle n'a JAMAIS pu les voir       : 337 (87 %)
```

La cause est la durée de vie : **médiane 4,4 h** pour un lot, 97 % sous la semaine, contre-mesurée sur
deux tranches choisies par la date, 90 % pour les lots d'avant août et 96 % pour ceux de septembre.

Et le cas qui l'a fait trouver est celui de la session qui écrit ceci. Sur **#5684**, le rappel a
commenté, il a été ignoré, le lot a vécu quelques heures et la loupe ne pouvait pas le voir : il a été
fusionné et clos, **son critère n'existant nulle part**, ni dans le corps ni dans un commentaire.

## Décision

**Le rappel parle à deux moments** : à l'ouverture de l'issue, et à l'ouverture d'une demande qui
ferme le lot, où il **nomme** les lots muets. Le second existe parce que le premier ne repasse pas, et
qu'il arrive parfois avant que son auteur sache son critère.

**Et rien ne refuse, toujours pas.** L'arbitrage de #4961 visait « le rouge qui tombe sur qui n'a pas
la main », un garde jugeant un chantier ouvert des jours plus tôt par quelqu'un d'autre. Il a écarté le
**rouge**, pas le signalement. Un refus aurait d'ailleurs un coût mesuré : 59 des 289 lots rattachés
clos depuis la règle sont muets dans leur corps, soit 20 %, et un rouge qui tombe une fois sur cinq est
celui qu'on apprend à contourner.

**La portée se déclare désormais là où elle est lue.** Le mot qui manquait n'est pas dans la loupe, qui
fait ce qu'elle annonce, mais dans la **justification** d'un autre dispositif. Un silence assumé sur
une fausse raison est pire qu'un silence nu : il a l'air d'avoir été pesé, donc personne ne le
rouvre.

## Conséquences

Trois surfaces portent maintenant le chiffre plutôt que la phrase : la docstring du rappel, l'en-tête
de son atelier, et la ligne du tableau des gardes.

Le second rappel arrive au dernier moment où écrire le critère coûte peu. Après la fusion, la clôture
le relira et il sera trop tard.

**Ce que cette ADR ne prétend pas.** Elle ne ferme pas le trou, elle le déplace : un lot livré sans
demande de fusion, ou dont la demande ne déclare pas le fermer, échappe aux deux rappels. Elle ne juge
pas davantage la **qualité** d'un critère, ni sa **péremption** quand le remède change, que
l'[ADR 5335](5335-la-profondeur-d-une-passe-ne-se-mecanise-pas.md) déclare non mécanisables et que les
compétences `ouvrir-une-issue` et `clore-une-issue` portent en consigne.
