# lot/parcours-du-depot Specification

## Purpose
Offrir, pour déposer une nuit, les seules étapes qui servent à la forme de ce dépôt, et en rendre compte
dans les mots de ce qui est réellement parti.

## Requirements

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

### Requirement: La date de dépôt se lit en français

La date de dépôt d'un passage SHALL s'afficher sous la forme « 21/06/2026 » dans toute phrase et toute
colonne lue par un humain : l'état de l'écran de lot, le compte rendu de `deposer`, la ligne « Dépôt » de
`statut-passage`, et la colonne « Déposé le » des tables. Elle MUST NOT s'afficher sous sa forme de
stockage, ni se lire comme absente quand le passage est déposé. La forme de stockage est un instant
local, avec son heure : c'est elle que les lecteurs SHALL accepter.

Les sorties `--json` SHALL garder la valeur telle que la base la porte.

*Vérifié par* : un test du lecteur commun sur la forme que la production écrit, construite par
`LocalDateTime#toString` et non recopiée à la main ; les tests de `deposer`, de `statut-passage` et de
l'écran de lot, dont les témoins portent cette forme. Rendre la chaîne brute, ou n'accepter qu'une date
seule, les fait rougir.

#### Scenario: Un passage déposé le 21 juin à 8 h

- **WHEN** la base porte `2026-06-21T08:00:15.123456` comme date de dépôt
- **THEN** l'écran de lot dit « Passage déposé le 21/06/2026. », et la colonne « Déposé le » affiche
  « 21/06/2026 »

#### Scenario: La sortie de script

- **WHEN** `statut-passage --json` rend ce passage
- **THEN** la clé `deposeLe` porte la valeur de la base, inchangée

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
