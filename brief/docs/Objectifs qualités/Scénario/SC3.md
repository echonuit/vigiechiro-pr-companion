# SC3 - Ajout d'un nouveau protocole VigieChiro

Dans une **version ultérieure** de l'application, il est possible d'**ajouter un nouveau protocole VigieChiro** (par exemple le protocole *Pédestre* ou *Routier*) sans réécrire le cœur de l'application, en n'introduisant que les éléments spécifiques au nouveau protocole.

## Contexte

L'édition initiale de l'application traite essentiellement le protocole *Point fixe Carré* dont est issu le jeu de données d’exemple. Mais VigieChiro propose plusieurs protocoles complémentaires (parcours pédestre nocturne, parcours routier en voiture, point fixe permanent...) avec des conventions de nommage de fichiers différentes et des paramètres d'acquisition propres.

## Critères d'acceptation

- Les conventions de parsing des noms de fichiers WAV et des paramètres d'acquisition sont **isolées dans une classe ou un ensemble de classes par protocole**.
- L'ajout d'un nouveau protocole se fait par **création d'une nouvelle classe** implémentant une interface bien définie, **sans modification du code existant** (principe Ouvert/Fermé).
- L'IHM s'adapte au protocole détecté à l'import (libellés, colonnes affichées) sans avoir à être réécrite.

## Objectif lié

[O6 - Modularité pour accueillir d'autres protocoles VigieChiro](../Objectifs%20qualités/O6.md)
