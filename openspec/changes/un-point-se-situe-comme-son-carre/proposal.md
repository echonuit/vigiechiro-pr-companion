## Why

Pendant la recette réelle de #5597, l'observateur a collé une position pour trouver son carré, puis a dû ressaisir cette même position, coupée en deux champs, pour créer son premier point d'écoute (#5687, #5688). Les deux écrans ne lisent pas une position de la même façon : le site prend une paire collée (`PositionCollee`), le point deux champs lus axe par axe (`AnalyseurCoordonnees`). Le porteur juge que deux manières de saisir des coordonnées déstabilisent (4 octobre 2026).

## What Changes

- **Un seul champ « Position »** dans la modale de point, lu au fil de la saisie par la règle du site. Les champs Latitude et Longitude disparaissent. Glisser le marqueur écrit la paire dans le champ. **BREAKING** pour l'interface : la saisie axe par axe disparaît.
- **La règle de lecture du site s'enrichit** des formes que le point lisait et que le site refusait : degrés-minutes décimales (`43°24.06'N`) et cardinal après un décimal (`43.401 N`). Une paire à virgule décimale française reste refusée, avec un motif qui dit d'écrire le point décimal.
- **À la création d'un site depuis une position collée**, une case « Créer aussi le premier point d'écoute à cette position ». Cochée, elle crée le point à la position collée, avec un code automatique.
- **Le code d'un nouveau point est proposé** : `Z` suivi du premier numéro libre du site, comme le portail nomme un point libre. Il est imposé par la case, proposé et modifiable dans la modale de point.
- **Un point du même site à 40 m au plus** déclenche un avertissement à la création, sans empêcher d'enregistrer. 40 m est le rayon que le portail utilise pour nommer un point.

## Capabilities

### New Capabilities
- `sites/declaration-d-un-point` : situer un point d'écoute, lui donner son code, et le créer, depuis la modale de point ou en même temps que son site.

### Modified Capabilities
- `sites/declaration-de-carre` : l'exigence « Les formats de position acceptés, et ceux qui sont refusés » s'étend aux degrés-minutes décimales et au cardinal après un décimal, et le refus d'une virgule décimale dit quoi écrire.

## Impact

- `sites/model` : `PositionCollee` (formes ajoutées), le calcul du code suivant, la recherche d'un point proche.
- `sites/viewmodel` : `PointEditViewModel` (champ unique, code proposé, avertissement), `SiteEditViewModel` (la case et ce qu'elle crée).
- `sites/view` : `ModalePoint.fxml` et son contrôleur, `ModaleSite.fxml` et son contrôleur.
- `AnalyseurCoordonnees` ne sert plus à l'écran de point ; la ligne de commande (`ajouter-point --lat --lon`) ne change pas.
- Hors de ce changement : les noms des points systématiques `A1` à `H2` (#5608, à instruire) ; la réconciliation avec un point distant (#3750).
