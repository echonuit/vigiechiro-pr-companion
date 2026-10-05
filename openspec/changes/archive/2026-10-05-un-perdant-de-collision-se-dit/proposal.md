## Why

Quand deux tranches veulent le même nom, l'import garde `_000` pour la plus ancienne et renomme l'autre en `_001`. Kaleidoscope ne produit que des `_000` : une nuit réactivée depuis un dossier qu'il a découpé ne retrouve aucun de ses `_001`, et le compte rendu les range parmi les séquences introuvables, motif « aucun fichier de ce nom dans le dossier » (en erreur dans le terminal). Le motif envoie l'utilisateur chercher un fichier qui n'a jamais été sur son disque, et tait le geste qui le retrouverait (#5720, trouvé en instruisant le retour de Samuel #5604).

## What Changes

- La réactivation depuis un dossier **reconnaît** un perdant de collision : un nom horodaté dont le suffixe est `_001` ou au-delà. Un nom sans horodatage, où `_001` est un index de tranche, reste une absence ordinaire.
- Les deux comptes rendus de la réactivation, le chiffré de la modale et le textuel du terminal, les présentent **à part**, en avertissement, avec leur nombre, leur cause, le geste qui les retrouve (réactiver depuis les enregistrements bruts) et, au conditionnel, l'écart permanent possible : une nuit déposée avec Kaleidoscope n'aura jamais d'observations sur ces séquences. Les séquences introuvables, dans le constat du terminal comme dans le segment « Manquantes » de la barre et ses motifs, ne comptent plus que les vraies absences.
- La commande `reactiver` dit la même chose que l'écran. Sa sortie `--json` gagne la clé `perdantsDeCollision` ; `manquantes` garde son sens et compte toujours toutes les séquences non revenues.

## Capabilities

### New Capabilities

- `passage/reactivation-d-une-nuit` : la réactivation d'une nuit archivée, ouverte ici par les seules exigences de ce changement. Les autres comportements de la réactivation restent tenus par leurs tests et ne sont pas repris.

### Modified Capabilities

(aucune)

## Impact

`RebranchementSequences` (le motif décidé à l'origine `DOSSIER`), `BilanReactivation` et `RapportReactivation` (le compte des perdants), `CompteRenduChiffreReactivation` (la barre, les motifs et les mentions de la modale), `CompteRenduReactivation` (le constat du terminal), `Reactiver` (la clé JSON). Aucun changement de ce qui est rebranché : la régénération automatique des perdants depuis les bruts est écartée de ce changement, comme l'import par référence (#5719).
