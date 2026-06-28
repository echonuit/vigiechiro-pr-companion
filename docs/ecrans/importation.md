# Importation

L'écran **Importation** copie une nuit depuis la carte SD, la renomme et la transforme en séquences
écoutables. Il prend la forme d'un **assistant en trois temps**.

## L'assistant d'import

![L'assistant d'import : dossier source, inspection, rattachement.](../assets/captures/apercu-import-assistant.png)

1. **Dossier source** : désignez le dossier de la carte SD (ou une copie déjà sur disque).
2. **Inspection** (lecture seule) : l'application détecte le journal du capteur, le relevé climatique
   et les enregistrements WAV, et annonce ce qu'elle va renommer. **Aucun fichier de la carte n'est
   modifié à ce stade.**
3. **Rattachement** : indiquez le site, le point d'écoute, l'année et le numéro de passage ; un
   aperçu montre le préfixe qui sera appliqué. Une **carte de confirmation** (lecture seule) affiche le
   **carré du site** et **ses points**, le **point choisi en surbrillance** (indigo) et les autres en
   gris : un coup d'œil pour vérifier qu'on rattache la nuit au bon endroit.

Le bouton **Importer cette nuit** lance la copie (sans toucher aux originaux), le renommage avec le
préfixe `CarXXXXXX-AAAA-PassN-YY-`, puis la transformation des enregistrements en séquences de 5 s
ralenties dix fois.

## Source compressée (.zip)

Vous pouvez aussi désigner une **archive `.zip`** (ou la glisser-déposer) plutôt qu'un dossier :
l'application la **décompresse** d'abord, avec une barre de progression et un bouton « Annuler »,
avant de poursuivre l'inspection comme pour un dossier ordinaire.

![Décompression d'une archive .zip choisie comme source : progression et annulation.](../assets/captures/apercu-import-decompression.png)

## L'inspection vous alerte

L'inspection signale les anomalies **avant** l'import, pour éviter d'importer une mauvaise nuit.

Un **mélange** dans le dossier (plusieurs enregistreurs aux séries différentes, ou plusieurs nuits
aux dates non consécutives, alors qu'un dossier ne devrait contenir qu'une seule nuit d'un seul
enregistreur) déclenche un avertissement, sans bloquer l'import :

![Cas « mélange » : un avertissement signale que le dossier contient plusieurs enregistreurs ou plusieurs nuits.](../assets/captures/apercu-import-melange.png)

Une **incohérence** entre le journal du capteur et les enregistrements (série ou date qui ne
correspondent pas) est signalée plus fermement :

![Cas « incohérence » : le journal ne correspond pas aux enregistrements (série et date).](../assets/captures/apercu-import-incoherence.png)

Dans les deux cas, l'import reste possible : à vous de vérifier que le dossier correspond bien à une
seule et même nuit avant de continuer.

## Pendant l'import

Une fois lancé, l'import affiche une **barre de progression** (copie puis transformation) et **gèle
le formulaire** le temps de l'opération.

![Import en cours : barre de progression, formulaire gelé.](../assets/captures/apercu-import-en-cours.png)

## Rapport d'import

L'import est **résilient** : un fichier illisible ou de format invalide n'interrompt pas toute la nuit.
Les enregistrements exploitables sont importés, et un **rapport** récapitule à la fin ce qui a été
importé, **ignoré** (fichier non pertinent) ou **rejeté** (avec la raison). Les fichiers rejetés sont
listés directement sous le message de fin d'import.

![Import terminé avec rapport : la liste des fichiers rejetés et leur raison s'affiche sous le message de succès.](../assets/captures/apercu-import-rejets.png)
