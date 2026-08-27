## Context

Voir `proposal.md`, section Why, pour la motivation. Ce qui suit n'est que l'état du code qui contraint
l'approche.

Trois pièces existent déjà et ne sont pas à écrire.

| Pièce | Ce qu'elle fait | Où |
|---|---|---|
| `ClientVigieChiro#carreStoc` | Position vers numéro de carré, par `GET /grille_stoc/cercle` | `commun/api/` |
| `ReponsesVigieChiro#numeroCarreStoc` | Lit le **premier** élément de la réponse | `commun/api/` |
| `ControleCarreStoc` | Confronte le carré d'un point au carré déclaré, en aval | `sites/model/` |

Deux traits de l'existant pèsent sur la conception.

Le rayon est **figé** dans `RAYON_CARRE_STOC_METRES`, à 10 000 m. Sa javadoc dit pourquoi : les carrés
STOC font 2 km de côté, un point tombe donc à moins de 1,5 km du centre du sien, et le rayon large
sert à « répondre quand même (le carré voisin) plutôt que rien ».

`numeroCarreStoc` ne lit **que** le champ `numero`, jamais `centre`, et sa javadoc dit pourquoi : la
plateforme mélange les conventions de coordonnées, les localités d'un site étant stockées en
`[lat, lon]` à rebours du GeoJSON. Ne pas lire `centre` évite de trancher ce débat pour lire un
numéro. Cette abstention est un acquis à ne pas dépenser.

Le contrat de l'endpoint est relevé dans `dev-docs/api-vigiechiro.md` : trois paramètres obligatoires
`lng`, `lat`, `r`, une requête `$near` MongoDB, donc des résultats **triés par distance croissante**,
plafonnés à 80.

## Goals / Non-Goals

**Goals**

- Serrer le rayon **sans** lire `centre` ni calculer une distance côté client.
- Séparer la lecture d'un format de texte de l'appel réseau, pour que le premier se teste seul.
- Suivre les patrons déjà posés sur la même modale plutôt que d'en inventer : geste optionnel par
  `OptionalBinder`, fermeture sans jeton, réouverture à l'arrivée du jeton.

**Non-Goals**

- Toucher `ControleCarreStoc`, son rayon ou son verdict. Il garde son rôle en aval.
- Rendre la modale capable d'afficher une carte, ou de résoudre un lien.
- Rattraper une position fausse. L'application dit quel carré elle a trouvé ; elle ne juge pas si
  l'observateur a collé le bon endroit.

## Decisions

### D1. Le rayon se serre côté serveur, en paramètre d'appel

`carreStoc` prend un rayon en paramètre. L'appelant existant, `ControleCarreStoc`, passe les 10 000 m
qu'il utilise déjà ; la proposition passe 1 500 m.

**Écarté : filtrer côté client.** Appeler au rayon large, lire `centre` dans la réponse, calculer la
distance et rejeter au-delà du seuil. Cela oblige à lire `centre`, donc à trancher la convention de
coordonnées que `numeroCarreStoc` s'abstient de trancher depuis #733. Le serveur sait déjà répondre
« rien » : lui poser la bonne question coûte un paramètre, la trancher soi-même coûte une décision de
plus et un risque d'inversion silencieuse.

**Écarté : un second point d'entrée dédié.** Une méthode `carreStocServre()` à côté de l'autre. Deux
méthodes qui ne diffèrent que par une constante finissent par diverger ailleurs.

### D2. 1 500 m, et l'arithmétique qui le fonde

Le côté d'un carré STOC vaut 2 km, propriété relevée dans la javadoc de `carreStoc`. La demi-diagonale
d'un carré de 2 km vaut donc `1000 x racine(2)`, soit environ 1 414 m : c'est la distance maximale
entre un point du carré et son centre, atteinte aux quatre coins. 1 500 m couvre cette distance avec
une marge de 86 m.

**Écarté : 1 414 m exact.** Sans marge, une différence entre la distance géodésique du serveur et
l'arithmétique plane ci-dessus ferait perdre les points de coin.

**Écarté : garder 10 000 m.** Le motif est dans `proposal.md`. Une proposition se valide sans se
relire, un contrôle se lit à côté d'une vérité déjà tapée.

**Mesuré le 2026-08-27**, sur la grille réelle autour du carré `040110`, ce qui change deux choses
que ce paragraphe affirmait sans preuve.

| Position | Ce que la grille rend |
|---|---|
| Coin commun à quatre carrés | 4 mailles, toutes à **1 412 m** |
| Le même coin, à `r = 1400` | **0 maille** |
| Milieu d'un côté commun | 2 mailles, à **997,7 m chacune**, ex aequo au dixième de mètre |

L'arithmétique prédisait 1 414 m au coin ; la grille en rend 1 412. Et le rejet du « 1 414 m exact »
n'était pas une précaution de principe : à 1 400 m un point de coin ne propose **rien**. La marge de
88 m est ce qui le sauve.

Le cas limite est plus large que « le coin exact ». Au milieu d'un côté, deux centres sont à distance
**strictement égale**, et un décalage de 5 m suffit à faire basculer le premier élément. Sur une bande
de quelques mètres le long de chaque bord, le « premier » est donc un tirage. D11 en tire les
conséquences.

### D3. On garde « le premier élément »

`numeroCarreStoc` ne change pas. Le tri par distance croissante du `$near` fait du premier élément le
carré de la position, pour tout point intérieur. Serrer le rayon ne change pas cette lecture, il change
seulement ce que le serveur consent à rendre.

**L'abstention devient un choix documenté, et non une ignorance.** La javadoc de `numeroCarreStoc`
disait ne pas lire `centre` pour éviter de trancher la convention de coordonnées de la plateforme. La
réponse brute du 2026-08-26 la tranche gratuitement : `grille_stoc` rend
`"centre": {"coordinates": [6.293767361, 44.44544392], "type": "Point"}` pour une interrogation à
`lat=44.4467, lng=6.2981`, donc l'ordre **`[lon, lat]`**, celui de GeoJSON. L'autre lecture placerait
ce centre à 5 626 km. Les localités d'un site, elles, restent en `[lat, lon]` : la plateforme mélange
bien les deux conventions, et on sait désormais laquelle est où.

On continue de ne pas lire `centre`, parce que le numéro suffit. La différence est qu'on ne le fait
plus faute de savoir.

### D4. L'analyseur de position est une classe pure

Lire un texte collé et en tirer une paire de nombres ne demande aucun réseau. Cette lecture vit dans
une classe sans dépendance, séparée du service qui appelle le portail.

Le dépôt appelle PIT sur les classes pures livrées, survivants lus un par un. Un analyseur de format
est exactement le genre de code où une mutation survit sans qu'un test ne bronche.

### D5. L'ordre est latitude puis longitude, et ne se devine pas

**Écarté : deviner l'ordre par la plage des valeurs.** Une latitude tient dans plus ou moins 90, une
longitude dans plus ou moins 180 : l'heuristique ne tranche que lorsqu'un des deux nombres dépasse 90.
En France métropolitaine la latitude vaut environ 41 à 51 et la longitude environ -5 à 10 : les deux
tiennent dans plus ou moins 90, et l'heuristique ne tranche jamais là où on en aurait besoin.

**Écarté : deviner par l'emprise de la France.** Cela marcherait en métropole et réordonnerait en
silence une position réellement erronée. Un carré plausible et faux est le défaut que tout ce
changement cherche à éviter.

L'ordre retenu est celui que produisent le « Copier les coordonnées » de Google Maps et le clic droit
d'OpenStreetMap, donc celui que l'observateur colle sans y penser.

### D6. Une URL de carte est refusée, pas analysée

**Écarté : analyser les URL de carte.** Les liens longs portent bien la position (`@lat,lon,zoom` chez
Google, `#map=zoom/lat/lon` ou `mlat=&mlon=` chez OpenStreetMap). Les liens courts `maps.app.goo.gl`
n'en portent aucune tant qu'ils ne sont pas résolus, et les résoudre veut dire appeler Google, donc
ajouter une dépendance externe et une destination de plus pour la position de l'observateur.

Accepter les liens longs et refuser les courts donnerait une fonctionnalité qui s'explique mal : deux
liens copiés depuis la même carte, l'un marche et l'autre non. Refuser les deux avec un motif qui dit
quoi coller à la place se comprend d'un coup. Ce choix se rouvre si le besoin se mesure.

### D7. Le geste est fermé sans jeton, patron de #4210

`ModaleSiteController` ferme déjà « Vérifier sur Vigie-Chiro » tant qu'aucun jeton n'est disponible,
et le commentaire du code en donne le motif : sans cela, on tapait, on cliquait, on payait un
aller-retour, et l'encart répondait « impossible » alors que l'application le savait avant le clic.
#4205 fait rouvrir le geste dès qu'un jeton arrive, sans rouvrir la fenêtre.

Le geste de position suit le même patron. En inventer un autre sur la même modale ferait deux
comportements pour une même cause.

### D8. Le service est optionnel, patron de `ControleCarreStocModule`

Le service qui enchaîne analyse puis appel se lie par `OptionalBinder`, et le contrôleur n'offre pas
le geste quand il est absent. C'est le montage déjà tenu par `ControleCarreStocModule`,
`PublicationPointModule` et `RechercheCarreExistantModule` : même raison d'être, même clé qualifiée.

### D10. Le rembourrage du zéro se fait à l'entrée, une seule fois

Mesuré le 2026-08-26 : la grille rend « 40110 » là où le catalogue des sites déclare
« Vigiechiro - Point Fixe-040110 », et `GET /sites?q=40110` ne trouve rien. R1 est juste, c'est la
grille qui ampute le zéro de gauche des départements 01 à 09.

Le rembourrage se fait dans `numeroCarreStoc`, à l'endroit unique où ce numéro entre dans
l'application.

**Écarté : rembourrer au point de comparaison**, dans `ControleCarreStoc`. Cela réparerait le
symptôme observé et laisserait le défaut intact pour le lecteur suivant. Il y en a un, et c'est
précisément la proposition que ce changement ajoute.

**Écarté : rembourrer dans le viewmodel.** Le format d'un numéro de carré est une règle du domaine,
R1, et non une affaire d'affichage. Un modèle qui rend un numéro invalide et compte sur la vue pour
le rattraper déplace la règle hors de sa place.

Ce rembourrage répare aussi un défaut de production : `ControleCarreStoc` comparait « 40110 » à
« 040110 » par `equals`, donc rendait `Diverge` à tort dans neuf départements. Sa garde et la cécité
de la sonde qui l'a laissé passer vivent en #4592, hors de ce changement.

### D11. Plusieurs carrés candidats se nomment, aucun ne se choisit

Quand les deux premières mailles sont à des distances proches, l'écran les **nomme toutes les deux** et
ne pré-remplit rien. L'observateur tranche.

**Ce n'est pas un défaut de calcul à corriger.** Pour un point exactement sur une frontière, « le carré
qui le contient » n'existe pas sans une convention, du type intervalle semi-ouvert où le carré de
gauche possède son bord. La plateforme ne stocke que des **centres** : sa représentation ne peut pas
porter cette convention, et aucune question ne la lui fera rendre. C'est un fait du domaine, que le
produit doit dire plutôt que masquer.

Le seuil se dérive de la géométrie. Pour un point à `x` mètres d'un bord, l'écart entre les deux
distances vaut environ `2x`. Un seuil d'écart de **50 m** désigne donc les points situés à moins de
25 m du bord, soit l'ordre de grandeur de ce qu'on vise en cliquant sur une carte.

**Écarté : choisir le plus proche et se taire.** C'est le comportement actuel. Il propose un numéro
sur deux au hasard le long des bords, et un numéro faux et plausible est le défaut que tout ce
changement cherche à éviter.

**Écarté : ne rien proposer près d'un bord.** Deux candidats valent mieux que rien : ils épargnent le
détour par le portail, qui est la corvée que ce chantier supprime.

**Écarté : demander la convention à la plateforme.** Elle ne l'a pas. Le carroyage y est stocké en
points.

### D9. Ce que cette note ne fait pas

Le « pourquoi » durable de D1 et D2 - le rayon d'une proposition n'est pas celui d'un contrôle - est
une décision d'architecture. Elle appelle une ADR dans `dev-docs/decisions/`, numérotée par l'issue du
chantier, à la passe de clôture qui lui revient. Cette note la prépare, elle ne la remplace pas.

## Risks / Trade-offs

**La géométrie stockée de la grille était une hypothèse. Elle est mesurée, et elle tient.** D2
supposait que le serveur stocke un **point** par carré, son centre, et que `r` est donc une distance
au centre. Des polygones auraient fait mesurer `$near` jusqu'au bord le plus proche, et le serrage
n'aurait pas eu lieu. → Mesuré le 2026-08-26 par la sonde `grille_stoc_mesure_une_distance_au_centre`
(#4574), contre le serveur réel, autour du point `Z1` du carré `130711` :

| Rayon demandé | Mailles rendues | Prédit pour une maille de 2 km en points |
|---|---|---|
| 10 000 m | 78 | 78,5 |
| 5 000 m | 21 | 19,6 |
| 2 000 m | 3 | 3,1 |
| 1 500 m | **1** | - |
| 500 m | 1 | - |
| 100 m | **0** | - |
| 1 m | **0** | - |

Le palier à 1 m rend **zéro** : la grille stocke des centres, pas des polygones, sinon un point situé
dans son carré serait à distance nulle et sortirait à tout rayon. Les comptes aux grands rayons
confirment la maille de 2 km par un second chemin : `pi x r carré / 2000 carré`, soit 78,5 prédites
contre 78 mesurées à 10 km. D2 tient, et 1 500 m se fige.

**La mesure a porté sur un point favorable.** `Z1` est entre 100 m et 500 m de son centre, donc près
du milieu de son carré. Elle ne dit rien du cas de coin, où un point est à 1 414 m de son centre et
d'autant de trois voisins. Le cas de coin reste tel que D2 le décrit : assumé, et sans effet sur une
position relevée à la main.

**Une position collée n'est pas la position du micro.** L'observateur peut coller sa maison plutôt que
son point d'écoute. Un carré fait 2 km de côté, donc un écart de quelques centaines de mètres est sans
effet ; un écart de plusieurs kilomètres donne un carré voisin, plausible et faux. → L'écran nomme le
carré trouvé, et « Vérifier sur Vigie-Chiro » reste le geste suivant. L'application ne peut pas
distinguer les deux cas, et ne prétend pas le faire.

**Le remplacement d'une saisie est une hypothèse posée sans être demandée.** L'exigence « la déduction
remplace une saisie en le disant » tranche un cas que personne n'a arbitré : que faire quand le champ
porte déjà six chiffres. → Consigné ici comme tel. C'est le seul endroit de ce changement où une
décision de comportement a été prise par défaut, et elle se rouvre au premier avis contraire.

**1 500 m fige une propriété de la grille dans une constante.** Si le carroyage changeait de maille, la
constante mentirait sans bruit. → La constante porte son arithmétique dans sa javadoc, comme
`RAYON_CARRE_STOC_METRES` porte la sienne : un lecteur qui doute peut refaire le calcul.

**Un analyseur de format attire les mutations survivantes.** Bornes, séparateurs, signes, ordre. → PIT
sur la classe pure, survivants lus un par un, et le test qui manque s'écrit avant d'être cru.

## Migration Plan

Aucune migration de données : rien n'est persisté par ce changement. Le champ de position vit le temps
de la modale.

Repli : le service se lie par `OptionalBinder` (D8). Retirer la liaison du module retire le geste de
l'écran, sans toucher au reste de la modale ni à `ControleCarreStoc`.

## Open Questions

**Faut-il rouvrir les URL de carte plus tard ?** D6 les refuse toutes pour ne pas expliquer une
distinction incompréhensible. Si les observateurs collent surtout des liens longs, la décision se
rouvre sans changer une seule exigence : c'est un format de plus dans l'analyseur. La question demande
une mesure d'usage, pas un arbitrage.
