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

### Requirement: La distance entre deux points s'affiche sans se juger

Sur la fiche d'un site, la carte d'un point géolocalisé SHALL afficher sa distance au point géolocalisé
le plus proche du même site, quand il en existe un. Elle MUST NOT qualifier cette distance : ni
avertissement, ni icône de sévérité, ni mention d'une règle de protocole, quelle que soit sa valeur. Le
protocole Point Fixe n'impose aucune distance minimale entre deux points ; le seul voisinage que
l'application signale est celui de la création, à 40 m.

*Vérifié par* : un test du libellé (une distance de 100 m rend la phrase nue, sans le mot « protocole »),
un test d'interface sur une fiche dont deux points sont à cent mètres l'un de l'autre, qui lit la
distance et ne trouve aucune étiquette d'alerte. Remettre un seuil doit les faire rougir.

#### Scenario: Deux points à cent mètres

- **WHEN** l'observateur ouvre la fiche d'un site dont les points `A1` et `B2` sont à cent mètres l'un de
  l'autre
- **THEN** leurs cartes disent « à 100 m du point le plus proche », sans avertissement

#### Scenario: Un seul point géolocalisé

- **WHEN** le site n'a qu'un point géolocalisé
- **THEN** sa carte n'affiche aucune distance

### Requirement: La case du premier point prévient d'un autre carré

À la déclaration d'un site, quand la case « Créer aussi le premier point d'écoute à cette position » est
cochée et que la position collée tombe sans ambiguïté dans un autre carré que le numéro saisi, la modale
SHALL l'afficher sous la case, en avertissement, en nommant le carré de la position et le carré saisi.
L'avertissement MUST NOT empêcher de créer le site ni son point.

Il MUST NOT s'afficher quand le numéro concorde, quand la case est décochée, quand le numéro saisi n'a
pas ses six chiffres, ni quand la position est sur une frontière entre deux carrés. Le carré de la
position SHALL se lire sur le carroyage embarqué, celui du bouton « Situer » : le verdict vaut hors
connexion. Sa phrase SHALL être celle que la modale de point emploie pour la même divergence.

*Vérifié par* : un test de ViewModel sur les cinq cas (autre carré, concordance, case décochée, frontière,
numéro incomplet), qui exige la phrase de la modale de point par le même type et constate que la création
aboutit ; un test d'interface qui lit l'avertissement, le bouton « Créer » resté offert, puis sa
disparition après « Situer ». Taire la divergence fait rougir les deux.

#### Scenario: Un numéro tapé, une position ailleurs

- **WHEN** l'observateur tape le carré 130711, colle une position du carré 040110 sans cliquer « Situer »,
  et coche la case
- **THEN** un avertissement nomme le carré 040110 et le carré 130711, et « Créer » reste offert

#### Scenario: Après « Situer »

- **WHEN** l'observateur clique « Situer »
- **THEN** le numéro devient 040110 et l'avertissement disparaît

#### Scenario: Sur une frontière

- **WHEN** la position collée est à distance égale de deux carrés
- **THEN** aucun avertissement ne s'affiche
