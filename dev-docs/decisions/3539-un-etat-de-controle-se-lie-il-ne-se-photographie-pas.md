---
type: adr
title: "Un état de contrôle se lie, il ne se photographie pas"
status: stable
article: A15
chantier: "#3539 (lot 2 du chantier #3536)"
decided_at: 2026-08-14
verification: certaine
enforced_by:
  - "OngletReglagesEmplacementsTest#appliquer_allume_retablir"
verified:
  - by: machine:ci
    at: 2026-08-14
relations:
  prolonge: ["3095"]
---

# Un état de contrôle se lie, il ne se photographie pas

## Contexte

Un booléen est lu au moment où l'écran se construit, puis **posé** sur un contrôle : `setDisable`,
`setVisible` + `setManaged`, un `String` de libellé. Ce qui le rendait vrai peut changer ; le contrôle,
lui, ne rebouge plus.

Cette rupture est plus insidieuse que celle du lot 4 - l'écran qui ne voit pas arriver la donnée -
pour deux raisons qui se cumulent :

- **l'instantané est juste à l'instant où il est pris**, donc rien ne se voit au premier lancement.
  Le défaut n'apparaît qu'à la deuxième visite ;
- les écrans restent **vivants** dans la pile du `Navigateur` (« revenir ré-affiche l'instance
  précédente, état préservé ») : aucune reconstruction ne rattrape la photo.

Trois cas ont été corrigés, et un quatrième trouvé par balayage. Ils **n'ont pas la même signature de
surface**, et c'est le fait marquant du lot.

## Décision

**Ce qui se décide au montage, c'est le câblage ; ce qui varie, c'est la donnée.** Une propriété porte
l'état, le contrôle s'y **lie**, et la source qui l'alimente est réinterrogée au bon moment.

Deux précautions, chacune mesurée sur un cas réel :

⚠️ **Ne pas dériver l'état de ce qui est affiché.** Avant « Appliquer », les chemins sélectionnés dans
les réglages ne sont pas encore la configuration persistée : un bouton lié à eux s'allumerait sur une
**intention**. Ce qui décide, c'est ce qui a été **écrit** (#3543).

⚠️ **Recalculer ne doit pas défaire un choix de l'utilisateur** (ADR 3095). Repeupler la liste d'un
`ComboBox` par `clear()` puis `addAll()` remet sa valeur à `null`, et une liaison bidirectionnelle
propage ce `null` jusqu'au ViewModel : la saison se recharge **sans filtre**, et le tableau se rouvre
en grand sous les yeux de l'utilisateur. `setAll` remplace le contenu sans passer par la liste vide
(#3544).

## Le critère de tri, et les trois familles légitimes

La question n'est **pas** « y a-t-il un `setVisible` ? » mais :

> le fait qui le rend vrai peut-il changer pendant la vie de l'application ?

Trois familles répondent non, et sont donc légitimes :

| Famille | Pourquoi le fait est figé |
|---|---|
| un **drapeau de fonctionnalité** (`Optional<Service>.isPresent()`) | l'onglet des réglages annonce que la bascule prend effet au **prochain démarrage** |
| une **ressource embarquée** dans l'artefact | son contenu est celui du paquet livré |
| un contrôle de **modale** | la modale est rebâtie à chaque ouverture |

## Ce que le balayage a démenti

Le lot s'est terminé par un inventaire (#3545) de **138 sites** relevés sur `setVisible`,
`setManaged` et `setDisable`. **Aucun n'est un défaut**, et le seul jumeau confirmé - le libellé figé
de la fiche site (#3672) - **ne porte aucun de ces trois mots-clés** : c'est une concaténation de
chaîne dans un appel à `empiler`.

Trois des quatre formes sont **invisibles au mot-clé** :

| Forme | Ce qu'un `grep` en voit |
|---|---|
| un booléen posé au montage | le mot-clé |
| une **sortie prématurée** qui n'installe ni items ni liaison | rien |
| un **`String` figé** dans un record | rien |
| un **rappel qui court-circuite** le point d'entrée qui rafraîchit | rien |

## Conséquences

- ⚠️ **La mesure d'entrée d'un balayage oriente le regard, et peut l'orienter à côté.** Compter les
  `setDisable` désignait 138 sites dont zéro défaut, et manquait le seul vrai. Ce sont les **lectures**
  des trois premiers cas qui ont donné la forme à chercher.
- **Un relevé se rejoue** ([ADR 3664](3664-un-releve-qui-n-a-pas-ouvert-les-fichiers-est-une-hypothese.md)).
  Le premier balayage des sorties prématurées filtrait sur le **nom** des méthodes et rendait zéro ;
  en prenant les méthodes appelées depuis `initialize()`, il en trouve **huit**. Toutes légitimes,
  mais la preuve publiée était fausse - et le filtre par nom était précisément une hypothèse sur la
  façon dont le défaut se nomme, alors que la leçon du lot est qu'il ne se nomme pas.
- L'inventaire **se cite**, il ne se recopie pas ([ADR 3535](3535-un-inventaire-ne-se-duplique-pas-il-se-cite.md)) :
  il vit dans #3545, et `patterns.md` y renvoie.

## Alternatives écartées

- **Un garde mécanique sur `setVisible` / `setDisable`.** Il rendrait 138 sites dont zéro défaut : un
  rouge qui se déclenche sur l'état sain du jour s'apprend à s'ignorer, et ce dépôt en a déjà fait
  l'expérience. Le seul garde envisagé est **étroit** et porte sur la forme qui s'est répétée : le
  libellé de navigation calculé (#3702).
- **Interdire `setVisible` hors d'une liaison.** Cela condamnerait les trois familles légitimes, dont
  les points d'extension que JavaFX ré-invoque lui-même (`updateItem`, `layoutLayer`).
