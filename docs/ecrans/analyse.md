# Espèces & observations

L'écran **« Espèces & observations »** est la porte d'entrée du prisme **biodiversité** : il **exploite
transversalement** vos observations (toutes nuits confondues) pour répondre à « quelles espèces ai-je
détectées, où, quand et combien ? ». Il complète le prisme **collecte & passages** (sites, import,
validation), qui produit la donnée nuit par nuit.

On l'ouvre depuis la carte **« Espèces & observations »** de l'accueil.

![L'inventaire par espèce : chaque espèce détectée, son groupe, ses compteurs et sa période.](../assets/captures/apercu-analyse.png)

## L'inventaire

Une table récapitule vos espèces. Un sélecteur **Regrouper** propose deux angles (le pivot espèce ↔ lieu) :

- **Par espèce** : une ligne par espèce, avec son **groupe** taxonomique, le nombre de **détections**, de
  **passages**, de **carrés** et de **points** où elle apparaît, et sa **période** d'observation.
- **Par carré** : une ligne par carré, avec sa **richesse spécifique** (nombre d'espèces distinctes) et son
  total de détections — utile pour le rendu Vigie-Chiro.

![L'inventaire par carré : la richesse spécifique (nombre d'espèces) de chaque carré.](../assets/captures/apercu-analyse-carre.png)

L'espèce retenue pour chaque observation est le **taxon validé** par l'observateur s'il existe, sinon la
**proposition Tadarida** ; les pseudo-taxons « bruit » et « oiseau » sont exclus.

## Filtrer par niveau de confiance

Le filtre **Statut** restreint l'inventaire selon l'état de revue des observations :

- **Validée** : l'observateur a confirmé la proposition Tadarida ;
- **Corrigée** : l'observateur a saisi une autre espèce ;
- **Non touchée** : proposition Tadarida non encore revue.

Sans filtre (**Tous les statuts**), tout est pris en compte. Tant qu'aucune nuit n'a été importée et
validée (résultats Tadarida), l'écran invite à le faire.

## Filtrer et exporter

Le champ **Filtrer** restreint la table à la volée (insensible à la casse et aux accents) : par **nom ou
code d'espèce** en mode *Par espèce*, par **numéro de carré ou nom de site** en mode *Par carré*. Le
bouton **« 📤 Exporter… »** enregistre l'inventaire **affiché** (tel que filtré) en **CSV**, prêt pour un
tableur ou un partage.
