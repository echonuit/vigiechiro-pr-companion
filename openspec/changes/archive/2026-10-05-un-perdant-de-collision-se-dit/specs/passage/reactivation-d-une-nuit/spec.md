## Purpose

Ce que l'application garantit quand une nuit archivée retrouve son audio depuis un dossier désigné par
l'utilisateur, et ce que son compte rendu dit de ce qui n'est pas revenu. Ouverte par le cas des
perdants de collision ; les autres comportements de la réactivation restent tenus par leurs tests.

## ADDED Requirements

### Requirement: Un perdant de collision absent du dossier se dit pour ce qu'il est

Quand une réactivation depuis un dossier de séquences ne retrouve pas une séquence dont le nom est
horodaté et finit par `_001` ou au-delà, l'application SHALL la compter comme **perdant de
collision**, et non comme une séquence introuvable. Les comptes rendus de la réactivation, celui de
l'écran comme celui du terminal, SHALL présenter ces séquences à part, en avertissement, avec :

- leur nombre ;
- leur cause : des enregistrements qui se chevauchent, dont la tranche a été renommée `_001` à
  l'import, et qu'un dossier découpé par un autre outil, Kaleidoscope compris, ne contient pas ;
- le geste qui les retrouve : réactiver depuis les enregistrements bruts ;
- au conditionnel, l'écart qui peut être permanent : si la nuit a été déposée avec Kaleidoscope,
  Vigie-Chiro n'a jamais reçu ces séquences, qui n'auront pas d'observations.

Les séquences introuvables SHALL ne compter que les autres. À l'écran, la barre qui ventile les
séquences du passage SHALL leur donner un segment distinct de « Manquantes », et leurs noms SHALL
former un motif à part, distinct de « aucun fichier de ce nom dans le dossier ». Une réactivation où
seuls des perdants de collision manquent SHALL produire, dans le terminal comme à l'écran, un compte
rendu de sévérité avertissement, pas d'erreur.

La règle ne vaut que pour un nom horodaté : sans horodatage, le suffixe `_001` est l'index de la
deuxième tranche d'un enregistrement, et son absence SHALL rester une séquence introuvable.

*Vérifié par* : aucun dispositif encore ; ce changement écrit le test qui réactive, depuis un dossier
qui ne contient que les `_000`, une nuit portant une paire de collision, et lit le constat, son
compte et sa sévérité, sur les deux comptes rendus. Il est rouge avant le correctif, le perdant y
étant « aucun fichier de ce nom dans le dossier », compté parmi les « Manquantes », et en erreur dans
le terminal. Un second cas tient la règle du nom sans horodatage.

#### Scenario: un dossier découpé par Kaleidoscope
- **WHEN** une nuit importée depuis la carte, dont la base porte des séquences `_001`, est réactivée
  depuis un dossier qui ne contient que des `_000`
- **THEN** le compte rendu annonce ces séquences comme venues d'enregistrements qui se chevauchent,
  avec leur nombre et le conseil de réactiver depuis les enregistrements bruts, en avertissement, et
  ne les compte pas parmi les séquences introuvables

#### Scenario: la barre de la modale
- **WHEN** la modale ventile les séquences d'une nuit qui a laissé des perdants de collision
- **THEN** ils forment un segment à part, et non une part du segment « Manquantes »

#### Scenario: seuls des perdants manquent
- **WHEN** toutes les séquences de la nuit sont revenues sauf des perdants de collision
- **THEN** le compte rendu est un avertissement, et son titre reste « Réactivation partielle »

#### Scenario: un nom sans horodatage
- **WHEN** une séquence dont le nom ne porte pas d'horodatage et finit par `_001` est absente du
  dossier
- **THEN** elle reste une séquence introuvable, avec le motif « aucun fichier de ce nom dans le
  dossier »

### Requirement: La ligne de commande dit ce que l'écran dit des perdants de collision

La commande `reactiver` SHALL rendre le même constat que l'écran. Sa sortie `--json` SHALL porter la
clé `perdantsDeCollision`, le nombre de perdants de collision restés absents. La clé `manquantes`
SHALL garder son sens : le nombre de toutes les séquences qui ne sont pas revenues, perdants
compris, pour qu'un script existant lise la même valeur qu'avant.

*Vérifié par* : aucun dispositif encore ; ce changement écrit le test de projection JSON de
`reactiver` et un cas `bats` qui lance la commande sur une nuit portant une paire de collision. Le
texte vient du même compte rendu que l'écran, ce que la parité vérifie en comparant les deux rendus.

#### Scenario: la sortie JSON
- **WHEN** `reactiver --json` laisse deux perdants de collision et une séquence introuvable
- **THEN** la sortie porte `"perdantsDeCollision": 2` et `"manquantes": 3`

#### Scenario: la sortie texte
- **WHEN** `reactiver` laisse des perdants de collision
- **THEN** le terminal rend le constat des perdants de collision avec les mêmes phrases que l'écran
