# Référence par écran

Cette section décrit chaque écran de l'application, avec ses différents états. Elle complète le
[parcours métier](../parcours/index.md) : le parcours explique *quand* utiliser un écran, la
référence explique *en détail* ce que chaque écran propose.

| Écran | Rôle |
|---|---|
| [Accueil](accueil.md) | Point d'entrée vers les activités |
| [Recherche](recherche.md) | Sauter directement à un site, un point ou un passage, depuis n'importe quel écran |
| [Mes sites](sites.md) | Gérer les sites de suivi et leurs points d'écoute |
| [Passage](passage.md) | Pivot d'une nuit : statut, navigation, suppression |
| [Importation](importation.md) | Importer une nuit depuis la carte SD |
| [Qualification](qualification.md) | Écouter les séquences et poser un verdict de qualité |
| [Préparer le dépôt](lot.md) | Préparer et déposer une nuit vérifiée |
| [Sons & validation](validation.md) | Relire les observations Tadarida (espèces), écouter, discuter avec le validateur |
| [Carte & passages](multisite.md) | Vue agrégée des passages (tri, filtres, vues sauvegardées) |
| [Ma saison](saison.md) | Solde de la saison : ce qu'il reste à faire, point par point |
| [Espèces & observations](analyse.md) | Exploiter les observations toutes nuits confondues : quelles espèces, où, quand, combien |
| [Activité de la nuit](activite.md) | La courbe des contacts heure par heure et par espèce, sur l'axe de la nuit |
| [Synthèse de la nuit](synthese.md) | Ce que chaque espèce a produit, et ce que ce nombre vaut au regard du référentiel national |
| [Diagnostic](diagnostic.md) | Diagnostic d'une nuit (climat, anomalies) |
| [Audit de cohérence](audit.md) | Confronter disque, base et Vigie-Chiro : plus rien ne diverge en silence |
| [Réglages](reglages.md) | Préférences de l'application, par domaine (menu principal (☰)) |

L'écran **Qualification** propose en plus des [raccourcis clavier](../raccourcis-clavier.md) dédiés
(verdict, écoute, navigation) pour traiter les séquences rapidement.

Chaque écran ci-dessus dispose de sa **fiche détaillée** (son nom est un lien), illustrée par les
captures de ses différents états.

## Quitter un écran en cours de saisie

Un garde-fou **transverse** vous protège des pertes accidentelles : si vous tentez de **quitter un écran
où une saisie n'est pas enregistrée**, l'application **demande confirmation** avant de partir. Vous pouvez
annuler pour revenir enregistrer, ou confirmer pour quitter en abandonnant les modifications.

![Confirmation avant de quitter un écran avec des modifications non enregistrées.](../assets/captures/apercu-navigation-garde-saisie.png)

## Sauvegarder et restaurer la base

Tout votre travail (sites, points, passages, observations) vit dans une **base locale**. Le menu **« ☰ »**
de la barre du haut permet de la **protéger** :

- **Sauvegarder la base…** : vous choisissez un **dossier** (un disque externe, par exemple) et
  l'application y écrit une **copie horodatée** et cohérente de la base. À faire régulièrement, et avant
  toute manipulation importante.
- **Sauvegarde complète (base + audio)…** : la base **et** tous vos dossiers de son. C'est la **seule
  sauvegarde qui protège vraiment** : voir l'encadré ci-dessous. Elle peut peser plusieurs gigaoctets et
  prendre du temps : l'application vous le dit avant de commencer.
- **Restaurer une sauvegarde…** : vous choisissez un fichier de sauvegarde ; après **confirmation**,
  l'application **remplace** la base courante par celle-ci. Par sécurité, l'**état courant est d'abord mis
  de côté** (fichier `vigiechiro.db.avant-restauration`), et l'application revient à l'accueil pour
  repartir sur la base restaurée.
- **Restaurer une sauvegarde complète…** : remet la base **et** les dossiers de son, **là où ils
  étaient**. Si un disque n'est pas branché, les dossiers qu'il portait sont placés dans votre dossier
  de travail et la base est corrigée pour les y retrouver : elle ne désigne jamais un dossier absent.
  L'application vous dit ce qui a changé de place, et ce que la sauvegarde ne contenait pas.

!!! warning "Une sauvegarde contient vos localisations, en clair"
    Une sauvegarde n'est **pas chiffrée** : elle porte la base - donc les **coordonnées de vos points
    d'écoute** - et, pour la sauvegarde complète, vos enregistrements. C'est ce qui la rend utile :
    lisible par une autre installation de l'application, sans mot de passe à retrouver le jour où tout
    le reste a échoué.

    Rangez-la en conséquence : un disque que vous gardez vaut mieux qu'un dossier synchronisé sur un
    service en ligne. La localisation précise des gîtes d'espèces protégées ne se diffuse pas.

    L'application vous le rappelle au moment d'écrire : la sauvegarde complète le dit **avant** de
    copier, dans sa demande de confirmation, et la sauvegarde de la base le dit dans son compte rendu.

!!! tip "Une restauration complète vérifie avant de toucher à quoi que ce soit"
    Chaque dossier de la sauvegarde est confronté à ce qu'elle annonce contenir. Une seule
    discordance, et la restauration s'arrête **avant** d'avoir remplacé la base ou écrasé le moindre
    fichier : mieux vaut une restauration qui refuse qu'une restauration à moitié faite.

    Trois refus peuvent se produire, et dans les trois cas **rien n'a été touché** :

    - « **Cette sauvegarde a été écrite par une version plus récente de l'application** » : mettez
      l'application à jour, puis recommencez. Sa base contient des informations que cette version ne
      sait pas lire.

    ![Le refus d'une sauvegarde écrite par une version plus récente : rien n'a été touché.](../assets/captures/apercu-restauration-version-trop-recente.png)
    - « **La mise à jour de son schéma a échoué** » : la sauvegarde a bien été remise en place, mais
      l'application n'a pas su la mettre à jour, et **votre base d'avant a été rétablie**. Essayez une
      autre sauvegarde.
    - « **Il n'y a pas assez de place** » : la restauration a besoin d'un peu plus d'espace que ce que
      pèsent vos nuits, le temps de les remettre en place sans risque. Le message dit **combien
      libérer et où** ; vous pouvez aussi restaurer vers un autre emplacement.

!!! tip "« Sauvegarde restaurée, à un détail près »"
    Une restauration peut aboutir **et** vous demander un regard. C'est ce que dit ce titre, et le
    compte rendu qui l'accompagne nomme précisément ce qui a bougé. Trois cas :

    - **une nuit a changé de place.** Vos dossiers de son ne sont pas forcément là où ils étaient
      quand la sauvegarde a été prise. L'application les replace et vous dit lesquels : rien n'est
      perdu, mais le chemin a changé.
    - **une nuit manque à la sauvegarde.** Elle existait dans la base restaurée, ses sons n'étaient
      pas dans l'archive. La nuit revient, son audio non - c'est le cas à regarder en premier.
    - **la sauvegarde est trop ancienne** pour porter la liste de ses dossiers. L'application ne peut
      alors rien replacer, et vous le dit plutôt que de deviner.

    ![Une restauration qui aboutit et demande un regard : les nuits déplacées sont nommées une par une.](../assets/captures/apercu-restauration-nuits-deplacees.png)

    Dans les trois cas la base est restaurée. Ce qui est signalé porte sur les **sons**, pas sur vos
    métadonnées.

!!! tip "Quand la place est juste, l'application remet vos nuits une par une"
    Pour remettre vos dossiers de son sans jamais laisser un dossier à moitié écrit, l'application les
    recopie **à côté** de leur place, puis les y fait basculer d'un coup. Cela demande un peu de place
    en plus, le temps de l'opération.

    Quand cette place manque, elle ne refuse pas : elle procède **une nuit à la fois**, et vous le dit
    dans le compte rendu. Chaque nuit reste complète ; ce qui change est qu'une coupure de courant en
    plein milieu laisserait les premières remises en place et pas les dernières - au lieu de tout
    laisser dans l'état d'avant.

    Elle ne refuse que s'il n'y a même pas la place pour **une seule** nuit, et vous dit alors combien
    libérer.

    ![Une restauration menée une nuit à la fois : le compte rendu dit ce que la garantie a perdu.](../assets/captures/apercu-restauration-une-nuit-a-la-fois.png)

    ![Le refus faute de place : combien libérer, où, et rien n'a été touché.](../assets/captures/apercu-restauration-place-insuffisante.png)

## Ces fichiers que l'application dépose sans qu'on les lui demande

Vous croiserez trois fichiers que vous n'avez pas créés. Aucun n'est un déchet, et aucun ne doit être
supprimé pendant que l'application tourne.

| Fichier | Quand il apparaît | À quoi il sert |
|---|---|---|
| `sauvegardes/vigiechiro-avant-migration-V39.db` | après une mise à jour de l'application, quand la base doit évoluer | une copie de votre base **juste avant** l'évolution. Si la mise à jour vous déplaît, c'est celle-là qu'on restaure |
| `vigiechiro.db.avant-restauration` | à chaque restauration | l'état d'avant, mis de côté au cas où vous vous seriez trompé de sauvegarde |
| `.verrou` | tant que l'application est ouverte | il empêche une **seconde fenêtre** d'écrire dans la même base en même temps |

!!! note "Les fichiers « avant-migration » s'accumulent, et l'application vous dit combien"
    Un par évolution de la base, et l'application ne les supprime jamais : ils sont votre filet, elle
    n'a pas à décider à votre place quand vous n'en avez plus besoin. Sur une grosse base, pensez à
    faire le ménage de temps en temps, en gardant les plus récents.

    Encore faut-il savoir ce qu'il y a à ranger. **☰ → Restaurer une sauvegarde** ouvre désormais la
    liste de ce que le dossier contient - date, taille, nature de chacune, et le **total occupé** -
    au lieu d'un sélecteur de fichiers où l'on choisissait un `.db` sans rien savoir de lui.
    « Parcourir… » reste là pour une sauvegarde rangée ailleurs.

    ![Choisir une sauvegarde à restaurer : date, taille, nature et total.](../assets/captures/apercu-restauration-choix-sauvegarde.png)

    En ligne de commande, `vigiechiro lister-sauvegardes` donne la même chose, et
    `vigiechiro supprimer-sauvegarde --nom <nom> --confirmer` fait le ménage. Sans `--confirmer`, la
    commande vous dit ce qui serait perdu et ne touche à rien.

!!! warning "« VigieChiro Companion est déjà ouvert »"
    L'application refuse de démarrer une **seconde fois** sur le même dossier de travail. Ce n'est pas
    une précaution excessive : deux fenêtres qui écrivent la même base la corrompent, et vous ne vous
    en apercevriez que bien plus tard.

    Le message nomme le processus qui occupe la place. Fermez l'autre fenêtre, puis relancez. Si
    l'application a été fermée brutalement, le verrou est libéré tout seul : il n'y a rien à
    supprimer à la main.

    ![Le refus au démarrage : le dossier de travail est déjà ouvert, et le message nomme le processus qui l'occupe.](../assets/captures/apercu-demarrage-dossier-occupe.png)

!!! warning "La sauvegarde de la base seule ne protège pas vos sons"
    La base contient vos **métadonnées** (sites, nuits, observations, validations) : pas l'**audio**, qui
    vit dans des dossiers à côté.

    Et Vigie-Chiro ne vous rendra **pas** vos sons : une nuit déposée **en archives** (le mode par défaut)
    ne laisse **aucun** fichier audio sur la plateforme. Si le disque les perd, ils sont **perdus**. La nuit
    reste consultable (observations, vérifications) mais **muette**.

    Avant toute manipulation risquée, faites donc une **sauvegarde complète**. Et si un dossier de son
    n'est pas accessible au moment de la copie (carte SD non montée, disque débranché), l'application vous
    le **dit** : une sauvegarde qu'on croit complète et qui ne l'est pas vaut moins que pas de sauvegarde
    du tout.

## Repartir d'une base neuve

Il arrive qu'on veuille **tout reprendre à zéro** : base corrompue, expérimentation qui a mal tourné,
poste que l'on veut remettre à neuf. Le menu **« ☰ » → « Repartir d'une base neuve… »** mène cette
procédure de bout en bout : et surtout, **refuse de la commencer** si elle vous ferait perdre quelque
chose sans que vous l'ayez voulu.

Elle se déroule en trois temps.

1. **L'application regarde ce que vous perdriez.** Nuit par nuit, elle établit d'où reviendrait l'audio :
   du **disque** (vos fichiers sont là), du **serveur** (la nuit a été déposée en WAV), ou de **nulle
   part**.
2. **Elle vous le montre, et vous décidez.** La confirmation **énumère les nuits** dont l'audio ne
   reviendra pas. C'est en cliquant « oui » sur **ce texte-là** que vous acceptez la perte : pas sur un
   « êtes-vous sûr ? » anonyme.
3. **Elle exécute** : sauvegarde complète → base neuve → tout ce que Vigie-Chiro connaît de vous est
   **retéléchargé** (sites, points, nuits, observations) → audit final. Puis l'application **se ferme** :
   relancez-la pour repartir sur la base neuve.

!!! danger "Deux refus, avant toute destruction"
    - **Si une nuit perdrait son audio** et que vous ne l'avez pas explicitement accepté, la procédure
      **s'arrête** : sans rien toucher.
    - **Si Vigie-Chiro ne répond pas**, elle s'arrête **aussi**, même si vous avez accepté la perte : la
      base neuve se **remplit depuis la plateforme**. La vider alors que le serveur est injoignable vous
      laisserait un poste **vide**.

    Dans les deux cas, **rien n'a été modifié**. Un refus n'est pas une panne à mi-chemin.

Ce qui **revient toujours** : sites, points, nuits, observations, avis des validateurs. Ce qui **ne
revient pas tout seul** : l'**audio**. L'application **nomme les nuits** concernées à la fin, pour que vous
sachiez exactement quels fichiers rebrancher (réimportez-les depuis vos disques, ou depuis votre
sauvegarde complète).

## Récupérer de l'espace disque

Ce qui pèse, ce sont les **enregistrements d'origine** (les fichiers « bruts »), qui peuvent atteindre
**plusieurs gigaoctets par nuit**. Ils **ne servent pas** à l'écoute ni à la validation, lesquelles
s'appuient sur les séquences transformées.

L'application ne conserve **plus** ces copies par défaut : décocher « Conserver les originaux » à
l'[import](importation.md) reste la façon la plus simple de ne pas remplir son disque. Pour l'espace
déjà occupé, supprimez les sous-dossiers `bruts/` avec votre gestionnaire de fichiers - **vous** êtes
maître de vos fichiers, l'application n'en efface aucun.
