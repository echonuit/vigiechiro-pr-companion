## Why

Sur l'écran de lot, le lot 14 de #5596 a donné au titre et au bouton de la dernière étape le nom du geste :
« Lancer la participation » dès qu'une participation est liée. Le fil d'étapes du haut de l'écran lisait une
liste figée, et nommait le même geste « Marquer déposé ». Trouvé le 5 octobre 2026 en mettant côte à côte les
aperçus de la clôture.

Le renommer ne suffisait pas. Le fil rend toutes ses étapes franchies dès que la nuit est sur la plateforme :
une puce « Lancer la participation » se serait affichée faite au-dessus d'un bouton encore offert. La recette
portait déjà ce constat (S4-C03). Le porteur a choisi le 5 octobre que le fil suive aussi l'état du bouton.

La confirmation de « Réinitialiser le dépôt », elle, n'avait pas suivi le lot 25 : elle parlait d'archives ZIP
pour un dépôt en séquences, alors que l'infobulle du même bouton distinguait déjà les deux formes.

## What Changes

- La dernière étape du fil porte le nom que son bouton porte.
- Sur une nuit déposée par l'application, elle reste l'étape courante tant que l'analyse n'est pas demandée.
- La confirmation de « Réinitialiser le dépôt » ne nomme d'archives que s'il y en a.

## Capabilities

### New Capabilities

Aucune.

### Modified Capabilities

- `lot/parcours-du-depot` : deux exigences de plus, sur la dernière étape du fil et sur la confirmation de
  réinitialisation.

## Impact

- `lot/viewmodel` : le type neuf `DernierGesteDuDepot`. Le calcul des étapes ne change pas.
- `lot/view` : `LotController`, `EtapeDeposerUI`, `EtapeTeleversementController`.
- Six aperçus de l'écran de lot, `docs/ecrans/lot.md`, la fiche M-Lot du brief, la session de recette S4.
- La ligne de commande n'a pas de fil d'étapes : rien n'y change.
