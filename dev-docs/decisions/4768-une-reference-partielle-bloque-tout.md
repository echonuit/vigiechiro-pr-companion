---
type: adr
title: "Une référence partielle ne dégrade pas la garde, elle la bloque"
status: stable
article: A17
chantier: "#4768, lot 2 de l'EPIC #4640"
decided_at: 2026-08-29
verification: humaine
verification_note: "la complétude d'un relevé se lit en confrontant les colonnes de la table aux composants du type comparé : ni l'un ni l'autre n'a de compteur, et le banc du DAO fait l'aller-retour composant par composant"
relations:
  prolonge: ["4640-pour-ne-rien-effacer-il-faut-se-souvenir-de-ce-qu-on-avait-vu"]
verified:
  - by: human:nedseb
    at: 2026-08-29
generated:
  by: "process:assistance-par-agents"
  at: 2026-08-29
---

# Une référence partielle ne dégrade pas la garde, elle la bloque

## Contexte

La migration V43 a créé `participation_relevee` pour porter la base de l'ADR 4640. Elle y a stocké le
**vent** et la **couverture** du bloc météo. Or `MeteoDepot` en porte quatre composants : les
températures de début et de fin partent vers la plateforme depuis #1844.

La base relue en manquait donc toujours deux. La comparaison les voyait divergentes **à chaque fois**,
et plus aucun envoi ne pouvait partir sur une nuit qui porte une température, c'est-à-dire sur les
nuits les mieux renseignées.

Le défaut a vécu deux issues. Il a fallu #4768 pour le voir, alors que le lot 1 avait posé la table et
que le début du lot 2 l'utilisait déjà.

## Pourquoi il ne s'est pas vu

Les bancs construisaient leurs blocs météo avec le constructeur de commodité à deux arguments, celui qui
« conserve les appels antérieurs à #1844 ». Toute la suite comparait donc des blocs dont les
températures étaient nulles des deux côtés, où l'oubli est invisible.

Et la première confirmation du défaut a été **circulaire** : elle simulait `releve.base(...)` avec une
base amputée à la main. Un mock ne démontre jamais un défaut de stockage : il rejoue l'hypothèse. Le
vrai rouge vit au DAO, sur l'aller-retour.

## Décision

**Un relevé qui sert de référence porte TOUT ce que la comparaison regarde, ou il n'est pas une
référence.**

En stocker une partie ne rend pas la garde *moins précise* : cela la rend **toujours vraie**, donc
toujours bloquante. C'est le contraire de l'intuition, et c'est ce qui rend le défaut si coûteux : une
base partielle ressemble à une base, elle se lit sans erreur, et elle refuse tout.

Le corollaire opératoire : **la table de référence et le type comparé se modifient ensemble.** Ajouter
un composant à l'un sans l'autre est le défaut, et il ne se signale d'aucune façon.

## Ce que cette décision empêche

Elle interdit d'ajouter un champ à la comparaison sans l'ajouter au relevé, l'ordre naturel, puisque la
comparaison est le code qu'on écrit et le relevé la table qu'on oublie.

Elle interdit aussi de « garder la colonne pour plus tard » : une colonne absente d'un relevé de
référence n'est pas une fonctionnalité manquante, c'est un blocage silencieux.

## Ce qui la tient

Le banc du DAO fait l'aller-retour **composant par composant**, températures comprises, sur un bloc
dont les quatre valeurs diffèrent. C'est le seul endroit où un oubli de colonne rougit : un banc de la
synchronisation ne le peut pas, puisqu'il fournit lui-même la base.

## Conséquences

- V44 ajoute `meteo_temperature_debut` et `meteo_temperature_fin`, et le DAO les lit avec `wasNull()`,
  une température nulle et un zéro n'étant pas la même chose.
- Le filet de migration défait V43 et V44 d'un seul `DROP TABLE`, V44 n'ajoutant que des colonnes.
- Le constructeur à deux arguments de `MeteoDepot` reste utile, mais un banc qui compare des bases doit
  employer les quatre : c'est ce qui manquait.
