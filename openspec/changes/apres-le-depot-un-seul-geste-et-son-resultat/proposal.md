## Why

Pendant le dépôt réel de #5597, la fin du dépôt a laissé l'utilisateur sans savoir quoi faire, puis sans savoir ce qu'il avait fait. Deux boutons « Lancer la participation » coexistaient sous un titre d'étape resté « Marquer le passage déposé » (#5676). Une fois cliqué, le bouton s'est grisé quelques secondes puis est revenu à l'identique, le résultat étant parti dans le bandeau du haut de page (#5682). Le journal disait pourtant que la participation était planifiée, et deux clics suivants ont reçu `400 Already PLANIFIE`.

Les deux issues portent sur le même geste, celui qui suit le dépôt. Les traiter ensemble évite de déplacer deux fois le même bouton.

## What Changes

- Après un dépôt connecté complet, **un seul** bouton lance la participation : celui de l'étape 4, choisi par le porteur le 3 octobre 2026. Le compte rendu du dépôt nomme la prochaine étape sans bouton.
- Le titre de l'étape 4 dit le geste qu'elle offre : « Lancer la participation » quand une participation est liée, « Marquer le passage déposé » sinon.
- Le résultat d'un lancement se dit **dans l'étape 4**, près du bouton : demande acceptée, analyse déjà demandée, refus avec son motif, plateforme injoignable.
- Une analyse demandée (planifiée, en cours, relancée par le serveur) ne s'offre plus à être relancée : le bouton se grise et dit pourquoi, en renvoyant à la carte « Traitement Vigie-Chiro ».
- Un refus dit son motif à l'écran, comme la commande `lancer-traitement-vigiechiro` le fait déjà.

## Capabilities

### New Capabilities
- `lot/lancement-de-la-participation` : le geste qui demande à Vigie-Chiro d'analyser une nuit déposée, son unique point d'action à l'écran, et ce que l'on sait de son résultat, à l'écran comme en ligne de commande.

### Modified Capabilities

Aucune. La capacité `lot/depot-sur-vigie-chiro`, décrite en delta par `un-depot-entame-se-regenere` et `un-refus-de-s3-n-est-pas-un-refus-de-droits`, porte le téléversement ; ce changement ne touche à aucune de ses exigences.

## Impact

- Écran de lot : `Lot.fxml` (titre de l'étape 4, zone de retour de l'étape), `EtapeDeposerUI`, `CompteRenduDepotUI`, `SuiviTraitementUI`.
- ViewModels : `DepotViewModel` (restitution du lancement), `TraitementViewModel` (analyse demandée).
- Ligne de commande : aucun changement de comportement ; `lancer-traitement-vigiechiro` sert de référence de parité.
- Captures du lot, `docs/ecrans/lot.md`, recette S4.
- Hors de ce changement : l'import des observations à la fin de l'analyse (#5784), qui touche la même zone et vient ensuite.
