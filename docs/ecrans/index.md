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
| [Réglages](reglages.md) | Préférences de l'application, par domaine (menu ☰) |

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
- **Restaurer une sauvegarde complète…** : remet la base **et** les dossiers de son.

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
