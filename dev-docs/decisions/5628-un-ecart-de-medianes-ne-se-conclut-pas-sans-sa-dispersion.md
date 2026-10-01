---
type: adr
title: "Un écart de médianes ne se conclut pas sans sa dispersion, et celle d'une médiane est robuste"
status: stable
article: A3
chantier: "#5628, lot du chantier #5592 (les suites de #5562 : ce que les instruments de coût couvrent)"
decided_at: 2026-10-01
verification: certaine
enforced_by:
  - ".github/scripts/mesure_duree_portail.py"
verified:
  - by: machine:ci
    at: 2026-10-01
relations:
  complete: ["3560-tourner-sans-conclure-a-trois-formes"]
generated:
  by: "process:assistance-par-agents"
  at: 2026-10-01
---

# Un écart de médianes ne se conclut pas sans sa dispersion, et celle d'une médiane est robuste

## Contexte

L'ADR 3560 a choisi **deux médianes glissantes** contre un butoir, sur mesure : une médiane ne se
déplace pas pour deux aberrantes sur douze, là où « médiane + 30 % » aurait crié deux fois sur
trente. Ce choix tient.

Il laisse une question entière : **de combien deux médianes doivent-elles différer pour que l'écart
veuille dire quelque chose ?** L'instrument comparait l'écart à un pourcentage - vingt - et un
pourcentage ne sait rien de la population qu'il résume.

Le 2026-09-30, `mutation-ihm.yml` a annoncé **+59 %**. Mesuré à corpus constant - même commit, donc
même code muté - l'écart des deux médianes valait **23,9 min pour une dispersion de 57,1**, soit
**0,42 dispersion**. Les deux fenêtres avaient la même dispersion, 79,3 contre 80,9 : rien n'avait
changé que le **tirage**. C'est aussi pourquoi l'annonce est passée de +24 % à +59 % en une journée,
la fenêtre glissante ayant absorbé une exécution longue et perdu une courte.

Un avertissement de cette espèce fait chercher une cause qui n'existe pas. C'est arrivé.

## La décision

**Un écart de médianes ne se conclut pas sans sa dispersion.** L'instrument refuse de conclure quand
l'écart est plus petit que la dispersion de ses propres fenêtres, et il **affiche** cette dispersion
dans tous les cas, y compris quand elle le laisse conclure.

**La dispersion d'une médiane est l'écart absolu médian**, et non l'écart-type. Deux mesures l'ont
imposé, contre ce que l'intuition proposait.

L'écart-type **groupé** sur les vingt-quatre exécutions inclut le changement de régime lui-même :
plus l'écart est réel, plus il gonfle. Aucun des dix ateliers mesurables n'atteignait une unité,
`release.yml` et ses +708 % compris. Un seuil là aurait rendu cet avertisseur **entièrement muet**.

Calculé **par fenêtre**, il reste trompé par une population **bimodale**. La fenêtre récente de
`release.yml` va de 1 à 50 min : son écart-type de 19,5 écrase un écart de 7,6 min qui est pourtant
un triplement. Une médiane résiste aux aberrantes ; sa dispersion doit y résister aussi.

**Le minimum vaut un.** Un écart égal à la dispersion est un écart aussi grand que l'écart habituel,
ce qui est le plancher pour le dire autre chose qu'un tirage. Et il est mesuré : sur les ateliers qui
atteignent cette règle le 2026-10-01, `recette-filmee.yml` est à **3,24** et les deux `mutation-*` à
**0,42** et **0,41**. Un est dans le seul trou de la distribution, 2,4 fois au-dessus du plus haut
des muets et 3,2 fois en dessous du seul parlant.

## Ce que la décision ne dit pas

**Elle ne remplace pas le seuil de vingt pour cent**, elle s'y ajoute : un écart doit être grand en
proportion **et** grand devant la dispersion. Les deux conditions répondent à deux questions
différentes, et une seule ne suffit pas.

**Elle ne juge pas une dispersion nulle.** Quand la moitié des exécutions ont la même durée à la
seconde près, un déplacement **est** un signal, et l'instrument conclut. Le cas n'a pas été observé -
la plus petite dispersion du dépôt vaut une seconde - mais la forge rend des secondes entières et un
job de moins de dix secondes peut l'atteindre.

## Conséquences

**Deux ateliers cessent d'avertir**, `mutation-ihm.yml` et `mutation-model.yml`, et c'est l'effet
voulu : leurs annonces étaient du bruit. `recette-filmee.yml` continue d'avertir.

**Le minimum est ajusté sur trois points et se périmera** si la distribution bouge. Il vit donc dans
une constante nommée, avec sa mesure et sa date, pour qu'une relecture voie le trou qu'elle suppose.

**Une première dérivation a été fausse**, et elle est écrite là où le chiffre vit. Un trou avait été
lu entre 0,42 et 1,01 et un minimum de 0,7 proposé, en comptant `release.yml` parmi les parlants. Il
n'atteint jamais cette règle : le refus des compositions non comparables l'arrête avant, ses fenêtres
mêlant `schedule` et `push`. Un seuil ajusté sur un cas que la règle ne juge pas est ajusté sur rien.

**C'est le troisième refus de cet instrument**, après l'historique illisible et les compositions non
comparables. Les deux premiers portaient sur ce qu'il **lit** ; celui-ci porte sur ce qu'il
**conclut**.
