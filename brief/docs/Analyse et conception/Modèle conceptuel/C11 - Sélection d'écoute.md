# C11 - Sélection d'écoute

Sous-ensemble de séquences d'écoute sélectionné pour permettre à l'utilisateur de **vérifier que l'enregistrement de la nuit est exploitable** (sound check global). Créée au moment où l'utilisateur ouvre la vue de vérification.

| Attribut | Type | Contraintes | Notes |
|---|---|---|---|
| méthode de constitution | énum | `RéparTemporel` (par défaut) / `Aléatoire` / `Manuel` | RéparTemporel = N séquences réparties uniformément sur la nuit. |
| taille | entier | par défaut 10-30 séquences | Configurable. |
| séquences rattachées | référence × N | obligatoire | Ordonnées par horodatage de l'enregistrement original source. |
| séquences écoutées | référence × M | dérivé | Mis à jour à chaque play de l'utilisateur. |

## Règles applicables

- [R12](Règles%20métier.md#r12) - sélection auto à l'ouverture (méthode `RéparTemporel` par défaut).
- [R13](Règles%20métier.md#r13) - verdict global saisi par l'utilisateur, sans seuil obligatoire d'écoute.
- [R14](Règles%20métier.md#r14) - un passage `À jeter` ne peut pas rejoindre un lot prêt à déposer.

## Voisins dans le modèle

- **Rattachée à** 1 [Passage](C5%20-%20Passage.md) (cardinalité 0..1 côté passage).
- **Porte sur** 1..N [Séquences d'écoute](C8%20-%20Séquence%20d%27écoute.md).

## Retour

- [⬅ Modèle conceptuel - Vue d'ensemble](index.md)
