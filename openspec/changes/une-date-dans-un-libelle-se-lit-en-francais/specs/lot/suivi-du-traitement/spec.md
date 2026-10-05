## ADDED Requirements

### Requirement: `statut-passage` date l'import des résultats en français

Quand les résultats d'une nuit ont été importés, la commande `statut-passage` SHALL dire la date de cet import en
français, sans son heure : « importé le 21/06/2026 ». Sa sortie `--json` garde la valeur telle que la base la
porte : c'est un contrat de script.

*Vérifié par* : `StatutPassageTest`.

#### Scenario: Des résultats « Vu » importés le 21 juin

- **WHEN** la base porte l'import des résultats à `2026-06-21T08:00:00`
- **THEN** la ligne des résultats se lit « oui ("Vu", importé le 21/06/2026) »
