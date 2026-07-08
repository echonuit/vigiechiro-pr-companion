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
- **Cartes d'actions** : Vérifier l'enregistrement, Diagnostic matériel, Préparer le dépôt,
  Validation Tadarida et Écouter les non identifiés. Une seule carte est mise en avant : la
  **prochaine action recommandée**.

## Le déverrouillage de la validation

La carte **Validation Tadarida** est **grisée** tant que la nuit n'est pas déposée (voir la capture
ci-dessus, au statut « Vérifié »). Une fois la nuit **déposée**, elle se déverrouille :

![Le même écran au statut « Déposé » : la carte « Validation Tadarida » est devenue accessible.](../assets/captures/apercu-passage-depose.png)

!!! info "Pourquoi cet ordre ?"
    Vigie-Chiro ne renvoie les résultats Tadarida que 24 à 48 h après le dépôt : la validation des
    espèces vient donc nécessairement **après** le dépôt. Voir le [parcours métier](../parcours/index.md).

## Écouter les non identifiés

Tadarida ne retient qu'une partie des enregistrements d'une nuit : les autres (bruit, sons trop faibles,
séquences non classées) **n'apparaissent pas dans la validation**. La carte **Écouter les non identifiés**
ouvre la vue [Sons & validation](validation.md) sur **ces séquences‑là** : celles qui existent sur le
disque mais **sans identification Tadarida**. Vous pouvez les **écouter** pour vérifier par vous‑même s'il
s'y cache une chauve‑souris que l'algorithme aurait manquée. Leur colonne d'espèce affiche « — » (aucune
proposition).

**Valider à la main.** Si vous reconnaissez une espèce, sélectionnez la séquence, choisissez le taxon dans
la liste puis cliquez sur **Corriger** : une observation est créée pour cette séquence, qui passe alors au
statut **Corrigée** avec votre taxon. La séquence **reste** dans la liste (vous voyez ce qui a déjà été
traité) et l'observation rejoint le reste de vos données. Le bouton **Valider** — qui « retient la
proposition Tadarida » — reste inactif ici, faute de proposition à retenir.

## Annuler le dépôt

Sur un passage **déposé**, un bouton **Annuler le dépôt** (barre du haut, visible sur la capture ci-dessus)
permet de revenir en arrière : après confirmation, la nuit repasse du statut « Déposé » à « **Prêt à
déposer** ». Les **validations Tadarida déjà saisies sont conservées** : annuler le dépôt ne touche pas
votre travail de validation, il rouvre seulement l'étape de dépôt (par exemple pour corriger quelque chose
avant de redéposer). Le bouton n'apparaît que lorsqu'il a un sens, c'est-à-dire sur un passage
effectivement déposé.

## Purger les originaux

Le **Résumé de la nuit** affiche le **volume des bruts** : la copie des enregistrements d'origine
conservée sur le disque au moment de l'import. Ces fichiers ne servent **pas** à l'écoute ni à la
validation (celles-ci s'appuient sur les séquences transformées) et peuvent être **volumineux**.

Un bouton **Purger les originaux** (barre du haut) permet, après **confirmation**, de **supprimer** ces
fichiers bruts pour **récupérer de l'espace disque**. Les **séquences d'écoute, la validation et le
dépôt sont conservés** : seul le volume des bruts tombe à **0**. Le bouton n'apparaît que si la nuit
conserve encore des originaux. Cette suppression est **définitive**.

> Pour purger **toutes les nuits** d'un coup, utilisez le menu « ☰ → Purger les originaux importés… »
> (voir [Accueil et navigation](index.md)).

## Modifier le rattachement

Si une nuit a été rattachée par erreur (mauvaise année ou mauvais numéro de passage), le bouton
**Modifier le rattachement** ouvre une fenêtre permettant de corriger ces informations.

![La fenêtre de modification du rattachement : année et numéro de passage.](../assets/captures/apercu-passage-rattachement.png)
