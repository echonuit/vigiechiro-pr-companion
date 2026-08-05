# ADR 2791 - La commune se dérive du GPS et s'attache au point, jamais au carré

- **Statut** : Accepté - 2026-07-29
- **Chantier** : #2791 (lot 0 de l'EPIC #2790)
- **Vérification** : certaine - `MigrationSchemaTest#table_commune_du_point_creee`

## Contexte

Le parcours « envoyer un sous-ensemble à un expert » (EPIC #2790) demande de filtrer « les grands
Rhinolophes de mes nuits sur Aix ». Or « Aix » n'existait nulle part dans le modèle : un site porte le
nom que son propriétaire lui a donné (« Jardin de Serge »), un carré porte un numéro. Le produit sait
pourtant déjà situer un **carré** dans une région (ADR 2351 : les deux premiers chiffres du numéro
sont le département, puis la table département → région embarquée). Il manquait le maillon commune,
et il ne peut venir que du GPS : c'est le seul fait géographique exact que porte un point d'écoute.

## Décision

### 1. La commune s'attache au point, jamais au carré

Rien n'empêche un carré de 2 km (R26) d'être à cheval sur plusieurs communes, ni même sur deux
départements : toute correspondance carré → commune serait fausse par construction. La commune est
dérivée des coordonnées GPS du **point** (point-dans-polygone) et vit à côté de lui.

### 2. Dérivée une fois par l'API Géo, best-effort, rattrapable

La résolution appelle `geo.api.gouv.fr/communes?lat=&lon=` (référentiel officiel, gratuit, sans clé)
et **persiste** le résultat : un point ne bouge pas, sa commune non plus. Hors ligne ou hors
référentiel, la commune reste simplement absente - jamais bloquante, jamais devinée - et un
**rattrapage** comble plus tard les points en attente. Un GPS modifié efface d'abord la commune
mémorisée : une commune absente vaut mieux qu'une commune fausse.

### 3. Table latérale, pas de composantes sur l'entité

`point_commune (point_id, commune_name, commune_insee)`, l'absence de ligne disant « non résolue ».
Le record `PointDEcoute` est construit en ~25 endroits en main : on ne lui ajoute pas deux
composantes pour un fait dérivé et recalculable (même arbitrage que l'ADR 2525 et l'EPIC arité
#2483, appliqué ici à des valeurs et non un booléen).

### 4. Une seule table département → région

Le code INSEE fait foi ; département et région ne sont **pas stockés**, ils se dérivent via
`RegionsFrancaises`, extraction de la table posée par l'ADR 2351 (`RegionDuCarre` y délègue
désormais). Les libellés restent des clés de jointure du référentiel d'activité, et la normalisation
absorbe la divergence de codes : Corse `20` côté numérotage carré, `2A`/`2B` côté INSEE, outre-mer
`97x` → vide (repli `national`, plus large mais jamais faux).

## Conséquences

- Nouvel hôte sortant (`geo.api.gouv.fr`), best-effort intégral comme le client GBIF : à documenter
  sur la page sécurité/réseau.
- Le critère « Lieu » de la vue audio (#2794) pourra proposer commune, carré, point et site.
- Un diagnostic « département du point ≠ département du carré » devient possible (divergence
  légitime en bord de carré, mais qui mérite d'être montrée) : différé, hors périmètre du lot.
- Les déclencheurs (création et édition de point, synchro, CLI de rattrapage) arrivent dans les PR
  suivantes du lot ; ce socle n'en impose aucun.

## Alternatives écartées

- **Colonnes sur `listening_point`** : churn de construction sur toute la base de code pour un fait
  recalculable (cf. décision 3).
- **Référentiel géographique embarqué** (polygones des communes) : des dizaines de Mo dans un
  fat-jar de 32 Mo, pour un besoin couvert par un appel unique par point.
- **Stocker département et région** : redondants avec le code INSEE, et divergents à la première
  réforme territoriale ; on dérive.
- **Géocodage à la volée à l'affichage** : dépendance réseau permanente des vues pour une donnée
  immuable ; on persiste.
