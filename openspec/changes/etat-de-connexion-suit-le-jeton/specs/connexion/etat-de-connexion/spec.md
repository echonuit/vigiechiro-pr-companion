## ADDED Requirements

### Requirement: Un jeton refusé par l'API est oublié

Quand l'API Vigie-Chiro répond `401` à une lecture ou à une écriture authentifiée, le système SHALL
effacer le jeton enregistré si c'est lui qui est parti. Il MUST NOT l'effacer sur un `401` du stockage des
fichiers, ni sur un autre statut, ni quand le jeton refusé est un jeton ponctuel de la ligne de commande
ou n'est plus celui qui est enregistré.

*Vérifié par* : `TransportSignaleUnJetonRefuseTest`, qui joue un `401` en lecture, en écriture et sur le
stockage, et cinq autres statuts ; `StockageConnexionTest`, sur un jeton étranger, le jeton enregistré
et un jeton périmé encore écrit ; `SourceDuJetonTest`, sur la source liée par l'application, avec et
sans jeton ponctuel.

#### Scenario: Un 401 de l'API sur le jeton enregistré

- **WHEN** l'API répond `401` à une requête partie avec le jeton enregistré
- **THEN** le jeton enregistré est effacé

#### Scenario: Un 401 du stockage des fichiers

- **WHEN** le stockage répond `401` à l'envoi des octets d'un fichier
- **THEN** le jeton enregistré reste en place

#### Scenario: Un jeton ponctuel refusé

- **WHEN** une commande lancée avec `--token` reçoit un `401`
- **THEN** la connexion enregistrée de l'application reste en place

### Requirement: Un geste de plateforme se ferme dès qu'il découvre un jeton absent ou refusé

Au retour d'un geste de plateforme, sur le fil JavaFX, l'écran SHALL relire l'état de connexion. Quand
le jeton est absent, expiré ou vient d'être refusé, le geste SHALL se fermer, son motif de blocage
nommant l'entrée « Se connecter à Vigie-Chiro… », et un nouveau clic MUST NOT renvoyer de requête. Cela
vaut pour « Récupérer depuis Vigie-Chiro » de Mes sites, « Vérifier sur Vigie-Chiro » de la modale de
carré et « Publier sur Vigie-Chiro » de la fiche d'un site. L'ouverture de la modale de connexion SHALL
relire l'état de même. Un jeton encore valide, jusqu'à son quatorzième jour, MUST NOT fermer le geste.

*Vérifié par* : `JetonPerimeOuRefuseViewTest`, sur l'écran monté avec le vrai client et le vrai
transport, l'horloge pilotée : jeton périmé et `401` sur chacun des trois gestes, modale rouverte, et le
quatorzième jour en témoin.

#### Scenario: Jeton périmé, premier clic

- **WHEN** le jeton a dépassé ses 14 jours et l'utilisateur clique « Récupérer depuis Vigie-Chiro »
- **THEN** aucune requête ne part, le message nomme la reconnexion, et le bouton se grise

#### Scenario: Jeton refusé, second clic

- **WHEN** la plateforme a refusé le jeton en `401` au premier clic
- **THEN** le bouton est grisé, et le second clic n'envoie rien

#### Scenario: Modale de connexion rouverte

- **WHEN** le jeton a expiré et l'utilisateur ouvre puis ferme la modale de connexion, sans autre geste
- **THEN** les gestes de plateforme sont grisés

#### Scenario: Dernier jour valide

- **WHEN** le jeton en est à son quatorzième jour
- **THEN** la récupération part, et le bouton reste ouvert

### Requirement: Le message d'un jeton absent ou refusé nomme le geste et l'endroit

Quand un geste de plateforme d'un écran échoue faute de jeton valide, le message SHALL nommer la
reconnexion et l'endroit où la faire : le menu principal, entrée « Se connecter à Vigie-Chiro… ». Il MUST
NOT présenter ce cas comme une panne de réseau. Un message que la ligne de commande lit aussi SHALL dire
« reconnectez-vous » sans nommer de menu.

*Vérifié par* : `JetonPerimeOuRefuseViewTest`, qui lit le texte dessiné des trois écrans ;
`RechercheCarreExistantTest` et `RapprochementSitesTest` pour les verdicts et les rapports ;
`RapatriementCarreTest` pour le message partagé avec la ligne de commande.

#### Scenario: Vérifier un carré avec un jeton refusé

- **WHEN** la plateforme refuse le jeton pendant « Vérifier sur Vigie-Chiro »
- **THEN** le message dit que le carré n'a pas été vérifié, nomme la reconnexion et le menu, et ne parle pas de plateforme injoignable
