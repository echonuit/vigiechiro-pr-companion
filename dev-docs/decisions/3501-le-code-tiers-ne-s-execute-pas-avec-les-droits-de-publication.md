# ADR 3501 - Le code tiers ne s'exécute pas avec les droits de publication

- **Statut** : Accepté - 2026-08-07
- **Chantier** : #3501, décision prise au lot 3 (#2723) du chantier #2720 et restée non écrite ; issues #2737, #2738, #2739
- **Vérification** : certaine - `.github/scripts/verifie-epinglage.sh`

## Contexte

L'audit du 28 juillet 2026 a relevé une chaîne de publication non durcie. Trois faits, un seul mécanisme :

- **28 actions référencées, 28 sur un tag mutable** (`actions/checkout@v7`, `setup-java@v5`,
  `peaceiris/actions-gh-pages@v4`…), et le conteneur du vérificateur de données externes tiré en
  `:latest` dans un workflow disposant de droits d'écriture ;
- l'outillage de publication installé par `npx --yes` **au moment de publier**, dans un job autorisé à
  écrire contenus, issues et PR ;
- un **seul job** faisant tout : analyse des commits, calcul de version, création du tag, publication.

Ces trois faits ont la même conséquence. **La compromission d'un tiers devient immédiatement la
nôtre, au moment exact où nos droits sont maximaux, et sans qu'aucun diff du dépôt ne l'ait montré.**
Un tag se déplace sans que rien ne bouge ici ; `npx --yes` résout ses versions à chaque exécution ;
et le job qui publie exécutait tout le reste.

⚠️ Le point aveugle est là : les trois se lisent comme des choix de commodité, et aucun ne produit de
symptôme tant que rien ne se passe. Une chaîne de publication non durcie ne rougit jamais.

## Décision

**Ce qui s'exécute dans un job privilégié est épinglé, commité et revu. Le reste s'exécute sans
privilège.**

Trois conséquences, qui sont les trois remèdes du lot :

1. **Épinglage total.** Chaque `uses:` porte un SHA de commit complet, le tag lisible en commentaire ;
   chaque conteneur porte un digest. Aucune référence mobile, nulle part - un workflow peu privilégié
   d'aujourd'hui est le workflow privilégié de demain.
2. **Outillage commité.** Les dépendances de publication vivent dans un manifeste et un lockfile
   versionnés, installés par `npm ci`. Toute montée passe par un diff que quelqu'un relit.
3. **Privilèges séparés.** Un job **sans droit d'écriture** analyse et calcule ; un job privilégié
   minimal consomme son résultat et ne fait que tagger et publier, sans exécuter de code tiers au-delà
   des actions épinglées. C'est la séparation déjà pratiquée sur la documentation, où la construction
   se fait sans secret et le déploiement avec.

## Conséquences

- **L'épinglage ne fige pas, il rend chaque montée visible et différable.** C'est la réponse au seul
  reproche sérieux qu'on puisse lui faire. Dependabot continue de proposer les montées ; ce qui change
  est qu'elles arrivent en PR relisible plutôt qu'en silence.
- Un diff de workflow devient moins lisible : `actions/checkout@3d3c42e5…` ne dit pas sa version. Le
  commentaire de tag à droite est donc **obligatoire**, pas décoratif.
- **Deux gardes, deux questions distinctes**, et les confondre fait conclure de travers.
  `verifie-epinglage.sh` refuse une référence déplaçable ; `verifie-fraicheur-actions.sh` **ignore**
  les références non épinglées et mesure le retard de celles qui le sont. La première tient la règle,
  la seconde tient sa contrepartie - puisque l'épinglage gèle, il faut mesurer ce qui a vieilli.
  Chacune porte son auto-test, exécuté avant elle.
- Le seul `uses:` qui n'est pas un SHA git est épinglé par **digest Docker**, ce qui est plus fort.
  Une sonde naïve le comptera à tort comme mutable : compter les références mobiles demande d'accepter
  les deux formes.

## Alternatives écartées

- **Épingler seulement les workflows privilégiés.** C'était la priorité de mise en œuvre, pas une
  cible : la frontière entre privilégié et non privilégié bouge, et un `uses:` mobile survit à la
  raison qui l'avait laissé passer.
- **Faire confiance aux tags des éditeurs connus.** Un tag n'est pas une promesse de l'éditeur, c'est
  une référence mutable de git. La confiance ne porte pas sur qui publie, mais sur ce qui ne peut plus
  changer.
- **Une image de publication préconstruite** plutôt qu'un lockfile. Retenue comme possible à l'époque,
  écartée à l'usage : elle déplace le problème vers la construction de l'image, qu'il faudrait épingler
  et relire à son tour.
