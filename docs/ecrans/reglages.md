# Réglages

L'écran **Réglages** regroupe les **préférences de l'application**, organisées par domaine. Il est
accessible à tout moment depuis le menu **☰** (en haut à droite) → **Réglages…**.

![L'écran Réglages : un onglet par domaine (Général, Import, Fonctionnalités).](../assets/captures/apercu-reglages.png)

Sous chaque réglage, une ligne dit **ce qu'il engage** : pas seulement ce qu'il fait. L'onglet
**Import** porte l'option de conservation des originaux, décochée par défaut :

![L'onglet Import des Réglages : « Conserver les originaux pour ré-analyse ultérieure », décoché.](../assets/captures/apercu-reglages-import.png)

L'onglet **Dépôt** porte le choix qui décide si vos sons restent téléchargeables depuis Vigie-Chiro :

![L'onglet Dépôt des Réglages : « Forme du dépôt », avec la conséquence de chaque mode sur l'audio conservé en ligne.](../assets/captures/apercu-reglages-depot.png)

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
- **Emplacements** : **où vivent** le dossier de travail et la base de données (`vigiechiro.db`). Voir
  la section [ci-dessous](#emplacements) : ce choix a des
  conséquences qu'il vaut la peine de connaître.
- **Fonctionnalités** : **activer ou désactiver** les fonctionnalités optionnelles de l'application
  (l'**import depuis Vigie-Chiro**, le **diagnostic** du capteur, la **préparation du dépôt**, la
  **vérification**, l'**importation Tadarida**, l'**analyse** « Espèces & observations » et la
  **recherche globale**). Désactiver une fonctionnalité retire son point d'entrée de l'interface.
  Contrairement aux autres réglages, ces bascules prennent effet **au prochain démarrage** de
  l'application (un bandeau le rappelle sur l'onglet).

![L'onglet Fonctionnalités des Réglages : un interrupteur par fonctionnalité optionnelle, chacun rappelant que la bascule prend effet au prochain démarrage.](../assets/captures/apercu-reglages-fonctionnalites.png)

Une modification est **enregistrée immédiatement** et **conservée d'une session à l'autre**. Seul
l'**effet** des bascules de l'onglet « Fonctionnalités » s'applique au **prochain démarrage**.

!!! note "Un réglage, parfois à deux endroits"
    Certains réglages restent aussi accessibles là où on en a besoin : la source des fiches espèces
    figure également dans le menu ☰. Les deux emplacements pilotent le **même** réglage et restent
    synchronisés.

    « Conserver les originaux » figurait aussi sur l'écran d'import ; il n'y est plus. C'est une option
    de ré-analyse, dont l'immense majorité des imports n'a pas à se soucier : elle vit désormais ici,
    où on la trouve quand on la cherche.

## Où l'application range ses données (onglet « Emplacements ») {#emplacements}

![L'onglet Emplacements des Réglages : le dossier de travail et la base de données, chacun avec son chemin courant, un bouton « Choisir… » et le défaut rappelé ; en tête, l'avertissement « choisir un emplacement ne déplace pas vos données ».](../assets/captures/apercu-reglages-emplacements.png)

L'onglet **Emplacements** décide **où vivent** deux choses :

- le **dossier de travail**, qui contient les sessions et leur audio ;
- la **base de données** (`vigiechiro.db`) : vos observations, vos validations, les liens avec Vigie-Chiro. C'est le **seul fichier irremplaçable** ; le reste se réimporte ou se recalcule.

Pour chacun, le chemin courant est affiché, un bouton **« Choisir… »** ouvre un sélecteur de dossier, et l'emplacement **par défaut** est rappelé. Un dossier que l'application ne peut pas utiliser (un fichier, un dossier non inscriptible) est **refusé au moment du choix**, avec la raison : vous ne le découvrez pas au prochain démarrage.

!!! warning "Changer un emplacement ne déplace pas vos données"
    Un emplacement est un **pointeur** : le changer dit à l'application **où aller lire au prochain démarrage**, il ne déplace rien. Si vous pointez la base vers un dossier **vide**, l'application y démarrera sur une base **neuve**, l'ancienne restant **intacte** à son ancien emplacement. Pour l'emporter, **copiez le fichier vous-même** avant de redémarrer. C'est le même principe que pour votre audio : l'application ne touche pas à vos fichiers à votre place.

Une fois le choix **appliqué**, un message rappelle qu'il vaudra **au prochain démarrage**, avec un bouton **« Quitter l'application »** pour redémarrer tout de suite. Un bouton **« Rétablir les emplacements par défaut »** annule un choix précédent.

!!! note "En ligne de commande"
    La commande `vigiechiro emplacements` fait la même chose sans l'interface : sans option elle **affiche** les emplacements, `--definir-travail` / `--definir-base` les **changent** (avec la même vérification), `--reinitialiser` les **rétablit**. Utile pour scripter une installation.

!!! info "Sous Flatpak, un dossier réseau demande une autorisation"
    La version Flatpak est volontairement limitée aux dossiers qu'elle a le droit de lire. Un disque externe ou une carte SD conviennent. Un **partage réseau** (NAS) ouvert depuis votre gestionnaire de fichiers, lui, est monté à un endroit que le bac à sable ne voit pas : pour y ranger vos données, il faut l'**autoriser** (`flatpak override --user --filesystem=xdg-run/gvfs`) ou utiliser la version `.deb`. La version Windows et le `.deb` n'ont pas cette limite.
