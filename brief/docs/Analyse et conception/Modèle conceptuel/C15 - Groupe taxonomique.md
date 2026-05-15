# C15 - Groupe taxonomique

Niveau hiérarchique au-dessus du taxon, utile pour les filtres groupés (« tous les murins », « toutes les pipistrelles »).

| Attribut | Type | Contraintes | Notes |
|---|---|---|---|
| niveau | énum | `Genre` / `Famille` / `Ordre` | Plusieurs niveaux possibles, on en choisit un par groupe. |
| nom | texte | obligatoire | Ex. `Myotis`, `Pipistrellus`, `Vespertilionidae`, `Chiroptera`. |
| taxons membres | référence × N | dérivée | Mise à jour à chaque ajout de taxon. |

## Voisins dans le modèle

- **Regroupe** 1..N [Taxons](C14%20-%20Taxon.md).

## Retour

- [⬅ Modèle conceptuel - Vue d'ensemble](index.md)
