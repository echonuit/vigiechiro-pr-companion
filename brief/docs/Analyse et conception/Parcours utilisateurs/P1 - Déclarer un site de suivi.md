# P1 - Déclarer un site de suivi 🌐

[← Retour au hub des parcours](index.md) · **Section B — Approfondissements** · ✅ MUST

> **Persona principal** : Marie. **MoSCoW** : MUST. **Objectifs qualité visés** : [O2 Facilité d'apprentissage](../../Objectifs%20qualités/Objectifs%20qualités/O2.md), [SC1 Onboarding](../../Objectifs%20qualités/Scénario/SC1.md).

Marie a créé son site sur le portail web Vigie-Chiro (<https://vigiechiro.herokuapp.com/>) et a noté son **n° de carré** (6 chiffres) ainsi que les **codes des points** (lettre + chiffre, ex. `A1`, `Z4`). Elle ouvre l'application pour la première fois et veut déclarer son dans l'application site afin de pouvoir importer ses nuits ensuite.

1. Marie démarre l'application. L'écran d'accueil détecte qu'aucun site n'est encore déclaré et lui propose une seule action mise en avant : « **Ajouter mon premier site de suivi** ».
2. Elle clique. Un formulaire s'ouvre avec :
    - **N° de carré** (6 chiffres, validé à la saisie : doit faire exactement 6 chiffres, l'application avertit si elle oublie le leading zero pour les départements 1-9)
    - **Nom convivial** (optionnel, pour reconnaître le site facilement, ex. « Étang de la Tuilière »)
    - **Protocole** (préselectionné `Point Fixe`, seul protocole supporté pour le moment)
    - **Liste des points** : Marie ajoute autant de codes que nécessaire (ex. `A1`, `B2`). Pour chaque point, elle peut ajouter des coordonnées GPS et un descriptif (tous optionnels).
3. Marie valide. Le site est enregistré localement. L'écran d'accueil bascule vers la **vue des sites** avec son site fraîchement créé.
4. Elle peut désormais cliquer sur « Importer une nuit » et le formulaire suivant lui propose de choisir le site et le point concernés (parcours [P2 - Importer une nuit d'enregistrement](P2%20-%20Importer%20une%20nuit%20d%27enregistrement.md)).

## Règles métier visibles

- R1 : leading zero obligatoire pour les départements 1 à 9 (validation à la saisie).
- R2 : les codes points doivent faire exactement 1 lettre + 1 chiffre.
- R3 : information rappelée mais non bloquante - le protocole Point Fixe attend 2 passages annuels (15 juin → 31 juillet, 15 août → 31 septembre).

## Variante : créer un site directement depuis l'import

Si l'utilisateur arrive avec une nuit à importer **sans avoir préalablement déclaré le site**, la modale d'import (parcours [P2](P2%20-%20Importer%20une%20nuit%20d%27enregistrement.md)) propose un raccourci « **+ Créer un nouveau site** » qui ouvre le même formulaire que ci-dessus, puis ré-ouvre la modale d'import avec le site fraîchement créé pré-sélectionné. L'utilisateur n'a pas besoin de quitter son flux d'import.
