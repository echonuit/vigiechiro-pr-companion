## Context

Voir `proposal.md` pour le pourquoi. L'état du code, relevé sur `main` à `57c0ce800` :

- `ServiceLot.genererArchivesDepot` refuse tout statut autre que `PRET_A_DEPOSER` et `DEPOSE` (et
  `RECUPERE` a son propre message, #2581). `DEPOT_EN_COURS` tombe dans « préparez-le d'abord ».
- `DepotVigieChiro.deposer` bascule en `DEPOT_EN_COURS` dès qu'il reste des unités, et seul
  `ServiceLot.reinitialiserDepot` ramène à `PRET_A_DEPOSER`, en **effaçant le plan**. Un refus laisse
  donc le passage en `DEPOT_EN_COURS` jusqu'à la réinitialisation.
- `EtapesWorkflow.jalonDe` affiche `DEPOT_EN_COURS` comme le jalon « Prêt à déposer » (#980).
- La génération lit `sequenceDao.findBySession`, la même liste que `sequencesADeposer` qui alimente la
  source du dépôt, résolue par la même règle. La liste est figée par la préparation. Les identifiants
  des archives viennent de la partition (`CompacteurDepot.planifier`), donc sont stables.
- Pendant un téléversement, `SourceArchivesRegenerables` **produit** les archives absentes dans le
  même dossier `depot/`, sous une fenêtre de deux. Une génération concurrente écrirait les mêmes
  fichiers.
- Seul l'écran sait qu'un téléversement tourne (`DepotViewModel.marquerEnCours`) : le service n'en a
  aucune trace. `DepotVigieChiro` est une liaison **optionnelle** (`DepotVigieChiroModule`), fournie
  par `@Provides @Singleton` ; `ServiceLot` l'est par `LotModule`, toujours présent.
- La commande `exporter-lot` génère aussi, par le même service.

## Goals / Non-Goals

**Goals :**

- Admettre la génération en `DEPOT_EN_COURS` sans perdre la progression.
- Refuser la génération pendant un téléversement **dans ce processus**, avec un message qui dit quoi
  faire.
- Un banc qui exerce la génération réelle entre un refus et une relance.

**Non-Goals :**

- Exclure deux **processus** (l'application et la CLI lancées ensemble sur la même base). Le registre
  vit en mémoire ; voir Risks.
- L'état figé « Annulation… » de la capture 4 : sa cause n'est pas établie, il n'est pas dans ce lot.
- Changer le texte du conseil de régénération : il devient vrai sans changer.

## Decisions

### 1. Un registre des téléversements en cours, dans `lot/model`

Un petit objet `TeleversementsEnCours` (un ensemble concurrent d'identifiants de passage, avec
`inscrire` et `retirer`) est fourni en `@Singleton` par `LotModule`. `DepotVigieChiro.deposer`
s'inscrit à l'entrée et se retire dans un `finally` ; `ServiceLot.genererArchivesDepot` le consulte.

L'inscription rend un jeton `AutoCloseable`, ce qui fait du `try` la seule façon de s'inscrire et
empêche d'oublier le retrait.

Options écartées :

- **Faire dépendre `ServiceLot` de `DepotVigieChiro`.** La liaison est optionnelle : une installation
  sans la fonctionnalité de dépôt ne pourrait plus construire le service de lot.
- **Un drapeau en base.** Il survivrait à un plantage et bloquerait la génération à tort jusqu'à un
  nettoyage manuel, c'est-à-dire exactement le genre de coincement que ce lot corrige. Il fermerait la
  course entre deux processus, au prix d'un défaut pire que celui qu'il couvre.
- **Laisser l'écran seul griser le bouton.** La CLI et tout autre appelant du service resteraient sans
  garde, et l'écran a déjà montré (capture 4) un bouton actif pendant un téléversement qui se terminait.

### 2. La garde de statut devient une liste d'admis

`PRET_A_DEPOSER`, `DEPOT_EN_COURS` et `DEPOSE` sont admis ; `RECUPERE` garde son message ; tout le reste
reçoit « préparez-le d'abord ». La garde de téléversement actif passe **avant** la garde de statut :
pendant un téléversement le passage est justement `DEPOT_EN_COURS`, et c'est le message du téléversement
qui dit vrai.

### 3. Le banc de bout en bout remplace la simulation, il ne la contredit pas

Le test de #3946 reste : il établit que le moteur retente une unité refusée. Le nouveau banc ajoute ce
qu'il ne voyait pas, la génération par le service entre les deux dépôts, sur une base réelle.

### 4. Le pourquoi durable va en ADR 5599 à la clôture

« Un passage dont le dépôt est entamé se régénère, sauf pendant un téléversement », avec les options
écartées ci-dessus. Cette note la prépare.

## Risks / Trade-offs

- [Deux processus sur la même base : l'application téléverse, la CLI génère] → Non couvert, et écrit.
  C'est un usage rare (le même observateur, deux interfaces, le même passage, au même moment) ; le
  couvrir exigerait le drapeau en base écarté plus haut. À rouvrir si un cas réel se présente.
- [Régénérer écrit aussi les archives déjà en ligne] → Du disque pendant un temps, pas de réseau : la
  relance ne renvoie que les restantes. Le bouton « Supprimer les archives de dépôt » reste là.
- [Le compte rendu conseille de régénérer après un contenu refusé ; si le refus tient au contenu des
  séquences elles-mêmes, l'archive régénérée est identique et sera refusée de même] → Hors de ce lot :
  le geste est désormais suivable, et un contenu durablement refusé se verra au second refus.

## Migration Plan

Aucune donnée : un registre en mémoire et une garde élargie. Retour arrière par retour de version.
