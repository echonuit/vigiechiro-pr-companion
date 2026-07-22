# FAQ et dépannage

Les questions les plus fréquentes lors de l'utilisation de VigieChiro Companion.

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

### Les boutons de l'écran Préparer le dépôt sont grisés

L'écran signale des **alertes de cohérence** à corriger avant de préparer le dépôt (par exemple des
séquences d'écoute manquantes ou un journal du capteur absent). Tant que ces points ne sont pas
réglés, la préparation reste bloquée. Voir l'écran [Préparer le dépôt](ecrans/lot.md).

### Quel dossier dois-je téléverser sur Vigie-Chiro ?

Celui indiqué dans le **récapitulatif** de l'écran Préparer le dépôt après avoir cliqué sur « Vérifier et préparer le dépôt ».
Le téléversement est **manuel** : vous l'effectuez depuis votre navigateur, puis vous revenez
marquer la nuit comme déposée. Voir l'écran [Préparer le dépôt](ecrans/lot.md).

## Validation des espèces

### L'action « Sons & validation » est grisée

La validation des espèces n'est accessible qu'**une fois la nuit déposée** : Vigie-Chiro ne renvoie
les résultats d'identification que 24 à 48 h après le dépôt. L'ordre est donc Vérifier, puis
Déposer, puis Valider. Voir le [Parcours métier](parcours/index.md).

### Où récupérer les résultats Tadarida ?

Sur la plateforme Vigie-Chiro, 24 à 48 h après le dépôt : vous y téléchargez un fichier CSV de
résultats, que vous **importez** ensuite dans l'écran [Validation](ecrans/validation.md).

## Espace disque et archives

### Mon disque est plein : que puis-je supprimer sans rien perdre d'important ?

**Les « bruts »** (les enregistrements d'origine) sont ce qui pèse le plus lourd, et ils ne servent
ni à l'écoute ni à la validation : celles-ci s'appuient sur les séquences transformées. Vous pouvez
donc les supprimer **vous-même**, avec votre gestionnaire de fichiers, sans conséquence sur votre
travail.

L'application ne les conserve d'ailleurs **plus par défaut** : sur vos imports récents, il n'y a
probablement rien à supprimer. Voir [Importer une nuit](ecrans/importation.md).

!!! note "L'application ne supprime jamais vos fichiers"
    Vous en gardez la maîtrise : il n'y a pas de bouton qui efface votre audio. Si vous rangez ou
    effacez des fichiers, l'application s'en aperçoit et le dit, sans jamais crier à la corruption.

### L'audio d'une nuit a disparu, puis-je le récupérer ?

Oui, **si vous avez encore les fichiers** : le bouton **Réactiver ce passage** les rebranche depuis un
dossier que vous désignez, après avoir **vérifié** que ce sont bien les mêmes (voir
[Passage](ecrans/passage.md)). C'est la marche à suivre quand vous avez déplacé vos dossiers ou changé
de point de montage.

Vous n'êtes pas obligé de les rapatrier : l'application demande si elle doit les **copier** ou les
**laisser où ils sont**. Sur un serveur de fichiers ou un disque externe, la seconde réponse évite un
doublon, au prix d'une nuit muette quand le support n'est pas branché.

En revanche, **la plateforme Vigie-Chiro ne vous rendra pas cet audio** si votre dépôt a été fait au
format ZIP (le mode par défaut) : le serveur n'en conserve pas de copie téléchargeable. Sans vos
fichiers d'origine, la perte est **définitive** — c'est exactement ce que la confirmation vous
rappelle.

### L'audit de cohérence signale-t-il un audio absent comme une erreur ?

Non. Un audio absent est un **état**, pas une corruption : vous êtes maître de vos fichiers, et
l'application n'a pas à s'alarmer que vous les ayez rangés ailleurs. Le passage apparaît en simple
**information**, avec le décompte des séquences encore présentes. Pour le réécouter, désignez le
dossier où ils se trouvent désormais : voir **Réactiver ce passage**.

## Divers

### Où mes données sont-elles stockées ?

Dans une **base de données locale** sur votre ordinateur. L'application ne dialogue avec aucun
serveur : les échanges avec Vigie-Chiro (dépôt, récupération des résultats Tadarida) se font
**manuellement** via votre navigateur.

### Comment écouter une séquence ?

La **vue audio** (sonogramme et spectrogramme) est présente dans les écrans de
[Qualification](ecrans/qualification.md) et de [Validation](ecrans/validation.md) (écoute, validation
et corpus de sons de référence). La barre <kbd>Espace</kbd> lance ou met en pause la lecture
(voir les [Raccourcis clavier](raccourcis-clavier.md)).

Le **niveau sonore est normalisé** automatiquement : les cris de faible et de forte amplitude sont
ramenés à un volume comparable, pour les écouter et les comparer sans réajuster le volume à chaque
séquence.

### L'application a eu un comportement inattendu, comment le signaler ?

Deux entrées du menu **☰** préparent un signalement utile.

![Le menu ☰ déployé : sauvegardes, purge, journaux, « À propos », réglages et connexion.](assets/captures/apercu-menu-outils.png)

**☰ → « À propos »** donne la **version** que vous utilisez, ainsi que votre système et votre dossier
de travail. Sans la version, un défaut est difficile à reproduire : la même manipulation peut très
bien fonctionner sur une version voisine.

**☰ → « Ouvrir le dossier des journaux »** ouvre le dossier `logs/` de votre espace de travail :
joignez le fichier `vigiechiro-*.log` le plus récent. L'application y tient un **journal** de son
activité et de ses erreurs, qui aide à comprendre ce qui s'est passé même quand l'erreur n'affichait
aucun message à l'écran.
