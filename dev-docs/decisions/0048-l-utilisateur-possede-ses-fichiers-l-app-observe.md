# ADR 0048 - L'utilisateur possède ses fichiers : l'application observe leur disponibilité, elle ne les archive plus

- **Statut** : Accepté - 2026-07-21
- **Chantier** : #1038 (reformulé), né de l'exploration « import et réactivation par référence, sans copie »
- **Vérification** : humaine — que l'application observe la disponibilité des fichiers sans les archiver est un comportement, non un invariant statique
- **Reformule** : #1038 (chemin du workspace configurable) ; rend sans objet le geste d'archivage de l'EPIC #1297 ; résout #2028 (« Libérer l'espace disque ») par conséquence.
- **Amende** : [ADR 0005](0005-reactivation-cascade-de-preuves-archive-etat-observe.md), dont elle retire le geste d'archivage : « archivé » n'est plus un statut à écrire, seulement un état observé.

## Contexte

Le dossier de travail (« workspace ») contient aujourd'hui trois natures de données, et l'application
se comporte comme leur **propriétaire** :

- `bruts/` : les enregistrements originaux, **copiés** de la carte SD (R7) ;
- `transformes/` : les séquences d'écoute, **dérivées** des bruts par `TransformationAudio`, plus le CSV
  Tadarida ;
- `vigiechiro.db` : la base SQLite - observations, validations, verdicts, ancrage, liens Vigie-Chiro.

De ces trois, **seule la base est irremplaçable** : les bruts se réimportent depuis la carte, les
transformés se recalculent depuis les bruts. Tout le reste du workspace est un **cache**.

#1038 demandait de rendre le chemin du workspace configurable, motivé par les **gros volumes** (persona
Samuel : pointer un disque externe). L'exploration a montré que la question posée n'était pas la bonne.
Le modèle de persistance **ne suppose nulle part que les fichiers vivent dans le workspace** :

- les chemins sont stockés **complets** et ouverts tels quels ; « audio disponible » se calcule par
  `Files.exists(Path.of(cheminFichier))` (`SequenceDEcoute`) ;
- l'identité d'un fichier est **prouvable** indépendamment de son emplacement : `sha256` intégral sur les
  bruts, `content_fingerprint` (SHA-256 des 64 premiers Kio) plus `size_bytes` sur les séquences (V23,
  #1299) ;
- la réactivation est **déjà** un rebranchement de chemins vérifiés par empreinte (`RebranchementSequences`,
  cascade #1309), pas un recalcul.

Référencer l'audio **en place** (sur un NAS, ou déjà présent dans un dossier de l'utilisateur) est donc
**architecturalement atteignable** : la partie difficile - prouver qu'un fichier est bien le bon - est déjà
écrite.

Restaient deux mécaniques centrées sur la possession, en tension avec cette idée : l'**archivage/purge**
(qui *supprime* les fichiers pointés par la base) et la **comptabilité d'espace disque**. Et une posture :
l'application traitait « archivé » comme un **geste déclaré et destructif** (`archived_at`, gardé au statut
DÉPOSÉ, suivi d'une purge).

Or le volume produit par les enregistreurs rend illusoire l'espoir de contrôler le disque de l'utilisateur :
il déplacera des dossiers, changera un point de montage, réorganisera. Il faut **vivre avec**, pas prétendre
l'empêcher.

## Décision

**1. L'audio peut être référencé en place.** Un fichier dont le chemin est **hors** de la racine du
workspace est *référencé* : l'application ne le supprime jamais, et sa disponibilité vaut son montage. Sous
la racine, il est *possédé*. La distinction se **dérive du chemin** : aucune colonne nouvelle.

**2. « Archivé » devient un état observé, pas un geste.** La source de vérité est la `DisponibiliteAudio`
(COMPLÈTE / PARTIELLE / ABSENTE), déjà calculée en un point unique par `ServiceDisponibiliteAudio` (balayage
groupé, mis en cache, `invaliderTout()` prévu pour les interventions manuelles de l'utilisateur). Audio
absent = passage **en sommeil** ; présent = **écoutable**.

**3. Le geste délibéré d'archivage et de purge est retiré.** Plus de bouton « Archiver », plus de
suppression de fichiers par l'application, plus de garde « seul un passage déposé s'archive ». L'utilisateur
a la maîtrise de ses fichiers.

**4. La réconciliation remplace le contrôle.** Une étape **distincte de l'audit** (qui, lui, reste
consultatif - ADR 0012) mappe l'observé vers l'état du passage :

- fichiers disparus → **sommeil** ;
- réapparus au même chemin **ET identité reconfirmée** (empreinte quand elle existe, cascade #1309 en repli
  - coût négligeable : taille plus 64 Kio) → **réveil** ;
- déplacés → **réactivation** (la garde d'identité existante rebranche vers le nouvel emplacement, sans
  copier).

Le même chemin **ne suffit pas** : un fichier homonyme d'empreinte divergente (redécoupe, autre nuit,
restauration divergente) est un **conflit**, pas un réveil. C'est précisément le risque que #1299/#1309 ont
fermé, et qu'on ne rouvre pas : réveiller sur `Files.exists` seul, ce serait valider un cri en écoutant un
autre audio.

**5. Le dépôt reste auto-suffisant.** Déposer téléverse l'audio ; un passage dont l'audio est absent avant
dépôt **ne peut pas** être déposé. La contrainte que la garde DÉPOSÉ protégeait s'**auto-impose** : nul
besoin d'une garde dédiée pour l'énoncer.

## Conséquences

- **#1038 se réduit à « où vit la `.db` ».** L'audio volumineux n'a plus à être recopié ni déplacé : on
  pointe là où il vit déjà. Reste, comme seule relocalisation légitime, celle de la base - petit fichier,
  irremplaçable, qu'on peut vouloir mettre au sûr. Le déplacement du **cache** audio devient marginal.
- **À retirer :** `ServiceArchivagePassage`, l'action `ActionArchivage`, la commande CLI `Archiver`, et
  l'exposition correspondante du `PassageViewModel`. La vérité de `archived_at` (V24) migre vers la
  `DisponibiliteAudio` ; ses lecteurs (audit, import, dépôt) sont recâblés sur l'état observé. La colonne
  est **dépréciée** ; son retrait effectif est une migration de suite, non bloquante.
- **#2028 (« Libérer l'espace disque ») est résolue par conséquence** : l'application ne possède plus de
  purge à exposer.
- **Aucune régression de la garantie scientifique** : le réveil vérifié réutilise la machinerie d'identité
  déjà posée (#1299/#1309).
- **L'import sans copie se scinde proprement** : transformés déjà présents → **référence pure** (zéro
  calcul, cas « je les ai déjà dans mon dossier ») ; bruts seuls → on référence le brut, mais les
  transformés se **calculent** et s'écrivent quelque part (workspace ou cache dédié).
- **Flatpak** : un chemin référencé hérite de la limite du portail XDG. Un NAS monté via gvfs
  (`/run/user/UID/gvfs`) n'est **pas** accordé au bac à sable ; un volume sous `/media` ou `/run/media`
  l'est (ces accès existent déjà pour les cartes SD, cf. `flatpak/`). À documenter dans le README du
  paquet, non bloquant.
- La mise en œuvre (retrait du geste, service de réconciliation, mode « par référence » de l'import et de
  la réactivation, recadrage de #1038) est un chantier suivi séparément.

## Alternatives écartées

- **Garder l'archivage comme geste.** Maintient l'illusion d'un contrôle que le volume des données dément,
  et fait porter à l'application la suppression de fichiers qu'elle ne possède pas toujours.
- **Toujours copier dans le workspace.** Sûr, mais impose de dupliquer des téraoctets pour de gros parcs,
  et réduit #1038 à un déménagement de copie plutôt qu'à un simple pointage.
- **Distinguer possédé / référencé par une colonne.** Inutile : la nature se lit sur le chemin (sous la
  racine du workspace, ou non).
- **Réveiller sur `Files.exists` seul.** Rapide, mais rouvre le risque d'un fichier homonyme divergent -
  un cri validé sur le mauvais audio. L'empreinte referme ce risque pour un coût négligeable.
