# Passage

L'écran **Passage** est le **pivot** d'une nuit donnée : il rassemble en une seule page tout ce qui
concerne cette nuit et ouvre les écrans spécialisés (vérification, dépôt, validation, diagnostic).
C'est l'écran sur lequel vous revenez entre chaque étape du [parcours](../parcours/index.md).

## Anatomie de l'écran

![L'écran Passage au statut « Vérifié » : bandeau d'identité, statut du workflow, résumé et cartes d'actions.](../assets/captures/apercu-passage.png)

De haut en bas :

- **En-tête** : l'identifiant de la nuit (carré / point / numéro / année) et les actions sur le
  passage (**Modifier le rattachement**, **Supprimer**).
- **Bandeau d'identité** : date et plage horaire, enregistreur, **statut** et **verdict**.
- **Statut du workflow** : une frise qui situe la nuit dans sa progression
  (Importé, Transformé, Vérifié, Prêt à déposer, Déposé).
- **Résumé de la nuit** : volumes (bruts et transformés), durée audible, nombre de séquences.
- **Cartes d'actions** : Vérifier l'enregistrement, Diagnostic matériel, Préparer le dépôt et
  Validation Tadarida. Une seule carte est mise en avant : la **prochaine action recommandée**.

## Le déverrouillage de la validation

La carte **Validation Tadarida** est **grisée** tant que la nuit n'est pas déposée (voir la capture
ci-dessus, au statut « Vérifié »). Une fois la nuit **déposée**, elle se déverrouille :

![Le même écran au statut « Déposé » : la carte « Validation Tadarida » est devenue accessible.](../assets/captures/apercu-passage-depose.png)

!!! info "Pourquoi cet ordre ?"
    Vigie-Chiro ne renvoie les résultats Tadarida que 24 à 48 h après le dépôt : la validation des
    espèces vient donc nécessairement **après** le dépôt. Voir le [parcours métier](../parcours/index.md).

## Modifier le rattachement

Si une nuit a été rattachée par erreur (mauvaise année ou mauvais numéro de passage), le bouton
**Modifier le rattachement** ouvre une fenêtre permettant de corriger ces informations.

![La fenêtre de modification du rattachement : année et numéro de passage.](../assets/captures/apercu-passage-rattachement.png)
