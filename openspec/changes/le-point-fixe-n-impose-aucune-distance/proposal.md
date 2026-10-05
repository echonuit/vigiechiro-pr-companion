## Why

La fiche d'un site affiche, sur la carte de chaque point, sa distance au point le plus proche. Sous
200 m, cette ligne passe en avertissement : « trop rapprochés pour le protocole, vérifiez la position ou
la saisie GPS ». Le seuil date de #154, qui l'appelle un « garde-fou de protocole » sans citer de source.

Lu dans le code du portail le 5 octobre 2026 (#5839) : `checkDistanceBetweenPoints(200)` n'existe que
dans le service du protocole Carré, qui exige de 5 à 13 points par carré et que Companion ne traite pas.
Le service du Point Fixe n'impose aucune distance entre deux points. L'application reproche donc à
l'observateur une règle que son protocole n'a pas.

Elle le fait en plus de façon incohérente depuis le lot 20 de #5596 : un point créé à 100 m d'un autre ne
reçoit aucun avertissement à la création (le voisinage se juge à 40 m), puis sa carte s'affiche « trop
rapprochés ».

Le porteur a tranché le 5 octobre : les références à 200 m disparaissent.

## What Changes

- La carte d'un point ne juge plus sa distance au point le plus proche. La distance reste affichée, comme
  une information.
- Le seuil de 200 m et la notion de « trop proche » sortent du code.
- Il ne reste qu'une définition du voisinage : 40 m, à la création d'un point.

## Capabilities

### New Capabilities

Aucune.

### Modified Capabilities

- `sites/declaration-d-un-point` : une exigence de plus, sur ce que la fiche d'un site dit de la distance
  entre deux points.

## Impact

- `sites/viewmodel/CartePoint`, `sites/view/CartesPointsSite`, la feuille de style des sites.
- Un commentaire de `commun/model/DistanceGeo`, qui justifiait un écart de 15 m par ce seuil.
- `docs/ecrans/sites.md` et l'aperçu de la fiche d'un site.
- Aucune commande ne lisait ce seuil : la ligne de commande n'a jamais signalé de proximité à 200 m.
