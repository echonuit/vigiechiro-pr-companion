## MODIFIED Requirements

### Requirement: Trois causes de refus définitif

Le système SHALL ranger un refus définitif dans l'une de trois causes :

- **droits** : un `401` ou un `403` de l'API, et l'appel qui n'est pas parti faute de jeton ;
- **stockage** : un `401` ou un `403` du stockage S3 ;
- **contenu** : tout autre refus définitif, quelle que soit sa provenance.

Un refus rejouable (`429`, `5xx`, coupure) SHALL ne porter aucune cause, comme aujourd'hui.

L'appel parti sans jeton est rangé en **droits** parce qu'une reconnexion est ce qui le fera partir.
Depuis qu'un `401` de l'API efface le jeton, c'est le cas des unités d'un dépôt parties après le premier
refus : elles MUST se réarmer à la reconnexion comme celles qui ont reçu le `401`, et le compte rendu
SHALL conseiller de se reconnecter, jamais de régénérer.

*Vérifié par* : des cas unitaires sur la règle de classement, un par couple statut et provenance, et
l'appel sans jeton (`CauseRefusTest`) ; `DepotApresUnJetonRefuseTest`, qui dépose dix unités avec le
vrai client sur une vraie base, la première recevant le `401`.

#### Scenario: Un 403 de l'API

- **WHEN** l'API répond `403` à la déclaration d'un fichier
- **THEN** la cause est **droits**

#### Scenario: Un 403 du stockage

- **WHEN** S3 répond `403` au `PUT` d'un fichier
- **THEN** la cause est **stockage**

#### Scenario: Un 400 du stockage

- **WHEN** S3 répond `400` au `PUT` d'un fichier
- **THEN** la cause est **contenu**

#### Scenario: Les unités parties après un 401

- **WHEN** la première unité d'un dépôt reçoit un `401` de l'API, et que les suivantes partent sans jeton
- **THEN** toutes sont rangées en **droits**, se réarment à la reconnexion, et le compte rendu dit « Reconnectez-vous »
