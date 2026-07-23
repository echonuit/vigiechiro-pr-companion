# ADR 0013 — Un passage local est ancré à sa participation serveur par un lien explicite

- **Statut** : Accepté — rétroactif
- **Chantier** : EPIC #720 (intégration API VigieChiro)
- **Vérification** : humaine — l'ancrage explicite du passage à sa participation est un comportement de dépôt ; sa présence structurelle ne capture pas la décision

## Contexte

L'application était un **miroir local** d'un flux que l'utilisateur faisait à la main sur la plateforme (import CSV Tadarida, corrections, export `_Vu`, dépôt au navigateur). En se branchant sur l'API VigieChiro, elle devient un **client de bout en bout** : elle dépose, suit le traitement, réimporte les résultats. Pour cela, un passage local (une **nuit**) doit savoir **à quelle participation serveur** il correspond - sinon il ne peut ni se re-déposer, ni se re-vérifier, ni rapatrier ses observations.

## Décision

Un passage local porte un **ancrage explicite** vers sa participation VigieChiro : un `LienVigieChiro` (`ENTITE_PASSAGE` → `idParticipation`), posé au dépôt ou à la reconstruction. Le **vocabulaire** distingue les deux mondes : localement l'objet est un **passage** (une nuit) ; « participation » est le mot du **serveur**.

Le contrat de l'API est traité comme **donné, pas négociable** : identifiants **`ObjectId`** (le `numero` est refusé), dates en **RFC 1123 UTC**, `_etag` obligatoire pour un `PATCH`, site **verrouillé** requis pour créer une participation.

## Conséquences

- Un passage ancré **se re-dépose** et **se re-vérifie** : l'ancrage est la clé de la synchronisation (dépôt délégué, import des résultats).
- Un passage **reconstruit** depuis la plateforme est ancré dès sa création : il retrouve ce que le serveur en sait (cf. [ADR 0005](0005-reactivation-cascade-de-preuves-archive-etat-observe.md)).
- Les particularités du contrat (ObjectId, dates, `_etag`) sont **isolées** dans le transport ([ADR 0007](0007-retours-http-type-scelle-reponse-api.md)) et vérifiées par une suite de contrat live.

## Alternatives écartées

- **Rapprocher passage et participation à la volée** (par date/point à chaque besoin). Fragile (deux nuits proches, un point renommé) et coûteux ; un lien persistant tranche une fois pour toutes.
- **Adopter le vocabulaire serveur partout** (« participation »). Éloigne le modèle local du langage de l'utilisateur de terrain, qui pense en **nuits** et en **passages**.
