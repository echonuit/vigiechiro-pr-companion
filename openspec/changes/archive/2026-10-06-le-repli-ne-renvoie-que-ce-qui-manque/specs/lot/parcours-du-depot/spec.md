## MODIFIED Requirements

### Requirement: Le repli manuel s'offre après un refus sans recours

Connecté et en forme WAV, l'écran de lot SHALL offrir le repli manuel dès qu'au moins une séquence du dépôt
est refusée définitivement par le stockage de Vigie-Chiro ou pour son contenu : une carte sans numéro,
« Repli : déposer à la main », placée sous la carte du téléversement, qui génère les archives ZIP des séquences qui ne sont pas en ligne,
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

## ADDED Requirements

### Requirement: Les archives d'un dépôt entamé en séquences ne rendent que ce qui manque

Sur une nuit dont le dépôt en séquences WAV est entamé, la génération d'archives SHALL n'y mettre que les
séquences que le plan de dépôt ne dit pas déposées. Elle MUST NOT y remettre une séquence déjà en ligne : le
serveur l'ajouterait une seconde fois aux fichiers de la participation (#5970). L'écran et la commande
`exporter-lot` SHALL suivre la même règle. Quand toutes les séquences sont déposées, la génération SHALL
refuser en le disant, plutôt que d'écrire une archive vide. Une nuit sans plan de dépôt, ou dont le dépôt est
en archives, SHALL se générer en entier.

*Vérifié par* : `RegenerationPendantUnDepotTest` (la règle sur une vraie base avec le moteur de dépôt, ses deux
témoins et le refus), `ExporterLotTest` (la commande passe par la même génération, et n'annonce aucune archive
quand elle refuse) et `ArchiveDuRepliSurLaPlateformeDeTestTest` (sur le code du serveur, une archive des seules
séquences absentes ne laisse aucun titre en double).

#### Scenario: Quatre séquences sur dix déjà en ligne

- **WHEN** l'utilisateur génère les archives d'une nuit de dix séquences dont quatre sont déposées en WAV
- **THEN** les archives contiennent les six autres séquences, et aucune des quatre

#### Scenario: Une nuit jamais déposée

- **WHEN** l'utilisateur génère les archives d'une nuit qui n'a aucun plan de dépôt
- **THEN** les archives contiennent toutes les séquences de la nuit

#### Scenario: Un dépôt entamé en archives

- **WHEN** l'utilisateur régénère les archives d'une nuit dont une archive est déjà en ligne
- **THEN** les archives contiennent toutes les séquences de la nuit, comme avant ce changement

#### Scenario: Tout est déjà en ligne

- **WHEN** la commande `exporter-lot` vise une nuit dont toutes les séquences sont déposées en WAV
- **THEN** elle refuse, et dit qu'il ne reste aucune séquence à déposer à la main
