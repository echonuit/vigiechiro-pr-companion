# sites/declaration-d-un-point Specification

## Purpose
Situer un point d'écoute, lui donner son code et le créer, que ce soit depuis la modale de point ou en
même temps que son site, avec une seule manière de saisir une position.

## Requirements

### Requirement: Une position se saisit dans un seul champ

La modale de point SHALL offrir un seul champ « Position », lu par la même règle que la position
collée à la déclaration du site (voir `sites/declaration-de-carre`). Elle MUST NOT offrir de champs
séparés pour la latitude et la longitude.

La lecture SHALL se faire au fil de la saisie : un texte lisible place le marqueur de la carte, un
texte illisible affiche son motif, et l'enregistrement attend qu'il soit lisible ou vide. Un champ vide
enregistre un point sans position, comme aujourd'hui. Déplacer le marqueur SHALL écrire la paire dans le
champ. En édition, le champ SHALL montrer la position enregistrée sous la même forme.

*Vérifié par* : un test de ViewModel qui lit le même texte, décimal puis degrés-minutes-secondes, dans
le site et dans le point, et obtient les mêmes coordonnées ; un test d'interface qui saisit une paire,
lit la position du marqueur, déplace le marqueur et relit le champ.  Une mutation qui fait diverger la lecture du point de celle du site doit les faire rougir.

#### Scenario: Le même texte, deux écrans, une position

- **WHEN** l'observateur colle « 43°17'47.3"N 5°22'11.2"E » dans la modale de site, puis dans la
  modale de point
- **THEN** les deux lisent la même latitude et la même longitude

#### Scenario: Un texte illisible ne s'enregistre pas

- **WHEN** l'observateur tape « 43.4 » dans le champ « Position »
- **THEN** le motif s'affiche, le marqueur ne bouge pas, et le bouton d'enregistrement est désactivé

#### Scenario: Le marqueur écrit la position

- **WHEN** l'observateur glisse le marqueur
- **THEN** le champ « Position » montre la paire latitude, longitude du nouvel emplacement

### Requirement: Un nouveau point reçoit le code Z suivant

À la création d'un point, le code SHALL être proposé : `Z` suivi du premier numéro libre parmi les
points du site (`Z1` si aucun ne commence par `Z`). C'est la règle du portail pour un point libre. Le
code proposé reste modifiable dans la modale de point. Les noms des points systématiques, de `A1` à
`H2`, relèvent de #5608 et ne sont pas proposés ici.

*Vérifié par* : un test du calcul pur (site vide, `Z1` et `Z3` présents, codes non `Z`), et un test de
ViewModel qui ouvre une création et lit le code proposé.

#### Scenario: Le premier numéro libre

- **WHEN** le site porte déjà les points `Z1`, `Z2` et `A1`
- **THEN** le code proposé est `Z3`

#### Scenario: Un trou se comble

- **WHEN** le site porte `Z1` et `Z3`
- **THEN** le code proposé est `Z2`

### Requirement: Un point voisin se signale

À la création d'un point, un point du même site situé à **40 m au plus** de la position saisie SHALL
déclencher un avertissement qui le nomme, sans empêcher d'enregistrer. 40 m est le rayon que le portail
utilise pour rattacher un point à un nom.

*Vérifié par* : un test du calcul pur de distance et de seuil (39 m signale, 41 m non), et un test de
ViewModel qui saisit une position voisine d'un point existant et lit l'avertissement.

#### Scenario: Un point à 30 m

- **WHEN** l'observateur saisit une position à 30 m du point `Z1` du même site
- **THEN** un avertissement nomme `Z1`, et l'enregistrement reste possible

### Requirement: Le premier point se crée avec son site

À la déclaration d'un site depuis une position collée et lue, la modale de site SHALL offrir une case
« Créer aussi le premier point d'écoute à cette position », décochée par défaut. Cochée, la création
du site SHALL créer aussi un point à cette position, avec le code `Z1`. La case MUST NOT apparaître
quand aucune position n'a été lue, ni quand le carré est récupéré de Vigie-Chiro : un carré récupéré
apporte ses points.

*Vérifié par* : un test d'interface qui colle une position, coche la case, crée, et constate un site et
un seul point à la position collée ; un second qui crée sans cocher et constate un site sans point ;
un troisième, sur un carré récupéré, qui constate l'absence de la case.  Une case de
recette S1 la montre.

#### Scenario: Case cochée

- **WHEN** l'observateur colle une position, coche la case et crée le site
- **THEN** le site existe avec un seul point, `Z1`, à la position collée

#### Scenario: Case décochée

- **WHEN** l'observateur colle une position et crée le site sans cocher la case
- **THEN** le site existe sans aucun point

#### Scenario: Sans position collée

- **WHEN** l'observateur saisit le numéro du carré sans coller de position
- **THEN** la case n'est pas offerte
