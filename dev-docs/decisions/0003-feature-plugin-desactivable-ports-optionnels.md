# ADR 0003 — Une feature est un plugin désactivable ; ses dépendances entre features sont des ports optionnels

- **Statut** : Accepté — rétroactif (chantiers #923, #1057)
- **Chantier** : EPIC #923 (« feature = plugin ») et EPIC #1057 (feature-flags)
- **Vérification** : humaine — la discipline du port optionnel (OptionalBinder, Optional<Port>) se juge en revue ; aucun motif ne distingue un port d'une dépendance en dur

## Contexte

L'application est un **socle** (« package-by-feature ») destiné à être décliné : tous les écrans ne concernent pas tous les usages, et le projet pédagogique de la SAÉ demande de pouvoir **retirer** une feature sans laisser de trou ni de dépendance morte. Il fallait un mécanisme qui rende une feature **activable/désactivable** sans que ses voisines ne cassent quand elle est absente.

## Décision

- Chaque feature est un **module Guice** autonome. Son activation est décrite par une `Fonctionnalite(id, Categorie)`, enregistrée dans `Fonctionnalites` (registre) et exposée dans l'onglet « Fonctionnalités ».
- Une dépendance **d'une feature vers une autre** passe par un **port optionnel** (`OptionalBinder.newOptionalBinder(...)`) : le consommateur reçoit un `Optional<Port>` et **fonctionne avec ou sans** le fournisseur. Feature absente → `Optional.empty()` → le consommateur s'en passe explicitement (et le dit, plutôt que de planter).

## Conséquences

- Désactiver une feature ne casse pas ses voisines : elles voient un `Optional.empty()`, jamais une classe manquante.
- Le câblage devient **déclaratif** : ce qui est optionnel est visible au type (`Optional<…>`), pas caché dans un `try/catch` de résolution.
- Coût : chaque consommateur d'un port optionnel doit traiter le cas « absent » - c'est voulu, c'est là qu'on dit honnêtement ce qui ne peut pas être fait sans la feature.

## Alternatives écartées

- **Dépendances dures entre features.** Une feature désactivée aurait alors cassé la compilation ou l'injection de ses voisines : impossible de décliner le socle.
- **Résolution par réflexion / service-loader ad hoc.** Perd la vérification au type et la lisibilité du graphe d'injection.

Voir aussi [ADR 0004](0004-cross-feature-sans-cycle-ports-commun.md), qui applique ce principe pour **briser les cycles** entre features.
