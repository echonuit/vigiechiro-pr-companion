# ADR 0004 — Pas de cycle entre features : les ponts passent par un port dans `commun`

- **Statut** : Accepté — rétroactif
- **Chantier** : garde-fou `ArchitectureTest.features_sans_cycle`
- **Vérification** : certaine — `DecisionsRespecteesTest#aucun_cycle_entre_les_features`

## Contexte

Le graphe des paquets `fr.univ_amu.iut.*` doit rester **sans cycle** (`ArchitectureTest.features_sans_cycle`, via ArchUnit) : un cycle entre features rend le socle inintelligible et non-déclinable. Or il arrive qu'une feature A ait besoin d'une donnée que possède une feature B, alors que **B dépend déjà de A** - ajouter la dépendance inverse fermerait un cycle. Cas concret : `passage` a besoin des **coordonnées d'un point d'écoute**, qui vivent dans `sites` ; mais `sites` dépend déjà d'autres briques et le lien direct `passage → sites` était refusé par ArchUnit.

## Décision

Quand un lien direct fermerait un cycle, on interpose un **port dans `commun`** : une interface neutre (ex. `commun.model.CoordonneesPoint`), **consommée** par la feature demandeuse et **implémentée** par la feature propriétaire (ex. `CoordonneesPointSites`), reliée par un **port optionnel** ([ADR 0003](0003-feature-plugin-desactivable-ports-optionnels.md)). Aucune des deux features ne dépend de l'autre : toutes deux ne connaissent que `commun`.

La **racine de composition** (`commun.di`) est la seule à connaître toutes les features - c'est son rôle, et `ArchitectureTest` l'exclut explicitement de l'analyse des cycles.

## Conséquences

- Le graphe de slices reste acyclique, vérifié en CI.
- Le contrat entre features est **nommé** et minimal (le port ne rend que ce qui traverse la frontière), au lieu d'exposer toute une feature à l'autre.
- Combiné à l'optionnalité : la feature demandeuse fonctionne même si la propriétaire est désactivée.

## Alternatives écartées

- **Autoriser le lien direct.** Ferme un cycle → build ArchUnit rouge, socle non-déclinable.
- **Remonter la donnée dans `commun`.** Déplacerait de la logique métier hors de sa feature propriétaire ; le port laisse la logique chez son propriétaire et n'expose que le strict nécessaire.
