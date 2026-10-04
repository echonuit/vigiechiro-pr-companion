Un lot du chantier #5596, une demande de fusion : #5824 (lot 25).

## 1. Voir avant d'écrire

- [ ] 1.1 Rendre l'écran de lot dans les deux formes, connecté et hors connexion, et écrire sur l'issue ce qu'il montre dans chacune. Fait quand l'inventaire tiré du code est confirmé ou corrigé par ce rendu.
- [ ] 1.2 Soumettre au porteur les textes de D7 et la question de D4. Fait quand il les a validés.
- [ ] 1.3 Mesurer, par recherche dans `src/test/java`, les tests qui lisent un titre numéroté ou le nombre d'étapes. Fait quand leur liste est écrite sur l'issue.

## 2. La forme du dépôt arrive à l'écran

- [ ] 2.1 Cas rouges de `ServiceLotTest` : la forme d'une nuit sans dépôt suit le réglage, celle d'un dépôt entamé suit ses unités. Puis `ServiceLot.formeDuDepot`, sur laquelle `sourceDepotParDefaut` s'appuie. Fait quand ces cas passent et que `RegenerationPendantUnDepotTest` reste vert.
- [ ] 2.2 Cas rouges de `EtapesDepotTest` : trois étapes sans l'étape des archives, quatre avec, et le rang courant dans chaque cas. Puis le calcul. Fait quand ils passent.
- [ ] 2.3 `LotViewModel` publie `etapeArchivesOfferte` et le rang de chaque étape. Fait quand un cas de `LotViewModelTest` le lit dans les trois situations (WAV connecté, ZIP connecté, WAV hors connexion).

## 3. L'écran n'offre que ce qui sert

- [ ] 3.1 Test d'interface rouge : connecté en forme WAV, la carte des archives et les éléments du dépôt manuel sont absents, et les titres sont numérotés 1, 2, 3. Témoins : forme ZIP connecté, et forme WAV hors connexion, où l'écran est celui d'aujourd'hui.
- [ ] 3.2 Câbler la carte, les éléments du dépôt manuel, les titres et le fil d'étapes. Fait quand 3.1 passe et que les tests existants de l'écran restent verts.
- [ ] 3.3 Infobulles de l'étape de téléversement et du bouton de lancement, selon la forme. Fait quand un cas les lit dans les deux formes.

## 4. Le compte rendu nomme ce qui est parti

- [ ] 4.1 Cas rouges de `CompteRenduChiffreDepotTest` : chaque phrase qui nomme l'unité, en séquences, et le contenu refusé en WAV sans conseil de régénération. Témoins en archives.
- [ ] 4.2 Le nom de l'unité se déduit du plan et arrive au compte rendu. Fait quand 4.1 passe.

## 5. Ce que l'on voit et ce que l'on lit

- [ ] 5.1 Captures du lot dans la forme WAV, déclarées aux trois endroits, ouvertes et relues une par une ; les trois gardes de captures rejoués à la main.
- [ ] 5.2 `docs/ecrans/lot.md` décrit les deux formes dans l'ordre du défaut, sans le repli automatique disparu ; recette S4 sans toucher aux lignes S4-47, S4-90 et S4-92.
- [ ] 5.3 Faire rejouer `ScenarioConnecteLancementTest` sur la branche par la session qui le porte. Fait quand elle le rend vert.
- [ ] 5.4 Mutation ciblée sur la décision `etapeArchivesOfferte`, sur `EtapesDepot` et sur le nom de l'unité ; survivants lus un par un.
