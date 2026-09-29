## Purpose

Dire à l'observateur ce qu'il peut faire d'un fichier que Vigie-Chiro a refusé pendant le dépôt d'une
nuit, selon que le refus vient de l'API de la plateforme ou de son stockage S3, et ne réarmer que ce
qu'un geste peut réellement réparer.

## ADDED Requirements

### Requirement: Un refus porte sa provenance

Quand un fichier est refusé définitivement pendant le dépôt, le système SHALL retenir si le refus
vient de l'API Vigie-Chiro (déclaration du fichier, demande d'URL de partie, finalisation) ou de son
stockage S3 (le `PUT` des octets vers une URL pré-signée).

Le système SHALL décider cette provenance d'après la requête qui a échoué, et jamais d'après le texte
de la réponse.

*Vérifié par* : un banc de `TeleverseurArchive`, qui n'en a aucun aujourd'hui, provoquant un `403` à
chacune des étapes (dépôt d'un seul bloc, URL de partie, `PUT` de partie, finalisation) et lisant la
cause retenue. À écrire.

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
À écrire.

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
