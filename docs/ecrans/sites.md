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

- **Points d'écoute** : une carte par point, avec sa description, son **statut GPS** (coordonnées
  renseignées et consultables sur OpenStreetMap, ou manquantes) et le nombre de passages rattachés.
  Le bouton **+ Ajouter un point** crée un nouveau point.
- **Passages enregistrés** : un tableau récapitulant, pour chaque nuit, sa date, son point, son
  numéro, son **statut** (Transformé, Vérifié, Déposé...), son **verdict** et son enregistreur.

Sur un site qui n'a pas encore de passage, le tableau est simplement vide :

![Le détail d'un site sans passage : le tableau des passages est vide.](../assets/captures/apercu-sites-detail-sans-passage.png)

## Ajouter ou modifier un point d'écoute

L'ajout ou la modification d'un point d'écoute se fait dans une **fenêtre dédiée** : code du point,
description, et coordonnées GPS (facultatives). En création, le formulaire est vierge ; en
modification, il est pré-rempli avec les valeurs existantes.

![La fenêtre de création d'un point d'écoute : formulaire vierge.](../assets/captures/apercu-sites-modale-point-creation.png)
