---
type: adr
title: "Une restauration **vérifie en place**, et replace un dossier là où il peut réellement aller"
status: stable
article: A17
chantier: "#2727, lot 1 (#2721) du chantier de dette #2720"
decided_at: 2026-08-03
verification: certaine
enforced_by:
  - "RestaurationCompleteTest#restauration_sur_une_autre_machine"
verified:
  - by: machine:ci
    at: 2026-08-03
---

# Une restauration **vérifie en place**, et replace un dossier là où il peut réellement aller

## Contexte

La restauration complète déversait les dossiers de son à la racine du dossier de travail et ne
touchait pas aux chemins persistés : la base restaurée continuait de désigner les emplacements de la
machine d'origine. La promesse de la doc écrans, « remet la base et les dossiers de son », ne tenait
donc que si l'on restaurait sur la machine **et** l'arborescence d'origine.

L'issue proposait un remède en quatre temps : **étaler dans une zone temporaire**, vérifier contre le
manifeste, réécrire les chemins, puis basculer. Deux de ces quatre temps ont été tenus tels quels ; le
premier a été écarté en cours d'écriture, et le troisième s'est révélé plus large que prévu.

## Décision

### La vérification porte sur la sauvegarde **en place**, sans zone temporaire

Chaque dossier de la sauvegarde est confronté à l'inventaire du manifeste **avant** que rien ne soit
touché. Une seule discordance annule tout, base comprise.

La zone temporaire est écartée pour une raison de place, mesurée sur le cas ordinaire : restaurer
40 Go **par-dessus ses propres 40 Go** demanderait 80 Go libres. Elle ferait donc échouer la
restauration la plus courante, celle où l'on remet ses propres données, pour protéger d'un cas plus
rare. Un dispositif de sûreté qui empêche l'usage normal n'est pas un dispositif de sûreté.

**Ce qu'on perd, et c'est étroit** : un disque qui se remplit **pendant** la copie laisse une
destination à moitié écrite. La vérification de la destination **après** copie le voit et le dit, là
où une zone temporaire l'aurait évité.

### Un dossier revient à son emplacement d'origine **s'il existe encore**

Sinon il est placé dans le dossier de travail, et les chemins persistés désignent l'endroit réel.

Le critère est que le dossier d'origine **existe**, et non que son parent soit créable. La nuance
évite un piège coûteux : `/mnt/disque-a` est un point de montage **vide** quand le disque n'est pas
branché. Le juger « créable » y déverserait des gigaoctets sur le disque système, que le montage du
vrai disque **masquerait** ensuite : l'utilisateur verrait ses nuits disparaître en rebranchant son
disque.

⚠️ **Conséquence assumée** : restaurer une nuit qu'on vient de **supprimer** la remet dans le dossier
de travail et non à sa place, puisque sa place n'existe plus. Le compte rendu le dit, et la base
pointe au bon endroit.

### Tous les chemins persistés suivent, pas seulement la racine

`recording_session.root_path` n'est pas le seul chemin en base : chaque original, chaque séquence
d'écoute, le journal du capteur, le relevé climatique et le CSV Tadarida portent le leur, **en
absolu**. Six tables.

Cette clause n'est pas un détail d'implémentation, c'est **la** décision qui fait tenir la promesse.
Ne réécrire que la racine donne une base qui **paraît** corrigée et une application qui ne retrouve
plus un seul fichier. C'est l'état dans lequel cette fonctionnalité a d'abord été livrée : les tests
Java relisaient `root_path` et concluaient au succès, tandis que l'E2E qui restaure sur une autre
machine puis demande où est l'audio répondait **PERDU**.

Un chemin situé **hors** de l'ancienne racine est laissé tel quel : un original resté sur la carte SD
n'a pas bougé, et le rebaser désignerait un fichier qui n'a jamais été là.

## Conséquences

- La restauration **rend un compte rendu** : ce qui a changé de place, et ce que la sauvegarde ne
  contenait pas. Un geste qui déplace des gigaoctets et corrige la base ne peut pas se contenter de
  « restauré ».
- Une sauvegarde **antérieure au manifeste** se restaure comme avant, et le bilan le dit plutôt que
  de laisser croire à mieux.
- ⚠️ L'inventaire des six tables est énuméré **deux fois** dans le code, ici et dans
  `RattachementDao.reprefixerChemins`, le socle ne pouvant pas dépendre d'une feature. Une septième
  table à chemin devra être ajoutée aux deux endroits : c'est le sujet de #3133.

## Alternatives écartées

**Restaurer systématiquement dans le dossier de travail.** Plus simple, mais déverse des gigaoctets
sur le disque système d'un utilisateur dont le disque d'origine est branché.

**Restaurer systématiquement à l'emplacement d'origine, en créant les dossiers manquants.** Ne marche
que sur la machine d'origine, c'est-à-dire pas dans le cas qui motive l'issue, et tombe dans le piège
du point de montage vide.

**Hacher le contenu des fichiers plutôt que l'inventaire.** Doublerait le temps de la sauvegarde
**et** celui de la restauration pour n'attraper en plus que la corruption silencieuse à taille égale,
alors que le socle a déjà `original_recording.sha256` en base pour ce cas.
