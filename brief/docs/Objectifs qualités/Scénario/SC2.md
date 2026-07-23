# SC2 - Aucune fuite d'identifiants traçants vers un tiers

Aucun **identifiant observateur**, **identifiant de participation**, **numéro de série de PR** ou **identifiant de carré** ne doit fuir vers un **tiers** par un canal involontaire (télémétrie, journal distant, partage automatique). La plateforme Vigie-Chiro, à qui l'application transmet légitimement ces données, n'est pas un tiers.

## Contexte

Le programme VigieChiro porte sur la faune et non sur les humains, mais les identifiants techniques (UUID de participation, numéro de série du PR, géolocalisation implicite via le numéro de carré) sont **traçants** : un tiers peut remonter à l'observateur, son terrain de prospection, sa fréquence d'observation. La plupart des bénévoles travaillent sur leur terrain personnel et tiennent à ne pas voir ces informations exposées.

## Critères d'acceptation

- Les seuls services distants contactés sont **la plateforme Vigie-Chiro** (protocole), **GitHub** (annonce de version, au démarrage) et **GBIF** (fiche d'espèce, à la demande) ; ils sont énumérés dans [O8](../Objectifs%20qualités/O8.md), et aucun n'emporte d'identifiant traçant hors Vigie-Chiro.
- La vérification de version au démarrage est une **lecture**, jamais un téléchargement ni une installation : rien ne se met à jour tout seul.
- Les journaux applicatifs restent **locaux** ; la couche réseau n'y consigne ni jeton, ni en-têtes, ni corps envoyé, ni URL signée.
- Le `.gitignore` du dépôt interdit le commit accidentel de fichiers de session contenant de vrais UUID.
- Le jeu de données d'exemple fourni dans `data/` est explicitement signalé comme tel et provient d'un PR de test.

## Objectif lié

[O8 - Confidentialité des identifiants observateur et participation](../Objectifs%20qualités/O8.md)
