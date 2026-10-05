## 1. Rouge

- [x] 1.1 `DateDeDepotReelleTest` : le lecteur commun sur la forme que la production écrit
- [x] 1.2 `DeposerTest`, `StatutPassageTest`, `LotViewModelTest` : témoins dans cette forme
- [x] 1.3 `TableNuitsTest` : la colonne « Nuit du » en français

## 2. Code

- [x] 2.1 `Horodatage.dateDe`, lu par `dateSeule` et par `ColonneDate.analyser`
- [x] 2.2 `FormatsLot.messageEtat` et `Deposer.rendreDepot` passent par `dateSeule`
- [x] 2.3 `TableNuits` : colonne de date

## 3. Ce qui montre

- [x] 3.1 Aperçus de l'écran de lot et de l'import régénérés et rouverts
- [x] 3.2 Mutations : lecteur qui n'accepte qu'une date seule, phrase qui recopie la valeur brute
