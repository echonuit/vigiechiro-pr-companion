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
ralenties dix fois (une séquence de 5 s devient 50 s à l'écoute, dans la bande audible). Un
enregistrement de plus de 5 s produit **plusieurs séquences**, chacune nommée avec l'**heure réelle de
son début** (ex. `…_225849`, `…_225854`, `…_225859`…) : c'est ce qui permet de retrouver, pour chaque
observation Tadarida, la séquence audio correspondante.

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

## Sécurités et cas particuliers

Au-delà du chemin nominal, l'import intègre plusieurs **garde-fous** qui vous protègent des erreurs
courantes. La plupart agissent en silence ; les autres vous **demandent confirmation** avant toute action
irréversible.

### Nuit déjà importée (doublon)

Si le passage que vous rattachez (même site, point, année et numéro de passage) a **déjà été importé**,
l'application le **détecte** et vous **demande confirmation** avant d'aller plus loin. Vous choisissez alors
d'**ignorer** la nuit (garder l'existant) ou de l'**écraser** (remplacer l'ancien import). L'écrasement est
**atomique** : soit le remplacement aboutit entièrement, soit rien n'est modifié, jamais un état
intermédiaire. Le rapport final distingue les enregistrements **importés**, **ignorés** et **rejetés**.

![Confirmation d'une nuit déjà importée : importer quand même comme nouveau passage, ou annuler.](../assets/captures/apercu-import-doublon.png)

Choisir d'**écraser** demande une **seconde confirmation** qui rappelle ce qui sera **définitivement
supprimé** (séquences, et le cas échéant les validations Tadarida déjà saisies) : l'action est irréversible.

![Seconde confirmation d'écrasement : rappel de la suppression définitive (séquences et validations) avant de confirmer.](../assets/captures/apercu-import-ecrasement.png)

### Reprise d'un import interrompu

Si un import a été **interrompu** (fenêtre fermée, coupure, annulation), il suffit de le **relancer** :
l'application reconnaît les fichiers déjà copiés et transformés et les **saute**, pour reprendre là où elle
s'était arrêtée au lieu de tout refaire.

### Import sans journal (mode dégradé)

Le **journal du capteur** enrichit l'inspection (série, dates, fréquence d'acquisition), mais **n'est pas
obligatoire** : si le dossier n'en contient pas, l'import reste possible en **mode dégradé**. Les contrôles
qui dépendent du journal sont simplement allégés ; vous restez responsable de vérifier que le dossier
correspond bien à la nuit attendue.

### Intégrité des fichiers et espace disque

Pendant la copie, l'application **vérifie l'intégrité** de chaque enregistrement (comparaison d'empreinte
entre l'original et la copie) pour écarter une copie corrompue. Si le **disque est plein**, un message
l'indique explicitement et les **fichiers temporaires sont purgés** pour ne pas laisser le disque encombré.

### Numéro de passage, préfixes et enregistrements déjà ralentis

- **Numéro de passage déjà pris** : au rattachement, si ce numéro est déjà utilisé sur ce point, un
  pré-contrôle vous en **avertit** avant l'import.
- **Fichiers déjà préfixés** : si les enregistrements portent déjà un préfixe Vigie-Chiro (nuit déjà
  renommée), l'application **ne le double pas** et **signale toute discordance** entre ce préfixe et le
  rattachement demandé.
- **Enregistrements déjà ralentis** : un fichier dont le son a **déjà** subi l'expansion temporelle ×10 est
  **rejeté** (avec explication dans le rapport), pour éviter une **double expansion** qui rendrait les
  fréquences dix fois trop basses. Importez toujours les **fichiers bruts** issus du capteur.
