---
type: adr
title: "Un signal se pose à l'écriture ; la rafale se règle chez le lecteur"
status: stable
article: A15
chantier: "#3537 (lot 1 du chantier #3536), décision prise entre #3541 et #3542"
decided_at: 2026-08-10
verification: certaine
enforced_by:
  - "RevisionDonneesTest#les_mutations_en_rafale_sont_amorties"
verified:
  - by: machine:ci
    at: 2026-08-10
---

# Un signal se pose à l'écriture ; la rafale se règle chez le lecteur

## Contexte

Un binding JavaFX observe une `Property`. Il ne sait pas observer **SQLite**. Tant qu'une écriture en
base ne laisse aucune trace observable, un écran ne peut se rafraîchir qu'en se rappelant lui-même,
typiquement au retour de navigation : il rate donc tout ce qui survient pendant qu'il est affiché.
C'était le défaut #1376, sur les compteurs de l'accueil.

#3541 a posé le mécanisme : le port `JournalMutations` (`commun.model`, sans JavaFX) et son côté
observable `RevisionDonnees` (`commun.viewmodel`). Il a posé **deux** règles d'appel :

1. **après validation**, jamais avant ;
2. **une fois par opération métier**, pas par ligne écrite.

La seconde a tenu trois jours.

## Ce qui l'a fait tomber

La cartographie de #3542 a montré que **la frontière d'une opération métier n'est pas visible depuis
l'endroit qui écrit**. `RapprochementSites` crée les sites **en boucle**, en appelant
`ServiceSites.creerSite` : exactement le service qu'appelle un ajout manuel. Ce service ne peut pas
savoir s'il sert un geste d'utilisateur ou une synchronisation de deux cent cinquante sites.

Tenir la règle imposait donc de **remonter l'appel** vers les ViewModel et les commandes CLI, c'est-à-dire
de l'éloigner du point d'écriture. Or l'omission silencieuse est précisément le défaut que ce mécanisme
corrige : le rendre plus facile à oublier revenait à le désarmer pour respecter sa propre grammaire.

Le coût de l'alternative était réel et mesuré : une synchronisation de 50 sites à 4 points aurait
produit **250 émissions**, soit 1 000 `COUNT(*)` sur le fil d'affichage.

## Décision

**La règle d'appel se réduit à une : tu écris, tu signales, après validation.** L'émission se pose à
côté de l'écriture, là où la prochaine écriture la trouvera.

**La rafale se règle chez le lecteur.** `RevisionDonnees` ne poste pas de nouvelle avancée tant que la
précédente n'est pas appliquée : deux cent cinquante signaux donnent **un** réveil. Le drapeau se
baisse **avant** l'avancée, pour qu'une mutation survenue pendant que les lecteurs réagissent reposte.

Aucune mutation n'est perdue : le lecteur **relit tout** à chaque réveil. Ce qui compte est qu'il
finisse à jour, pas qu'il soit réveillé autant de fois qu'il y a eu d'écritures.

**Le périmètre reste structurel** : seules les mutations qui peuvent changer l'inventaire affiché
(sites, points, passages, observations). Une validation, un verdict, une disposition de colonnes ne
changent aucun de ces nombres.

## Conséquences

- l'émission est à trois lignes de l'écriture, dans treize sites de production ;
- l'amortissement vit en **un** endroit, sous test, au lieu d'une vigilance dans chaque appelant ;
- la CLI émet aussi, sans le vouloir ni le savoir : elle appelle les mêmes services. C'est ce qui a
  révélé qu'elle n'a **pas de fil d'affichage** à rejoindre, `Platform.runLater` levant sans toolkit.
  L'exécuteur du fil d'affichage exécute donc **sur place** quand il n'y a pas de toolkit ;
- une émission posée dans une **vue** est un défaut de placement, pas une variante : celle de la
  restauration l'était, et rendait la commande CLI `restaurer` muette.

## Ce qu'on a appris sur l'inventaire, et qui vaut au-delà

**Trois inventaires successifs des écritures structurelles ont été faux.** Le `grep` sur
`passageDao.insert` manque `CreationPassageArchive`, qui nomme son DAO sur une ligne et appelle
`insert` sur la suivante. Le balayage multi-lignes manque `MoteurImport`, qui écrit le passage en
**SQL brut**, hors de tout DAO. Et le premier mécanisme d'émission déduisait l'effet structurel d'un
`RapportSynchro.nombre` qui compte les nuits **récupérées**, pas les passages **créés**.

C'est le même constat que l'[ADR 3498](3498-la-declaration-porte-sur-les-lectrices.md) établit sur les
commandes CLI : ni le nom, ni le service appelé, ni l'analyse d'appels ne tranchent, et **ils se
trompent dans les deux sens**. La différence est que 3498 a pu inverser la charge (déclarer les
lectrices, prendre le verrou par défaut) ; ici, aucune inversion équivalente n'existe : une écriture
qui n'annonce pas ne se signale nulle part.

**Le contrôle qui reste est mécanique et se relance** : aucun fichier de production ne doit contenir
une écriture sur les quatre tables comptées sans porter d'annonce. Il n'est pas automatisé, et c'est
une dette assumée : l'automatiser demanderait de reconnaître une écriture, ce qui est précisément le
problème.

## Alternatives écartées

**Émettre au ras de l'écriture, dans `DaoGenerique` et `UniteDeTravail`.** Complet par construction
pour 71 des 89 écritures, et un geste ajouté dans six mois émettrait tout seul. Écartée pour ce que
devient le **contrat** : le port ne dirait plus « une mutation validée » mais « la base a bougé », et
tous les lecteurs à venir en hériteraient. Deux conséquences le montrent : un import de 4 000
observations émettrait 4 000 fois, et une migration au démarrage émettrait alors qu'aucun écran
n'existe.

**Un signal typé par domaine** (`SITES`, `PASSAGES`…). Le seul lecteur d'aujourd'hui recalcule ses
quatre compteurs de toute façon, et un signal typé ajoute une façon d'avoir tort en silence : marquer
`SITES` en ayant aussi touché les points. La question se rouvre si un deuxième lecteur coûteux
apparaît.
