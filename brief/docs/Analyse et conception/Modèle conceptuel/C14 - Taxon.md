# C14 - Taxon

Un code 6 lettres (3 + 3 : trois premières lettres du genre + trois premières lettres de l'espèce) selon la nomenclature Tadarida.

| Attribut | Type | Contraintes | Notes |
|---|---|---|---|
| code | texte | exactement 6 caractères | Ex. `Pippip`, `Nyclei`, `Tadten`. Aussi : `noise`, `piaf` (pseudo-taxons). |
| nom latin | texte | optionnel | Ex. `Pipistrellus pipistrellus`. |
| nom vernaculaire FR | texte | optionnel | Ex. `Pipistrelle commune`. |
| groupe taxonomique | référence | obligatoire | Voir [C15 - Groupe taxonomique](C15%20-%20Groupe%20taxonomique.md). |

## Voisins dans le modèle

- **Appartient à** 1 [Groupe taxonomique](C15%20-%20Groupe%20taxonomique.md).
- **Classification de** 0..N [Observations](C13%20-%20Observation.md).

## Retour

- [⬅ Modèle conceptuel - Vue d'ensemble](index.md)
