## Why

L'application ne sait désigner un fichier que par le dialogue **natif** du système. Ce dialogue vit
hors de la fenêtre, donc hors de ce que le produit maîtrise : il ne se montre pas dans une
documentation filmée, il ne s'éprouve pas, et son apparence change d'une plateforme à l'autre. Le
« pourquoi » complet est dans #5282 et #5285.

Le besoin est venu du porteur du produit le 2026-09-05, dans ces termes : un sélecteur de repli
**fonctionnel et testé**, pour obtenir des gestes continus et cohérents de bout en bout.

## What Changes

- Une **seconde implémentation** du port `SelecteurFichier`, dessinée par l'application : une modale
  de son style, qui porte les trois gestes du contrat.
- Un **réglage** qui choisit entre le dialogue natif et celui de l'application. Le natif reste le
  défaut : ce que voit l'utilisateur ordinaire ne change pas.
- Une **couture unique** où ce choix s'applique. Aujourd'hui il n'y en a aucune : le sélecteur natif
  est construit **douze fois** en dur (`new SelecteurFichierModifiable(new SelecteurFichierJavaFx(...))`,
  compté sur `src/main/java` le 2026-09-06). Ajouter un repli sans couture dupliquerait le choix
  douze fois de plus.

Aucune rupture : le comportement par défaut est celui d'aujourd'hui.

## Capabilities

### New Capabilities

- `commun/designation-d-un-fichier` : désigner un dossier, un fichier existant, ou l'endroit où
  enregistrer. Le geste traverse les écrans - importation, qualification, analyse, audio, passage,
  multisite, audit, réglages - et il en existe **vingt-cinq** appels en production, répartis en huit
  `choisirDossier`, cinq `choisirFichier` et douze `enregistrerFichier` (comptés le 2026-09-06). La
  capacité couvre le geste et son réglage, pas l'écran qui l'appelle.

### Modified Capabilities

Aucune. Les trois capacités existantes - `diagnostic`, `passage`, `sites` - ne changent pas
d'exigence : elles appellent ce geste sans le décrire.

## Impact

**Code.** `src/main/java/fr/univ_amu/iut/commun/view/` : le port `SelecteurFichier` ne change pas de
signature, une implémentation s'ajoute, et les douze constructions en dur passent par la couture. Le
réglage rejoint `OngletReglagesEmplacements`, qui porte déjà les emplacements.

**Ce que cela rend possible ailleurs**, sans être livré ici : le banc filmé pose le réglage et
obtient un parcours continu, ce qui est l'objet du lot 4 de #5282. Le film montrera alors une
configuration qui **existe**, et non une maquette.

**Ce que cela ne règle pas.** Le bac à sable Flatpak borne ce que l'application peut lire, et un
dialogue dessiné ne change pas une permission ([ADR 0048], `docs/ecrans/reglages.md`). Il peut en
revanche laisser **saisir** un chemin, ce que le natif ne permet pas toujours.

**Dépendances.** Aucune nouvelle. Le patron existe déjà dans le dépôt : `ChoixSauvegarde` /
`ChoixSauvegardeJavaFx` / `ContenuChoixSauvegarde` est une modale dessinée par l'application derrière
un port synchrone, dont le contenu s'éprouve sans fenêtre.

**Un mot déjà pris.** `ChoixSauvegardeJavaFx` porte un paramètre nommé `repli` qui rend la main **au
sélecteur natif**. Celui de ce changement va en sens inverse. Les deux ne peuvent pas garder le même
nom sans que le prochain lecteur se trompe.

[ADR 0048]: https://companion-dev.echonuit.fr/decisions/0048-l-utilisateur-possede-ses-fichiers-l-app-observe/
