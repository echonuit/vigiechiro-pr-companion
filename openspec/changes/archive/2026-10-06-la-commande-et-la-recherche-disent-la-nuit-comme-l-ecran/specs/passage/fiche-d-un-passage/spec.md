## ADDED Requirements

### Requirement: `statut-passage` dit la plage horaire comme la fiche du passage

La ligne « Nuit » de la commande `statut-passage` SHALL dire les heures de début et de fin sans leurs secondes,
comme la fiche du passage : « 15/06/2026  (21:30 → 05:45) ». Sa sortie `--json` garde les heures telles que la
base les porte : c'est un contrat de script.

*Vérifié par* : `StatutPassageTest`.

#### Scenario: Une nuit dont la base porte des secondes non nulles

- **WHEN** la base porte la nuit du `2026-06-15`, de `21:30:07` à `05:45:52`
- **THEN** la ligne « Nuit » se lit « 15/06/2026  (21:30 → 05:45) », sans aucune seconde
- **AND** la sortie `--json` rend `21:30:07` et `05:45:52`
