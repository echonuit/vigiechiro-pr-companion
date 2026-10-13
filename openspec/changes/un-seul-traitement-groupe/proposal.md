## Why

Deux mécanismes appliquent un geste à plusieurs nuits : `MoteurTraitementGroupe`, que quatre gestes groupés
emploient, et la boucle propre de `ServiceReconstructionPassages.reconstruireTout`. L'utilisateur de « Tout
compléter » n'apprend ni d'avance quelles nuits ne pourront pas être complétées, ni après coup pourquoi l'une a
été sautée, et `reconstruire-passage --tout` sort en 0 quoi qu'il arrive.

Le porteur a tranché le 13 octobre 2026 de les unifier. Le constat, la cartographie des deux mécanismes et les
six décisions de l'instruction sont dans #2755.

## What Changes

- « Tout compléter » annonce avant de partir combien de nuits seront complétées, lesquelles sont écartées et
  pourquoi, et laisse renoncer.
- Le compte rendu dit le sort de chaque nuit, complétée, écartée, en échec ou non traitée, avec son motif.
- Une nuit dont l'analyse n'est pas terminée est écartée en cours de route, avec ce motif. Elle rejoignait
  des « ignorées » dont l'écran ne donnait pas la cause nuit par nuit.
- Une nuit qui échoue pour une raison inattendue est en échec, et le lot continue. Aujourd'hui cette exception
  fait sortir de la boucle sans compte rendu.
- Annuler arrête au prochain point de contrôle : la nuit en cours se défait et compte « non traitée ». La page
  utilisateur, qui dit que cette nuit « va au bout », est corrigée.
- **BREAKING** `reconstruire-passage --tout` sort en 1 dès qu'une nuit est en échec, et son JSON gagne la liste
  des nuits. Le compteur `ignorees` cède la place aux comptes par statut.

Ce qui ne change pas : les deux niveaux d'avancement, les totaux de séquences et d'observations, la
reconstruction d'une seule nuit, et le comportement des quatre gestes groupés existants.

Ce que le changement ne fait pas : il n'ajoute ni cases à cocher à la modale, ni barre par passage aux quatre
autres gestes.

## Capabilities

### New Capabilities

- `multisite/reconstruction-de-toutes-les-nuits` : « Tout compléter » et `reconstruire-passage --tout`.
  L'annonce avant de partir, le sort de chaque nuit, l'annulation, le compte rendu et le code de sortie.

### Modified Capabilities

Aucune. `multisite/reconstruction-d-une-nuit` ne porte que la date du compte rendu d'une nuit complétée seule,
et elle ne change pas.

## Impact

- **Le contrat du moteur**, dans `commun/model` : `ActionGroupee`, `CiblePassage`, `IssueTraitement`,
  `ResultatTraitementGroupe` et `MoteurTraitementGroupe`. Ses quatre porteurs (`PreparationGroupee`,
  `TeleversementGroupe`, `LancementCalculGroupe`, `ImportResultatsGroupe`) et ses deux surfaces (`TraitementLot`,
  `TraiterPassages`) suivent le contrat sans changer de comportement.
- **La reconstruction** : `ServiceReconstructionPassages` perd sa boucle, la modale et son view-model dans
  `multisite` gagnent l'annonce et le compte rendu par nuit, `cli/commande/ReconstruirePassage` change de
  sortie.
- **La documentation** : `docs/ecrans/multisite.md`, `dev-docs/cli.md`, qui ne documente pas `--tout`,
  `dev-docs/patterns.md`, les cases S4-64 et S4-65 de la recette, et les aperçus de la modale.
- **Les décisions** : l'ADR 2357 pose que l'annulation s'arrête entre deux passages et que l'éligibilité
  s'annonce sans réseau. Ce changement l'étend sur ces deux points, par une ADR numérotée par l'issue du lot
  qui touche le contrat.
- **Le journal des versions** : le changement de contrat de la commande.
