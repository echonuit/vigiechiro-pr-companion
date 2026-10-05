## Purpose

Offrir, pour déposer une nuit, les seules étapes qui servent à la forme de ce dépôt, et en rendre compte
dans les mots de ce qui est réellement parti.

## ADDED Requirements

### Requirement: L'écran connaît la forme du dépôt de la nuit

L'écran de lot SHALL tenir la forme du dépôt de la nuit affichée : celle des unités déjà déposées quand le
dépôt est entamé, sinon celle du réglage, dont le défaut est le WAV. Il SHALL la relire à l'ouverture et
après chaque téléversement.

*Vérifié par* : `RegenerationPendantUnDepotTest` (la forme d'une nuit sans dépôt suit le réglage ; celle d'un dépôt entamé
suit ses unités) et `LotViewModelTest`.

#### Scenario: Aucun dépôt entamé

- **WHEN** l'écran s'ouvre sur une nuit sans unité déposée, le réglage étant absent
- **THEN** la forme du dépôt est « séquences WAV »

#### Scenario: Dépôt entamé en archives sous un réglage WAV

- **WHEN** l'écran s'ouvre sur une nuit dont une archive est déjà en ligne, le réglage disant WAV
- **THEN** la forme du dépôt est « archives ZIP »

### Requirement: L'étape des archives n'est offerte que si elle sert

L'écran de lot SHALL offrir l'étape « Générer les archives de dépôt » quand la forme du dépôt est le ZIP,
ou quand l'application ne peut pas téléverser elle-même. Connecté et en forme WAV, il SHALL NOT l'offrir,
ni les éléments de l'étape de téléversement qui servent au dépôt manuel d'archives (la mention du dépôt
manuel, le chemin du dossier, « Copier », « Ouvrir le dossier (dépôt manuel) »).

*Vérifié par* : `LotDepotConnecteViewTest`, sur l'écran monté connecté, dans les deux formes, qui lit la
présence de la carte et de ces éléments. Le cas hors connexion en forme WAV est tenu au modèle de vue, par
`LotViewModelTest`, qui compte les étapes : aucun test d'interface ne monte l'écran hors connexion en
forme WAV, et aucun aperçu ne le montre (#5838).

#### Scenario: Connecté, forme WAV

- **WHEN** l'écran s'ouvre connecté sur une nuit dont le dépôt partira en séquences WAV
- **THEN** la carte « Générer les archives de dépôt » est absente, et l'étape de téléversement ne propose
  pas de dépôt manuel

#### Scenario: Connecté, forme ZIP

- **WHEN** l'écran s'ouvre connecté sur une nuit dont le dépôt partira ou est parti en archives ZIP
- **THEN** la carte « Générer les archives de dépôt » est présente, comme aujourd'hui

#### Scenario: Hors connexion, forme WAV

- **WHEN** l'écran s'ouvre sans connexion à Vigie-Chiro, le réglage disant WAV
- **THEN** la carte « Générer les archives de dépôt » est présente : elle sert au dépôt manuel

### Requirement: Les étapes se numérotent sans trou

Les titres des étapes et le fil d'étapes SHALL être numérotés de 1 à N dans l'ordre où l'écran les offre.
Sans l'étape des archives : « 1 » préparer, « 2 » téléverser, « 3 » lancer la participation ou marquer le
passage déposé. Avec elle : la numérotation actuelle, de 1 à 4.

*Vérifié par* : `EtapesDepotTest` (trois étapes ou quatre, et le rang de l'étape courante dans chaque
cas), et le test d'interface de l'exigence précédente, qui lit les titres.

#### Scenario: Trois étapes

- **WHEN** l'étape des archives est absente
- **THEN** le fil d'étapes compte trois puces, et les titres sont « 2. Téléverser sur Vigie-Chiro » et
  « 3. Lancer la participation »

#### Scenario: Quatre étapes

- **WHEN** l'étape des archives est présente
- **THEN** le fil d'étapes compte quatre puces, et les titres gardent leurs numéros 2, 3 et 4

### Requirement: Le compte rendu du dépôt nomme ce qui est parti

Le compte rendu d'un dépôt SHALL nommer ses unités d'après leur type : « séquence(s) » quand elles sont
toutes des séquences WAV, « archive(s) » quand elles sont toutes des archives ZIP, « unité(s) » quand le
plan mêle les deux. Il SHALL NOT conseiller de régénérer les archives pour une unité qui n'est pas une
archive.

*Vérifié par* : `CompteRenduChiffreDepotTest`, un cas par type pour chaque phrase qui nomme l'unité, et un
cas de contenu refusé en WAV qui constate l'absence du conseil.

#### Scenario: Dépôt complet en WAV

- **WHEN** toutes les séquences d'une nuit déposée en WAV sont en ligne
- **THEN** le compte rendu dit « Toutes les séquences de la nuit sont sur Vigie-Chiro »

#### Scenario: Contenu refusé en WAV

- **WHEN** la plateforme refuse le contenu de deux séquences
- **THEN** le compte rendu dit que deux séquences ont été refusées et renvoie à la table, sans nommer la
  régénération des archives

### Requirement: Le blocage d'une relance dit la raison qui vaut pour la nuit

Une nuit déjà analysée SHALL rester non relançable depuis l'écran, quelle que soit la forme de son dépôt.
L'explication du bouton grisé SHALL dire, pour un dépôt en archives, que l'audio n'est pas conservé et que
les observations ne pourraient pas être recalculées ; pour un dépôt en séquences, que la relance effacerait
les observations côté serveur avant de les recalculer. Dans les deux cas elle SHALL nommer
`lancer-traitement-vigiechiro --forcer`.

*Vérifié par* : un test d'interface qui lit l'infobulle dans les deux formes après un relevé « terminée ».  Le blocage lui-même est déjà tenu par `LotDepotConnecteViewTest`.

#### Scenario: Nuit analysée, déposée en WAV

- **WHEN** le relevé rend « terminée » pour une nuit déposée en séquences WAV
- **THEN** le bouton de lancement est grisé, et son explication ne parle pas d'un audio non conservé
