# lot/depot-sur-vigie-chiro Specification

## Purpose
Dire à l'observateur ce qu'il peut faire d'un fichier que Vigie-Chiro a refusé pendant le dépôt d'une
nuit, selon que le refus vient de l'API de la plateforme ou de son stockage S3, et ne réarmer que ce
qu'un geste peut réellement réparer.

## Requirements

### Requirement: Un refus porte sa provenance

Quand un fichier est refusé définitivement pendant le dépôt, le système SHALL retenir si le refus
vient de l'API Vigie-Chiro (déclaration du fichier, demande d'URL de partie, finalisation) ou de son
stockage S3 (le `PUT` des octets vers une URL pré-signée).

Le système SHALL décider cette provenance d'après la requête qui a échoué, et jamais d'après le texte
de la réponse.

*Vérifié par* : un banc de `TeleverseurArchive`, qui n'en a aucun aujourd'hui, provoquant un `403` à
chacune des étapes (dépôt d'un seul bloc, URL de partie, `PUT` de partie, finalisation) et lisant la
cause retenue.

#### Scenario: Le stockage refuse le dépôt d'un seul bloc

- **WHEN** le `PUT` S3 d'un fichier déposé en un seul bloc est refusé en `403`
- **THEN** le refus est retenu comme venant du stockage

#### Scenario: Le stockage refuse une partie

- **WHEN** le `PUT` S3 d'une partie d'un dépôt en parties est refusé en `403`
- **THEN** le refus est retenu comme venant du stockage

#### Scenario: L'API refuse la finalisation

- **WHEN** toutes les parties sont déposées et la finalisation est refusée en `403`
- **THEN** le refus est retenu comme venant de l'API

### Requirement: Trois causes de refus définitif

Le système SHALL ranger un refus définitif dans l'une de trois causes :

- **droits** : un `401` ou un `403` de l'API ;
- **stockage** : un `401` ou un `403` du stockage S3 ;
- **contenu** : tout autre refus définitif, quelle que soit sa provenance.

Un refus rejouable (`429`, `5xx`, coupure) SHALL ne porter aucune cause, comme aujourd'hui.

*Vérifié par* : des cas unitaires sur la règle de classement, un par couple statut et provenance.

#### Scenario: Un 403 de l'API

- **WHEN** l'API répond `403` à la déclaration d'un fichier
- **THEN** la cause est **droits**

#### Scenario: Un 403 du stockage

- **WHEN** S3 répond `403` au `PUT` d'un fichier
- **THEN** la cause est **stockage**

#### Scenario: Un 400 du stockage

- **WHEN** S3 répond `400` au `PUT` d'un fichier
- **THEN** la cause est **contenu**

### Requirement: Une reconnexion ne réarme que les refus de droits

Après une reconnexion réussie, le système SHALL rendre « à déposer » les fichiers refusés pour
**droits**, et SHALL laisser en l'état ceux refusés pour **stockage** ou pour **contenu**.

*Vérifié par* : `DepotUniteDaoTest`, sur base réelle, étendu à une unité refusée pour stockage que
`rearmer` doit laisser en l'état. À étendre. `RearmementDepotUnites` passe la seule cause **droits**
en dur au DAO et n'a pas de banc à lui.

#### Scenario: Reconnexion après un refus du stockage

- **WHEN** un fichier a été refusé par le stockage et l'observateur se reconnecte
- **THEN** le fichier reste refusé, et ne redevient pas « à déposer » du seul fait de la reconnexion

#### Scenario: Reconnexion après un refus de droits

- **WHEN** un fichier a été refusé par l'API en `403` et l'observateur se reconnecte
- **THEN** le fichier redevient « à déposer »

### Requirement: Le conseil ne nomme que le geste qui s'applique

Le compte rendu du dépôt SHALL conseiller, pour chaque cause présente :

- **droits** : se reconnecter, après quoi les fichiers redeviennent reprenables ;
- **stockage** : que se reconnecter n'y changera rien, puis relancer le téléversement, qui redemande
  des URL neuves, et, si le refus persiste, le dépôt manuel depuis le dossier de la nuit ;
- **contenu** : régénérer les archives, puis relancer.

Le compte rendu SHALL ne jamais conseiller de se reconnecter quand aucun des refus n'est de cause
**droits**. Quand les causes sont mêlées, il SHALL nommer le geste de chacune avec le nombre de
fichiers qu'il concerne.

L'écran et la commande `deposer-vigiechiro` SHALL donner le même conseil (ADR 0014).

*Vérifié par* : les tests du compte rendu de l'écran (`CompteRenduChiffreDepot`) et de la commande
(`DeposerVigieChiro`), un cas par cause et un cas mêlé, sur le texte rendu. À étendre. L'aperçu
`CaptureCompteRenduDepot` montre le cas mêlé avec un refus du stockage.

#### Scenario: Tous les refus viennent du stockage

- **WHEN** dix archives sont refusées par le stockage en `403`
- **THEN** le compte rendu dit que se reconnecter n'y changera rien, conseille de relancer le
  téléversement puis, si le refus persiste, le dépôt manuel, et ne contient pas « Reconnectez-vous »

#### Scenario: Refus mêlés de droits et de stockage

- **WHEN** deux archives sont refusées par l'API en `403` et trois par le stockage en `403`
- **THEN** le compte rendu conseille la reconnexion pour les deux premières et nomme le geste du
  stockage pour les trois autres, chacun avec son nombre

#### Scenario: Même conseil sur les deux surfaces

- **WHEN** le même bilan de dépôt est rendu par l'écran et par la commande `deposer-vigiechiro`
- **THEN** les deux nomment les mêmes gestes pour les mêmes causes

### Requirement: Un dépôt entamé se régénère

Le système SHALL admettre la génération des archives de dépôt d'un passage dont le dépôt est entamé
(« Dépôt en cours »), comme il l'admet pour un passage « Prêt à déposer » ou « Déposé ».

Les archives régénérées SHALL porter les mêmes identifiants que celles du plan de dépôt, tant que la
liste des séquences est inchangée : ce qui est déjà en ligne le reste, et une relance ne renvoie que ce
qui manque.

*Vérifié par* : un banc de bout en bout, sur base réelle, qui dépose, provoque un contenu refusé,
régénère par le service de lot **sans réponse simulée pour la génération**, relance, et constate l'unité
déposée.  Le test de #3946 simulait la génération et ne pouvait pas voir le refus.

#### Scenario: Régénérer après un contenu refusé

- **WHEN** une archive a été refusée pour son contenu, le passage est « Dépôt en cours », et
  l'observateur demande la génération des archives
- **THEN** les archives sont régénérées, et la relance du téléversement les retente

#### Scenario: Ce qui est en ligne le reste

- **WHEN** onze archives sur quatorze sont en ligne et l'observateur régénère les archives
- **THEN** une relance ne renvoie que les trois qui manquent

### Requirement: La commande génère ce que l'écran génère

La commande `exporter-lot` SHALL préparer le dépôt seulement s'il ne l'est pas encore, puis générer
les archives, selon la même règle que l'écran : un passage « Prêt à déposer », « Dépôt en cours » ou
« Déposé » voit ses archives générées sans nouvelle préparation (ADR 0014, parité).

Décidé par Sébastien pendant la réalisation (option a) : la commande refusait jusque-là tout passage
déjà préparé, parce qu'elle préparait avant de générer et que la préparation n'admet que « Vérifié ».

*Vérifié par* : `ExporterLotTest`, la commande sur un service simulé, avec un passage « Vérifié » (préparé puis généré) et un
passage « Dépôt en cours » (généré sans préparation).

#### Scenario: Régénérer un dépôt entamé en ligne de commande

- **WHEN** le passage est « Dépôt en cours » et l'observateur lance `exporter-lot`
- **THEN** les archives sont générées, et la commande ne tente pas de préparer le dépôt à nouveau

#### Scenario: Un passage vérifié se prépare puis se génère

- **WHEN** le passage est « Vérifié » et l'observateur lance `exporter-lot`
- **THEN** le dépôt est préparé, puis les archives sont générées, comme aujourd'hui

### Requirement: Pas de génération pendant un téléversement

Le système SHALL refuser la génération des archives d'un passage dont un téléversement est **en train
de tourner** dans l'application, en le disant et en nommant le geste : attendre la fin du
téléversement, ou l'annuler.

La garde SHALL tenir dans les deux sens de la course : elle s'arme au début du téléversement et se
lève à sa fin, qu'il aboutisse, échoue ou soit annulé.

*Vérifié par* : des cas unitaires sur le registre des téléversements en cours (armé, levé après succès,
levé après exception), et un cas sur le service de lot qui refuse pendant un téléversement armé.

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
