# Référence par écran

Cette section décrit chaque écran de l'application, avec ses différents états. Elle complète le
[parcours métier](../parcours/index.md) : le parcours explique *quand* utiliser un écran, la
référence explique *en détail* ce que chaque écran propose.

| Écran | Rôle |
|---|---|
| [Accueil](accueil.md) | Point d'entrée vers les activités |
| [Sites](sites.md) | Gérer les sites de suivi et leurs points d'écoute |
| [Passage](passage.md) | Pivot d'une nuit : statut, navigation, suppression |
| [Importation](importation.md) | Importer une nuit depuis la carte SD |
| [Qualification](qualification.md) | Écouter les séquences et poser un verdict de qualité |
| [Lot](lot.md) | Préparer et déposer un lot vérifié |
| [Validation](validation.md) | Relire les observations Tadarida (espèces) |
| [Multisite](multisite.md) | Vue agrégée des passages (tri, filtres, vues sauvegardées) |
| [Diagnostic](diagnostic.md) | Diagnostic d'une nuit (climat, anomalies) |
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
- **Restaurer une sauvegarde…** : vous choisissez un fichier de sauvegarde ; après **confirmation**,
  l'application **remplace** la base courante par celle-ci. Par sécurité, l'**état courant est d'abord mis
  de côté** (fichier `vigiechiro.db.avant-restauration`), et l'application revient à l'accueil pour
  repartir sur la base restaurée.

## Récupérer de l'espace disque : purger les originaux

À chaque import, l'application conserve par défaut une copie des **enregistrements d'origine** (les
fichiers « bruts »). Ils constituent une archive de sécurité mais **ne servent pas** à l'écoute ni à la
validation (celles-ci s'appuient sur les séquences transformées) et peuvent peser **plusieurs gigaoctets
par nuit**.

Le menu **« ☰ » → « 🧹 Purger les originaux importés… »** supprime ces fichiers « bruts » **pour toutes
les nuits** afin de libérer de l'espace. L'application **annonce l'espace récupérable** et demande
**confirmation** avant de supprimer ; les **séquences d'écoute, les validations et les dépôts sont
conservés**. Cette suppression est **définitive**.

> Pour ne purger qu'**une seule nuit**, utilisez le bouton « Purger les originaux » de sa fiche
> (voir [Passage](passage.md)). Et pour ne **plus jamais** conserver les originaux, décochez « Conserver
> les originaux » lors de l'[import](importation.md).
