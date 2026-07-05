# Sites

Les écrans **Sites** servent à gérer vos **sites de suivi** : un site correspond à un **carré
Vigie-Chiro** (six chiffres) et à ses **points d'écoute** (par exemple `A1`, `B2`). C'est le point
de départ de tout le reste : vous ne pouvez pas importer une nuit tant qu'un site n'est pas déclaré.

## Mes sites

L'écran **Mes sites** liste vos sites sous forme de cartes. Chaque carte indique le numéro de carré,
son nom, le nombre de **points d'écoute**, le nombre de **passages** enregistrés dans l'année, et un
**badge de fraîcheur** rappelant la date du dernier passage. Un bouton **Importer une nuit** est
proposé sur chaque carte, et **+ Nouveau site** en haut à droite.

![L'écran « Mes sites » : une carte par site, avec ses statistiques et un badge de fraîcheur.](../assets/captures/apercu-sites-mes-sites.png)

Le badge de fraîcheur passe du **vert** (dernier passage récent) à l'**orange** (plus ancien), au
**gris** (aucun passage). Un clic sur une carte ouvre le **détail du site**.

### Premier lancement

À la toute première ouverture, aucun site n'est déclaré : l'écran vous guide vers la création de
votre premier site, et rappelle que le carré et ses points doivent d'abord exister sur le portail
Vigie-Chiro.

![L'état initial de « Mes sites » : invitation à déclarer un premier site.](../assets/captures/apercu-sites-mes-sites-vide.png)

## Détail d'un site

Le **détail d'un site** réunit son identité (numéro de carré, département, protocole, date de
création, dernière nuit importée, nombre de passages), ses **points d'écoute** et la liste de ses
**passages**.

![Le détail d'un site : bandeau d'identité, points d'écoute et tableau des passages.](../assets/captures/apercu-sites-detail.png)

Le bouton **Modifier** (bandeau d'identité) ouvre une fenêtre pour **éditer la fiche du site** :
numéro de carré, nom, protocole et commentaire. Pratique pour corriger une saisie ou compléter le
site après coup, sans repasser par sa création.

![La fenêtre d'édition de la fiche site : numéro de carré, nom convivial, protocole et commentaire.](../assets/captures/apercu-sites-modale-edition.png)

- **Points d'écoute** : une carte par point, avec sa description, son **statut GPS** et le nombre de
  passages rattachés. Quand les coordonnées sont renseignées, le lien **« ✓ GPS — voir sur la carte »**
  ouvre la **carte multi-sites centrée sur ce point** (où le mode édition permet de corriger sa
  position). Quand elles **manquent**, le lien **« ⚠ GPS manquant — placer sur la carte »** ouvre cette
  même carte sur le carré du site, **mode édition déjà actif** : le point, affiché au centre de son
  carré, n'a plus qu'à être **glissé** à sa vraie position (puis enregistré). Le bouton **+ Ajouter un
  point** crée un nouveau point.
- **Passages enregistrés** : un tableau récapitulant, pour chaque nuit, sa date, son point, son
  numéro, son **statut** (Transformé, Vérifié, Déposé...), son **verdict** et son enregistreur.

Sur un site qui n'a pas encore de passage, le tableau est simplement vide :

![Le détail d'un site sans passage : le tableau des passages est vide.](../assets/captures/apercu-sites-detail-sans-passage.png)

## Ajouter ou modifier un point d'écoute

L'ajout ou la modification d'un point d'écoute se fait dans une **fenêtre dédiée** : code du point,
description, et coordonnées GPS (facultatives). En création, le formulaire est vierge ; en
modification, il est pré-rempli avec les valeurs existantes.

La fenêtre intègre une **carte-outil** centrée sur le carré du site, **synchronisée dans les deux
sens** avec les champs latitude / longitude (#153) : **glissez le marqueur** sur la carte pour fixer
la position (les champs se remplissent), ou **saisissez les coordonnées** (le marqueur se déplace).
Tant qu'aucun GPS n'est renseigné, le marqueur démarre **au centre du carré** en position
**approximative** (anneau pointillé) : un point de départ à caler, pas une position mesurée.

![La fenêtre de création d'un point d'écoute : formulaire vierge.](../assets/captures/apercu-sites-modale-point-creation.png)

![La même fenêtre en modification : les champs sont pré-remplis avec les valeurs du point existant.](../assets/captures/apercu-sites-modale-point.png)
