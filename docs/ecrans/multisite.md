# Multisite

La **Vue multi-sites** rassemble **tous vos passages**, tous sites confondus. Elle combine une
**carte** (à gauche) et un **tableau** (à droite) : la carte situe vos sites et points dans l'espace,
le tableau les liste pour les trier, filtrer et exporter. C'est l'écran adapté quand on suit plusieurs
sites et qu'on veut une vision d'ensemble.

## La carte et le tableau

![La vue multi-sites : carte des sites/points à gauche, tableau des passages à droite.](../assets/captures/apercu-multisite.png)

À **gauche**, la **carte** affiche chaque **carré** (emprise ≈ 2 km du site) et ses **points
d'écoute** sous forme de marqueurs **colorés selon le statut** du dernier passage (gris = importé,
indigo = transformé / vérifié, cyan = prêt à déposer, vert = déposé). Chaque marqueur porte son
libellé (`carré / point`) ; les points sans coordonnées GPS ne sont pas placés. Le **remplissage
de chaque carré** reflète sa **densité de passages** : plus un carré est fréquenté, plus son indigo
est foncé (échelle relative au carré le plus actif). Une **légende** superposée en bas à gauche
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
