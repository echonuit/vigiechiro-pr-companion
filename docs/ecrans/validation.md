# Validation

L'écran **Validation** (« Validation Tadarida ») sert à **relire les espèces** identifiées
automatiquement par l'outil Tadarida. Il n'est accessible **qu'après le dépôt** : Vigie-Chiro
renvoie les résultats d'identification 24 à 48 h plus tard (voir le [parcours](../parcours/index.md)).

## Importer les résultats Tadarida

À l'ouverture, l'écran attend que vous **importiez le fichier CSV** de résultats renvoyé par
Vigie-Chiro.

![L'écran Validation à l'entrée : avant l'import du CSV Tadarida.](../assets/captures/apercu-validation-import.png)

## Relire et corriger

Une fois le CSV importé, l'écran présente la liste des **observations** : pour chaque séquence,
l'**espèce proposée** par Tadarida et son **statut** (À revoir, Validée, Corrigée). La liste se
filtre par statut.

![L'écran Validation en revue : liste des observations, détail et écoute de la séquence sélectionnée.](../assets/captures/apercu-validation-revue.png)

Pour l'observation sélectionnée, le **détail** affiche la proposition (espèce et probabilité) et une
**écoute** (sonogramme et spectrogramme). Vous pouvez alors :

- **Valider** : retenir la proposition de Tadarida ;
- **Corriger** : retenir un autre taxon, que vous choisissez dans la liste.

Un **mode inventaire** permet de propager une validation aux autres détections de la même espèce.
L'**export** du fichier peut, en option (une case à cocher), inclure la trace du mode de validation.
