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
- **La vérification reste un geste séparé.** Situer une position remplit le champ du carré, rien de
  plus. « Vérifier sur Vigie-Chiro » garde son clic, ses conditions d'ouverture et son verdict.

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
| `sites/model/` | Un analyseur de position collée, classe pure, et un service qui enchaîne analyse puis `carreStoc` |
| `sites/viewmodel/SiteEditViewModel.java` | Le champ de position, son verdict, et le dépôt du numéro dans le champ du carré |
| `sites/view/ModaleSiteController.java` | Le champ, son bouton et le libellé du verdict |
| `docs/ecrans/sites.md` | La section « Déclarer un site » décrit le nouveau geste |

**API**

`GET /grille_stoc/cercle?lng&lat&r`, déjà appelée. Le contrat est relevé dans
`dev-docs/api-vigiechiro.md` : trois paramètres obligatoires, résultats triés par distance croissante
via un `$near` MongoDB. Le rayon est le `$maxDistance` de ce `$near`, en mètres. Aucun endpoint neuf.

**Dépendances**

Aucune. L'analyse de la position est locale, et le seul appel réseau existe déjà.

**Ce qui ne bouge pas**

`ControleCarreStoc` et son verdict `Concorde` / `Diverge` / `HorsGrille` restent tels quels : ils
gardent le rayon de 10 000 m et leur rôle de confort en aval. Le catalogue `CatalogueApi` ne change
pas. Aucune migration de base.
