## Why

Connecté et en séquences WAV, l'écran de lot n'offre plus l'étape des archives ni le dépôt manuel depuis le
lot 25 (#5824). Quand le portail refuse des séquences sans que la reprise puisse les faire passer, il ne reste
donc aucune voie pour finir le dépôt, ni à l'écran ni dans `deposer-vigiechiro`. Le constat, les réponses du
porteur et son accord sont sur #5867.

## What Changes

- Une règle unique dit quand le repli s'offre : au moins une séquence du dépôt est refusée définitivement par
  le stockage ou pour son contenu. Un échec que la reprise peut lever ne l'offre pas, un refus de droits non
  plus (une reconnexion le réarme).
- L'écran de lot, connecté en forme WAV, offre alors une carte sans numéro, « Repli : déposer à la main », sous
  la carte du téléversement. C'est la carte des archives existante : elle génère les archives ZIP de la nuit,
  et les éléments du dépôt manuel reviennent avec elle. Le fil d'étapes reste à trois.
- `deposer-vigiechiro` dit le même repli après les mêmes refus (ADR 0014).
- Le compte rendu de l'écran renvoie au repli au lieu de « déposez-les manuellement depuis le dossier de la
  nuit ».
- Le dernier geste du repli a deux portes (réponse du porteur du 5 octobre 2026 sur #5867) : la carte porte
  « Marquer le passage déposé », et `deposer` ne re-prépare plus un dépôt déjà préparé ou entamé. Sans elles,
  un dépôt fini à la main restait « Dépôt en cours ».
- Rechargée depuis le plan enregistré, la table de dépôt garde le caractère définitif d'un refus : le bouton
  ne promet plus de reprise à la réouverture de l'écran.
- Le bilan de récupérabilité ne nomme le serveur que si toutes les unités du plan sont déposées en WAV.

## Capabilities

### New Capabilities

- `audit/bilan-de-recuperabilite` : d'où l'audio d'une nuit peut revenir, et ce qui interdit de nommer le
  serveur.

### Modified Capabilities

- `lot/parcours-du-depot` : l'étape des archives reste absente du fil en forme WAV, mais la carte et le dépôt
  manuel reviennent en repli après un refus sans recours ; la commande dit le même repli ; le dernier geste a
  ses deux portes ; un refus définitif le reste à la réouverture.

## Impact

- Modèle : `ServiceLot`, `DepotUniteDao` (une lecture du plan enregistré, aucune migration),
  `ServiceRecuperabilite`.
- Modèle de vue : `DepotViewModel`, `SuiviLignesDepot`, `CompteRenduChiffreDepot`.
- Vue : `Lot.fxml`, `LotController`, `EtapeDesArchives`, `EtapeTeleversementController`, `RepliManuelUI`.
- Ligne de commande : `DeposerVigieChiro`, `Deposer`.
- Hors du code : `docs/ecrans/lot.md`, la fiche M-Lot du brief, une session de recette, un aperçu neuf, et
  une ADR qui dit ce qu'elle change aux ADR 5677 et 5824.
- Le générateur d'archives n'est pas touché : il produit les archives de toute la nuit.
