# ADR 0037 — Une barre d'actions plie, elle ne tronque pas ; et tout texte coupé n'est pas une barre qui ne plie pas

- **Statut** : Accepté — 2026-07-20
- **Chantier** : #2012, #2046 ; analyse de #1641, #1701, #1873, #1579

## Contexte

Cinq issues ouvertes disaient la même chose : « des libellés sont tronqués ». Sur la fiche passage, **sept boutons sur sept** étaient coupés, dont « Supp… » pour une action destructrice — impossible à distinguer de « Supprimer les archives ».

Aucun test ne rougit là-dessus. Un test vérifie qu'un bouton **fait** ce qu'il doit ; il ne vérifie pas qu'on peut **lire** ce qu'il dit. Ces cinq issues sont toutes nées d'une revue visuelle.

## Décision

**1. Le slot d'actions d'un écran est un `FlowPane`, pas une `HBox`.** Une `HBox` ne renvoie **jamais** à la ligne : elle rétrécit ses enfants, et un `Button` trop étroit ellipse son texte. Le nombre d'actions d'un écran ne fait que croître au fil des fonctionnalités — le conteneur doit **plier**.

Le slot partagé `entete-actions` (fiche passage, mes sites, fiche site) est passé en `FlowPane`, et la règle CSS troque `-fx-spacing` pour `-fx-hgap` / `-fx-vgap`. La fiche passage affiche désormais ses huit actions sur deux rangées, libellés entiers.

**2. Un texte coupé n'a pas une cause mais deux, et le remède de l'une aggrave l'autre.**

| Mécanisme | Symptôme | Remède |
|---|---|---|
| **Le conteneur ne plie pas** | Une rangée de boutons se comprime jusqu'à l'ellipse | `FlowPane` |
| **Un enfant est gourmand** | Un enfant en `HBox.hgrow="ALWAYS"` prend toute la largeur restante et écrase ses voisins | **Pas** un `FlowPane` |

Le second cas se reconnaît à la présence d'un `hgrow` : un `FlowPane` **ne sait pas l'exprimer**. Le convertir ferait perdre l'intention de mise en page — un `Region` ressort qui ancre des boutons à droite, un `FlowPane` de puces qui doit occuper la largeur restante — et déplacerait le défaut au lieu de le corriger.

C'est le cas de la barre de filtres de l'inventaire (#1873) et de la barre d'actions de « Sons & validation » (#1641). Les deux tronquent, aucune des deux ne se répare par ce remède.

**3. Le seul juge est la capture.** Avant d'appliquer l'un ou l'autre, ouvrir l'aperçu, **recadrer et agrandir** ; puis regarder le FXML pour savoir lequel des deux mécanismes est en cause. L'ordre compte : la capture dit **qu'il y a** un défaut, le FXML dit **lequel**.

## Conséquences

- Une nouvelle barre d'actions d'écran réutilise `entete-actions` : elle hérite du pliage.
- Un `hgrow` dans une barre qui tronque est un **signal d'arrêt** : ce n'est pas ce défaut-là.
- Effet de bord observé, et qui vaut avertissement : en convertissant `entete-actions`, le **bandeau d'identité** de la fiche site a cessé de tronquer ses cellules (« N° DE CA… » redevenu « N° DE CARRÉ »). La `HBox` imposait au conteneur une largeur minimale égale à la somme de ses boutons, qui débordait la fenêtre et rognait ce qui suivait. Un défaut de troncature peut donc venir d'**un autre bloc que celui où il se voit**.

## Ce qui a été écarté

**Verser les actions secondaires dans un menu ☰** (c'était la piste que je recommandais en ouvrant #2012). Elle **détruit** l'affordance construite par #789, #1300 et #1302 : un `MenuItem` désactivé ne reçoit pas le survol, donc pas d'infobulle, et l'idiome du projet veut alors que le motif passe **dans le libellé**. Or ces motifs sont des phrases entières (« Suppression impossible : ce passage est déposé sur Vigie-Chiro. Annulez d'abord le dépôt. »). Six actions déplacées, c'étaient six explications à réécrire en suffixes courts, avec perte.

Le constat qui la fondait reste vrai : **huit actions de même poids visuel en tête d'écran, ce n'est plus une barre, c'est une liste.** Mais cela se traite en décidant lesquelles méritent d'être toujours visibles — une question de produit, pas de mise en page.

**Élargir la fenêtre de capture** (autre piste de #1701). Elle fait disparaître le symptôme de l'aperçu sans rien changer pour l'utilisateur qui travaille sur un écran étroit.
