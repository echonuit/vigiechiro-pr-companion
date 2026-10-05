## MODIFIED Requirements

### Requirement: L'étape des archives n'est offerte que si elle sert

L'écran de lot SHALL offrir l'étape « Générer les archives de dépôt » quand la forme du dépôt est le ZIP,
ou quand l'application ne peut pas téléverser elle-même. Connecté et en forme WAV, il SHALL NOT l'offrir
comme une étape du fil, ni les éléments de l'étape de téléversement qui servent au dépôt manuel d'archives
(la mention du dépôt manuel, le chemin du dossier, « Copier », « Ouvrir le dossier (dépôt manuel) »), tant
que le repli manuel de l'exigence « Le repli manuel s'offre après un refus sans recours » n'est pas offert.

*Vérifié par* : `LotDepotConnecteViewTest`, sur l'écran monté connecté, dans les deux formes, qui lit la
présence de la carte et de ces éléments. Le cas hors connexion en forme WAV est tenu au modèle de vue, par
`LotViewModelTest`, qui compte les étapes : aucun test d'interface ne monte l'écran hors connexion en
forme WAV.

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

## ADDED Requirements

### Requirement: Le repli manuel s'offre après un refus sans recours

Connecté et en forme WAV, l'écran de lot SHALL offrir le repli manuel dès qu'au moins une séquence du dépôt
est refusée définitivement par le stockage de Vigie-Chiro ou pour son contenu : une carte sans numéro,
« Repli : déposer à la main », placée sous la carte du téléversement, qui génère les archives ZIP de la nuit,
et avec elle les éléments du dépôt manuel. Il SHALL NOT l'offrir tant qu'aucun téléversement n'a été refusé,
ni après un échec que la reprise peut lever, ni quand les seuls refus tiennent aux droits, qu'une reconnexion
réarme. Le fil d'étapes SHALL rester à trois étapes, et leurs numéros inchangés. Le repli SHALL être offert
de la même façon à la réouverture de l'écran, puisqu'il se lit dans le plan de dépôt enregistré.

*Vérifié par* : `DepotUniteDaoTest` (la règle, cause par cause, sur le plan enregistré dans une vraie base),
`DepotViewModelTest` (le compte relu à l'ouverture d'une nuit et après un téléversement),
`EtapeDesArchivesTest` (repli offert ou non selon la forme, la connexion et le statut, fil à trois étapes) et
`LotRepliManuelViewTest` (l'écran monté connecté, un téléversement refusé, la carte trouvée sous celle du
téléversement, son titre et sa consigne lus).

#### Scenario: Aucun refus

- **WHEN** l'écran s'ouvre connecté sur une nuit en forme WAV dont aucune séquence n'est refusée
- **THEN** le repli n'est pas offert

#### Scenario: Échec que la reprise peut lever

- **WHEN** un téléversement se termine avec des séquences en échec après une coupure
- **THEN** le repli n'est pas offert, et l'écran propose de reprendre le dépôt

#### Scenario: Refus de droits

- **WHEN** toutes les séquences refusées le sont pour une cause de droits
- **THEN** le repli n'est pas offert : le compte rendu dit de se reconnecter

#### Scenario: Refus du stockage

- **WHEN** le stockage de Vigie-Chiro refuse définitivement deux séquences
- **THEN** la carte « Repli : déposer à la main » apparaît sous la carte du téléversement, sa consigne dit
  que deux séquences ont été refusées, et le fil d'étapes compte toujours trois puces

#### Scenario: Réouverture de l'écran

- **WHEN** l'écran se rouvre sur une nuit dont le plan enregistré porte une séquence refusée pour son contenu
- **THEN** le repli est offert sans relancer de téléversement

### Requirement: La ligne de commande dit le même repli

`deposer-vigiechiro` SHALL, quand le dépôt se termine avec au moins une séquence refusée définitivement par le
stockage ou pour son contenu, nommer le repli manuel et la commande qui produit les archives,
`exporter-lot --passage N`. Elle SHALL NOT le nommer pour un dépôt en archives, ni quand les seuls refus
tiennent aux droits.

*Vérifié par* : `DeposerVigieChiroTest`, qui joue la commande sur un dépôt refusé par le stockage et pour son
contenu, puis sur un refus de droits. Le cas d'un dépôt en archives est tenu au niveau de la règle, par
`DepotUniteDaoTest` : une archive refusée n'y est pas comptée.

#### Scenario: Séquences refusées par le stockage

- **WHEN** `deposer-vigiechiro` se termine sur un dépôt en séquences dont deux sont refusées par le stockage
- **THEN** la sortie nomme `exporter-lot --passage N` et le dépôt à la main sur le portail

#### Scenario: Refus de droits seulement

- **WHEN** les seules séquences refusées le sont pour une cause de droits
- **THEN** la sortie conseille de se reconnecter, et ne nomme pas le repli

### Requirement: Le dernier geste du repli a sa porte

En repli, la carte SHALL offrir « Marquer le passage déposé », grisé tant qu'aucune archive n'est générée, et
qui rend le passage « Déposé ». Elle SHALL NOT l'offrir hors du repli, où ce geste appartient à la dernière
étape. La commande `deposer` SHALL marquer déposé un passage dont le dépôt est déjà préparé ou entamé, sans
le préparer de nouveau ; elle SHALL préparer d'abord un passage seulement vérifié, comme avant. Une fois le
passage déposé, le repli SHALL se retirer.

*Vérifié par* : `LotRepliManuelViewTest` (le bouton grisé sans archive, ouvert avec, cliqué, et absent en
forme ZIP), `EtapeDesArchivesTest` (le repli retiré une fois le passage déposé) et `DeposerTest` (la commande
sur un dépôt entamé puis sur un passage vérifié).

#### Scenario: Archives générées, dépôt fait à la main

- **WHEN** l'utilisateur, en repli et ses archives générées, clique « Marquer le passage déposé »
- **THEN** le passage devient « Déposé », et la carte du repli se retire

#### Scenario: La commande sur un dépôt entamé

- **WHEN** `deposer --passage N` est lancée sur une nuit au statut « Dépôt en cours »
- **THEN** le passage est marqué déposé, sans nouvelle préparation, et la sortie donne sa date de dépôt

### Requirement: Un refus définitif le reste à la réouverture

La table de dépôt, reposée depuis le plan enregistré à l'ouverture de l'écran, SHALL garder le caractère
définitif d'un refus. Le bouton de téléversement SHALL NOT s'appeler « Reprendre le dépôt » quand il ne reste
à téléverser que des unités refusées définitivement.

*Vérifié par* : `SuiviLignesDepotTest` (le plan rechargé, un refus définitif et un échec rejouable) et
`LotRepliManuelViewTest` (l'écran rouvert sur un tel plan, le libellé du bouton lu).

#### Scenario: Réouverture sur un refus du stockage

- **WHEN** l'écran s'ouvre sur une nuit dont une séquence est en ligne et l'autre refusée définitivement
- **THEN** le bouton de téléversement s'appelle « Téléverser sur Vigie-Chiro », et le repli est offert

