Un lot du chantier #5596, une demande de fusion : #5784 (lot 24).

## 1. La carte importe quand le relevé dit « terminée »

- [x] 1.1 Soumettre au porteur les textes de D6 et la question du libellé du bouton, avant tout code. Fait quand il les a validés.
- [x] 1.2 Écrire les cas rouges de `TraitementViewModelTest` : un relevé terminé sur une nuit sans observation appelle l'import et restitue son compte rendu ; une nuit qui a ses observations n'est pas réimportée et la carte le dit ; un import qui lève laisse « Analyse terminée » et dit l'échec avec son motif. Deux témoins : un relevé « en cours » n'importe rien, et `chargerDernierReleve` sur un cache « terminée » n'importe rien. Rouges contre un stub qui compile.
- [x] 1.3 Donner au port `ImportObservations` la lecture « la nuit a déjà ses observations », implémentée dans `validation`, avec son cas de test. Fait quand ce cas passe et que les tests de l'import existant restent verts.
- [x] 1.4 Relever puis importer dans une seule tâche de fond, et restituer les deux volets. Fait quand les cas de 1.2 passent et que les cas existants de `TraitementViewModelTest` restent verts.
- [x] 1.5 Écrire le test d'interface rouge : sur l'écran de lot monté avec un suivi et un import bouchons, cliquer « Actualiser » et lire `#lblImportTraitement`. Puis câbler la ligne dans `Lot.fxml` et `SuiviTraitementUI`. Fait quand le test passe, et qu'il tombe quand on retire l'appel d'import.
- [x] 1.6 Raccourcir l'état « terminée » et faire dire à la ligne d'import, à l'ouverture, ce qu'il reste à faire (D6, quatrième ligne). Fait quand un cas le lit dans les deux états, et que `FormatsTraitementTest` suit.

## 2. La ligne de commande suit

- [x] 2.1 Écrire les cas rouges de `EtatTraitementVigieChiroTest` : sans `--importer` rien n'est importé et la sortie renvoie à l'option ; avec l'option sur une analyse terminée l'import part et la commande rend `0` ; sur une nuit déjà importée il ne part pas ; un import qui échoue rend `2` ; sur une analyse en cours la commande rend `3` sans importer.
- [x] 2.2 Ajouter `--importer`. Fait quand 2.1 passe.
- [x] 2.3 Écrire le cas rouge du verrou : la commande est dispensée du verrou sans l'option, et le prend avec. Puis faire suivre `LectureSeule` et `StrategieExecutionCli` (D7). Fait quand ce cas passe et que `ClassementLectureEcritureTest` reste vert sans changer son compte.

## 3. Ce que l'on voit et ce que l'on lit

- [x] 3.1 Capture de la carte après un import, déclarée aux trois endroits (classe de capture, script, manifeste), ouverte et relue ; les trois gardes de captures rejoués à la main.
- [x] 3.2 `docs/ecrans/lot.md` et `dev-docs/cli.md` disent le geste ; un cas de recette S4 le pose, sans toucher aux lignes S4-47 à S4-50, S4-90 et S4-92.
- [x] 3.3 Faire rejouer `ScenarioConnecteLancementTest` sur la branche par la session qui le porte, avant la fusion. Fait quand elle le rend vert.
- [ ] 3.4 Mutation ciblée sur le chemin d'import de `TraitementViewModel` et sur la décision du verrou ; survivants lus un par un.
