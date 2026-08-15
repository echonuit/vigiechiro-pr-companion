# P1 - Déclarer un site de suivi 🌐

[← Retour au sommaire des parcours](index.md) · **Section B - Chaîne de production**

> **Persona principal** : Marie. **Objectifs qualité visés** : [O2 Facilité d'apprentissage](../../Objectifs%20qualités/Objectifs%20qualités/O2.md), [SC1 Onboarding](../../Objectifs%20qualités/Scénario/SC1.md).

Marie a créé son site sur le portail web Vigie-Chiro (<https://vigiechiro.herokuapp.com/>) et a noté son **n° de carré** (6 chiffres) ainsi que les **codes des points** (lettre + chiffre, ex. `A1`, `Z4`). Elle ouvre l'application pour la première fois et veut déclarer son dans l'application site afin de pouvoir importer ses nuits ensuite.

1. Marie démarre l'application. L'écran d'accueil détecte qu'aucun site n'est encore déclaré et lui propose une seule action mise en avant : « **Ajouter mon premier site de suivi** ».
2. Elle clique. Un formulaire s'ouvre avec :
    - **N° de carré** (6 chiffres, validé à la saisie : doit faire exactement 6 chiffres, l'application avertit si elle oublie le leading zero pour les départements 1-9)
    - **Nom convivial** (optionnel, pour reconnaître le site facilement, ex. « Étang de la Tuilière »)
    - **Protocole** : menu déroulant à deux valeurs - `PointFixeStandard` (par défaut : protocole VigieChiro à la lettre, déclenche les alertes [R3](../Modèle%20conceptuel/Règles%20métier.md#r3) / [R4](../Modèle%20conceptuel/Règles%20métier.md#r4) en cas de passage hors fenêtre) ou `PointFixeRecherche` (cas Samuel : dates et fréquences libres, R3 / R4 muettes)
    - **Liste des points** : Marie ajoute autant de codes que nécessaire (ex. `A1`, `B2`). Pour chaque point, elle peut ajouter des coordonnées GPS et un descriptif (tous optionnels).
3. À côté du n° de carré, un bouton « **Vérifier sur Vigie-Chiro** » lui dit, avant qu'elle ne valide, si un site porte déjà ce carré sur le portail. Trois réponses, et la troisième ne se confond pas avec la première : le carré est libre, le carré est déjà déclaré (le message nomme le site trouvé et son protocole), ou la vérification n'a pas pu se faire (hors connexion, portail injoignable) et le carré n'est donc **pas** vérifié.
4. Marie valide. Le site est enregistré localement. L'écran d'accueil bascule vers la **vue des sites** avec son site fraîchement créé.
5. Elle peut désormais cliquer sur « Importer une nuit » et le formulaire suivant lui propose de choisir le site et le point concernés (parcours [P2 - Importer une nuit d'enregistrement](P2%20-%20Importer%20une%20nuit%20d%27enregistrement.md)).

## Règles métier visibles

- R1 : leading zero obligatoire pour les départements 1 à 9 (validation à la saisie).
- R2 : les codes points doivent faire exactement 1 lettre + 1 chiffre.
- R3 : sur un site `PointFixeStandard`, l'application rappelle (sans bloquer) que le protocole attend 2 passages annuels (15 juin → 31 juillet, 15 août → 31 septembre). Sur un site `PointFixeRecherche`, R3 est muette.

## Variante : le carré existe déjà sur le portail

C'est le cas le plus fréquent, et celui qui a coûté le plus cher : le portail **n'autorise pas** à activer un carré sans y créer un point. Qui vient d'activer son carré a donc déjà, en ligne, un site **et** un point positionné.

Redéclarer ce carré dans l'application produit alors deux sites pour le même carré et un point local qui n'est pas celui du portail ; la nuit déposée ensuite échoue, loin de sa cause. La vérification de l'étape 3 renvoie donc au geste juste : « **Récupérer depuis Vigie-Chiro** », en haut de la vue des sites, qui rapatrie le site **et ses points déjà positionnés**, plutôt que de les ressaisir.

## Variante : créer un site directement depuis l'import

Si l'utilisateur arrive avec une nuit à importer **sans avoir préalablement déclaré le site**, la modale d'import (parcours [P2](P2%20-%20Importer%20une%20nuit%20d%27enregistrement.md)) propose un raccourci « **+ Créer un nouveau site** » qui ouvre le même formulaire que ci-dessus, puis ré-ouvre la modale d'import avec le site fraîchement créé pré-sélectionné. L'utilisateur n'a pas besoin de quitter son flux d'import.
