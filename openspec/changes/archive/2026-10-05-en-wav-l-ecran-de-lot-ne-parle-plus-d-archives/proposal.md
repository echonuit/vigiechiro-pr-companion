## Why

Depuis #5821, le dépôt part en séquences WAV quand aucun réglage n'est posé. L'écran de lot, lui, est resté construit autour des archives ZIP, et il ne lit jamais la forme du dépôt. Pour un observateur connecté en mode WAV, il affiche une étape « Générer les archives » qui ne le concerne pas, y écrit que le téléversement « produit ses archives et les supprime », compte des « archives » dans le compte rendu d'un dépôt de séquences, conseille de « régénérer les archives » pour réparer un refus, et justifie le blocage d'une relance par un audio « non conservé après un dépôt en archives » alors que la plateforme le garde. La page `docs/ecrans/lot.md` décrit le même écran, avec en plus un repli automatique vers le WAV disparu depuis l'ADR 0034 (#5824).

Le défaut touche désormais tout poste sans réglage, alors qu'il ne touchait avant que ceux qui avaient choisi le WAV.

## What Changes

- L'écran de lot **connaît la forme du dépôt** de la nuit : celle d'un dépôt entamé s'il y en a un, sinon celle du réglage.
- **En mode WAV et connecté, l'étape « Générer les archives » disparaît**, avec ce qui sert au dépôt manuel d'archives dans l'étape de téléversement. Elle reste en mode ZIP, et hors connexion, où elle sert au dépôt manuel (décision du porteur, 4 octobre 2026).
- **Les étapes se renumérotent** quand l'étape des archives est absente : 1. Préparer, 2. Téléverser, 3. Lancer la participation. Le fil d'étapes suit.
- **Le compte rendu du dépôt nomme ce qui est parti** : « séquence(s) » pour un dépôt en WAV, « archive(s) » pour un dépôt en ZIP. Il ne conseille de régénérer les archives que pour des archives.
- **Les infobulles de l'étape de téléversement** ne parlent d'archives et de compression que si le dépôt en produit.
- **Le blocage de la relance est gardé** dans les deux modes ; son explication dit la raison qui vaut pour le mode de la nuit.
- `docs/ecrans/lot.md` décrit les deux formes dans l'ordre du défaut, sans le repli automatique disparu.

## Capabilities

### New Capabilities
- `lot/parcours-du-depot` : les étapes que l'écran de lot offre pour déposer une nuit, selon la forme du dépôt et la connexion, et les mots dans lesquels il en rend compte.

### Modified Capabilities

Aucune dans les spécifications principales. Deux capacités encore en delta sont touchées par voisinage, sans que leurs exigences changent de sens : `lot/lancement-de-la-participation` (changement `apres-le-depot-un-seul-geste-et-son-resultat`) écrit le titre « 4. Lancer la participation », qui devient « 3. » quand l'étape des archives est absente ; la règle de numérotation est posée ici, et l'archivage devra la reporter. `lot/depot-sur-vigie-chiro` porte le téléversement lui-même, qui ne change pas.

## Impact

- Modèle : `ServiceLot` expose la forme du dépôt d'une nuit (la règle de #5677, déjà écrite pour la source du dépôt).
- ViewModels : `LotViewModel`, `SuiviEtapesLot`, `EtapesDepot` (trois ou quatre étapes), `FormatsLot`, `CompteRenduChiffreDepot` (nom de l'unité).
- Vues : `Lot.fxml` (carte de l'étape des archives masquable, titres numérotés liés), `EtapeTeleversement.fxml` et son contrôleur, `EtapeDeposerUI`.
- Aucun changement du moteur de dépôt, de la ligne de commande ni du blocage de la relance dans `DepotVigieChiro`.
- Captures du lot dans les deux modes, `docs/ecrans/lot.md`, recette S4.
- Le scénario filmé S4-47 sème une archive déjà déposée : sa nuit reste en mode ZIP, et son écran ne change pas.
