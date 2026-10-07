## 1. Rouge

- [x] 1.1 `PublicationSurLeCarreDUnTiersTest` : mention présente sur un carré de tiers, absente sur un carré à soi et sur un carré jamais relié, empêchements inchangés par la marque
- [x] 1.2 `PublicationSurLeCarreDUnTiersViewTest` : la carte et la modale, le geste resté offert, le texte dessiné non coupé

## 2. Code

- [x] 2.1 `PublicationDepuisLaFiche` lit la marque et rend la mention, hors des empêchements
- [x] 2.2 `CartesPointsSite` la pose sous les actions d'une carte qui offre de publier
- [x] 2.3 `IntentionPublication`, `ModalePoint.fxml` et son contrôleur : une ligne sous la case, masquée et non gérée quand elle est vide

## 3. Ce qui montre et ce qui dit

- [x] 3.1 `docs/ecrans/sites.md` et `dev-docs/api-vigiechiro.md`
- [x] 3.2 Paire avant et après de la carte et de la modale, sur un carré de tiers

## 4. Vérification

- [x] 4.1 Mutations vues rouges : marque ignorée, mention inversée, empêchement ajouté, carte muette, modale toujours visible, carte sans condition de geste
