## Why

Le repli manuel (ADR 5867) fait générer des archives ZIP de toute la nuit, séquences déjà en ligne comprises.
#5970 a observé ce que le serveur en fait, sur son code à la révision épinglée : à l'extraction, il ajoute chaque
fichier de l'archive sans chercher s'il est déjà là. Une séquence déjà en ligne figure alors deux fois dans les
fichiers de la participation, que l'application relit pour son audit et sa récupération. Le même banc montre
qu'une archive limitée aux séquences absentes ne crée aucun doublon.

Le porteur a décidé le 5 octobre 2026 que les archives du repli ne contiennent que ce qui n'est pas en ligne
(#5975).

## What Changes

- Sur une nuit dont le dépôt en séquences WAV est entamé, la génération d'archives écarte les séquences que le
  plan de dépôt dit déposées. L'écran et `exporter-lot` suivent la même règle.
- Quand toutes les séquences sont en ligne, la génération refuse en le disant, au lieu d'écrire une archive vide.
- Hors de ce cas, rien ne change : une nuit jamais déposée, ou dont le dépôt est en archives, se génère en entier.
- La consigne de la carte du repli dit « des séquences qui ne sont pas en ligne », et non plus « de la nuit ».

## Capabilities

### New Capabilities

Aucune.

### Modified Capabilities

- `lot/parcours-du-depot` : l'exigence du repli manuel change d'un mot, et une exigence neuve dit ce que les
  archives d'un dépôt entamé en séquences contiennent.

## Impact

- `lot/model/ServiceLot` (la génération lit le plan de dépôt), `lot/view/RepliManuelUI` (la consigne).
- `exporter-lot` par le même service, sans code propre.
- Le banc de la plateforme de test, qui gagne le cas du remède ; le cas de recette `S4-105` ; l'aperçu du repli ;
  la page et la maquette de l'écran de lot ; une ADR qui amende la 5867.
