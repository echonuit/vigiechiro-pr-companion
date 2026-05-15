# C12 - Résultats d'identification (post-Tadarida)

Le fichier produit par Tadarida côté serveur Vigie-Chiro et téléchargé manuellement par l'utilisateur après le dépôt. Importé dans l'application pour la **validation taxonomique** (SHOULD / cible étirable). Stocké physiquement sous le nom de fichier `<uuid>-participation-<uuid>-observations.csv`.

| Attribut | Type | Contraintes | Notes |
|---|---|---|---|
| chemin sur disque | texte | obligatoire | Fichier `*-observations.csv` ou `*-observations_Vu.csv`. |
| format détecté | énum | `Brut` (avec guillemets) / `Vu` (réinjectable, sans guillemets) | Reconnu à l'import. |
| date d'import | datetime | obligatoire | Tracée pour la cohérence. |

## Voisins dans le modèle

- **Annote** 0..1 [Passage](C5%20-%20Passage.md).
- **Agrège** 1..N [Observations](C13%20-%20Observation.md).

## Retour

- [⬅ Modèle conceptuel - Vue d'ensemble](index.md)
