---
type: adr
title: "Une lecture de masse se fait par lot, et se garde en comptant des requêtes"
status: stable
article: A9
chantier: "#4251, #4271, #4278, #4283, #4286, #4289, #4293"
decided_at: 2026-08-23
verification: certaine
enforced_by:
  - "RequetesDeLAuditTest#les_requetes_ne_suivent_pas_les_nuits"
verified:
  - by: machine:ci
    at: 2026-08-23
---

# Une lecture de masse se fait par lot, et se garde en comptant des requêtes

## Contexte

Sept chemins de lecture composaient leurs lignes en interrogeant la base **pour chacune** : une requête
par site pour ses points, une par point pour ses passages, jusqu'à onze par nuit dans l'audit complet.

Le coût de ce défaut a une propriété qui le rend durablement invisible : **il croît avec l'inventaire de
l'utilisateur**. Il est donc nul sur les jeux d'essai, absent des relevés, et maximal chez celui qui a le
plus de données - le coordinateur départemental que l'objectif [O5](https://brief.echonuit.fr/Objectifs%20qualit%C3%A9s/Objectifs%20qualit%C3%A9s/O5/)
est censé protéger.

Mesuré avec préchauffage, trois essais, à cent cinquante carrés : « Mes sites » 165-241 ms, « Carte &
passages » 339-377 ms, « Ma saison » 386-409 ms. Après lecture par lot, 6 à 18 ms, et surtout **une
pente qui disparaît** : le coût ne double plus de soixante à cent cinquante carrés.

Le repère qui a fait trouver six des sept : **un commentaire qui énonce le principe pour une partie des
tables seulement**. Chacun des fichiers portait une phrase du genre « lues une seule fois, pas une par
ligne », appliquée à deux ou quatre tables, en laissant dehors précisément celles qui portaient le
volume.

## Décision

**1. Toute boucle qui compose des lignes lit par lot avant de boucler.** `PointDao#findParSites`,
`PassageDao#findParPoints`, `SequenceDao#findParIds` rendent leur résultat groupé ; quand la table ne
porte qu'une ligne par entité, un `findAll()` indexé en mémoire suffit (`ContexteAudit`).

**2. Les tables de volume restent lues par session.** Originaux et séquences d'écoute : une nuit en
porte des milliers. Les charger d'un bloc échangerait un défaut de lenteur contre un défaut de mémoire,
ce qui n'est pas un progrès.

**3. Un garde de lecture compte des requêtes, jamais des millisecondes.** Deux formes : un `Mockito.spy`
sur le DAO (`never()` sur la lecture unitaire, `times(1)` sur la groupée), ou un **compteur de
connexions** comparé à deux tailles de jeu. La seconde seule attrape les requêtes qui vivent plus bas
dans la pile d'appels.

**4. Le jeu d'un banc fait varier la dimension qui porte le défaut.** Un banc qui monte à mille passages
sur **un seul carré** ne voit rien d'une requête lancée par site. `JeuDuBancTest` refuse que le jeu
retombe à un carré.

## Pourquoi pas le chronomètre

Trois façons de se tromper, toutes rencontrées le même jour :

- **la première mesure d'un processus n'est pas une mesure** : le démarrage de la JVM coûte ~300 ms
  quelle que soit la taille des données. Un « 487 ms » annoncé publiquement était pour l'essentiel ce
  démarrage ;
- **lire le code sous-compte** : l'audit semblait faire six requêtes par nuit, le compteur en a trouvé
  **onze** - cinq vivaient un appel plus bas ;
- **une session voisine charge la machine** : un banc filmé faisait varier les relevés du simple au
  double, et un relevé « après » est sorti plus lent que l'« avant ».

Le compteur de connexions est déterministe et insensible à ces trois-là.

## Ce qui a été écarté

**Supprimer `LotsDeParametres`.** Sa raison d'être annoncée était **fausse** : il n'existe pas de refus
de SQLite « au-delà de quelques centaines de paramètres liés » - mesuré, le pilote embarqué en accepte
cinquante mille. La classe **reste** parce qu'une requête de cinquante mille marqueurs fait une centaine
de kilo-octets de SQL à construire et analyser, mais son commentaire dit désormais cela et non une
protection qui n'existe pas. **Une protection qu'on croit avoir dispense de chercher celle qui manque.**

**Un butoir en millisecondes dans la CI.** Il se noierait dans la variance de la machine et rougirait un
jour de charge sans qu'aucun code ait bougé - exactement ce qui est arrivé pendant ce chantier.

## Conséquences

- Le cas de recette qui filmait le voile d'occupation de « Mes sites » (#4172) a **perdu son sujet** : le
  chargement ne dure plus assez pour qu'une image le porte. Il a été retiré en le disant, plutôt que de
  gonfler la fixture pour fabriquer une lenteur que le produit n'a plus.
- La parité CLI ↔ IHM ([ADR 0014](0014-parite-cli-ihm.md)) porte aussi sur la **façon de lire** :
  corriger une surface et pas l'autre crée l'asymétrie au lieu de la résoudre (#4300).
- L'objectif O5 du brief a gagné la **topologie** comme troisième dimension de volume, et perdu un
  protocole de mesure qui prescrivait de mesurer à froid.
