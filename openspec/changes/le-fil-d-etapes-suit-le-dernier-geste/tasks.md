## 1. Rouge

- [x] 1.1 `DernierGesteDuDepotTest` : nom de la dernière étape selon le geste, en trois et quatre étapes
- [x] 1.2 `DernierGesteDuDepotTest` : état de la dernière étape pour chacun des trois gestes
- [x] 1.3 `LotDepotConnecteViewTest` : la dernière puce porte le nom du bouton
- [x] 1.4 `LotDepotConnecteViewTest` : courante avant le lancement, franchie une fois l'analyse planifiée
- [x] 1.5 `LotDepotConnecteViewTest` : la confirmation de réinitialisation sous les deux formes

## 2. Code

- [x] 2.1 `DernierGesteDuDepot`, qui porte le nom du bouton et corrige un fil calculé
- [x] 2.2 `EtapeDeposerUI` compose le geste ; `LotController` l'applique au fil qu'il dessine
- [x] 2.3 `EtapeTeleversementController` : une phrase pour l'infobulle et la confirmation

## 3. Ce qui montre et ce qui dit

- [x] 3.1 Six aperçus de l'écran de lot régénérés et rouverts
- [x] 3.2 `docs/ecrans/lot.md`, fiche M-Lot, session S4 (constat S4-C03, cas S4-102)

## 4. Décision

- [x] 4.1 ADR : le fil suit le nom et l'état du dernier geste
- [x] 4.2 Mutations : nom figé, état figé, analyse demandée ignorée, écouteur retiré, confirmation figée
