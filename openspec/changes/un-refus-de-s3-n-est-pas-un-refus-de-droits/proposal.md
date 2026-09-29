## Why

Un `403` rendu par le stockage S3 de Vigie-Chiro est rangé avec les refus de droits de l'API, si
bien que l'écran de dépôt et la commande `deposer-vigiechiro` conseillent « Reconnectez-vous : elles
redeviendront reprenables », un geste qui ne peut rien pour une URL pré-signée. Samuel l'a suivi sur
la 2.193.0 (reconnexion, redémarrage) pour le même résultat. Le constat, le journal et la cause sont
dans #5598 ; le chantier est #5596.

Le lot 1 (#5597, fusionné par #5610) a levé la cause des `SignatureDoesNotMatch` du 14 septembre,
mais le classement reste faux pour tout refus de S3 à venir : URL expirée, droits du seau, horloge
décalée.

## What Changes

- Un refus définitif porte sa **provenance** : l'API Vigie-Chiro, ou son stockage S3. Elle se décide
  à l'émission, là où l'on sait quelle requête a échoué, et jamais depuis le texte de la réponse.
- Une troisième cause de refus, `STOCKAGE`, pour un `401` ou un `403` venu de S3. Les deux causes
  existantes gardent leur sens : `AUTHENTIFICATION` pour un `401` ou un `403` de l'API, `CONTENU`
  pour les autres `4xx`.
- Une reconnexion ne réarme plus qu'`AUTHENTIFICATION`, comme aujourd'hui, et `STOCKAGE` n'en est
  plus.
- Le compte rendu de l'écran et la commande disent, pour un refus du stockage, que se reconnecter
  n'y changera rien, et nomment les deux gestes qui s'appliquent : relancer le téléversement, qui
  redemande des URL neuves, puis le dépôt manuel depuis le dossier si le refus persiste.
- En multipart, la première étape en échec se distingue : URL de partie et finalisation (API), ou
  `PUT` d'une partie (S3).

Aucune rupture : la colonne `cause_refus` porte un nom de constante (`TEXT`, V41), et une valeur de
plus ne demande pas de migration.

## Capabilities

### New Capabilities

- `lot/depot-sur-vigie-chiro` : ce que le dépôt d'une nuit dit et fait d'un refus définitif, selon
  qu'il vient de l'API ou du stockage. Aucune spécification ne couvrait le dépôt : celle-ci n'en
  écrit que la part que ce changement touche, et les autres règles du dépôt la rejoindront à mesure
  qu'un changement les touchera.

### Modified Capabilities

Aucune.

## Impact

- `lot/model` : `CauseRefus`, `TeleverseurArchive.Resultat`, `EchecUnite`.
- `commun/api` : `ClientVigieChiro.deposerEnParts`, qui doit dire quelle étape a échoué.
- `lot/viewmodel/CompteRenduChiffreDepot` et `cli/commande/DeposerVigieChiro` : les deux surfaces
  (ADR 0014).
- `lot/outils/CaptureCompteRenduDepot` : l'aperçu qui montre le compte rendu, à étendre au refus du
  stockage.
- `docs/ecrans/lot.md` si la page décrit le conseil de reconnexion.
- Les bases existantes : des unités refusées par S3 avant ce changement portent
  `AUTHENTIFICATION`. Une reconnexion les réarmera encore une fois, ce qui est sans danger (elles
  redeviennent « à déposer », et la relance les retente de toute façon) ; `design.md` le dit.
