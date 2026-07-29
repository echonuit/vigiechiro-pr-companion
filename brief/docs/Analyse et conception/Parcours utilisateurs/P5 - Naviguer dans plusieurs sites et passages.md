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
5. Quand les mêmes gestes reviennent nuit après nuit sans qu'aucune décision ne change, Karim **coche plusieurs lignes** et les traite d'un coup : préparer le dépôt, téléverser, importer les résultats, déclencher le calcul. L'application lui dit **avant** de partir lesquelles seront écartées et pourquoi, puis rend compte de chacune.
6. Il suit sa saison point par point : le **solde** lui dit ce qu'il reste à faire au regard du protocole, et une **campagne** regroupe les passages d'un même suivi pour les filtrer et les exporter ensemble.

## Notes pour Samuel

Avec ses 24 enregistreurs en parallèle pendant 40-50 nuits par saison, Samuel a en pratique **plus de 1 000 passages par saison**. La vue tabulaire doit donc :

- supporter des filtres multi-critères performants (R&D potentielle, à arbitrer)
- permettre des actions de masse (changement de verdict, suppression, export)
- rester réactive même à plusieurs centaines de lignes (cf. [O5](../../Objectifs%20qualités/Objectifs%20qualités/O5.md))

## Ce que « actions de masse » a fini par vouloir dire

Les trois enrichissements annoncés ici sont **livrés** (EPIC #2349) : le [solde de saison](../Maquettes/M-Saison.md) point par point (#2356), la **campagne** comme dimension de regroupement, de filtre et d'export (#2355), et les **actions groupées** (#2357).

La demande de départ, « permettre des actions de masse (changement de verdict, suppression, export) », ne décrivait pas tout à fait le besoin. Ce qui coûte à Karim et à Samuel n'est pas de reposer vingt verdicts : c'est de **refaire vingt fois la chaîne de production**. Les quatre actions livrées sont donc celles de cette chaîne : préparer le dépôt, téléverser, importer les résultats, déclencher le calcul.

Deux des actions imaginées restent **volontairement unitaires**, et ce n'est pas un reste à faire :

- le **verdict** est un jugement porté sur une nuit après l'avoir écoutée ; l'appliquer en série reviendrait à le poser sans l'avoir formé ;
- la **suppression** détruit les fichiers du disque. Elle demande une confirmation qui nomme ce qui sera perdu, nuit par nuit.

Pour la même raison, aucune action groupée n'expose d'option **destructrice** : ni relancer un calcul (Vigie-Chiro efface les observations avant de recalculer, et l'audio d'un dépôt en archives n'est plus là pour être relu), ni remplacer un jeu de résultats déjà validé.
