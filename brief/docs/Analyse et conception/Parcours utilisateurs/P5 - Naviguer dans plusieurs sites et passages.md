# P5 - Naviguer dans plusieurs sites et passages 🗂

[← Retour au sommaire des parcours](index.md) · **Section B - Chaîne de production**

> **Persona principal** : Karim / Samuel. **Objectifs qualité visés** : [O5 Capacité d'affichage](../../Objectifs%20qualités/Objectifs%20qualités/O5.md), [O6 Modularité](../../Objectifs%20qualités/Objectifs%20qualités/O6.md).

Karim revient d'une semaine de chantier sur 3 carrés différents avec 5 enregistreurs déployés en parallèle. Il a 8 nouvelles nuits à traiter. Il a besoin de **se repérer rapidement** dans son volume sans perdre une nuit dans une autre.

1. Karim ouvre l'application. La **vue des sites** lui présente une liste arborescente :
    - Site « Carré 640380 - PARC42 » (dernier passage il y a 2 jours)
       - Point `A1` - 3 passages cette saison, 1 à vérifier
       - Point `B2` - 2 passages cette saison
       - Point `C3` - 0 passage
    - Site « Carré 752204 - ZAC NORD » (dernier passage il y a 5 jours)
       - …
2. Une **vue tabulaire alternative** liste tous les passages tous sites confondus, triables et filtrables par site, point, n° de passage, statut d'avancement, verdict, date.
3. Karim utilise la vue tabulaire pour repérer les 8 passages au statut `Importé` ou `Transformé` qui attendent sa vérification. Il peut faire un import groupé en sélectionnant plusieurs dossiers SD à la suite (variante du parcours [P2](P2%20-%20Importer%20une%20nuit%20d%27enregistrement.md)).
4. Pour chaque passage, il enchaîne [P3](P3%20-%20Vérifier%20l%27enregistrement%20par%20échantillonnage.md) puis [P4](P4%20-%20Préparer%20un%20lot%20prêt%20à%20déposer.md) en gardant le contexte global de son chantier visible (badges colorés indiquant le site/chantier).

## Notes pour Samuel

Avec ses 24 enregistreurs en parallèle pendant 40-50 nuits par saison, Samuel a en pratique **plus de 1 000 passages par saison**. La vue tabulaire doit donc :

- supporter des filtres multi-critères performants (R&D potentielle, à arbitrer)
- permettre des actions de masse (changement de verdict, suppression, export)
- rester réactive même à plusieurs centaines de lignes (cf. [O5](../../Objectifs%20qualités/Objectifs%20qualités/O5.md))

## Enrichissements prévus

> Ces évolutions sont **décidées et maquettées, pas encore livrées**. Elles prolongent ce parcours sans en modifier les étapes actuelles.

- **Un solde de saison, point par point.** La navigation multi-sites répond à *où en sont mes passages* ; elle ne répond pas à *qu'est-ce qu'il me reste à faire*. [M-Saison](../Maquettes/M-Saison.md) restitue les règles de protocole R3 et R4, déjà vérifiées par l'application, sous forme d'un reste à faire par point (#2356).
- **Une campagne pour regrouper les passages d'un même suivi**, en dimension de regroupement, de filtre et d'export. Le rattachement reste facultatif (#2355).
- **Des actions groupées sur plusieurs passages** : préparer, téléverser, importer les résultats, sur une sélection plutôt qu'un par un (#2357). C'est l'attente déjà exprimée plus haut par « permettre des actions de masse ».
