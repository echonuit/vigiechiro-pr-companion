# multisite/reconstruction-d-une-nuit Specification

## Purpose
Retrouver sur le poste une nuit déposée sur Vigie-Chiro et dont le passage local manque ou est incomplet. Cette
capacité ne décrit pour l'instant que la date du compte rendu.

## Requirements

### Requirement: Le compte rendu d'une reconstruction date la nuit à l'heure du site

Le compte rendu d'une nuit complétée SHALL nommer la nuit par sa date en français et son heure, à l'heure
murale du site : « Nuit du 03/07/2026 à 22:00 complétée ». Il MUST NOT recopier l'instant que la plateforme
porte, avec son décalage.

*Vérifié par* : `ReconstructionViewModelTest`.

#### Scenario: Une nuit commencée à 22 heures, heure du site

- **WHEN** la plateforme porte le début de la nuit à `2026-07-03T22:00:00+02:00`
- **THEN** le compte rendu porte « Nuit du 03/07/2026 à 22:00 », et aucun « 2026-07-03T »
