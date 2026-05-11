# SC2 - Aucune fuite d'identifiants traçants

Aucun **identifiant observateur**, **identifiant de participation**, **numéro de série de PR** ou **identifiant de carrée** ne doit fuir hors de l'environnement local de l'utilisateur via des canaux involontaires (télémétrie, log distant, partage automatique).

## Contexte

Le programme VigieChiro porte sur la faune et non sur les humains, mais les identifiants techniques (UUID de participation, numéro de série du PR, géolocalisation implicite via le numéro de carrée) sont **traçants** : un tiers peut remonter à l'observateur, son terrain de prospection, sa fréquence d'observation. La plupart des bénévoles travaillent sur leur terrain personnel et tiennent à ne pas voir ces informations exposées.

## Critères d'acceptation

- L'application **ne contacte aucun service distant** par défaut.
- Si l'application produit des logs (utile pour le débogage), les identifiants traçants sont anonymisés (hash, redaction).
- Le `.gitignore` du dépôt étudiant interdit le commit accidentel de fichiers de session contenant de vrais UUID.
- Le jeu de données pédagogique fourni dans `data/` est explicitement signalé comme tel et provient d'un PR de test.

## Objectif lié

[O8 - Confidentialité des identifiants observateur et participation](../Objectifs%20qualités/O8.md)
