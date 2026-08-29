## Why

Le numéro de carré est le seul champ obligatoire pour déclarer un site, et c'est celui que
l'observateur ne connaît pas quand il relève près de chez lui. Il doit ouvrir le portail, y trouver
son carré sur une carte, en recopier les six chiffres, puis revenir. Cette corvée se paie avant
l'import, parce qu'une nuit ne se téléverse pas sans site rattaché.

Le « pourquoi » public est dans l'EPIC [#4573](https://github.com/echonuit/vigiechiro-pr-companion/issues/4573),
qui porte ce chantier et ses cinq lots. Il vient de l'issue
[#733](https://github.com/echonuit/vigiechiro-pr-companion/issues/733),
qui demandait déjà de proposer le carré depuis la position plutôt que de le saisir à la main. Elle a
livré l'inverse de ce qu'elle demandait : un contrôle en aval, `ControleCarreStoc`, qui confronte les
coordonnées d'un point au carré déjà tapé. Son motif est écrit dans le code : le carré appartient au
site, il se saisit à sa création, « écran où aucune coordonnée n'est connue ».

Ce changement donne à cet écran une coordonnée. Le motif du refus tombe, et la moitié de #733 laissée
sur la table se livre sans rien changer au contrôle déjà en place.

Rien ne mesure aujourd'hui à quelle fréquence un observateur bute sur ce champ. Le besoin vient d'une
observation de terrain du porteur du produit, pas d'un comptage, et ce changement ne prétend pas le
contraire.

## What Changes

- La modale « Nouveau site » accepte une **position collée** (« 43.296482, 5.369780 ») et en déduit le
  numéro de carré, qu'elle dépose dans le champ des six chiffres. Le formulaire reste utilisable sans
  elle : la saisie manuelle du carré ne change pas.
- L'analyseur de position accepte le **décimal** et le **degré-minute-seconde**, dans l'ordre
  **latitude puis longitude**, qui est celui que produisent le « Copier les coordonnées » de Google
  Maps et le clic droit d'OpenStreetMap. Une URL de carte n'est **pas** acceptée : elle est refusée
  avec un motif qui dit quoi coller à la place.
- La proposition se fait au **rayon serré**. `ClientVigieChiro#carreStoc` fige aujourd'hui un rayon de
  10 000 m, lu dans `RAYON_CARRE_STOC_METRES`, pour rendre le carré voisin plutôt que rien. Ce
  relâchement est tenable pour un contrôle, où un humain a déjà tapé la vérité. Il ne l'est pas pour
  une proposition, qu'un utilisateur validera sans la relire. Le rayon devient un paramètre d'appel,
  et la proposition demande 1 500 m.
- Hors de la grille (mer, outre-mer, étranger), **rien n'est proposé**, et l'écran le dit comme une
  réponse et non comme une panne. Les carrés d'outre-mer sont numérotés `00xxxx`, constat de
  [#3298](https://github.com/echonuit/vigiechiro-pr-companion/issues/3298), et la grille STOC ne les
  porte pas.
- Le numéro rendu par la grille est **rembourré à six chiffres**. Mesuré le 2026-08-26 : la grille
  rend « 40110 » là où le catalogue déclare « Point Fixe-040110 », et `GET /sites?q=40110` ne trouve
  rien. R1 est juste, c'est la grille qui ampute le zéro des départements 01 à 09.
- **Le carré se déduit hors ligne.** Le carroyage national est déjà embarqué,
  `carrenat.csv.gz`, 137 481 mailles pour toute la métropole. Situer une position ne demande donc
  **rien au réseau**, et le geste fonctionne sans jeton. Mesuré le 2026-08-27 : le référentiel local
  reproduit `GET /grille_stoc/cercle` au centimètre.
- **La vérification reste un geste séparé, et c'est le seul qui appelle le réseau.** Situer remplit le
  champ du carré. « Vérifier sur Vigie-Chiro » garde son clic, ses conditions d'ouverture et son
  verdict : « ce carré existe-t-il en Point Fixe » est une question de portail, pas de géométrie.

Aucun comportement existant n'est retiré : ni la saisie manuelle, ni `ControleCarreStoc`, ni la
vérification. Pas de **BREAKING**.

## Capabilities

### New Capabilities

- `sites/declaration-de-carre` : ce que l'écran de déclaration d'un site garantit sur le numéro de
  carré - sa saisie, sa déduction depuis une position, et ce qu'il refuse de déduire.

### Modified Capabilities

Aucune. `openspec/specs/` ne porte encore aucune capacité : c'est le premier changement du dépôt, et
il n'a rien à modifier.

## Impact

**Code touché**

| Fichier | Ce qui bouge |
|---|---|
| `commun/api/ClientVigieChiro.java` | `carreStoc` prend un rayon en paramètre ; l'appelant existant garde les 10 000 m |
| `commun/api/ReponsesVigieChiro.java` | `numeroCarreStoc` rembourre le numéro à six chiffres |
| `sites/model/` | Un analyseur de position collée, classe pure, et un service qui enchaîne analyse puis `carreStoc` |
| `sites/viewmodel/SiteEditViewModel.java` | Le champ de position, son verdict, et le dépôt du numéro dans le champ du carré |
| `sites/view/ModaleSiteController.java` | Le champ, son bouton et le libellé du verdict |
| `commun/view/carte/` vers le modèle | Le référentiel du carroyage sort d'un paquet de vue |
| `docs/ecrans/sites.md` | La section « Déclarer un site » décrit le nouveau geste |

**API**

**Aucune, pour la proposition.** Elle se calcule sur le référentiel embarqué.

`GET /grille_stoc/cercle?lng&lat&r` reste appelée par le contrôle en aval, inchangé. Le contrat est relevé dans
`dev-docs/api-vigiechiro.md` : trois paramètres obligatoires, résultats triés par distance croissante
via un `$near` MongoDB. Le rayon est le `$maxDistance` de ce `$near`, en mètres. Aucun endpoint neuf.

**Dépendances**

Aucune. L'analyse de la position est locale, et le seul appel réseau existe déjà.

**Ce qui ne bouge pas**

Le **code** de `ControleCarreStoc` ne change pas : il garde son rayon de 10 000 m, ses trois verdicts
et son rôle de confort en aval. Le catalogue `CatalogueApi` ne change pas non plus, et il n'y a aucune
migration de base.

Son **comportement**, lui, change, et il faut le dire plutôt que de laisser lire l'inverse. Il
comparait « 40110 » à « 040110 » par `equals` et rendait donc `Diverge` à tort dans les départements
01 à 09. Le rembourrage le répare par ricochet. La garde qui aurait dû l'attraper, et la sonde live
aveugle qui l'a laissé passer, vivent en
[#4592](https://github.com/echonuit/vigiechiro-pr-companion/issues/4592) : hors de ce changement.
