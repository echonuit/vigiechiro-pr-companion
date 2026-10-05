## Purpose

Lire une nuit d'enregistrement d'un coup d'œil, et agir sur elle : son identité, ses horaires, son statut, et
ce que l'application sait de sa participation. Cette capacité ne décrit pour l'instant que les dates et les
heures que la fiche compose dans ses libellés.

## ADDED Requirements

### Requirement: La plage horaire se lit en français et sans secondes

Le bandeau d'identité de la fiche d'un passage SHALL afficher la plage horaire de la nuit sous la forme
« 22/06/2026  20:25 -> 07:47 » : la date en français, puis l'heure de début et l'heure de fin sans leurs
secondes. Il MUST NOT recopier la date ni les heures telles que la base les stocke.

*Vérifié par* : `PassageViewModelTest`, sur le libellé, et `PassageVueIntegrationTest`, sur l'écran monté.

#### Scenario: Une nuit du 22 juin

- **WHEN** la base porte la date `2026-06-22` et les heures `20:25:00` et `07:47:00`
- **THEN** le bandeau affiche « 22/06/2026  20:25 -> 07:47 »

### Requirement: L'alerte de fenêtre saisonnière dit ses dates en français

Quand la date d'un passage tombe hors de la fenêtre de sa saison, l'alerte SHALL donner la date du passage et
les deux bornes de la fenêtre en français. Elle MUST NOT en donner une en forme ISO.

*Vérifié par* : `ServicePassageTest`.

#### Scenario: Un passage du 1er août pour une fenêtre close le 31 juillet

- **WHEN** le passage est du 1er août 2026 et que la fenêtre va du 15 juin au 31 juillet 2026
- **THEN** l'alerte porte « du 01/08/2026 » et « [15/06/2026 -> 31/07/2026] »

### Requirement: L'écart de nuit avec la participation se dit en français

Quand la nuit d'un passage diffère de celle que sa participation porte sur Vigie-Chiro, le message qui le
signale SHALL donner les deux dates en français, la locale et celle de la participation.

*Vérifié par* : `SynchronisationParticipationTest`.

#### Scenario: Un jour d'écart

- **WHEN** le passage est du 3 juillet 2026 en local et du 4 juillet 2026 sur la participation
- **THEN** le message porte « nuit du 03/07/2026 en local » et « du 04/07/2026 sur la participation »
