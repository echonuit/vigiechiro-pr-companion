# analyse/export-d-une-image Specification

## Purpose
Emporter un graphique ou une carte de l'application sous forme d'image, avec de quoi dire d'où elle vient. Cette
capacité ne décrit pour l'instant que la légende de l'image.

## Requirements

### Requirement: La légende d'une image exportée date l'export en français

L'image exportée SHALL porter une légende qui nomme l'application, sa version et la date de l'export, sous la
forme « exporté le 26/07/2026 ». Elle MUST NOT porter la date en forme ISO.

*Vérifié par* : `LegendeExportActiviteTest`.

#### Scenario: Export du 26 juillet 2026

- **WHEN** l'utilisateur exporte une image le 26 juillet 2026 avec la version 1.4.0
- **THEN** la légende se lit « VigieChiro Companion 1.4.0 · exporté le 26/07/2026 »
