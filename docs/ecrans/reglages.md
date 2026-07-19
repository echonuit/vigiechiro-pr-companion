# Réglages

L'écran **Réglages** regroupe les **préférences de l'application**, organisées par domaine. Il est
accessible à tout moment depuis le menu **☰** (en haut à droite) → **Réglages…**.

![L'écran Réglages : un onglet par domaine (Général, Import, Fonctionnalités).](../assets/captures/apercu-reglages.png)

Chaque **onglet** correspond à un domaine de l'application ; il se remplit tout seul des réglages que
ce domaine propose. Aujourd'hui :

- **Général** : la **source des fiches espèces** hors chiroptères (Wikipédia FR ou, par défaut, GBIF).
  Le Plan National d'Actions reste prioritaire pour les chauves-souris. L'effet est immédiat sur les
  prochaines fiches ouvertes.
- **Import** : **conserver les originaux pour ré-analyse ultérieure** (copie des WAV bruts avant
  transformation ; désactivé par défaut, cf. [Importer une nuit](importation.md)).
- **Dépôt** : la **forme du dépôt** et la **taille maximale d'une archive**.

    La **forme du dépôt** décide de ce qui part sur Vigie-Chiro, et ce choix a une conséquence qu'il
    vaut la peine de connaître :

    | Forme | Durée | L'audio après traitement |
    |---|---|---|
    | **Archives ZIP** (par défaut) | rapide | la plateforme extrait puis **supprime** l'archive sans conserver les sons : ils ne sont plus téléchargeables depuis Vigie-Chiro, et la participation **ne pourra pas être relancée** |
    | **Séquences WAV** | plus lent (un envoi par son) | chaque son **reste en ligne**, et la participation **reste relançable** |

    Si vous comptez pouvoir relancer l'analyse plus tard, ou réécouter vos sons depuis le site,
    choisissez les séquences WAV.

    La **taille maximale d'une archive** (700 Mo par défaut, la limite acceptée par la plateforme) est
    utile pour générer des archives plus petites (connexion fragile) ; le changement s'applique à la
    **prochaine génération** d'archives.
- **Fonctionnalités** : **activer ou désactiver** les fonctionnalités optionnelles de l'application
  (l'**import depuis Vigie-Chiro**, le **diagnostic** du capteur, la **préparation du dépôt**, la
  **vérification**, l'**importation Tadarida**, l'**analyse** « Espèces & observations » et la
  **recherche globale**). Désactiver une fonctionnalité retire son point d'entrée de l'interface.
  Contrairement aux autres réglages, ces bascules prennent effet **au prochain démarrage** de
  l'application (un bandeau le rappelle sur l'onglet).

Une modification est **enregistrée immédiatement** et **conservée d'une session à l'autre**. Seul
l'**effet** des bascules de l'onglet « Fonctionnalités » s'applique au **prochain démarrage**.

!!! note "Un réglage, parfois à deux endroits"
    Certains réglages restent aussi accessibles là où on en a besoin : la source des fiches espèces
    figure également dans le menu ☰. Les deux emplacements pilotent le **même** réglage et restent
    synchronisés.

    « Conserver les originaux » figurait aussi sur l'écran d'import ; il n'y est plus. C'est une option
    de ré-analyse, dont l'immense majorité des imports n'a pas à se soucier : elle vit désormais ici,
    où on la trouve quand on la cherche.
