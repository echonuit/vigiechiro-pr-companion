---
type: adr
title: "Une partie multipart part sans le type que sa signature ne couvre pas"
status: stable
article: A17
chantier: "#5597, lot 1 du chantier #5596 (retour de terrain 2.193.0)"
decided_at: 2026-09-30
verification: certaine
enforced_by:
  - "src/test/java/fr/univ_amu/iut/commun/api/SignatureS3DepotTest.java"
verification_note: "le test recalcule la signature S3 que le serveur pose sur l URL d une partie et montre qu un type envoyé la contredit. Il ne joue pas un vrai S3 : le dépôt réel d une nuit entière a été vérifié à la main le 30 septembre 2026, avec l AppImage 2.195.0"
verified:
  - by: machine:ci
    at: 2026-10-05
generated:
  by: "process:assistance-par-agents"
  at: 2026-10-05
---

# Une partie multipart part sans le type que sa signature ne couvre pas
## Contexte

Aucun dépôt connecté n'aboutissait depuis juillet 2026 : S3 refusait chaque archive en
`SignatureDoesNotMatch`. Le journal de Samuel du 14 septembre (2.193.0) a été le premier à en porter
la cause, depuis que #3469 a cessé de jeter le corps du refus.

Le serveur Vigie-Chiro signe l'URL d'une **partie** multipart sans `Content-Type`
(`fichier_multipart_continue` dans `vigiechiro-api`). Le client envoyait le type mime de l'archive. En
signature S3 v2, le type fait partie de la chaîne signée : les deux signatures ne pouvaient pas
coïncider. Le seuil multipart est de 5 Mo et une archive réelle pèse de 38 à 377 Mo, donc toutes
passaient par ce chemin. Les bancs, eux, restaient verts : le bouchon acceptait n'importe quelle
partie (voir l'[ADR 4356](4356-le-bouchon-n-apprend-pas-ce-que-le-serveur-reel-eprouve.md)).

## Décision

**`TransportVigieChiro.deposerPartie` n'envoie aucun `Content-Type`.** Le client envoie exactement ce
que le serveur a signé, ni plus ni moins. Le dépôt d'un fichier en un seul bloc, lui, garde son type :
son URL est signée avec.

## Ce que cette décision n'autorise pas

Elle n'autorise pas à « remettre le type par propreté ». Un `PUT` sans type ressemble à un oubli, et
c'est le correctif qu'un lecteur ferait de bonne foi. Il casserait tous les dépôts sans faire rougir
un seul banc à bouchon.

## Conséquences

La règle dépend de ce que le serveur signe, pas de ce que S3 accepte : si `vigiechiro-api` change sa
signature, c'est ici qu'il faudra suivre. `SignatureS3DepotTest` recalcule la chaîne signée pour que
l'écart se voie sans réseau.
