---
type: adr
title: "Une question de prose se pose aux énoncés de la couche, à côté du moteur"
status: stable
article: A3
chantier: "#5939 (une question en langage courant n'atteignait pas la prose), lot 10 de #5812"
decided_at: 2026-10-05
verification: certaine
enforced_by:
  - "scripts/graphify/couche_semantique.py"
verified:
  - by: machine:ci
    at: 2026-10-05
relations:
  complete: ["5790-le-graphe-lit-la-prose-d-un-perimetre-declare"]
generated:
  by: "process:assistance-par-agents"
---

# Une question de prose se pose aux énoncés de la couche, à côté du moteur

## Le contexte

L'ADR 5790 a fait entrer la prose dans le graphe : des énoncés, chacun avec sa justification et
sa page. `AGENTS.md` prescrit d'interroger le graphe avant tout autre outil, par `graphify query`.

Personne n'avait regardé par où une question atteint ces énoncés. Le moteur choisit ses points de
départ parmi les libellés qui ressemblent aux mots de la question, puis parcourt le graphe depuis
eux. Un symbole de code au libellé exact capte le départ, et la justification d'un énoncé, qui
porte le pourquoi, n'est pas lue du tout.

## Ce qui a été mesuré

Le 5 octobre 2026, sur le graphe de référence, sept questions posées en langage courant :

| | `graphify query` | `cherche` |
|---|---:|---:|
| reçoivent de la prose qui touche au sujet | 5 | 7 |
| dont la page attendue, nommée d'avance, parmi les cinq premiers énoncés | non mesuré | 6 |
| ne reçoivent aucune prose | 1 | 0 |

La question sans réponse était « pourquoi le dépôt se fait en WAV par défaut plutôt qu'en ZIP ».
`WAV` et `ZIP` sont deux constantes Java, et la couche portait plus de trente énoncés sur le
sujet. `cherche` y rend la décision au troisième rang.

La septième question de `cherche`, « quelles pages décrivent le dépôt en archive ? », rend la
page attendue au huitième rang. Ses cinq premiers énoncés parlent du dépôt en archive, depuis la
maquette du même écran et depuis trois décisions.

## La décision

**Une question sur un pourquoi ou sur une règle se pose aux énoncés, par une commande du dépôt,
`couche_semantique.py cherche`. Elle se lance à côté de `graphify query`, pas à sa place.** Le
moteur reste l'outil pour aller d'un nœud à ses voisins, et pour le code.

Seuls les énoncés sont candidats, jamais un symbole de code. La commande compare les mots de la
question à ceux du libellé et de la justification, sans accent ni casse.

**C'est la présence d'un mot qui note, pas sa répétition.** Un mot du libellé compte double, un
mot rare pèse plus qu'un mot courant, et à note égale le plus court passe devant. Une première
notation comptait les répétitions : une longue justification qui redisait « dépôt » passait
devant l'énoncé dont le libellé portait tous les mots de la question.

**Sur un graphe sans couche, la commande refuse.** Une liste vide s'y lirait « la prose n'en dit
rien », alors qu'aucune prose n'a été lue.

## Ce que cela ne couvre pas

Une question dont aucun mot n'est dans l'énoncé qui y répond. La commande compare des mots, pas
des sens : un synonyme lui échappe, et elle le dit en rendant zéro.

Les pages hors du périmètre de l'ADR 5790, qui n'ont pas d'énoncé.

Sept questions, c'est une mesure courte. La notation n'a pas été réglée sur elles : les deux
essayées en tenaient six chacune, pas les mêmes, et c'est la plus simple qui est restée.

## Les alternatives écartées

- **Remplacer `graphify query`.** Une seule porte d'entrée, au prix d'un détour pour toute
  question de code, que le moteur sert bien.
- **Corriger le choix des points de départ du moteur.** Il vit hors du dépôt.
- **Une recherche par le sens**, vecteurs ou modèle. Elle ferait entrer une dépendance et un coût
  par question, là où la couche est déjà du texte rédigé pour être lu.
- **Compter les répétitions.** C'est la première notation, écartée pour la raison dite plus haut.

## Comment on le sait

L'auto-test de `scripts/graphify/couche_semantique.py`, lancé par `lint.yml`, joue la commande
sur des graphes fabriqués : un symbole de code qui porte le mot de la question n'est pas rendu,
la justification répond quand le libellé ne porte aucun mot, un mot rare départage, et un graphe
sans couche fait refuser.

Les sept questions se rejouent à la main sur le graphe de référence, que le runner ne porte pas.
