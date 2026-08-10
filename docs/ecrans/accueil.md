# Accueil

L'**écran d'accueil** est le point d'entrée de l'application. Les activités y sont regroupées en
**deux prismes**, deux portes d'entrée complémentaires : **produire** la donnée puis l'**exploiter**.

![L'écran d'accueil et ses deux prismes : « Collecte & passages » et « Espèces & biodiversité ».](../assets/captures/apercu-accueil.png)

## Le bandeau de compteurs

Au-dessus des cartes, quatre compteurs disent ce que contient votre base : sites, points d'écoute,
passages, observations. Un compteur **à zéro** reste affiché, en gris atténué : il vous dit ce qui
n'a pas encore commencé, sans attirer l'œil.

![Le bandeau de compteurs : deux sites et trois points d'écoute renseignés, passages et observations
encore à zéro.](../assets/captures/apercu-accueil-compteurs.png)

Les compteurs **suivent la base**, pas vos déplacements dans l'application : si une synchronisation
ou un import ajoute des données pendant que vous êtes sur l'accueil, ils changent sous vos yeux, sans
que vous ayez à quitter l'écran et à y revenir.

Le bandeau reste **masqué** tant que la base est vide, au premier lancement : l'accueil reste épuré
plutôt que d'afficher une rangée de zéros.

## Collecte & passages

Le workflow de **production** de la donnée : vos carrés, vos points d'écoute et vos nuits de capture.

| Activité | Ouvre | Pour |
|---|---|---|
| **Mes sites** | [Sites](sites.md) | vos carrés et points d'écoute |
| **Carte & passages** | [Carte & passages](multisite.md) | la carte de vos sites et le tableau de tous les passages (filtres, tri, export) |
| **Audit de cohérence** | [Audit](audit.md) | vérifier base et fichiers, réparer, réinitialiser |

## Espèces & biodiversité

L'**exploitation** de la donnée : l'inventaire des espèces détectées et vos sons de référence.

| Activité | Ouvre | Pour |
|---|---|---|
| **Espèces & observations** | [Analyse](analyse.md) | l'inventaire de vos espèces détectées (où, quand, combien : par espèce ou par carré) |
| **Activité de la nuit** | [Activité](activite.md) | la courbe des contacts heure par heure, pour lire la forme de la nuit |
| **Sons & validation** | [Sons & validation](validation.md) | écouter, valider et exporter vos sons de référence |

## Atteindre les autres écrans

Depuis ces points d'entrée, vous atteignez ensuite les autres écrans : un **site** donne accès à
ses **passages** (les nuits), et un [Passage](passage.md) ouvre les écrans de qualification, de
dépôt, de validation et de diagnostic. L'**import** d'une nuit se lance d'ailleurs **depuis une carte
site ou un passage**, et non plus depuis l'accueil. La barre du haut affiche un fil d'Ariane qui
rappelle où vous vous trouvez.

!!! tip "Premiers pas"
    Si vous découvrez l'application, commencez par la [Prise en main](../prise-en-main.md) :
    installation, lancement et tour d'horizon.
