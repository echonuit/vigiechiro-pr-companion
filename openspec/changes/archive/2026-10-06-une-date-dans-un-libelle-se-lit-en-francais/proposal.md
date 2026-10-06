## Why

Le lot 31 de #5596 a mis en français les dates que l'application affiche seules, et l'a écrit dans la
spécification : la table des nuits de l'import, la date de dépôt. Le lot 38 (#5901, livré par #5959) a étendu la
règle aux dates prises dans un libellé composé, à neuf endroits, sans écrire aucune exigence. Ces comportements
ne sont tenus que par leurs tests, et rien ne dit à un lecteur de la spécification quelle forme l'utilisateur lit.

Le porteur a demandé le 5 octobre 2026 de les ajouter (#5969).

## What Changes

Rien dans le produit : ce changement décrit ce que #5959 a livré.

- Une exigence par comportement observable, rangée dans la capacité de l'écran ou de la commande qui le montre.
- Chaque exigence nomme le test de #5959 qui la tient.
- Les deux sites que le lot 38 a laissés en forme ISO restent hors de la spécification, avec leur raison : le
  détail d'un résultat de recherche (#5949), et le message qui reprend une nuit telle que l'utilisateur l'a
  tapée dans une commande.

## Capabilities

### New Capabilities

- `analyse/detail-d-une-espece` : la colonne « Passage » du détail d'une espèce, sa forme et son tri.
- `analyse/export-d-une-image` : la légende qu'une image exportée porte.
- `passage/fiche-d-un-passage` : la plage horaire, l'alerte de fenêtre saisonnière et l'écart de nuit avec la
  participation.
- `qualification/qualification-d-une-nuit` : la plage horaire du bandeau.
- `multisite/reconstruction-d-une-nuit` : le compte rendu d'une nuit complétée.

### Modified Capabilities

- `audit/bilan-de-recuperabilite` : une exigence sur la date d'une nuit dans son libellé.
- `lot/suivi-du-traitement` : une exigence sur la date d'import que `statut-passage` annonce.

## Impact

Aucun code. Sept delta specs, dix exigences, toutes tenues par des tests qui existent sur `main`.
