## MODIFIED Requirements

### Requirement: La vérification sur Vigie-Chiro reste un geste séparé

Situer une position MUST remplir le champ du numéro de carré et **rien d'autre**. L'application MUST
NOT interroger le portail sur l'existence de ce carré sans que l'observateur l'ait demandé.

Deux questions distinctes se posent au portail, et les enchaîner ferait payer un aller-retour réseau
que l'observateur n'a pas demandé, sur un numéro qu'il n'a pas encore relu.

**Créer est une demande** (#5607). Quand l'observateur clique « Créer » sans qu'aucun verdict
d'existence ne soit affiché pour ce numéro, la modale SHALL interroger le portail avant d'enregistrer.
Un carré qu'on n'a pas vérifié serait sinon déclaré sans que rien ne dise, avant le dépôt, qu'il n'est
pas sur la plateforme : c'est le cas de Samuel sur le carré 202013.

- carré déjà déclaré en Point Fixe : la modale MUST NOT créer le site ; elle reste ouverte, affiche le
  verdict et propose « Récupérer ce carré » ;
- carré absent, présent seulement sous un autre protocole, ou portail injoignable : la modale crée le
  site, et le bandeau de retour de « Mes sites » SHALL porter le verdict.

**Vérifié par** : `SiteEditSituerPositionTest#situer_n_interroge_pas_la_plateforme`, qui compte les
appels au portail, et `#le_depot_efface_le_verdict_d_existence` pour la frontière inverse. Le volet
« Créer » n'a encore aucun dispositif : ce changement écrit les tests de la modale qui comptent les
appels au portail et lisent le bandeau de « Mes sites », un par issue.

#### Scenario: Situer ne déclenche aucune vérification

- **WHEN** l'observateur colle une position et demande à situer
- **THEN** le champ du carré est rempli
- **AND** aucun verdict d'existence sur Vigie-Chiro n'est affiché tant qu'il n'a pas cliqué
  « Vérifier sur Vigie-Chiro »

#### Scenario: Créer sans avoir vérifié un carré absent

- **WHEN** l'observateur saisit un carré absent de Vigie-Chiro et clique « Créer » sans avoir vérifié
- **THEN** le portail est interrogé une fois, le site est créé
- **AND** le bandeau de « Mes sites » dit qu'il faudra activer le carré en Point Fixe sur le portail
  avant de pouvoir déposer

#### Scenario: Créer sans avoir vérifié un carré déjà en Point Fixe

- **WHEN** l'observateur saisit un carré déjà déclaré en Point Fixe et clique « Créer » sans avoir vérifié
- **THEN** aucun site n'est créé, la modale reste ouverte avec le verdict « existe déjà »
- **AND** « Récupérer ce carré » est proposé

#### Scenario: Créer après avoir vérifié n'interroge pas une seconde fois

- **WHEN** un verdict d'existence est déjà affiché pour le numéro saisi et l'observateur clique « Créer »
- **THEN** le portail n'est pas interrogé de nouveau

## ADDED Requirements

### Requirement: Le verdict d'existence dit ce qu'il implique pour le dépôt

Un carré ne reçoit des nuits déposées que s'il existe en **Point Fixe** sur Vigie-Chiro. Le verdict
d'existence, à l'écran comme dans `creer-site`, SHALL dire pour chacun des quatre cas ce qu'il implique
pour le dépôt :

| Cas | Ce que dit le verdict | Gravité à l'écran |
|---|---|---|
| Présent en Point Fixe | il existe déjà : le récupérer ici, pour qu'il soit rattaché | avertissement |
| Présent seulement sous un autre protocole | il existe, mais pas en Point Fixe ; on peut le déclarer ici, et pour y déposer des nuits il faudra l'activer en Point Fixe sur le portail (y créer un point), puis le récupérer ici | avertissement |
| Absent | il n'existe pas encore ; on peut le déclarer ici, et pour y déposer des nuits il faudra l'activer en Point Fixe sur le portail (y créer un point), puis le récupérer ici | avertissement |
| Portail injoignable ou non connecté | la vérification n'a pas eu lieu, et le verdict le dit | information |

La phrase sur le portail SHALL être la même partout où elle paraît : vérification, rapatriement et
`creer-site`. `creer-site` SHALL l'écrire sur sa sortie d'erreur et MUST NOT changer sa sortie
standard, qui ne porte que l'identifiant du site créé.

**Vérifié par** : aucun dispositif encore. Ce changement écrit un test par cas sur le texte rendu de
la vérification, un test de `creer-site` sur ses deux sorties, et un cas `bats`. Rouges avant le
correctif : le verdict « absent » y est « vous pouvez le déclarer ici », en succès ; le carré sous un
autre protocole y est « existe déjà, récupérez-le » ; `creer-site` y crée sans rien écrire.

#### Scenario: Un carré absent

- **WHEN** l'observateur vérifie un carré qu'aucun site de Vigie-Chiro ne porte
- **THEN** le verdict, en avertissement, dit qu'on peut le déclarer ici et qu'il faudra l'activer en
  Point Fixe sur le portail avant de pouvoir déposer

#### Scenario: Un carré présent seulement en Routier

- **WHEN** l'observateur vérifie un carré que Vigie-Chiro porte en Routier et pas en Point Fixe
- **THEN** le verdict nomme le site Routier et dit qu'il faudra activer le carré en Point Fixe sur le
  portail
- **AND** « Récupérer ce carré » n'est pas proposé

#### Scenario: creer-site sur un carré absent

- **WHEN** `creer-site` crée un site dont le carré n'existe pas en Point Fixe sur Vigie-Chiro
- **THEN** la commande sort en 0, sa sortie standard ne porte que l'identifiant du site
- **AND** sa sortie d'erreur porte la phrase sur le portail
