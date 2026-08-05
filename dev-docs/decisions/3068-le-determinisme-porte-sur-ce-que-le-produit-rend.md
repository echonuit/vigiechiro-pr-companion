# ADR 3068 - Le déterminisme des captures porte sur ce que le **produit** rend

- **Statut** : Accepté - 2026-08-01
- **Chantier** : #3068, suites de la clôture #3018
- **Vérification** : humaine - l'arbitrage porte sur ce qu'on accepte de voir varier ; aucun scan ne distingue une variation tolérée d'une régression

## Contexte

Les captures sont **versionnées** et régénérées à chaque push sur `main`. `captures.md` en tirait une
règle d'or sans réserve : *un rendu non déterministe salirait le dépôt à chaque CI*.

Quatre fichiers l'enfreignaient en silence. Les aperçus montrant la carte (`apercu-multisite`, `-filtre`,
`-edition`, `-carte-pleine`) changent d'un build à l'autre **sans qu'aucun code ne change**.

## Ce que la mesure a établi

| | | Revu le 2026-08-05 |
|---|---|---|
| Ampleur | **0,34 %** des pixels (2 302 sur 682 000) | médiane **1,22 %**, maximum **2,51 %** |
| Portée | **quatre** aperçus | **seize** |
| Localisation | le panneau de carte, **exclusivement** | confirmée |
| Forme | tracés **fins de routes** - une tuile absente ferait 65 000 px | confirmée |
| Quiétude portée de 0,75 s à 3 s | **aucun effet** | confirmé |

Le résidu ne vient donc pas d'un chargement incomplet, mais du **rendu servi par OpenStreetMap**, qui
n'est pas identique à chaque requête. Aucune attente ne le corrigera : la condition de stabilité est
satisfaite, et les pixels diffèrent quand même.

## Révision du 2026-08-05 : la décision tient, les chiffres non

Reprise à la demande, en instruisant la piste « figer les tuiles » que ce document avait écartée. Trois
corrections, et une confirmation.

**La portée était sous-estimée : seize aperçus, pas quatre.** Aux quatre de `multisite` s'ajoutent
`apercu-analyse-carte`, les huit aperçus d'import (dont l'assistant porte une bande cartographique en
bas de page) et les deux modales de point.

**L'amplitude aussi.** Matrice des écarts entre les **30** versions successives d'`apercu-analyse-carte`,
soit 435 paires : médiane **1,22 %**, maximum **2,51 %**. Le 0,34 % venait d'un échantillon plus étroit.

**Le mécanisme est plus précis que « le rendu n'est pas identique à chaque requête ».** Il est
**discret** : sur les 137 tuiles présentes dans les caches laissés par les exécutions, **85 ont des
octets différents d'un cache à l'autre**, avec 2 à 4 versions distinctes pour un même chemin. Chaque
tuile ayant un petit nombre de variantes, la composition n'a qu'un **nombre fini d'états** - ce que la
matrice montre : 18 états distincts pour 30 versions, dont un revenant **sept fois** à des dates non
consécutives. Un rendu aléatoire à chaque requête ne produirait jamais deux captures identiques au bit
près à des semaines d'écart.

**La décision, elle, est confirmée**, et pour une raison de plus que celles écrites en 2026-08-01. Les
frontières de modules la rendent coûteuse à défaire :

- `com.gluonhq.maps` **exporte** `com.gluonhq.maps.tile` et **utilise** `TileRetriever` comme service :
  le point d'extension est donc bien public ;
- mais le paquet d'implémentation (`com.gluonhq.impl.maps.tile.osm`) n'est **pas exporté**. Fournir
  notre propre retriever ne permettrait pas de **déléguer** au retriever réseau de Gluon : il faudrait
  réimplémenter téléchargement et cache ;
- et `provides` s'applique à **toute l'application**, pas seulement aux outils de capture. On
  remplacerait un composant de l'application **livrée** pour un besoin de **documentation** ;
- la voie douce - rediriger le cache de Gluon vers un dossier stable - est fermée elle aussi :
  `com.gluonhq.attach.storage` n'exporte son paquet d'implémentation qu'au module `attach.util`.

⚠️ Une révision de cette ADR **ne serait pas absurde** le jour où l'une de ces contraintes tombe (un
point d'injection du retriever côté application, ou un `StorageService` configurable). Le « non » est
motivé par un rapport coût/bénéfice, pas par un principe.

**Ce qui change en pratique** : depuis #3359, la CI ne **committe** plus les écarts sous 4 % sur ces
seize aperçus. On garde la source vivante et sa variabilité ; on cesse simplement d'en faire un commit,
une PR et un conflit à chaque exécution. Le corollaire de 2026-08-01 - « sur ces fichiers, un diff n'est
pas un signal » - reste vrai, et devient enfin sans conséquence sur l'historique.

⚠️ Un défaut **distinct** existait au même endroit, et a été corrigé : l'attente des tuiles était un
délai fixe de six secondes, donc une course contre le réseau. Une capture pouvait partir avec des tuiles
**manquantes** - un fond absent, pas une nuance de rendu. C'est désormais une condition sur l'état du
graphe de scène, et les captures de carte sont passées de 48 s à 10 s. Les deux sujets se ressemblent et
ne sont pas le même : corriger le premier ne fait rien au second.

## Décision

La règle de déterminisme porte sur **ce que le produit rend**. Une entrée **extérieure au dépôt** n'y est
pas soumise.

Concrètement, pour les captures de carte : **on garde la dépendance aux tuiles OpenStreetMap**, avec leur
variabilité résiduelle.

Ces captures valent précisément parce qu'elles montrent une **vraie** carte, au même titre que les autres
montrent de vraies données depuis une base semée - c'est la même exigence que celle de
l'[ADR 3018](3018-un-outil-compose-depuis-la-racine.md), qui a fait cesser les données fabriquées. Figer
la source de tuiles rendrait l'image plus stable et **moins vraie**.

## Conséquences

- **Sur ces quatre fichiers, un diff de captures n'est pas un signal.** La revue s'y fait à l'œil, pas au
  `cmp`. C'est le corollaire pratique, et il n'est pas anodin : au cours du chantier #3050, cette
  variation m'a fait conclure **deux fois** qu'un changement modifiait le rendu alors qu'il n'y était pour
  rien.
- Le contrôle qui tranche reste le même, et il est désormais écrit : avant de lire un écart entre deux
  images, **mesurer ce que produit l'absence de changement** - deux exécutions du même code.
- La règle d'or, elle, ne bouge pas pour tout le reste : signaux de synthèse plutôt qu'audio réel, pas
  d'horodatage, attente explicite des chargements asynchrones.

## Alternatives écartées

- **Figer la source de tuiles** (cache versionné, serveur unique). Rend les captures reproductibles au
  prix de ce qui fait leur valeur : elles cesseraient de montrer la carte que l'utilisateur voit.
  **Réexaminé le 2026-08-05** et écarté de nouveau, avec un motif technique supplémentaire : les
  frontières de modules obligeraient à remplacer le `TileRetriever` de l'**application livrée** pour un
  besoin de documentation (voir la révision plus haut). L'instantané lui-même serait pourtant bon marché
  - **137 tuiles, 2,4 Mo** - ce n'est donc pas son poids qui l'écarte.
- **Substituer une image pré-rendue à la carte, dans les outils de capture seulement.** Déterminisme
  total et zéro impact sur l'application, mais la capture cesserait de traverser le vrai composant
  carte : exactement ce que l'[ADR 3018](3018-un-outil-compose-depuis-la-racine.md) a fait cesser pour
  les données fabriquées.
- **Retirer le fond de carte des captures.** Même objection, en pire : l'écran perdrait ce qu'il est.
- **Ne rien écrire et continuer.** C'était l'état de départ. Une règle d'or que quatre fichiers
  enfreignent sans que ce soit dit n'est plus une règle : elle apprend surtout à ignorer les règles.
