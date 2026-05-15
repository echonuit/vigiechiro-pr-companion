# C14 - Taxon

<figure markdown="span" style="text-align: center; margin: 1.5rem 0;">
  ![Planche illustrée des quatre espèces de chiroptères présentes dans le dataset fourni : Pipistrellus pipistrellus (pipistrelle commune), Nyctalus leisleri (noctule de Leisler), Tadarida teniotis (molosse de Cestoni) et Rhinolophus hipposideros (petit rhinolophe), de la plus petite à la plus grande](../../assets/illustrations/taxons-set.webp){ style="max-width: 100%; border-radius: 8px;" }
  <figcaption style="font-size: 0.85em; color: #666; margin-top: 0.5rem; font-style: italic;">Les quatre taxons fil rouge du dataset (codes Tadarida : <code>Pippip</code>, <code>Nyclei</code>, <code>Tadten</code>, <code>Rhihip</code>), du plus petit au plus grand.</figcaption>
</figure>

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
