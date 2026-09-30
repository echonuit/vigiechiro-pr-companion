## Purpose

Dire à l'observateur ce qu'il peut faire d'un fichier que Vigie-Chiro a refusé pendant le dépôt d'une
nuit, et lui permettre de suivre réellement le geste conseillé, y compris quand le dépôt est déjà
entamé.

## ADDED Requirements

### Requirement: Un dépôt entamé se régénère

Le système SHALL admettre la génération des archives de dépôt d'un passage dont le dépôt est entamé
(« Dépôt en cours »), comme il l'admet pour un passage « Prêt à déposer » ou « Déposé ».

Les archives régénérées SHALL porter les mêmes identifiants que celles du plan de dépôt, tant que la
liste des séquences est inchangée : ce qui est déjà en ligne le reste, et une relance ne renvoie que ce
qui manque.

*Vérifié par* : un banc de bout en bout, sur base réelle, qui dépose, provoque un contenu refusé,
régénère par le service de lot **sans réponse simulée pour la génération**, relance, et constate l'unité
déposée. À écrire. Le test de #3946 simulait la génération et ne pouvait pas voir le refus.

#### Scenario: Régénérer après un contenu refusé

- **WHEN** une archive a été refusée pour son contenu, le passage est « Dépôt en cours », et
  l'observateur demande la génération des archives
- **THEN** les archives sont régénérées, et la relance du téléversement les retente

#### Scenario: Ce qui est en ligne le reste

- **WHEN** onze archives sur quatorze sont en ligne et l'observateur régénère les archives
- **THEN** une relance ne renvoie que les trois qui manquent

### Requirement: Pas de génération pendant un téléversement

Le système SHALL refuser la génération des archives d'un passage dont un téléversement est **en train
de tourner** dans l'application, en le disant et en nommant le geste : attendre la fin du
téléversement, ou l'annuler.

La garde SHALL tenir dans les deux sens de la course : elle s'arme au début du téléversement et se
lève à sa fin, qu'il aboutisse, échoue ou soit annulé.

*Vérifié par* : des cas unitaires sur le registre des téléversements en cours (armé, levé après succès,
levé après exception), et un cas sur le service de lot qui refuse pendant un téléversement armé. À
écrire.

#### Scenario: Générer pendant un téléversement

- **WHEN** un téléversement du passage est en cours et l'observateur demande la génération
- **THEN** la génération est refusée, avec un message qui dit qu'un téléversement tourne et qu'il faut
  attendre sa fin ou l'annuler, et aucune archive n'est écrite

#### Scenario: Générer après un téléversement interrompu

- **WHEN** un téléversement a échoué ou a été annulé, et l'observateur demande la génération
- **THEN** la génération est admise

### Requirement: « Préparez-le d'abord » ne se dit qu'à un passage non préparé

Le refus qui demande de préparer le dépôt SHALL ne s'adresser qu'à un passage qui ne l'est pas : ni
« Prêt à déposer », ni « Dépôt en cours », ni « Déposé ». Il SHALL ne jamais s'afficher quand l'écran
montre le jalon « Prêt à déposer ».

*Vérifié par* : les cas du service de lot sur chaque statut, un par statut. À étendre.

#### Scenario: Un passage vérifié mais pas préparé

- **WHEN** le passage est « Vérifié » et l'observateur demande la génération
- **THEN** le refus demande de préparer le dépôt d'abord

#### Scenario: Un dépôt entamé

- **WHEN** le passage est « Dépôt en cours » et aucun téléversement ne tourne
- **THEN** aucun refus ne demande de préparer le dépôt
