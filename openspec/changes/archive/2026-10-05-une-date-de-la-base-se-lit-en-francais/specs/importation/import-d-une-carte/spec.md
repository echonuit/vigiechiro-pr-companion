## ADDED Requirements

### Requirement: La date d'une nuit se lit en français dans la table

Sur une carte de plusieurs nuits, la colonne « Nuit du » de la table des nuits SHALL afficher la date de
chaque nuit sous la forme « 03/07/2026 », celle que l'avertissement et la confirmation du même écran
emploient. Elle MUST NOT afficher la forme ISO. La colonne SHALL se trier dans l'ordre des dates.

*Vérifié par* : un test de la table qui lit le texte dessiné dans la cellule, et le scénario multi-nuits
qui retrouve une ligne par sa date affichée.

#### Scenario: Trois nuits de juillet

- **WHEN** l'inspection détecte les nuits des 3, 4 et 5 juillet 2026
- **THEN** la colonne « Nuit du » affiche « 03/07/2026 », « 04/07/2026 » et « 05/07/2026 »
