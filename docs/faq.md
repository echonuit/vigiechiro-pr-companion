# FAQ et dépannage

Les questions les plus fréquentes lors de l'utilisation de VigieChiro.

## Installation et lancement

### L'application affiche un avertissement de sécurité à l'ouverture

Les installeurs ne sont pas signés : votre système peut afficher un avertissement à la première
ouverture (Gatekeeper sur macOS, SmartScreen sur Windows). Autorisez l'application pour continuer.
Voir la [Prise en main](prise-en-main.md).

## Sites et import

### Je n'arrive pas à importer une nuit

Vous devez d'abord **déclarer un site** : un carré Vigie-Chiro et au moins un point d'écoute. Tant
qu'aucun site n'existe, l'import n'est pas possible. Voir l'écran [Sites](ecrans/sites.md).

### Les fichiers de ma carte SD risquent-ils d'être modifiés ?

Non. L'import **copie** les fichiers sans jamais toucher aux originaux de la carte ; ce sont les
copies qui sont renommées et transformées. Voir l'écran [Importation](ecrans/importation.md).

### L'import affiche un avertissement « mélange » ou « incohérence »

Ce sont des **avertissements**, pas des blocages. « Mélange » signale plusieurs enregistreurs ou
plusieurs nuits dans le dossier ; « incohérence » signale que le journal du capteur ne correspond
pas aux enregistrements. Vérifiez que le dossier correspond bien à une seule nuit avant de
continuer : l'import reste possible. Voir l'écran [Importation](ecrans/importation.md).

## Vérification et dépôt

### Les boutons de l'écran Lot sont grisés

L'écran signale des **alertes de cohérence** à corriger avant de préparer le lot (par exemple des
séquences d'écoute manquantes ou un journal du capteur absent). Tant que ces points ne sont pas
réglés, la préparation reste bloquée. Voir l'écran [Lot](ecrans/lot.md).

### Quel dossier dois-je téléverser sur Vigie-Chiro ?

Celui indiqué dans le **récapitulatif** de l'écran Lot après avoir cliqué sur « Préparer le lot ».
Le téléversement est **manuel** : vous l'effectuez depuis votre navigateur, puis vous revenez
marquer la nuit comme déposée. Voir l'écran [Lot](ecrans/lot.md).

## Validation des espèces

### L'action « Validation Tadarida » est grisée

La validation des espèces n'est accessible qu'**une fois la nuit déposée** : Vigie-Chiro ne renvoie
les résultats d'identification que 24 à 48 h après le dépôt. L'ordre est donc Vérifier, puis
Déposer, puis Valider. Voir le [Parcours métier](parcours/index.md).

### Où récupérer les résultats Tadarida ?

Sur la plateforme Vigie-Chiro, 24 à 48 h après le dépôt : vous y téléchargez un fichier CSV de
résultats, que vous **importez** ensuite dans l'écran [Validation](ecrans/validation.md).

## Divers

### Où mes données sont-elles stockées ?

Dans une **base de données locale** sur votre ordinateur. L'application ne dialogue avec aucun
serveur : les échanges avec Vigie-Chiro (dépôt, récupération des résultats Tadarida) se font
**manuellement** via votre navigateur.

### Comment écouter une séquence ?

La **vue audio** (sonogramme et spectrogramme) est présente dans les écrans de
[Qualification](ecrans/qualification.md), de [Validation](ecrans/validation.md) et de la
[Bibliothèque](ecrans/bibliotheque.md). La barre <kbd>Espace</kbd> lance ou met en pause la lecture
(voir les [Raccourcis clavier](raccourcis-clavier.md)).

Le **niveau sonore est normalisé** automatiquement : les cris de faible et de forte amplitude sont
ramenés à un volume comparable, pour les écouter et les comparer sans réajuster le volume à chaque
séquence.
