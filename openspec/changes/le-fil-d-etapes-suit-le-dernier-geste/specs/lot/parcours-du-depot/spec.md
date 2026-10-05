## ADDED Requirements

### Requirement: La dernière étape du fil suit le dernier geste

La dernière étape du fil d'étapes SHALL porter le nom que le bouton de la dernière carte porte :
« Marquer déposé » tant qu'aucune participation n'est liée au passage, « Lancer la participation » dès
qu'une participation est liée. Le fil et la carte MUST NOT nommer le même geste de deux façons.

Sur une nuit déjà sur la plateforme dont la participation est liée, cette étape SHALL rester l'étape
**courante** tant que l'analyse n'est ni demandée ni faite, et n'être franchie qu'ensuite. Sans
participation liée, une nuit marquée déposée a tout son fil franchi. Tant que la nuit n'est pas sur la
plateforme, le dernier geste ne déplace pas l'étape courante.

*Vérifié par* : un test du geste appliqué à un fil calculé (nom et état, en trois comme en quatre étapes,
pour chacun des trois états du geste), et un test d'interface qui confronte la dernière puce au texte du bouton, puis
la lit courante avant le lancement et franchie une fois l'analyse planifiée. Figer le nom, figer l'état
ou ignorer l'analyse demandée les fait rougir.

#### Scenario: Nuit déposée, participation à lancer

- **WHEN** l'observateur ouvre le lot d'une nuit que l'application a déposée, sans avoir lancé la
  participation
- **THEN** la dernière étape du fil dit « Lancer la participation » et elle est l'étape courante

#### Scenario: Analyse demandée

- **WHEN** l'observateur lance la participation et que le relevé rend l'analyse planifiée
- **THEN** la dernière étape du fil est franchie, et le bouton n'est plus offert

#### Scenario: Dépôt marqué à la main

- **WHEN** aucune participation n'est liée et que le passage est marqué déposé
- **THEN** la dernière étape dit « Marquer déposé » et tout le fil est franchi

### Requirement: La confirmation de réinitialisation suit la forme du dépôt

La question posée par « Réinitialiser le dépôt » SHALL dire ce que la réinitialisation conserve dans les
mots de la forme du dépôt : les archives ZIP sur disque et la participation quand l'étape des archives
est offerte, la participation seule sinon. Elle MUST NOT nommer d'archives pour un dépôt en séquences.
L'infobulle du bouton et la question SHALL lire la même phrase.

*Vérifié par* : un test d'interface qui lit la question posée sous les deux formes, sans confirmer.

#### Scenario: Dépôt en séquences

- **WHEN** l'observateur clique « Réinitialiser le dépôt » sur un dépôt connecté en séquences WAV
- **THEN** la question dit « La participation Vigie-Chiro est conservée. » et ne nomme aucune archive
