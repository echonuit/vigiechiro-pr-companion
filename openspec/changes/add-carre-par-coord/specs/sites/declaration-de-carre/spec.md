## Purpose

Ce que l'écran de déclaration d'un site garantit sur le numéro de carré : comment il se saisit,
comment il se déduit d'une position que l'observateur colle depuis une carte, et ce que l'application
refuse de déduire plutôt que de le deviner.

## ADDED Requirements

### Requirement: Déduire le numéro de carré d'une position collée

L'écran de déclaration d'un site SHALL accepter une position géographique collée et en déduire le
numéro de carré de la grille nationale, qu'il dépose dans le champ des six chiffres.

La déduction est un **confort**, jamais une condition : déclarer un carré en le tapant reste possible
à tout moment.

**Vérifié par** : un test de scénario sur la modale de site, sur le patron de
`ScenarioModaleCarreTest`. Aucun n'existe aujourd'hui pour ce geste ; ce changement le crée.

#### Scenario: Une position dans la grille rend son carré

- **WHEN** l'observateur colle « 43.296482, 5.369780 » dans le champ de position et demande à situer
- **THEN** le champ du numéro de carré porte le numéro à six chiffres de la grille pour cette position
- **AND** le geste « Vérifier sur Vigie-Chiro » devient ouvert, puisque le numéro est complet

#### Scenario: Déclarer sans jamais coller de position

- **WHEN** l'observateur tape les six chiffres du carré à la main et ne renseigne aucune position
- **THEN** le site se crée comme avant ce changement, sans que la position soit exigée

### Requirement: Les formats de position acceptés, et ceux qui sont refusés

L'écran SHALL accepter une position en **degrés décimaux** et en **degrés-minutes-secondes**, dans
l'ordre **latitude puis longitude**.

L'écran MUST refuser un texte qu'il ne sait pas lire, et son refus MUST dire quoi coller à la place.
Il ne devine ni l'ordre des deux nombres, ni une position à partir d'une URL de carte.

**Vérifié par** : des tests unitaires sur l'analyseur de position, classe pure, couvrant chaque format
accepté et chaque forme refusée. Ils n'existent pas ; ce changement les crée.

#### Scenario: Position en degrés décimaux

- **WHEN** l'observateur colle « 43.296482, 5.369780 »
- **THEN** la position est lue comme latitude 43.296482 et longitude 5.369780

#### Scenario: Position en degrés-minutes-secondes

- **WHEN** l'observateur colle une position écrite en degrés, minutes et secondes avec ses points
  cardinaux
- **THEN** elle est lue comme la même position que son équivalent décimal

#### Scenario: Une URL de carte est refusée, et le refus dit quoi coller

- **WHEN** l'observateur colle une URL Google Maps ou OpenStreetMap
- **THEN** aucune position n'est lue
- **AND** le motif affiché nomme le format attendu, deux nombres séparés par une virgule

#### Scenario: Un texte illisible ne remplit rien

- **WHEN** l'observateur colle un texte qui ne porte pas deux nombres lisibles
- **THEN** aucune position n'est lue, et le champ du numéro de carré n'est pas touché

### Requirement: Un carré ne se propose que si la position tombe dedans

L'application MUST proposer un numéro de carré **seulement** quand la position donnée appartient à un
carré de la grille. Quand aucun carré ne la contient, elle MUST ne rien proposer et le dire comme une
**réponse**, distincte d'une panne.

Une proposition est acceptée sans être relue. Rendre le carré voisin serait rendre un numéro faux et
plausible, qui contaminerait ensuite le préfixe des fichiers de la nuit.

**Vérifié par** : une sonde de contrat live confrontant une position hors grille au serveur réel, sur
le patron de `ContratApiVigieChiroLiveTest#grille_stoc_rend_le_carre_du_point`. Cette sonde-là existe
pour le cas nominal ; le cas hors grille n'a pas la sienne, et ce changement la crée.

#### Scenario: Une position hors de la grille ne propose rien

- **WHEN** l'observateur colle une position en mer, outre-mer ou à l'étranger
- **THEN** aucun numéro n'est déposé dans le champ du carré
- **AND** le message dit que cette position n'appartient à aucun carré de la grille, sans laisser
  croire à un échec réseau

#### Scenario: Une position dans un carré rend ce carré, pas son voisin

- **WHEN** la position donnée appartient à un carré de la grille
- **THEN** le numéro proposé est celui de ce carré

### Requirement: La déduction remplace une saisie en le disant

Quand le champ du numéro de carré porte déjà six chiffres qui diffèrent du numéro déduit, l'écran
SHALL remplacer la saisie **et** afficher ce qui a été remplacé.

Un numéro tapé à la main est une intention. L'écraser sans un mot ferait disparaître une divergence
que l'observateur avait peut-être raison de tenir.

**Vérifié par** : un test de scénario sur la modale. Il n'existe pas ; ce changement le crée.

#### Scenario: Le numéro déduit diffère du numéro tapé

- **WHEN** le champ porte déjà six chiffres et que la position déduit un numéro différent
- **THEN** le champ porte le numéro déduit
- **AND** le message nomme le numéro qui vient d'être remplacé

#### Scenario: Le numéro déduit confirme le numéro tapé

- **WHEN** le champ porte déjà six chiffres et que la position déduit le même numéro
- **THEN** le champ est inchangé, et le message dit que la position confirme la saisie

### Requirement: Situer une position ne demande rien au réseau

Le geste qui situe une position MUST fonctionner **sans connexion et sans jeton**. Le carré se déduit
d'un référentiel embarqué, et non de la plateforme.

Deux questions distinctes se posent, et une seule appelle le réseau : « quel carré couvre cette
position » est de la géométrie, « ce carré existe-t-il en Point Fixe » est une question de portail.
Fermer le premier geste hors connexion ferait de la plateforme une condition pour déclarer chez soi,
ce que l'écran existe précisément pour éviter.

**Vérifié par** : un test de scénario sur la modale, qui situe une position sans qu'aucun jeton ne
soit disponible. Il n'existe pas.

#### Scenario: Sans jeton, situer une position fonctionne

- **WHEN** l'observateur ouvre la modale sans être connecté et colle une position de métropole
- **THEN** le numéro de carré est déposé dans le champ des six chiffres
- **AND** aucun message ne parle de connexion

#### Scenario: Sans jeton, la vérification reste fermée

- **WHEN** l'observateur n'est pas connecté
- **THEN** « Vérifier sur Vigie-Chiro » reste fermé, et son motif dit où se connecter
- **AND** la déclaration du carré reste possible

#### Scenario: Une position hors du référentiel embarqué

- **WHEN** la position collée est hors de la France métropolitaine
- **THEN** aucun numéro n'est déposé, et le message le dit comme une réponse et non comme une panne

### Requirement: La vérification sur Vigie-Chiro reste un geste séparé

Situer une position MUST remplir le champ du numéro de carré et **rien d'autre**. L'application MUST
NOT interroger le portail sur l'existence de ce carré sans que l'observateur l'ait demandé.

Deux questions distinctes se posent au portail, et les enchaîner ferait payer un aller-retour réseau
que l'observateur n'a pas demandé, sur un numéro qu'il n'a pas encore relu.

**Vérifié par** : un test de scénario sur la modale, qui compte les appels au portail après un geste
de position. Il n'existe pas ; ce changement le crée.

#### Scenario: Situer ne déclenche aucune vérification

- **WHEN** l'observateur colle une position et demande à situer
- **THEN** le champ du carré est rempli
- **AND** aucun verdict d'existence sur Vigie-Chiro n'est affiché tant qu'il n'a pas cliqué
  « Vérifier sur Vigie-Chiro »

### Requirement: Le numéro déduit porte six chiffres, département en tête

La grille nationale rend le numéro **sans son zéro de gauche** dans les départements 01 à 09.
L'application MUST le rembourrer à six chiffres avant de le rendre à qui que ce soit.

Six chiffres est la forme que R1 impose et que le catalogue des sites respecte. Un numéro à cinq
chiffres ne remplit pas le champ, n'ouvre pas la vérification, et ne désigne aucun site du catalogue.

**Vérifié par** : des tests unitaires sur la lecture de la réponse de la grille, dont un cas de
département à un chiffre. Le cas existant n'éprouve que le département 13, qui ne perd aucun zéro.

#### Scenario: Un carré de département à un chiffre

- **WHEN** la grille rend « 40110 » pour la position donnée
- **THEN** le numéro rendu à l'écran est « 040110 »

#### Scenario: Un carré de département à deux chiffres passe inchangé

- **WHEN** la grille rend « 130711 »
- **THEN** le numéro rendu est « 130711 »

#### Scenario: Le champ accepte un numéro déduit en Alpes-de-Haute-Provence

- **WHEN** la position collée tombe dans un carré du département 04
- **THEN** le champ des six chiffres est complet
- **AND** « Vérifier sur Vigie-Chiro » est ouvert

### Requirement: Plusieurs carrés candidats se nomment, aucun ne se choisit

Quand la grille rend plusieurs carrés à des distances proches, l'écran SHALL les **nommer** et MUST
NOT en déposer un dans le champ des six chiffres.

Un point situé sur la frontière de deux carrés n'appartient à aucun des deux sans une convention que
la grille ne porte pas : elle ne stocke que des centres. Mesuré le 2026-08-27, deux centres sont à
distance strictement égale au milieu d'un côté commun, et quatre le sont à un coin. Choisir le
« premier » y revient à tirer au sort, et un numéro faux et plausible est le défaut que ce changement
cherche à éviter.

Le seuil se dérive de la géométrie : pour un point à `x` mètres d'un bord, l'écart entre les deux
distances vaut environ `2x`. Un écart de moins de **50 m** désigne les points à moins de 25 m d'un
bord.

**Vérifié par** : des tests unitaires sur la lecture de la réponse de la grille, avec deux mailles
proches puis deux mailles éloignées, et un test de scénario sur la modale. Aucun n'existe.

#### Scenario: Deux carrés à distance proche

- **WHEN** la position collée tombe à moins de 25 m d'un bord, et que la grille rend deux carrés dont
  les distances diffèrent de moins de 50 m
- **THEN** le champ des six chiffres reste vide
- **AND** le message nomme les deux numéros et dit que la position est sur une frontière

#### Scenario: Un carré nettement plus proche que le suivant

- **WHEN** la grille rend plusieurs carrés dont le deuxième est à plus de 50 m du premier
- **THEN** le premier est déposé dans le champ, comme pour un carré unique

#### Scenario: Un seul carré rendu

- **WHEN** la grille ne rend qu'un carré
- **THEN** il est déposé dans le champ, sans mention de frontière
