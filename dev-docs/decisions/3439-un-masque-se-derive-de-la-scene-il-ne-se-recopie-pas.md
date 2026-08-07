# ADR 3439 - Un masque se dérive de la scène, il ne se recopie pas

- **Statut** : Accepté - 2026-08-07
- **Chantier** : #3439, suite de l'[ADR 3068](3068-le-determinisme-porte-sur-ce-que-le-produit-rend.md)
- **Vérification** : certaine - `ZoneCarteApercuTest#le_rectangle_epouse_la_carte`

## Contexte

`filtrer-bruit-cartes.sh` compare chaque aperçu à sa version committée **hors** du rectangle de sa
carte, à tolérance zéro. Le masque est nécessaire : le bruit de tuiles vaut jusqu'à 23,8 % de l'image,
et aucun seuil global ne le sépare du signal.

Ces rectangles étaient **recopiés à la main** dans le script, avec l'avertissement qu'« une capture
dont la mise en page bouge doit voir son rectangle remesuré ». Personne ne le remesurait.

## Ce que la liste disait de faux

| Aperçu | Déclaré | Réel |
| --- | --- | --- |
| `sites-modale-point` | `18,331,464,457` | `25,363,535,601` |
| `multisite-edition` | `12,90,865,571` | `19,144,462,564` |
| `import-decompression-volume` | `524,825,547,849` | `42,441,1058,629` |

Trois défauts distincts, et le plus grave n'est pas celui qui se voyait.

**Sous-couverture.** Sur `sites-modale-point`, 144 lignes de carte restaient dehors : le bruit y
repassait, et ces deux fichiers ont changé dans **8 des 20** commits d'aperçus depuis l'arrivée du
filtre, contre **1 sur 20** pour une carte correctement masquée. Facteur huit.

**Sur-couverture, et c'est la face grave.** Sur `multisite-edition`, le rectangle couvrait le
**tableau de données entier** - Carré, Point, Commune, Année, N° passage, Date - plus la barre de
recherche et les filtres. **55 % de la surface masquée n'était pas de la carte** : 224 233 pixels de
produit, comparés à rien. Une colonne fausse, un libellé tronqué, une date mal formatée n'auraient fait
rougir personne.

**Incomplétude.** Le rendu dépose **19** zones ; la liste en déclarait **16**. Trois écrans d'import
portaient une carte que personne n'avait recensée.

**Détournement.** L'entrée `import-decompression-volume` faisait 23 × 24 pixels dans une liste dont
toutes les autres font des centaines de pixels de côté. Elle ne désignait aucune carte : elle cachait
les deux chiffres d'une **estimation de temps restant**, qui dépend de la vitesse de la machine. Un
correctif de non-déterminisme rangé parmi les masques de carte, donc invisible (#3483).

## Décision

Les rectangles sont **dérivés de la scène** au moment du rendu, par `ZoneCarteApercu`, et déposés dans
un `apercu-<nom>.png.carte` que le filtre lit aussitôt. Ces fichiers ne sont **jamais committés** :
produits et consommés dans la même exécution, les committer les ferait vieillir - le défaut même qu'ils
corrigent.

Une carte se reconnaît à la classe de style `carte-sites`, que `CarteSites` se pose à elle-même. Les
quatre surfaces cartographiques du produit passent par ce composant : aucun marqueur n'a été ajouté, et
l'outillage ne dépend pas de Gluon Maps.

Un rectangle dérivé ne peut pas se démoder. La question « les autres sont-ils justes ? » cesse de se
poser, puisque plus personne ne les écrit.

## ⚠️ Ce que la mesure a coûté d'apprendre : où déposer le fichier

Une première version appelait `ZoneCarteApercu` avec la scène, **juste après le `snapshot`**. Le
raisonnement paraissait solide : l'image est déjà prise, rien de ce qui suit ne peut la changer.

Il était faux. `apercu-passage-rattachement.png` est ressorti **différent de la version d'intégration
continue**, sur 40 543 pixels - un champ marqué invalide, un autre porteur du focus. Vérifié en
retirant le changement et en régénérant : sans lui, la capture retombe **au bit près** sur celle de la
CI.

L'image en cours n'était pas touchée ; **les captures suivantes du même outil** l'étaient. Écrire sur
disque entre le `snapshot` et la fermeture du stage suffit à laisser passer une validation de
formulaire avant la capture d'après.

La mesure se fait donc **pendant** que la scène est montée, et l'écriture **après** `RenduPng.ecrire`,
hors du cycle de vie du stage.

## Conséquences

- **18 divergences CI/poste avant, 3 après** : la version du JDK, le nom de l'utilisateur, et
  l'estimation de temps restant que ce chantier a mise au jour (#3483) ;
- le refus explicite de « zéro rectangle trouvé » : cela ne veut pas dire « aucune carte », le produit
  en porte quatre, mais que le rendu n'a pas déposé ses zones. Sans ce refus, le filtre passerait en
  silence et le bruit repartirait en commit ;
- `deposer(null, png)` **efface** un fichier devenu obsolète : sans cela, un écran dont on retire la
  carte garderait son masque, et cette zone cesserait d'être comparée.

## Ce que cette ADR apprend au-delà de son cas

Un masque est un **renoncement à vérifier**. Tant qu'il est écrit à la main, personne ne sait plus ce à
quoi il fait renoncer : ici, un tableau de données entier, et une estimation qui n'avait rien d'une
carte. Dériver le masque de ce qu'il prétend couvrir le rend à la fois juste et **lisible** - le
fichier déposé dit, à chaque rendu, exactement ce qu'on cesse de comparer.
