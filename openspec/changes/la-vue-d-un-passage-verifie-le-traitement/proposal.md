## Why

Depuis #5784, « Actualiser » sur la carte « Traitement Vigie-Chiro » de l'écran de lot relève l'état de
l'analyse et, si elle est terminée, importe les observations. `etat-traitement-vigiechiro --importer` fait
de même. La vue d'un passage, elle, ne sait rien du traitement : elle ne porte que le lien vers la
participation sur le portail (#1124).

Qui ouvre un passage déposé doit donc aller jusqu'à l'écran de lot pour savoir si l'analyse est finie et
récupérer ses observations. Le porteur a demandé le 5 octobre 2026 que la vue du passage l'offre (#5862).

## What Changes

- La vue d'un passage gagne un bouton « Vérifier le traitement », à côté de « Voir la participation ».
- Le bouton relève l'état de l'analyse et, si elle est terminée et que la nuit n'a pas ses observations,
  les importe. C'est le geste de l'écran de lot, par la même règle.
- Le résultat se lit dans le bandeau de retour de la vue, dans les mots de l'écran de lot.
- Sans participation liée, ou hors connexion, le bouton reste visible, grisé, et dit pourquoi.
- Les phrases du traitement quittent `lot/viewmodel` pour le socle, sans changer d'un mot, pour que les
  deux écrans lisent le même texte.

## Capabilities

### New Capabilities

Aucune.

### Modified Capabilities

- `lot/suivi-du-traitement` : la capacité gagne une seconde surface, la vue d'un passage.

## Impact

- `passage/view` (`Passage.fxml`, `PassageController`, `CartesActionPassage`), `passage/viewmodel`.
- `lot/viewmodel/FormatsTraitement`, déplacé vers `commun/viewmodel` ; `lot/viewmodel/TraitementViewModel`
  pour son import.
- `passage/outils/CapturePassage` et ses aperçus, `docs/ecrans/passage.md`, la fiche M-Passage du brief,
  un cas de recette.
- Aucun changement de la ligne de commande, qui porte déjà le geste, ni de l'écran de lot.
