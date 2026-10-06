# recherche/recherche-globale Specification

## Purpose
Retrouver un site, un passage ou une espèce depuis n'importe quel écran, en tapant ce qu'on en sait. Cette
capacité dit ce qu'un résultat montre et ce qu'une requête trouve.

## Requirements

### Requirement: Le détail d'un résultat dit la date de la nuit en français

Le détail d'un résultat de passage et celui d'un résultat d'espèce SHALL dire la date de la nuit en français,
« 21/06/2026 », comme le reste de l'application, et jamais sous la forme que la base porte.

*Vérifié par* : `ServiceRechercheGlobaleTest`.

#### Scenario: Un passage du 21 juin 2026

- **WHEN** la recherche rend le passage enregistré le `2026-06-21`
- **THEN** son détail se lit « Passage 2026 · 21/06/2026 »

#### Scenario: Une espèce observée le 21 juin 2026

- **WHEN** la recherche rend une espèce observée la nuit du `2026-06-21`
- **THEN** son détail finit par « 21/06/2026 », et ne porte pas « 2026-06-21 »

### Requirement: Une nuit se cherche par la date qu'on lit, et encore par sa forme ISO

La recherche SHALL trouver une nuit quand la requête reprend la forme française de sa date, entière ou en partie,
et SHALL continuer de la trouver par la forme ISO.

*Vérifié par* : `ServiceRechercheGlobaleTest`.

#### Scenario: Chercher par le jour et le mois

- **WHEN** l'utilisateur tape `21/06`
- **THEN** la nuit du 21 juin 2026 est dans les résultats

#### Scenario: Chercher par la forme ISO

- **WHEN** l'utilisateur tape `2026-06`
- **THEN** la nuit du 21 juin 2026 est dans les résultats
