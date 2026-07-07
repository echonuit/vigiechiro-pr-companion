# Carte & passages

La vue **Carte & passages** rassemble **tous vos passages**, tous sites confondus. Elle combine une
**carte** (à gauche) et un **tableau** (à droite) : la carte situe vos sites et points dans l'espace,
le tableau les liste pour les trier, filtrer et exporter. C'est l'écran adapté quand on suit plusieurs
sites et qu'on veut une vision d'ensemble.

## La carte et le tableau

![La vue Carte & passages : carte des sites/points à gauche, tableau des passages à droite.](../assets/captures/apercu-multisite.png)

À **gauche**, la **carte** affiche chaque **carré** (maille 2 km du carroyage national Vigie-Chiro) et
ses **points d'écoute** sous forme de marqueurs **colorés selon le statut** du dernier passage (gris = importé,
indigo = transformé / vérifié, cyan = prêt à déposer, vert = déposé). Chaque **carré** affiche son
**numéro** dans son coin, en **petit repère discret** (texte sombre à fin liseré clair) qui reste
lisible sans s'imposer ; chaque marqueur porte, lui, son **nom de point** abrégé (p. ex. `A1`) en
**clair**, finement contouré pour bien se détacher du fond de carte. Un point **sans coordonnées GPS**
est tout de même affiché, **au centre de
son carré**, sous forme de marqueur **approximatif** : un disque blanc cerné d'un **anneau pointillé**
(au lieu d'une pastille pleine), pour qu'on le repère sans le confondre avec une position mesurée ; son
info-bulle le signale (« position approximative, centre du carré »). Si plusieurs points d'un même carré
sont sans GPS, ils sont **répartis en éventail** autour du centre pour ne pas se superposer. Seul reste
**non plaçable** un point dont le carré est **hors carroyage officiel et sans aucun point géolocalisé**
(centre inconnu) : il n'apparaît pas sur la carte. Le **remplissage
de chaque carré** reflète sa **densité de passages** : plus un carré est fréquenté, plus son indigo
est foncé (échelle relative au carré le plus actif). **Au survol** d'un carré ou d'un point, une
**info-bulle** récapitule ses mini-stats (nombre de passages, points **avec GPS** et points **à
localiser**, répartition des statuts ; statut dominant pour un point) ; ces stats sont aussi lues par
les lecteurs d'écran. Une **légende**
superposée en bas à gauche
rappelle le code couleur des statuts et l'échelle de densité ; elle s'ouvre **repliée par défaut**
(réduite à son seul titre, pour ne pas masquer les points) et un **chevron** la déplie au besoin. Un
bouton **⤢** en haut à droite **recadre** la carte sur l'ensemble des carrés et points affichés
(pratique après un zoom ou un déplacement manuel). Le fond de carte OpenStreetMap apparaît quand une
connexion est disponible. La carte montre
**tous** les sites (vue d'ensemble) : elle n'est pas restreinte par les filtres du tableau.

Deux boutons placés **en bas**, chacun à l'extrémité **sous le panneau qu'il masque** (**◀ Carte** à
gauche, **Tableau ▶** à droite), **replient entièrement** un panneau pour donner toute la largeur à
l'autre (et le rouvrent) ; on ne peut pas masquer les deux. Replier la carte est aussi la
**dégradation élégante hors connexion** : quand le fond OpenStreetMap n'est pas joignable, le tableau
seul reste pleinement exploitable.

![Tableau replié, carte en plein écran : l'état où arrive « Voir sur la carte ». La poignée « Tableau ◀ » rouvre le tableau.](../assets/captures/apercu-multisite-carte-pleine.png)

C'est aussi l'état où l'on arrive en cliquant **« Voir sur la carte »** depuis un site, un point ou un
passage : le tableau se replie automatiquement pour centrer l'attention sur la carte.

À **droite**, le **tableau** liste chaque passage (carré, point, année, numéro, date, **statut**,
**verdict**). La barre du haut permet de **filtrer** (par carré, statut, verdict, année) et de
**réinitialiser** les filtres ; un menu **☰** à droite de la barre regroupe les actions secondaires
(**Vues** enregistrées et **export** de la sélection). On **trie** en cliquant l'en-tête d'une
colonne (Année et N° de passage se trient numériquement), en plus du sélecteur d'ordres. Un
**double-clic** sur une ligne ouvre l'écran du passage correspondant.

Quand un filtre est actif, le tableau et le résumé se recalculent en conséquence :

![La vue Carte & passages filtrée (ici par verdict) : le résumé est recalculé.](../assets/captures/apercu-multisite-filtre.png)

## Éditer les positions des points

Le bouton **« ✎ »** superposé **en haut à gauche de la carte** fait passer celle-ci en **mode édition**
(la pince devient **ambrée** quand le mode est actif) : on peut alors **glisser un marqueur** pour
corriger le GPS d'un point. Le marqueur **reste dans son carré** (il s'arrête au bord de la maille
2 km) ; un point **sans GPS**, affiché au centre de son carré, se **place** en le faisant glisser à
l'endroit voulu. Déplacer un point ne touche **que** ses coordonnées : son code, son descriptif et ses
passages sont conservés.

![Le mode édition des positions : la pince « ✎ » est active (ambré) et un bouton « 💾 » d'enregistrement apparaît sous elle, sur la carte.](../assets/captures/apercu-multisite-edition.png)

Les déplacements ne sont **pas enregistrés au fil de l'eau** : ils s'accumulent jusqu'au clic sur le
bouton **« 💾 »** qui apparaît alors **sur la carte, sous la pince** (inactif tant qu'aucun point n'a
bougé). Si vous **quittez le mode édition** alors que des déplacements ne sont pas enregistrés, une
fenêtre vous propose de les **Enregistrer**, de les **Abandonner**, ou d'**Annuler** (pour rester en
édition).

## Vues sauvegardées

Une combinaison de filtres utile peut être **enregistrée sous un nom** pour être rejouée d'un clic.
Les vues enregistrées s'affichent comme des **onglets** au-dessus du tableau : cliquer sur le nom d'un
onglet **rejoue** sa combinaison de filtres. Le bouton **« + Vue »**, au bout de la barre d'onglets,
enregistre les filtres **courants** sous un nouveau nom. Sur chaque onglet, **« ✎ »** le renomme et
**« ✕ »** le supprime.
