## Why

Quand le jeton Vigie-Chiro est périmé ou refusé par la plateforme, les gestes de plateforme restent
ouverts et le message reçu au clic ne dit pas quoi faire. Mesuré sur `main` à `df6826e104` (#6151) : sur
les trois écrans du port `EtatConnexion`, le bouton reste actif ; un `401` refait la même requête refusée
à chaque clic ; rouvrir la modale de connexion ne change rien. La page de connexion promet pourtant
qu'avec un jeton expiré, ces actions sont « grisées avec une explication ».

Le porteur a tranché le 12 octobre 2026 (#6151, chantier #6130) : un `401` de l'API efface le jeton
enregistré, comme le fait déjà la vérification `/moi` ; et le dépôt reste réarmable.

## What Changes

- Un `401` de l'API efface le jeton enregistré, en un seul endroit, le transport, et seulement s'il est
  celui du stockage. Un refus du stockage des fichiers n'efface rien, un jeton ponctuel non plus.
- Les trois écrans du port relisent l'état de connexion au retour de leurs gestes de plateforme, sur le
  fil JavaFX ; la modale de connexion le relit à son ouverture.
- Les messages d'un jeton absent, expiré ou refusé nomment le geste qui répare et l'endroit.
- Le dépôt range l'appel parti sans jeton en cause **droits** : réarmé à la reconnexion. **Changement
  voulu** : un dépôt lancé sans aucun jeton change de classement de la même façon.
- **Effet accepté** : une commande qui reçoit un `401` sur le jeton enregistré l'efface, et l'application
  se retrouve déconnectée.

## Capabilities

### New Capabilities

- `connexion/etat-de-connexion` : ce que l'application fait d'un jeton qui cesse de valoir, et ce que
  les écrans en montrent. La capacité existait dans le code depuis #4205 sans spécification ; ce
  changement n'en écrit que les exigences qu'il ajoute.

### Modified Capabilities

- `lot/depot-sur-vigie-chiro` : la cause **droits** couvre aussi l'appel qui n'est pas parti faute de
  jeton.

## Impact

- `commun.api` : `TransportVigieChiro`, `FournisseurToken`, `RapportSynchro`, `GesteDeReconnexion`.
- `commun.viewmodel.EtatConnexion` ; `connexion` : `StockageConnexion`, `ConnexionModule`,
  `RefletDuJeton`, `ConnexionModaleController`.
- `sites` : `MesSitesController`, `ModaleSiteController`, `SiteDetailController`, `CartesPointsSite`,
  `RapprochementSites`, `RechercheCarreExistant`, `RapatriementCarre`, `PublicationPoint`,
  `PublicationDepuisLaFiche`, `SitesViewModel`.
- `lot.model.CauseRefus`.
- Le banc de recette lie sa réserve de jeton elle-même, pour qu'un `401` l'efface comme en production.
- `docs/connexion-vigiechiro.md`, `docs/ecrans/sites.md`, `dev-docs/patterns.md`.
