# ADR 3624 - Un fait que rien ne peut faire rougir s'ancre autrement

- **Statut** : Accepté - 2026-08-13
- **Chantier** : #3624, clôture des suites du lot 1 (#3559)
- **Amende** : [ADR 3482](3482-l-audit-ramasse-ce-qu-une-suppression-laisse.md)
- **Vérification** : certaine - `src/test/bats/cli.bats`

Le test qui la tient est « la trace retirée de la console est bien dans le journal, et emplacements dit
où (#3624) », **vu rouge** en neutralisant l'amorçage de la journalisation : sans cette mutation à la
main, il rendrait le même vert qu'une journalisation cassée. Le versant Java est
`CliEmplacementsTest#afficher_nomme_le_dossier_des_journaux`.

## Contexte

Les six suites du lot 1 ont livré du socle : une classification des échecs de la CLI, un garde
d'espace, une base d'octets unique, deux contrats d'effacement nommés. Chacune est couverte par des
tests, et la mesure de mutation sur leurs classes pures tue 72 mutants sur 74 - les deux survivants
étant un mutant équivalent et un artefact de ciblage.

La clôture a pourtant trouvé trois défauts que **cet arsenal ne pouvait pas voir**, tous de la même
forme : un fait vrai, une conséquence visible, et **aucun dispositif capable de rougir dessus**.

**Le journal introuvable.** #3570 a retiré la pile d'un incident de la console et l'a mise dans
`<dossier de travail>/logs`. Deux tests couvrent ce geste : l'un vérifie qu'un handler s'installe sur
un dossier temporaire, l'autre que la console n'a plus de pile. Aucun ne vérifie que **la trace
atterrit dans le fichier** - c'est-à-dire la seule chose qui rende le retrait acceptable. Une
journalisation cassée rendait exactement le même vert : plus de pile nulle part. Et le dossier n'était
nommé par aucune commande, quand l'IHM l'ouvre depuis son menu.

**Les volumes d'aperçu.** [ADR 3573](3573-les-octets-se-comptent-en-base-1000.md) a fait compter les
octets en base 1000. Les fixtures des captures, elles, restaient écrites en base 1024 pour afficher des
chiffres ronds. Régénérées, elles publiaient « 432 Mo » là où le littéral disait 412, « 189 Mo » pour
180, « 5,4 Go » pour 5. Le masque de comparaison des captures ne juge pas la rondeur d'un nombre : rien
ne pouvait rougir.

**L'horodatage de l'occupant.** #3571 fait nommer le processus qui tient le dossier de travail. Il
l'écrit `2026-08-03T21:14:07`, le `toString()` d'un `LocalDateTime`, au milieu d'une phrase française.
Les tests vérifient qu'un occupant est inscrit et repris, ce qui reste vrai quel que soit le format.
Consigné en #3640.

## Décision

**Un fait que rien ne peut faire rougir s'ancre par un dispositif d'une autre nature.** Deux seulement
existent dans ce dépôt, et il faut en choisir un explicitement :

### Un test qui traverse, quand le fait est mécanique

La promesse « la trace est dans le fichier » traverse le `main`, la résolution du dossier de travail,
l'amorçage de la journalisation et l'écriture disque. Aucun test en processus ne la voit : il ne
survit pas au `System.exit` de `main`. Elle se tient en `bats`, sur le vrai fat-jar.

Le même test exige que `emplacements` **nomme** le dossier réellement écrit. Les deux moitiés vont
ensemble : une trace qui existe dans un dossier qu'on ne peut pas trouver ne vaut pas mieux qu'une
trace absente. C'est une conséquence directe de la parité CLI ↔ IHM que le brief pose en contrainte de
conception - l'IHM ouvre ce dossier depuis son menu, la ligne de commande n'avait rien.

### Un regard consigné, quand le fait est visuel

Le chiffre publié dans une capture n'est jugeable que par un œil. La décision est donc de **poser la
fixture dans la base où elle sera rendue** : une fixture d'aperçu est écrite pour ce qu'elle
**affiche**, pas pour ce qu'elle représente en machine. Écrire `412L * 1024 * 1024` pour obtenir
« 412 Mo » est un raisonnement qui devient faux le jour où le formateur change, sans que personne
n'ait rien fait de mal.

### Ce qui a été écarté

**Un test sur la rondeur des nombres affichés** : il figerait des chiffres de démonstration
arbitraires, et rougirait à chaque changement de fixture légitime. Le coût dépasse le gain.

**Ne rien faire, en comptant sur la passe 8.** C'est ce qui s'est produit : la passe 8 les a trouvés,
une fois, après coup. Elle reste le filet, elle n'est pas l'ancrage - elle ne s'exécute pas à chaque
commit.

## Amendement de l'ADR 3482

L'ADR 3482 décrit la suppression de l'audit ainsi : « (`ExtracteurZip.supprimerRecursivement`) est
**best-effort et silencieux** ». Les deux moitiés sont devenues fausses avec
[ADR 3574](3574-un-effacement-dit-son-contrat-dans-son-nom.md) : le symbole n'existe plus, et le
contrat s'est **inversé** - `ArborescenceFichiers.effacerAuMieux` rapporte ce qui a résisté, avec la
raison, précisément parce que `NettoyageDossiersOrphelins` en rend compte à l'utilisateur.

L'ADR 3574 aurait dû le déclarer et ne l'a pas fait. Une ADR étant immuable, l'amendement est porté
ici. C'est aussi un cas de la même forme : une page qui décrit fidèlement un mécanisme **remplacé** ne
rougit nulle part et se lit comme vraie.

## Conséquences

- `ServiceEmplacements.Emplacements` porte le dossier des journaux, et `emplacements` l'affiche en
  texte comme en JSON. La sortie machine gagne une clé `journaux` : un script qui range des
  diagnostics a besoin du chemin, pas d'une phrase.
- Le compteur `<!--inv:tests-bats-->` passe à 100. C'est la garde qui l'a signalé, pas une relecture.
- Les fixtures d'aperçu de `CaptureDialogues`, `CaptureLot` et `CapturePassage` sont en base 1000. Les
  PNG ne sont pas commités : `capture-vues.yml` les régénère au push sur `main`.
- ⚠️ **Cette ADR ne dit pas qu'il faut un `bats` par promesse.** Elle dit qu'une promesse dont on
  **retire le témoin habituel** - ici la pile sur la console - doit voir son nouveau témoin vérifié.
  Retirer sans vérifier, c'est déplacer la preuve hors de portée en la croyant conservée.
