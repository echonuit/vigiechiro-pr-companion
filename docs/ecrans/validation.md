# Validation

L'écran **Validation** (la vue audio unifiée « Sons & validation ») sert à **écouter, relire et
corriger** les espèces identifiées par l'outil Tadarida, et à constituer votre **corpus de sons de
référence**. On y arrive depuis plusieurs points : un **passage** (après dépôt, pour valider ses
résultats Tadarida), l'accueil (**Sons & validation**), **Espèces & observations** (les détections d'une
espèce) et **Carte & passages** (un passage ou le lot filtré).

![Sons & validation : table des observations, écoute pleine largeur et barre d'actions.](../assets/captures/apercu-sons-validation.png)

Quelle que soit la source, l'écran présente la **table des observations** (espèce, proposition Tadarida,
statut À revoir / Validée / Corrigée…), filtrable par statut, et un **panneau d'écoute pleine largeur**
(sonogramme + spectrogramme) pour la ligne sélectionnée. Les **colonnes de contexte** (passage, carré,
point) s'affichent quand la source couvre plusieurs passages et se masquent pour un passage unique.

## Relire et corriger

Pour l'observation sélectionnée, vous pouvez :

- **Valider** : retenir la proposition de Tadarida ;
- **Corriger** : retenir un autre taxon, choisi dans la liste ;
- **Marquer / retirer la référence** : ajouter l'observation à votre corpus de sons de référence, ou l'en retirer.

Un **mode inventaire** permet de propager une validation aux autres détections de la même espèce.

## Validation d'un passage (Tadarida)

Ouvert sur un **passage** (accessible **après le dépôt** : Vigie-Chiro renvoie les résultats
d'identification 24 à 48 h plus tard, voir le [parcours](../parcours/index.md)), l'écran permet
d'**importer le fichier CSV** de résultats Tadarida, puis d'**exporter** le fichier `_Vu` réinjectable
(avec, en option, la trace du mode de validation). Ces actions propres au passage vivent dans le menu « ☰ ».

## Sons de référence

Depuis l'accueil, l'activité **Sons & validation** ouvre l'écran sur **toutes les observations marquées
« référence »** : vous les **écoutez**, les **validez / corrigez**, **retirez** la référence, et
**exportez la bibliothèque** (un récapitulatif CSV + la copie des fichiers son) vers un dossier de votre
choix.
