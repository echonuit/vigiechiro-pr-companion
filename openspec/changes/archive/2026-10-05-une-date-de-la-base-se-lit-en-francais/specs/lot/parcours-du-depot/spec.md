## ADDED Requirements

### Requirement: La date de dépôt se lit en français

La date de dépôt d'un passage SHALL s'afficher sous la forme « 21/06/2026 » dans toute phrase et toute
colonne lue par un humain : l'état de l'écran de lot, le compte rendu de `deposer`, la ligne « Dépôt » de
`statut-passage`, et la colonne « Déposé le » des tables. Elle MUST NOT s'afficher sous sa forme de
stockage, ni se lire comme absente quand le passage est déposé. La forme de stockage est un instant
local, avec son heure : c'est elle que les lecteurs SHALL accepter.

Les sorties `--json` SHALL garder la valeur telle que la base la porte.

*Vérifié par* : un test du lecteur commun sur la forme que la production écrit, construite par
`LocalDateTime#toString` et non recopiée à la main ; les tests de `deposer`, de `statut-passage` et de
l'écran de lot, dont les témoins portent cette forme. Rendre la chaîne brute, ou n'accepter qu'une date
seule, les fait rougir.

#### Scenario: Un passage déposé le 21 juin à 8 h

- **WHEN** la base porte `2026-06-21T08:00:15.123456` comme date de dépôt
- **THEN** l'écran de lot dit « Passage déposé le 21/06/2026. », et la colonne « Déposé le » affiche
  « 21/06/2026 »

#### Scenario: La sortie de script

- **WHEN** `statut-passage --json` rend ce passage
- **THEN** la clé `deposeLe` porte la valeur de la base, inchangée
