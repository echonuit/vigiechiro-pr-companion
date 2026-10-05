## Why

Une fois un dépôt entamé, le passage est en `DEPOT_EN_COURS`, que l'écran affiche comme « Prêt à
déposer », et « Générer les archives » répond « préparez-le d'abord » : un conseil impossible, puisque la
préparation est faite. Samuel a dû redémarrer l'application (#5599, chantier #5596).

L'enquête d'ouverture a élargi le défaut. Le compte rendu conseille, après un contenu refusé,
« Régénérez les archives de la nuit, puis relancez ». Or un refus laisse le passage en
`DEPOT_EN_COURS`, justement l'état où la génération est refusée : le conseil n'a jamais pu être suivi.
Le test de #3946 qui le dit vérifié change la réponse simulée au lieu d'exercer la génération.

Remède retenu par Sébastien (remède A du bloc de prise) : admettre la génération pendant un dépôt
entamé, tant qu'aucun téléversement ne tourne.

## What Changes

- La génération des archives est admise en `DEPOT_EN_COURS`, en plus de « Prêt à déposer » et
  « Déposé ». Elle garde les identifiants des archives (même liste source, même partition), donc le
  plan de dépôt conserve ce qui est déjà en ligne, et seul le manquant repart à la relance.
- La génération est **refusée pendant un téléversement actif** du même passage, avec un message qui le
  dit et dit quoi faire (attendre la fin ou annuler). Aujourd'hui rien ne l'empêche, et la source
  pipelinée écrit dans le même dossier `depot/`.
- Le refus « préparez-le d'abord » ne reste que pour les passages réellement non préparés.
- Le conseil « régénérez, puis relancez » devient un geste suivable, éprouvé de bout en bout.

## Capabilities

### New Capabilities

Aucune.

### Modified Capabilities

- `lot/depot-sur-vigie-chiro` : ouverte par le changement `un-refus-de-s3-n-est-pas-un-refus-de-droits`,
  pas encore archivé. Ce changement y ajoute les exigences sur la génération des archives pendant un
  dépôt ; son delta porte donc aussi un `## Purpose`, sans effet si la capacité existe déjà à
  l'archivage.

## Impact

- `lot/model/ServiceLot.genererArchivesDepot` : la garde de statut, et la garde de téléversement actif.
- `lot/model/DepotVigieChiro.deposer` : s'inscrire dans le registre des téléversements pendant qu'il
  tourne.
- Un registre des téléversements en cours dans `lot/model`, fourni par `LotModule` (toujours présent),
  puisque `DepotVigieChiro` est une liaison optionnelle.
- Les deux surfaces qui génèrent : l'écran du lot (`LotViewModel`) et la commande `exporter-lot` ; le conseil du compte
  rendu et de `deposer-vigiechiro` n'a pas à changer de texte, il devient vrai.
- Un banc qui suit le geste de bout en bout, sans réponse simulée pour la génération.
