---
type: adr
title: "Aucun échec silencieux ; la sévérité de journalisation se décide à l'émission"
status: stable
article: A12
chantier: "EPIC #1523 (observabilité)"
verification: probable
enforced_by:
  - "scripts/adr/0008-echec-silencieux.py"
ratchet: 16
verified:
  - by: machine:suspects
---

# Aucun échec silencieux ; la sévérité de journalisation se décide à l'émission

## Contexte

Des échecs disparaissaient sans laisser de trace : `catch` muets, tâches longues qui gelaient l'interface sans un mot, exceptions avalées par un contrat *best-effort*. Quand un utilisateur signalait « ça n'a pas marché », rien dans le système ne permettait de savoir **quoi**. Il fallait une journalisation - **sans** pour autant noyer le journal sous les issues **normales** (un refus métier, une annulation), sinon on cesse de le lire.

## Décision

- **Backend : `java.util.logging` (JUL).** Zéro dépendance, cohérent avec le packaging (shade + jpackage). Un `FileHandler` tournant vers `<workspace>/logs/` + console, installé une fois à `App.start()` et `Cli.main()`. Les tests restent silencieux.
- **La sévérité se décide à l'émission**, selon la **nature** de l'issue :
  - une **annulation** (`OperationAnnuleeException`) ou un **refus métier** (`RegleMetierException`, et les `IllegalArgumentException` des validateurs) sont des issues **normales** → **FINE**, sans pile ;
  - seul un `Throwable` **inattendu** (un vrai bug) part en **WARNING/SEVERE avec** sa pile.

## Conséquences

- Un échec inattendu laisse **toujours** une trace horodatée avec pile dans `<workspace>/logs/`, même si son message est nul - exactement la classe de bug visée.
- Les refus et annulations **n'encombrent pas** le journal d'erreurs : il reste lisible.
- La règle vaut à **chaque surface** : l'IHM route ses `Throwable` via un helper partagé ; la CLI applique la même distinction dans son handler d'exécution (un refus de validation n'y est plus une trace SEVERE).
- Corollaire outillage : un menu « Ouvrir le dossier des journaux » rend la trace accessible sans fouiller le disque.

## Le corpus du garde : les deux arbres (#4462)

Le garde n'a jamais lu que `src/main/java`, et **aucune décision ne l'y avait restreint** : il est né avant que la question ne se pose. Un test qui avale son échec ment pourtant de la même façon qu'une classe de production, et plus mal : il rend vert sans avoir rien prouvé. Le corpus est donc les deux arbres.

Les **quatre** `catch` au corps vide de `src/test/java` ont été arbitrés un par un avant d'entrer dans le cliquet, qui passe de 12 à 16 :

| Endroit | Ce que le catch vide y fait |
|---|---|
| `SondeAccessibiliteTest:70` | l'exception est le comportement **attendu** : elle prouve que le dossier n'est pas inscriptible |
| `ParcoursMultisiteVersPassageE2ETest:216` | boucle de reprise ; l'assertion de l'appelant tranche si les trois essais échouent |
| `ParcoursSitesVersPassageE2ETest:144` | même boucle, même arbitrage |
| `BancDeRecetteUrlTest:110` | fermeture d'un guichet qui ne vit que le temps du cas |

Aucun n'est un échec avalé, et c'est pourquoi ils entrent dans le cliquet plutôt que de le faire rougir.

## Alternatives écartées

- **Une dépendance de logging (SLF4J+backend, Log4j).** Alourdit le module-graph pour un besoin que JUL couvre, alors que jlink modulaire est déjà exclu.
- **Tout journaliser au même niveau.** Noie les bugs sous les refus normaux : le journal devient du bruit qu'on cesse de consulter.
