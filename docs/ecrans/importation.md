# Importation

L'écran **Importation** importe une nuit depuis la carte SD, la renomme et la transforme en séquences
écoutables, **sans jamais modifier vos fichiers d'origine**. Il prend la forme d'un **assistant en
trois temps**.

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

Le bouton **Importer cette nuit** applique aux enregistrements le préfixe
`CarXXXXXX-AAAA-PassN-YY-`, puis les transforme en séquences de 5 s ralenties dix fois (une séquence
de 5 s devient 50 s à l'écoute, dans la bande audible). Un enregistrement de plus de 5 s produit
**plusieurs séquences**, chacune nommée avec l'**heure réelle de son début** (ex. `…_225849`,
`…_225854`, `…_225859`…) : c'est ce qui permet de retrouver, pour chaque observation Tadarida, la
séquence audio correspondante.

## Conserver ou non les originaux (espace disque)

Par défaut, l'import **conserve une copie des enregistrements d'origine** dans un dossier `bruts` de la
session (les fichiers de la carte SD, eux, ne sont jamais modifiés). Ces originaux ne servent **pas** à
l'écoute ni à la validation : celles-ci s'appuient sur les **séquences transformées**. Ils constituent
une archive de sécurité, qui peut peser **plusieurs gigaoctets par nuit**.

La case **« Conserver les originaux sur le disque »**, sous le bouton d'import, permet de **désactiver**
cette copie :

- **cochée** (par défaut) : les originaux sont copiés dans `bruts`, comme précédemment ;
- **décochée** : les enregistrements sont transformés **directement depuis la carte SD**, sans copie
  intermédiaire. Le dossier `bruts` n'est pas créé, ce qui **économise l'espace disque**. Les séquences
  d'écoute et le journal sont produits normalement ; seuls les fichiers d'origine ne sont pas archivés.

Votre choix est **mémorisé** d'un import à l'autre. Décochez cette case si l'espace disque devient
limité au fil des nuits importées.

## Source compressée (.zip)

Vous pouvez aussi désigner une **archive `.zip`** (ou la glisser-déposer) plutôt qu'un dossier :
l'application la **décompresse** d'abord, avec une barre de progression et un bouton « Annuler »,
avant de poursuivre l'inspection comme pour un dossier ordinaire.

![Décompression d'une archive .zip choisie comme source : progression et annulation.](../assets/captures/apercu-import-decompression.png)

## L'inspection vous alerte

L'inspection signale les anomalies **avant** l'import, pour éviter d'importer une mauvaise nuit.

Un **mélange** dans le dossier (plusieurs enregistreurs aux séries différentes) déclenche un
avertissement, sans bloquer l'import :

![Cas « mélange » : un avertissement signale que le dossier contient plusieurs enregistreurs (séries différentes).](../assets/captures/apercu-import-melange.png)

Une **incohérence** entre le journal du capteur et les enregistrements (série ou date qui ne
correspondent pas) est signalée plus fermement :

![Cas « incohérence » : le journal ne correspond pas aux enregistrements (série et date).](../assets/captures/apercu-import-incoherence.png)

Dans les deux cas, l'import reste possible : à vous de vérifier que le dossier correspond bien à ce
que vous attendez avant de continuer. Le cas de **plusieurs nuits** d'un même enregistreur, lui, n'est
pas un simple avertissement : il est **pris en charge** par le découpage décrit ci-dessous.

## Plusieurs nuits sur une même carte

Si vous laissez l'enregistreur tourner **plusieurs nuits** d'affilée (jusqu'à saturation de la carte),
le dossier contient les enregistrements de **N nuits**. Un passage Vigie-Chiro correspondant à **une
seule** nuit, l'inspection **détecte les nuits** et propose de **découper l'import** : chaque nuit
donnera un **passage distinct** (au même point, avec des **numéros de passage consécutifs** et la
**date propre** de chaque nuit).

Une **table des nuits** apparaît alors, une ligne par nuit :

![Plusieurs nuits détectées : la table liste chaque nuit (inclure, date, nombre de fichiers, état, n° de passage proposé) ; chacune deviendra un passage distinct.](../assets/captures/apercu-import-multi-nuits.png)

- **Importer** : case à cocher (cochée par défaut). Décochez une nuit pour ne pas l'importer ; les
  numéros de passage proposés se **renumérotent** automatiquement pour rester consécutifs.
- **Nuit du** : date du soir de la nuit (date du futur passage).
- **Fichiers** : nombre d'enregistrements de la nuit.
- **État** : **complète** ou **incomplète**. Une nuit est signalée **incomplète** quand le journal
  montre qu'elle s'est arrêtée anormalement (carte SD pleine, interruption). Elle reste **incluse par
  défaut** — à vous de décider de la conserver (pour la faire traiter par Tadarida) ou de la décocher.
- **Passage n°** : numéro attribué à la nuit, **auto-numéroté** à partir du prochain numéro libre du
  point (« — » si la nuit est décochée).
- La mention **« déjà importée »** rappelle qu'un passage existe déjà en base pour cette nuit.

Les numéros sont proposés **automatiquement** (consécutifs depuis le prochain libre) ; le bouton
**Importer** reste indisponible tant qu'aucune nuit n'est cochée ou qu'un numéro proposé est déjà pris.
Chaque nuit incluse est importée **indépendamment** (une transaction par nuit) : si l'une échoue, les
nuits déjà importées demeurent. À la fin, le récapitulatif indique le **nombre de passages créés** et
la **plage de dates** couverte.

Une carte ne contenant **qu'une seule** nuit est importée comme avant, sans table (le parcours
mono-nuit est inchangé).

## Pendant l'import

Une fois lancé, l'import affiche une **barre de progression** (avec l'estimation du temps restant) et
**gèle le formulaire** le temps de l'opération. En dessous, une **table de suivi par fichier** montre
où en est chaque enregistrement : en attente, en cours (avec l'étape — copie puis transformation),
terminé, ou **rejeté** avec la raison au survol. La copie et la transformation travaillent **en
parallèle** sur plusieurs fichiers à la fois. En import multi-nuits, la table repart à chaque nuit.

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

Choisir d'**écraser** demande **deux** confirmations. La première pose le principe : ce numéro de passage
est déjà pris, voulez-vous remplacer la nuit existante ?

![Première confirmation d'écrasement : le numéro de passage est déjà utilisé, remplacer la nuit existante ?](../assets/captures/apercu-import-ecrasement-principe.png)

La seconde rappelle ce qui sera **définitivement supprimé** (les séquences, et le cas échéant les
validations Tadarida déjà saisies) : l'action est irréversible.

![Seconde confirmation d'écrasement : rappel de la suppression définitive (séquences et validations) avant de confirmer.](../assets/captures/apercu-import-ecrasement.png)

Par sécurité, une **sauvegarde automatique de la base est écrite juste avant l'écrasement** (dans
`<workspace>/sauvegardes`) : si cette sauvegarde échoue, l'écrasement **n'a pas lieu**. Vous pouvez donc
toujours revenir en arrière via **☰ → Restaurer une sauvegarde…** (voir [Sauvegarder et restaurer la
base](index.md#sauvegarder-et-restaurer-la-base)).

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
