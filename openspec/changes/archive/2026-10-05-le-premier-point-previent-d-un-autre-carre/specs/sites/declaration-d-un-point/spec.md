## ADDED Requirements

### Requirement: La case du premier point prévient d'un autre carré

À la déclaration d'un site, quand la case « Créer aussi le premier point d'écoute à cette position » est
cochée et que la position collée tombe sans ambiguïté dans un autre carré que le numéro saisi, la modale
SHALL l'afficher sous la case, en avertissement, en nommant le carré de la position et le carré saisi.
L'avertissement MUST NOT empêcher de créer le site ni son point.

Il MUST NOT s'afficher quand le numéro concorde, quand la case est décochée, quand le numéro saisi n'a
pas ses six chiffres, ni quand la position est sur une frontière entre deux carrés. Le carré de la
position SHALL se lire sur le carroyage embarqué, celui du bouton « Situer » : le verdict vaut hors
connexion. Sa phrase SHALL être celle que la modale de point emploie pour la même divergence.

*Vérifié par* : un test de ViewModel sur les cinq cas (autre carré, concordance, case décochée, frontière,
numéro incomplet), qui exige la phrase de la modale de point par le même type et constate que la création
aboutit ; un test d'interface qui lit l'avertissement, le bouton « Créer » resté offert, puis sa
disparition après « Situer ». Taire la divergence fait rougir les deux.

#### Scenario: Un numéro tapé, une position ailleurs

- **WHEN** l'observateur tape le carré 130711, colle une position du carré 040110 sans cliquer « Situer »,
  et coche la case
- **THEN** un avertissement nomme le carré 040110 et le carré 130711, et « Créer » reste offert

#### Scenario: Après « Situer »

- **WHEN** l'observateur clique « Situer »
- **THEN** le numéro devient 040110 et l'avertissement disparaît

#### Scenario: Sur une frontière

- **WHEN** la position collée est à distance égale de deux carrés
- **THEN** aucun avertissement ne s'affiche
