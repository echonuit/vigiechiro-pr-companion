# Multisite

La **Vue multi-sites** rassemble **tous vos passages**, tous sites confondus. Elle combine une
**carte** (à gauche) et un **tableau** (à droite) : la carte situe vos sites et points dans l'espace,
le tableau les liste pour les trier, filtrer et exporter. C'est l'écran adapté quand on suit plusieurs
sites et qu'on veut une vision d'ensemble.

## La carte et le tableau

![La vue multi-sites : carte des sites/points à gauche, tableau des passages à droite.](../assets/captures/apercu-multisite.png)

À **gauche**, la **carte** affiche chaque **carré** (maille 2 km du carroyage national Vigie-Chiro) et
ses **points d'écoute** sous forme de marqueurs **colorés selon le statut** du dernier passage (gris = importé,
indigo = transformé / vérifié, cyan = prêt à déposer, vert = déposé). Chaque marqueur porte son
libellé (`carré / point`). Un point **sans coordonnées GPS** est tout de même affiché, **au centre de
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
rappelle le code couleur des statuts et l'échelle de densité ; un chevron la **replie** pour dégager
la carte. Le fond de carte OpenStreetMap apparaît quand une connexion est disponible. La carte montre
**tous** les sites (vue d'ensemble) : elle n'est pas restreinte par les filtres du tableau.

Deux poignées **◀ / ▶** posées au sommet du séparateur **replient entièrement** un panneau pour donner
toute la largeur à l'autre (et le rouvrent) : **◀** masque la carte, **▶** masque le tableau ; on ne
peut pas masquer les deux. Replier la carte est aussi la **dégradation élégante hors connexion** :
quand le fond OpenStreetMap n'est pas joignable, le tableau seul reste pleinement exploitable.

À **droite**, le **tableau** liste chaque passage (carré, point, année, numéro, date, **statut**,
**verdict**). La barre du haut permet de **filtrer** (par carré, statut, verdict, année), de
**réinitialiser** les filtres, et d'**exporter** la sélection. On **trie** en cliquant l'en-tête d'une
colonne (Année et N° de passage se trient numériquement), en plus du sélecteur d'ordres. Un
**double-clic** sur une ligne ouvre l'écran du passage correspondant.

Quand un filtre est actif, le tableau et le résumé se recalculent en conséquence :

![La vue multi-sites filtrée (ici par verdict) : le résumé est recalculé.](../assets/captures/apercu-multisite-filtre.png)

## Vues sauvegardées

Une combinaison de filtres utile peut être **enregistrée sous un nom** pour être rejouée d'un clic.
La fenêtre des vues permet d'**appliquer**, de **mettre à jour** ou de **supprimer** une vue.

![La fenêtre des vues sauvegardées : enregistrer et rejouer une combinaison de filtres.](../assets/captures/apercu-multisite-vues.png)
