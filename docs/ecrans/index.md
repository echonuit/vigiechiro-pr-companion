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
