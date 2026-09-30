Toutes ces tâches réalisent le lot #5599 du chantier #5596, en **une** demande de fusion. Si le lot se
révèle en demander deux, il s'ouvre en sous-chantier (ADR 4712) plutôt que d'absorber.

Chaque tâche commence par son test rouge (article A7).

## 1. Le geste conseillé, de bout en bout

- [x] 1.1 Écrire le banc de bout en bout, sur base réelle : un dépôt dont une archive est refusée pour
      son contenu laisse le passage « Dépôt en cours » ; la génération **par le service de lot** (pas
      une réponse simulée) ; la relance ; l'unité déposée, et les unités déjà en ligne non renvoyées.
      Rouge attendu : la génération refuse avec « préparez-le d'abord ».
- [x] 1.2 Admettre `DEPOT_EN_COURS` dans la garde de statut de `ServiceLot.genererArchivesDepot`,
      en gardant le message propre à `RECUPERE`. Vert : 1.1 passe.
- [x] 1.3 Un cas par statut sur la garde : « préparez-le d'abord » pour les statuts non préparés
      seulement, jamais pour `PRET_A_DEPOSER`, `DEPOT_EN_COURS` ni `DEPOSE`. Mutation : retirer
      `DEPOT_EN_COURS` de la liste doit faire rougir.

## 2. Pas de génération pendant un téléversement

- [x] 2.1 Écrire les cas du registre `TeleversementsEnCours` : inscrit pendant, retiré après un succès,
      après une exception et après une annulation. Rouge attendu : le registre n'existe pas.
- [x] 2.2 Le registre, fourni en `@Singleton` par `LotModule`, et `DepotVigieChiro.deposer` qui s'y
      inscrit par un jeton `AutoCloseable`. Vert : 2.1 passe ; `DepotVigieChiroTest` reste vert.
- [x] 2.3 Écrire le cas du service : génération refusée pendant un téléversement inscrit, avec un
      message qui dit d'attendre la fin ou d'annuler, et aucune archive écrite ; admise après le
      retrait. Puis la garde dans `genererArchivesDepot`, **avant** la garde de statut. Mutation :
      retirer la garde doit faire rougir.

## 3. Les deux surfaces et ce qui en dépend

- [x] 3.1 Relever les autres gardes du lot qui testent `PRET_A_DEPOSER` sans `DEPOT_EN_COURS`
      (`ActionsLotPossibles`, `TeleversementGroupe`, `PreparationGroupee`, `ServiceRattachement`,
      `FormatsLot`…), une par une, et consigner dans #5599 ce qu'on en fait. Toute correction qui
      déborde la génération se pose comme question au lieu de s'absorber.
- [x] 3.2 L'écran : le bouton « Générer les archives » est offert en « Dépôt en cours » hors
      téléversement, et le refus pendant un téléversement s'affiche sans redémarrage. Un test de vue ;
      l'aperçu de l'écran dans cet état, régénéré et relu. **Tenu ainsi** : le test de vue clique le
      bouton et lit le bandeau (vu rouge sur la mutation de la règle) ; les deux états ont été rendus
      par `CaptureLot` **en local** et relus, sans ajouter d'aperçu permanent au manifeste, ce qui
      toucherait les inventaires de captures : à décider avec le porteur.
- [x] 3.3 La commande `exporter-lot` ne prépare que si le passage ne l'est pas encore, puis génère selon
      `ServiceLot.archivesSeGenerent` (option a, décidée pendant la réalisation : elle préparait
      toujours, donc refusait tout passage déjà préparé). Cas d'`ExporterLotTest` : « Dépôt en cours » génère
      sans préparer ; « Vérifié » prépare puis génère. Rouge attendu sur le premier.

## 4. Avant la demande de fusion

- [x] 4.1 PIT sur `TeleversementsEnCours` et sur la garde de `ServiceLot`, survivants lus un par un.
- [x] 4.2 La batterie locale sans refus, `DocumentationAJourTest` vert, et `docs/ecrans/lot.md` relue :
      elle doit dire qu'on peut régénérer pendant un dépôt entamé, et pas pendant un téléversement.
