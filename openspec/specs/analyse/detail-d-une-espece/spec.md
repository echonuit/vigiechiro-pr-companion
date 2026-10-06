# analyse/detail-d-une-espece Specification

## Purpose
Lire, espèce par espèce, les observations qui la portent : où, quand et avec quelle proposition. Cette capacité
ne décrit pour l'instant que la colonne « Passage » de ce détail.

## Requirements

### Requirement: La colonne « Passage » dit la date en français

Dans le détail d'une espèce, la colonne « Passage » SHALL afficher la date du passage sous la forme
« 22/06/2026 », suivie de son numéro : « 22/06/2026 · n°1 ». Elle MUST NOT afficher la date telle que la base la
stocke. Une date que la base ne porte pas lisible SHALL s'afficher telle quelle, plutôt que de disparaître.

*Vérifié par* : `ColonnePassageDesObservationsTest`, qui lit le texte dessiné dans les cellules d'une table
montée, et `PassageObserveTest`, pour la date illisible.

#### Scenario: Trois passages de deux mois

- **WHEN** l'espèce a été observée le 22 juin 2026 aux passages 1 et 2, et le 1er juillet 2026 au passage 3
- **THEN** les cellules affichent « 22/06/2026 · n°1 », « 22/06/2026 · n°2 » et « 01/07/2026 · n°3 »

### Requirement: La colonne « Passage » trie dans le temps

Triée, la colonne « Passage » SHALL ranger les passages par date, puis par numéro pour une même date. Elle
MUST NOT les ranger comme un texte : en français, le 1er juillet passerait avant le 22 juin. Un passage dont la
date est illisible SHALL se ranger après les autres.

*Vérifié par* : `ColonnePassageDesObservationsTest`, sur la table triée, et `PassageObserveTest`, sur la règle
d'ordre seule.

#### Scenario: Tri croissant sur deux mois

- **WHEN** l'utilisateur trie la colonne « Passage » sur les trois passages ci-dessus
- **THEN** l'ordre est « 22/06/2026 · n°1 », « 22/06/2026 · n°2 », « 01/07/2026 · n°3 »
