## Why

À l'import d'une carte réutilisée, l'application juge « la nuit » d'après la **première date du
journal** de l'enregistreur. Ce journal s'allonge à chaque enregistrement sans jamais être vidé : sur
la carte de Samuel (2.193.0, 14 septembre), il commençait par une nuit importée le 29 août au carré
202016 puis effacée de la carte. L'écran a donc annoncé « Cette nuit a déjà été importée … carré 202016,
point G1 » au-dessus de trois nuits neuves, dont aucune n'était concernée. Le constat, la chronologie et
l'enquête sont dans #5600 (chantier #5596).

L'identité fausse n'alimente pas qu'un avertissement : la confirmation demandée au lancement de
l'import (#214) et le contrôle du numéro de passage (#2580) la lisent aussi.

## What Changes

- Les nuits jugées sont celles **présentes sur la carte**, c'est-à-dire celles de la table des nuits,
  tirées des noms des WAV. Le journal ne donne plus que le numéro de série de l'enregistreur.
- L'avertissement d'en-tête et la confirmation au lancement portent sur les nuits présentes déjà
  importées : les nuits **cochées** au lancement. Sur une carte à plusieurs nuits, chaque nuit concernée
  est nommée avec sa date.
- Le contrôle du numéro de passage ne reconnaît une nuit récupérée de Vigie-Chiro que si elle est parmi
  les nuits cochées.
- Une nuit que seul le journal cite, sans WAV sur la carte, n'est plus jamais jugée.

## Capabilities

### New Capabilities

- `importation/import-d-une-carte` : ce que l'import dit d'une carte dont des nuits ont déjà été
  importées ou récupérées. Aucune spécification ne couvrait l'import : celle-ci n'en écrit que la part
  touchée.

### Modified Capabilities

Aucune.

## Impact

- `importation/viewmodel` : `CompteRenduDInspection` (l'identité), `InspectionImportViewModel`
  (l'avertissement, la confirmation), `AvertissementsInspection` (la rédaction multi-nuits),
  `ControleNumeroPassage` et `ImportationViewModel` (le contrôle du n° de passage).
- `importation/view/ImportationController` : la confirmation, qui n'a pas à changer d'appel.
- Aucun changement de données. La table des nuits, déjà juste, ne change pas.
- Les trois tests de reproduction sont écrits et rouges (`InspectionImportViewModelTest`,
  `ImportationViewModelTest`, commit `79d085f60`).
