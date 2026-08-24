---
type: adr
title: "Un fichier écrit par plusieurs JVM se reconstruit, il ne s'ajoute pas"
status: stable
article: A17
chantier: "#4249, EPIC #4133"
decided_at: 2026-08-23
verification: certaine
enforced_by:
  - "IndexDesCasTest#deux_jvm_de_meme_pid_ne_s_effacent_pas"
verified:
  - by: machine:ci
    at: 2026-08-23
---

# Un fichier écrit par plusieurs JVM se reconstruit, il ne s'ajoute pas

## Contexte

Surefire tourne avec `forkCount=1C` : autant de JVM que de cœurs, et chacune filme les cas des classes
qui lui échoient. L'index qui relie un cas de recette à son clip était écrit **par ajout** au même
fichier.

Mesuré sur un tournage à neuf clips : **cinq lignes dans l'index**, pour neuf fichiers bien présents sur
le disque. Quatre cas filmés n'avaient aucune trace, et rien ne le signalait.

Le premier remède, un fragment par JVM nommé par son PID, a tenu sous Linux et cédé sous macOS :
**trois fragments pour quatre JVM**, et le cas S1-37 disparu. Le système avait réattribué un PID pendant
le tournage, et le second fragment avait écrasé le premier.

## Décision

Un fichier de sortie qu'alimentent plusieurs JVM se **reconstruit depuis des fragments**, un par JVM,
sous une identité qui ne peut pas se répéter :

- chaque JVM écrit son fragment dans `index.d/`, nommé `pid + UUID` ;
- l'index final est **reconstruit** depuis tous les fragments, sous `FileLock`, à la fermeture de chaque
  JVM.

Le PID seul dit d'où vient le fragment, ce qui aide à lire un tournage. L'UUID garantit qu'il ne sera
pas repris. Aucun des deux ne suffit.

## Conséquences

**L'index est reconstruit N fois plutôt qu'une.** C'est quelques millisecondes par fork, contre une
écriture partielle qui ne corrompt jamais l'ensemble : une JVM tuée laisse un fragment incomplet, pas un
index faux.

**Le défaut d'origine était invisible à tout signal automatique.** Les clips existaient, les tests
passaient, l'index se construisait, les gardes étaient verts. Un index amputé ressemble trait pour trait
à un index juste : il faut compter ses lignes contre les fichiers produits pour voir le manque. C'est
pourquoi `IndexDesCasTest` compte, et pourquoi le constructeur qui reçoit l'identité est ouvert au
paquet : sans cette couture, le défaut du PID réemployé n'est pas reproductible.

**Un test écrit après coup peut verrouiller le défaut.** Le premier `IndexDesCasTest` vérifiait que le
second écrivain **remplaçait** le premier. Il était vert, et il figeait l'écrasement comme comportement
attendu. Un test rédigé depuis le code observé décrit ce que le code fait, pas ce qu'il devrait faire :
c'est le même piège que celui de l'[ADR 3960](3960-un-garde-dit-la-couverture-qu-il-a-et-rend-l-etat-qu-il-emprunte.md).

## Alternatives écartées

- **Sérialiser les écritures sur le fichier final.** Un verrou tenu pendant toute l'écriture rend les
  forks dépendants les uns des autres, et une JVM tuée laisse le verrou derrière elle.
- **Un seul écrivain à la fin du tournage.** Il faudrait qu'une JVM sache que les autres ont fini, ce
  que surefire ne lui dit pas.
- **Réemployer `VerrouWorkspace`.** Il emploie le même `FileLock`, mais dans l'intention **inverse** :
  il **refuse** un second processus par `tryLock` et attrape `OverlappingFileLockException`
  ([ADR 2731](2731-un-seul-processus-par-workspace.md)). Ici on veut les accueillir tous, et le verrou
  ne sert qu'à sérialiser la reconstruction.
