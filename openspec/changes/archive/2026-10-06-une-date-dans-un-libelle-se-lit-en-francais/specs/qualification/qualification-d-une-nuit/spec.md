## Purpose

Écouter quelques séquences réparties sur une nuit pour dire si l'enregistrement est exploitable, et poser un
verdict sur le passage. Cette capacité ne décrit pour l'instant que la plage horaire de son bandeau.

## ADDED Requirements

### Requirement: La plage horaire de la Qualification est celle de la fiche du passage

Le bandeau de l'écran Qualification SHALL afficher la plage horaire de la nuit dans la forme que la fiche d'un
passage emploie, « 22/06/2026  20:25 -> 07:47 » (voir `passage/fiche-d-un-passage`). Les deux écrans MUST NOT
dire la même nuit de deux façons.

*Vérifié par* : `SelectionEcouteViewModelTest`, qui affirme la même chaîne que `PassageViewModelTest`.

#### Scenario: La même nuit sur les deux écrans

- **WHEN** l'utilisateur ouvre la Qualification d'une nuit du 22 juin 2026, de 20:25 à 07:47
- **THEN** le bandeau affiche « 22/06/2026  20:25 -> 07:47 », comme la fiche du passage
