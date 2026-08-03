# ADR 2731 - Un seul processus écrit dans un dossier de travail, et la seconde instance est **refusée**

- **Statut** : Accepté - 2026-08-03
- **Chantier** : #2731, lot 1 (#2721) du chantier de dette #2720
- **Vérification** : certaine - `ReservationWorkspaceTest#seconde_instance_refusee`

## Contexte

Rien n'empêchait deux processus de travailler sur le même dossier de travail : deux fenêtres de
l'application, une IHM et une CLI, ou une restauration lancée pendant un import. `SourceDeDonnees`
pose bien un `busy_timeout` de 10 s, mais il absorbe une contention **courte** entre deux écritures :
il ne protège pas le **remplacement du fichier** de base.

Le lot 1 venait précisément de poser des garanties qui supposent toutes un seul écrivain : migration
atomique (#2728), filet avant montée de version (#2729), restaurations vérifiées puis basculées
(#2727, #2730). Elles tombent entièrement si un second processus écrit pendant l'opération. Le pire
cas est nommé dans l'issue : **une instance migre pendant que l'autre écrit**.

Deux questions à trancher : **avec quel mécanisme**, et **que fait-on** de la seconde instance.

## Décision

### Un verrou de fichier système, pas un fichier de PID

Le verrou est un `FileChannel.tryLock()` sur `<workspace>/.verrou`. Le PID et l'horodatage sont
**écrits dans le fichier**, mais pour le **message** uniquement : jamais pour la décision.

Ce qui départage les deux mécanismes n'est pas la simplicité, c'est le **relâchement**. Le système
d'exploitation rend le verrou quand le processus meurt : un plantage ne condamne pas le dossier de
travail. Un fichier de PID demanderait de savoir si le PID 12345 est encore vivant, question sans
réponse portable, et sur laquelle une erreur coûte cher dans les deux sens : trop prudent, on bloque
un utilisateur pour toujours après un crash ; trop confiant, on laisse deux instances écrire.

**Un verrou qu'on ne sait pas relâcher est pire que pas de verrou** : il transforme un incident en
blocage définitif, et l'utilisateur n'a alors plus que la suppression manuelle d'un fichier caché
pour s'en sortir.

### La seconde instance graphique est refusée, pas basculée en lecture seule

L'issue laissait le choix ouvert. Le refus est retenu.

Un mode lecture seule n'existe **nulle part** dans le produit. Le livrer supposerait de gater chaque
écriture de chaque fonctionnalité (import, qualification, validation, dépôt, réglages), et de décider
pour chacune ce que « lecture seule » veut dire à l'écran. C'est un chantier en soi, pas un effet de
bord de celui-ci, et un mode à moitié appliqué serait pire que pas de mode du tout : l'utilisateur
croirait pouvoir travailler.

Le refus est immédiat, sans ambiguïté, et **réversible** : passer plus tard en lecture seule ne
remettrait pas en cause le verrou, seulement ce qu'on fait quand il est déjà pris.

### La CLI ne verrouille que ses opérations exclusives

L'IHM tient le verrou pour toute la durée de son exécution : c'est elle l'occupante. La CLI ne le
prend que pour la migration **ayant réellement quelque chose à appliquer**, la restauration et la
remise à zéro.

La nuance sur la migration est délibérée. Une commande de lecture lancée pendant que l'IHM tourne ne
migre rien ; la faire échouer sur un verrou lui coûterait plus que la protection ne lui rapporte, et
casserait l'usage scriptable qui fait l'intérêt de la CLI.

### Un processus ne se bloque pas lui-même

Une JVM ne peut pas prendre deux fois le même verrou de fichier (`OverlappingFileLockException`), et
n'a de toute façon pas à se protéger d'elle-même. La détention est donc connue au niveau du
processus : une restauration lancée **depuis** l'IHM réutilise le verrou de l'IHM, et le rendre à la
fin de l'opération ne le relâche pas.

## Conséquences

- Deux fenêtres sur le même dossier de travail : la seconde se ferme en nommant l'occupante.
- Une opération exclusive pendant qu'un autre processus travaille : refus explicite, avec le nom de
  l'occupant, plutôt qu'un échec SQLite tardif au milieu d'une écriture.
- Un plantage ne laisse aucun verrou derrière lui.
- ⚠️ **Ce que le verrou ne protège pas** : deux dossiers de travail **différents** pointant vers la
  même base (possible depuis #1038, où l'emplacement de la base est configurable). Le verrou porte
  sur le dossier de travail, pas sur le fichier de base. C'est une limite assumée, et non un oubli.
- ⚠️ Le verrou ne sérialise pas les écritures **à l'intérieur** d'un processus : ce n'est pas son
  rôle, c'est celui des transactions.

## Vérification

`ReservationWorkspaceTest` couvre le refus de la seconde réservation, le message qui nomme l'occupant
et dit quoi faire, et la libération qui rend le dossier. `VerrouWorkspaceTest` couvre le mécanisme :
prise, refus du second preneur, relâchement, réutilisation par le processus détenteur, et prise
temporaire pour une opération exclusive.

Une réserve d'honnêteté : **l'exclusion entre deux vrais processus n'est pas testée ici**. Une seule
JVM ne peut pas la simuler, le refus y venant du contrôle interne à la JVM et non du système. La
chaîne de décision (refus, message, libération) est bien exercée ; l'exclusion elle-même repose sur
la garantie de `FileChannel.tryLock`.
