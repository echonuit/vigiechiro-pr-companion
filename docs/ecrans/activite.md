# Activité de la nuit

L'écran **Activité de la nuit** trace le nombre de contacts par tranche horaire et par espèce. Là où
[Espèces & observations](analyse.md) répond « combien, et de quoi ? », celui-ci répond « **à quelle
heure ?** ». Il s'ouvre depuis l'écran [Passage](passage.md), pour une nuit, ou depuis l'accueil, sur
l'ensemble des nuits.

![La courbe d'activité d'une nuit : une couleur par espèce, l'aplat de la fenêtre nocturne, la légende sous le graphe.](../assets/captures/apercu-activite.png)

Une nuit a une forme : montée après le coucher du soleil, pic, décroissance jusqu'à l'aube. Cette
forme porte une information que le total efface. Deux nuits à 300 contacts n'ont rien à voir selon
que l'activité s'étale sur huit heures ou qu'elle tient en quarante minutes, et l'écart oriente une
interprétation autant qu'il signale un problème de capteur.

L'écran réunit :

- une **courbe par espèce**, sur un axe horaire qui court de **18 h à 8 h** — et non de minuit à
  minuit, qui couperait la nuit en deux ;
- l'**aplat de la fenêtre nocturne** (du coucher au lever du soleil au point d'écoute) : ce qui en
  déborde est de l'activité crépusculaire ou diurne ;
- une **légende** sous le graphe, qui nomme chaque courbe affichée ;
- la **largeur de tranche** au choix (15, 30 ou 60 minutes) : un pas fin dessine le détail d'un pic,
  un pas large lisse la forme générale ;
- une **case par espèce** détectée, avec son total ; les **cinq plus contactées** sont cochées par
  défaut, au-delà le graphe devient illisible ;
- une **barre de filtres** (carré, point, nuit, taxon parent, recherche libre) : filtrer re-trace ;
- des **onglets** qui séparent les catégories du référentiel — chiroptères, orthoptères et cigales,
  autres mammifères —, parce que le détecteur ne repère pas que des chauves-souris. Vos propres vues
  s'enregistrent à côté, avec « + Vue ».

## La nuit biologique

Une nuit à cheval sur deux dates reste **une seule nuit**. Le rattachement se fait par bascule à
midi : un contact enregistré à 2 h du matin le 22 juin appartient à la nuit du 21. C'est vrai à
l'écran comme dans les exports.

## Survol d'un point

Le survol d'un point donne la valeur exacte que l'axe n'indique qu'approximativement : l'espèce,
l'heure de la tranche et le nombre de contacts.

## Quand rien ne s'affiche

Le message d'absence **nomme sa cause**, parce qu'un « aucune donnée » ne dit pas quoi faire :

- aucune espèce détectée sur les nuits chargées ;
- aucune espèce ne correspond aux filtres — il faut alors en élargir ou en retirer un ;
- aucune espèce cochée — il suffit d'en cocher une.

![L'écran Activité sans espèce détectée : le message nomme la cause de l'absence.](../assets/captures/apercu-activite-vide.png)

## Toutes les nuits à la fois

Ouvert depuis l'accueil, l'écran couvre **tous les passages** : la barre de filtres sert alors à
restreindre progressivement (un carré, puis un point, puis une nuit). L'aplat nocturne disparaît dans
cette vue : plusieurs nuits n'ont pas de fenêtre commune, et en afficher une serait trompeur.

![L'écran ouvert sur toutes les nuits : la courbe cumule les passages et l'aplat nocturne a disparu.](../assets/captures/apercu-activite-transverse.png)

## Exporter l'image

Le bouton **Exporter l'image…** enregistre la courbe telle qu'elle est affichée, au format PNG.
L'image est **redessinée** pour l'occasion, et non photographiée : elle est donc fidèle même si la
fenêtre est réduite ou l'écran masqué.

Elle **porte son contexte**, inscrit sous le graphe : le carré, le point et le passage (ou « tous les
passages »), la largeur de tranche et les filtres actifs, la version de l'application et la date
d'export. Sans ces mentions, une courbe collée dans un compte rendu ne dit plus de quelle nuit elle
parle.

![L'image exportée : la courbe redessinée, avec sous elle son identité, ses réglages et sa provenance.](../assets/captures/apercu-activite-export.png)

L'écran **dit le résultat** de l'export, réussite comme échec : un fichier écrit sans que rien ne
l'annonce serait indiscernable d'un clic sans effet.

![Après l'export : un bandeau vert nomme le fichier écrit.](../assets/captures/apercu-activite-retour.png)

Pour produire le même contenu **en tableau** plutôt qu'en image, la ligne de commande expose
`exporter-activite` (une ligne par espèce et par tranche, ouvrable dans un tableur).
