## Why

À la déclaration d'un site, la case « Créer aussi le premier point d'écoute à cette position » (lot 19 de
#5596) crée un point à la position collée, tandis que le site prend le numéro du champ. Rien ne confronte
les deux. Qui tape un numéro, colle une position et coche la case sans passer par « Situer » crée un point
hors de son carré, sans un mot.

Le garde-fou avait été proposé au porteur le 4 octobre 2026 et livré sans, faute de réponse. Il a demandé
le 5 octobre de le livrer (#5860).

## What Changes

- Case cochée, quand la position collée tombe clairement dans un autre carré que le numéro saisi, un
  avertissement le dit sous la case en nommant les deux carrés.
- Il n'empêche pas de créer. Il ne dit rien sur une frontière, ni quand le numéro concorde, ni tant que le
  numéro est incomplet.

## Capabilities

### New Capabilities

Aucune.

### Modified Capabilities

- `sites/declaration-d-un-point` : une exigence de plus, sur ce que la case du premier point dit avant de
  créer.

## Impact

- `sites/viewmodel/PremierPoint`, `SiteEditViewModel`, `sites/view/ModaleSite.fxml` et son contrôleur.
- Un aperçu neuf, `docs/ecrans/sites.md`, la fiche M-Sites, un cas de recette S1.
- La commande `creer-site` ne prend pas de position et ne crée pas de point : rien n'y change.
