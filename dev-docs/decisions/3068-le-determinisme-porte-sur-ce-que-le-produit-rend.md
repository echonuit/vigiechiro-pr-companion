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

| | |
|---|---|
| Ampleur | **0,34 %** des pixels (2 302 sur 682 000) |
| Localisation | le panneau de carte, **exclusivement** |
| Forme | tracés **fins de routes** - une tuile absente ferait 65 000 px |
| Quiétude portée de 0,75 s à 3 s | **aucun effet** |

Le résidu ne vient donc pas d'un chargement incomplet, mais du **rendu servi par OpenStreetMap**, qui
n'est pas identique à chaque requête. Aucune attente ne le corrigera : la condition de stabilité est
satisfaite, et les pixels diffèrent quand même.

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
- **Retirer le fond de carte des captures.** Même objection, en pire : l'écran perdrait ce qu'il est.
- **Ne rien écrire et continuer.** C'était l'état de départ. Une règle d'or que quatre fichiers
  enfreignent sans que ce soit dit n'est plus une règle : elle apprend surtout à ignorer les règles.
