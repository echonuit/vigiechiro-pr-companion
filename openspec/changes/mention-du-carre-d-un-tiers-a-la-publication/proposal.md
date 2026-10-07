## Why

Le lien « Publier sur Vigie-Chiro » d'une carte de point et la case « Publier ce point sur Vigie-Chiro
après l'enregistrement » ont le même aspect sur le carré de l'utilisateur et sur celui d'un autre
observateur. L'application sait pourtant lequel elle a sous la main : la marque `site_tiers` (#2525) est
posée à chaque import d'un site distant, et ne servait qu'aux nuits opportunistes.

Le porteur a tranché le 7 octobre 2026 (#6132, chantier #6130) : l'écran le dit, par une mention. Ce
n'est ni un refus ni un garde, publier sur le carré d'un tiers étant l'usage majoritaire.

## What Changes

- Sur un carré marqué comme celui d'un tiers, une mention se lit sous le lien d'une carte de point qui
  offre de publier, et sous la case de la modale de création.
- Elle n'empêche rien, ne grise rien et ne demande aucune confirmation.
- Sans marque, rien ne s'affiche : carré à soi, carré jamais relié, propriétaire inconnu.

## Capabilities

### New Capabilities

- `sites/publication-d-un-point` : ce que l'écran dit avant de publier un point sur Vigie-Chiro. La
  capacité existait dans le code depuis #3458 sans spécification ; ce changement n'en écrit que
  l'exigence qu'il ajoute.

### Modified Capabilities

Aucune.

## Impact

- `sites/viewmodel/PublicationDepuisLaFiche`, `IntentionPublication`, `SiteDetailViewModel`,
  `sites/view/CartesPointsSite`, `ModalePoint.fxml` et son contrôleur, `SitesModule`.
- `docs/ecrans/sites.md` et `dev-docs/api-vigiechiro.md`.
- Aucune commande ne publie un point : la ligne de commande ne change pas.
- Les aperçus ne changent pas : l'injecteur de capture n'installe pas la publication, et aucun n'affiche
  le lien ni la case.
