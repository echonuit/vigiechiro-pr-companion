# ADR 3498 - La déclaration porte sur les lectrices, pas sur les écrivaines

- **Statut** : Accepté - 2026-08-09, **amendé** par [ADR 3575](3575-le-journal-fait-exception-et-le-cliquet-ne-recopie-rien.md) : le journal fait exception à la définition, et le cliquet devient un compteur au lieu d'une liste dérivable
- **Chantier** : #3498, lot 1 du chantier #3518 ; défaut trouvé à la passe 1 de la clôture de #2720
- **Vérification** : certaine - `ClassementLectureEcritureTest#aucune_commande_n_est_sans_classement`

## Contexte

L'application graphique **réserve** le dossier de travail pour toute sa durée (`VerrouWorkspace`,
#2731). La documentation de ce verrou nomme elle-même le cas qui l'a fait naître : « deux instances
graphiques, **une IHM et une CLI**, ou une restauration pendant un import ».

La CLI ne l'a jamais demandé. Un `vigiechiro importer` lancé pendant que l'application est ouverte
écrivait dans la même base, sans que rien ne l'en empêche ni ne le signale. Le verrou est
**coopératif** : il ne bloque personne, il refuse à qui le demande - et personne ne demandait.

Le lot 1 de #2720 avait construit le verrou ; les lots 4 et 5 ont travaillé la surface CLI sans le
reprendre. Aucune cérémonie de lot ne pouvait voir le trou : il n'est visible que depuis les deux
bouts à la fois.

Poser le verrou demande de savoir **quelles commandes écrivent**. Aucun signal mécanique ne le dit :

- le **nom** ne prouve rien - `metadonnees-passage` rattrape et envoie des métadonnées sans que rien
  ne l'annonce, tandis qu'`emplacements` écrit, mais ailleurs ;
- le **service appelé** classe `lister-sites` parmi les écrivains, `ServiceSites` sachant écrire ;
- l'**analyse d'appels** ne tranche pas non plus. Mesuré sur les 65 commandes avec ArchUnit, en
  suivant les appels jusqu'aux primitives : elle rate `supprimer-site` et signale `lister-carres`,
  qui n'écrit qu'un CSV à l'emplacement demandé par l'utilisateur. Elle se trompe **dans les deux
  sens**, et un vérificateur qui se trompe vers le vert est pire que pas de vérificateur.

## Décision

**Le verrou se prend par défaut. Une commande s'en dispense en se déclarant lectrice**, par
l'interface marqueur `fr.univ_amu.iut.cli.LectureSeule`.

Ce qui départage les deux sens de déclaration n'est pas leur précision - elles sont aussi faillibles
l'une que l'autre - c'est **le sens dans lequel une erreur se paie** :

| Déclaration oubliée | Conséquence |
|---|---|
| une **écrivaine**, si on déclarait les écrivaines | son écriture échappe au verrou. Le défaut persiste **en silence**, et rien ne le dira |
| une **lectrice**, comme ici | sa consultation est refusée pendant que l'application est ouverte. **Visible, gênante, sans danger** - et signalée le jour même |

La liste des lectrices est aussi la plus **stable** : une commande qui lit le restera, alors qu'une
commande qui se met à écrire ne rappellera à personne qu'il faut la déclarer.

« Lecture seule » veut dire : **ne touche ni la base ni les dossiers du dossier de travail**.
Interroger le réseau, ou écrire **hors** du dossier de travail, reste de la lecture seule : le verrou
protège le dossier de travail, pas le disque. La **configuration d'amorçage** en fait partie - elle
vit dans le dossier de configuration et se protège toute seule, en s'écrivant d'un seul coup (#3507).

⚠️ Cette frontière n'est pas une commodité, et elle a coûté un aller-retour. `emplacements` avait
d'abord été classée écrivaine, au motif qu'elle écrit la configuration d'amorçage ; sept tests ont
rougi en intégration continue. Le remède n'était pas de les isoler : la commande sert à **repointer**
le dossier de travail, et la verrouiller revient à refuser de déménager à qui déménage justement parce
que la place actuelle est occupée ou abîmée. `CliVerrouWorkspaceTest` fige ce cas.

Le refus est un `RefusAvantEcriture`, donc un code de sortie **2** : rien n'a été touché. Il est
traduit dans `migrerPuisExecuter`, car le gestionnaire d'exceptions de picocli ne voit que ce que lève
la commande - un refus né dans la stratégie d'exécution lui échappait et retombait en `1`, « échec,
état incertain », ce qui était faux dans le sens inquiétant.

## Ce que le garde vérifie, et ce qu'il ne vérifie pas

`ClassementLectureEcritureTest` exige que **chaque** commande soit classée : elle porte le marqueur,
ou elle figure dans la liste des écrivaines, tenue dans le test. Il vérifie aussi que cette liste ne
garde pas d'entrée périmée, qui ferait passer pour classée une commande renommée.

⚠️ **Il ne dit pas si le classement est juste.** Il dit qu'un choix a été fait. C'est l'oubli
silencieux qu'il rend impossible, pas l'erreur de jugement - et l'erreur de jugement, ici, se paie du
bon côté.

## Conséquences

- Une commande nouvelle arrive **protégée** : ne rien faire la verrouille. Le garde force alors sa
  conceptrice à trancher, plutôt que de la laisser hériter d'un défaut.
- Vingt-trois commandes sont déclarées lectrices, dont le groupe `api` et ses deux filles, en lecture
  seule par charte ([ADR 3006](3006-le-groupe-api-est-borne.md)).
- La migration du schéma prend le verrou de son côté : une commande de lecture sur une base à mettre à
  jour peut donc être refusée. C'est voulu - mettre à jour le schéma est une écriture.
- Le verrou est **réentrant dans un processus** (`DETENUS`) : un test qui le prendrait par
  `VerrouWorkspace.prendre` en rendrait celui de la CLI factice et ne prouverait rien. Les tests posent
  donc un verrou de fichier **brut** - et en `bats`, un verrou POSIX par `fcntl`, car `flock(1)` pose un
  verrou d'une autre famille, que `FileChannel.tryLock` ignore : le test aurait été vert sans rien
  bloquer.
