## Why

Samuel (2.193.0) a vu l'alerte « horaires non respectés » alors que son enregistreur s'était arrêté exactement à l'heure de fin que l'application affichait. La fin exigée se compare à la seconde (`lever + 30 min`, le lever venant de l'éphéméride avec ses secondes), mais l'écran et la commande `diagnostiquer` l'affichent au format `HH:mm`, qui **tronque**. Une fin exigée à 07:31:40 s'affiche « 07:31 » : l'observateur qui programme 07:31 s'arrête 40 secondes trop tôt (#5601).

## What Changes

- L'heure exigée **affichée** est arrondie du côté qui respecte le plancher du protocole : la fin à la minute **supérieure**, le début à la minute **inférieure**. Programmer l'heure affichée tient donc toujours la fenêtre.
- La comparaison reste à la seconde : le protocole est un plancher (#4984), et aucune tolérance n'est introduite.
- L'écran et la commande `diagnostiquer` lisent cet arrondi à une seule source.

## Capabilities

### Modified Capabilities

- `diagnostic/coherence-horaire-d-une-nuit` : l'exigence « Les deux plages sont montrées » dit comment la plage exigée s'arrondit à l'affichage.

## Impact

`CoherenceHoraire` (l'arrondi), `PlagesHoraires` (écran), `Diagnostiquer` (terminal et JSON). L'alternative, comparer à la minute, a été écartée par le porteur : elle accepterait un arrêt jusqu'à 59 secondes avant l'heure que le protocole exige.
