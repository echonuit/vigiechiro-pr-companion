# P1 - Déclarer un site de suivi 🌐

[← Retour au sommaire des parcours](index.md) · **Section B - Chaîne de production**

> **Persona principal** : Marie. **Objectifs qualité visés** : [O2 Facilité d'apprentissage](../../Objectifs%20qualités/Objectifs%20qualités/O2.md), [SC1 Onboarding](../../Objectifs%20qualités/Scénario/SC1.md).

Marie a créé son site sur le portail web Vigie-Chiro (<https://vigiechiro.herokuapp.com/>) et a noté les **codes des points** (lettre + chiffre, ex. `A1`, `Z4`). Le **n° de carré**, elle peut l'avoir noté ou non : depuis #4573, l'application le déduit d'une position collée depuis une carte. Elle ouvre l'application pour la première fois et veut déclarer son dans l'application site afin de pouvoir importer ses nuits ensuite.

1. Marie démarre l'application. L'écran d'accueil détecte qu'aucun site n'est encore déclaré et lui propose une seule action mise en avant : « **Ajouter mon premier site de suivi** ».
2. Elle clique. Un formulaire s'ouvre avec :
    - **N° de carré** (6 chiffres, validé à la saisie : doit faire exactement 6 chiffres, l'application avertit si elle oublie le leading zero pour les départements 1-9)
    - **ou une position collée**, sous le champ : « Situer » en déduit le carré et remplit le n°. Le carroyage national étant embarqué, ce geste marche **hors connexion**, à la différence de « Vérifier sur Vigie-Chiro ». Sur une frontière entre deux carrés, l'application les **nomme** et ne choisit pas : elle ne sait pas de quel côté était le micro, Marie si
    - **Nom convivial** (optionnel, pour reconnaître le site facilement, ex. « Étang de la Tuilière »)
    - **Protocole** : menu déroulant à deux valeurs - `PointFixeStandard` (par défaut : protocole VigieChiro à la lettre, déclenche les alertes [R3](../Modèle%20conceptuel/Règles%20métier.md#r3) / [R4](../Modèle%20conceptuel/Règles%20métier.md#r4) en cas de passage hors fenêtre) ou `PointFixeRecherche` (cas Samuel : dates et fréquences libres, R3 / R4 muettes)
    - **Liste des points** : Marie ajoute autant de codes que nécessaire (ex. `A1`, `B2`). Pour chaque point, elle peut ajouter des coordonnées GPS et un descriptif (tous optionnels).
3. À côté du n° de carré, un bouton « **Vérifier sur Vigie-Chiro** » lui dit, avant qu'elle ne valide, si un site porte déjà ce carré sur le portail. Trois réponses, et la troisième ne se confond pas avec la première : le carré est libre, le carré est déjà déclaré (le message nomme le site trouvé et son protocole), ou la vérification n'a pas pu se faire (portail injoignable) et le carré n'est donc **pas** vérifié. **Hors connexion, ce bouton est fermé** et son motif nomme le geste qui manque : la question ne peut être posée à personne, et l'annoncer avant le clic vaut mieux que de la poser pour rien. Déclarer le carré reste possible - travailler hors ligne est normal.
4. **Si le carré est déjà déclaré là-bas**, un bouton « **Récupérer ce carré** » paraît sous le verdict, et « Créer » se ferme le temps que le verdict tienne : le créer une seconde fois produirait le doublon qui fait échouer le dépôt. Marie récupère : le site arrive **rattaché** à son homologue Vigie-Chiro, avec ses points d'écoute **déjà positionnés**, la fenêtre se ferme et la fiche du carré s'ouvre avec le compte rendu. Ce qu'elle avait saisi (nom convivial, commentaire) est conservé ; la plateforme ne stocke aucun nom libre.
5. **Sinon**, Marie valide. Le site est enregistré localement. L'écran d'accueil bascule vers la **vue des sites** avec son site fraîchement créé.
6. Elle peut désormais cliquer sur « Importer une nuit » et le formulaire suivant lui propose de choisir le site et le point concernés (parcours [P2 - Importer une nuit d'enregistrement](P2%20-%20Importer%20une%20nuit%20d%27enregistrement.md)).

## Règles métier visibles

- R1 : leading zero obligatoire pour les départements 1 à 9 (validation à la saisie).
- R2 : les codes points doivent faire exactement 1 lettre + 1 chiffre.
- R3 : sur un site `PointFixeStandard`, l'application rappelle (sans bloquer) que le protocole attend 2 passages annuels (15 juin → 31 juillet, 15 août → 31 septembre). Sur un site `PointFixeRecherche`, R3 est muette.

## Variante : le carré existe déjà sur le portail

C'est le cas le plus fréquent, et celui qui a coûté le plus cher : le portail **n'autorise pas** à activer un carré sans y créer un point. Qui vient d'activer son carré a donc déjà, en ligne, un site **et** un point positionné.

Redéclarer ce carré dans l'application produit alors deux sites pour le même carré et un point local qui n'est pas celui du portail ; la nuit déposée ensuite échoue, loin de sa cause. Le geste juste n'est donc pas de redéclarer, mais de **rapatrier le carré par son numéro** depuis la fenêtre de déclaration : le site arrive rattaché, avec ses localités positionnées (étape 4 ci-dessus, livré par #3806).

« **Récupérer depuis Vigie-Chiro** », sur l'écran des sites, ne comble **pas** ce manque et ne le comblera pas : cette synchronisation part des participations, et n'atteint donc que les carrés où une nuit est **déjà** déposée. Mesuré le 2026-08-15 sur un compte réel : `/moi/sites` rend **0**, et le seul site des participations appartient à un tiers - les possesseurs de carrés sont rares, la plupart des observateurs déposent sur le carré de quelqu'un d'autre. C'est pourquoi le rapatriement passe par la **recherche par numéro**, qui rend le carré à qui le demande.

Et si le dépôt est tenté avant ce rattachement, le refus **nomme le geste** : récupérer le carré s'il existe en Point Fixe, l'activer sur le portail sinon (#3854).

## Variante : créer un site directement depuis l'import

Si l'utilisateur arrive avec une nuit à importer **sans avoir préalablement déclaré le site**, la modale d'import (parcours [P2](P2%20-%20Importer%20une%20nuit%20d%27enregistrement.md)) propose un raccourci « **+ Créer un nouveau site** » qui ouvre le même formulaire que ci-dessus, puis ré-ouvre la modale d'import avec le site fraîchement créé pré-sélectionné. L'utilisateur n'a pas besoin de quitter son flux d'import.
