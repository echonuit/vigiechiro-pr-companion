## Context

`ServiceLot.genererArchivesDepot` prend toutes les séquences de la session et les passe au compacteur. Le plan
de dépôt (`depot_unite`) porte une unité par séquence déposée en WAV, avec son statut. La règle du repli, elle,
se lit déjà dans ce plan (`DepotUniteDao.sequencesRefuseesSansRecours`).

## Goals / Non-Goals

**Goals** : une archive du repli ne rend pas au serveur un son qu'il a déjà.

**Non-Goals** : toucher au compacteur, au dépôt en archives ZIP et à sa régénération, au bilan de
récupérabilité, ni à ce que « Reprendre le dépôt » fait après un dépôt à la main.

## Decisions

**Filtrer à la génération, par le plan.** Les séquences écartées sont celles dont l'unité du plan est de type
séquence et de statut déposé. Écarté : filtrer dans le compacteur, qui ne connaît que des fichiers et sert aussi
le dépôt en archives ; et interroger la plateforme, que la génération ne joint pas et qui peut être hors
d'atteinte au moment du repli.

**La règle tient à la forme du plan, pas à l'offre du repli.** Toute génération sur un dépôt entamé en
séquences l'applique, que la carte du repli soit affichée ou que l'on passe par `exporter-lot`. Une règle qui
dépendrait de l'écran laisserait la commande rendre des doublons.

**Un dépôt entamé en archives se génère en entier.** Ses unités sont des archives, pas des séquences : le plan
ne dit pas quelles séquences une archive déposée contient, et la régénération d'un dépôt en archives est une
autre décision (ADR 5599).

**Tout en ligne : un refus, pas une archive vide.** Le repli ne s'offre que s'il reste une séquence refusée,
donc ce cas ne vient que de la commande. Elle refuse par une phrase qui dit qu'il n'y a rien à déposer à la main.

## Risks / Trade-offs

Le plan est la mémoire locale de ce qui est en ligne. Une séquence déposée puis retirée à la main sur le
portail resterait « déposée » pour l'application, et ne serait pas dans l'archive. C'est déjà la limite de la
reprise d'un dépôt, qui lit le même plan ; « Réinitialiser le dépôt » la lève.
