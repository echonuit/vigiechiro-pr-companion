## ADDED Requirements

### Requirement: Une nuit du bilan porte sa date en français

Le libellé d'une nuit dans le bilan de récupérabilité SHALL donner sa date en français, après son point et son
numéro de passage. Il MUST NOT la donner en forme ISO.

*Vérifié par* : `ServiceRecuperabiliteTest`.

#### Scenario: Une nuit du 1er juillet au point Z41

- **WHEN** le bilan nomme une nuit du 1er juillet 2026 au point Z41
- **THEN** son libellé porte « Z41 » et « 01/07/2026 », et aucun « 2026-07 »
