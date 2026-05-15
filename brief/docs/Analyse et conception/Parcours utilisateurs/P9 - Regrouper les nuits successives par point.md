# P9 - Regrouper les nuits successives par point 🔁

[← Retour au hub des parcours](index.md) · **Section C — Cibles étirées** · ⚪ COULD (à arbitrer, voir note interne)

> **Persona principal** : Karim / Samuel. **MoSCoW** : COULD (à arbitrer - voir note de complexité). **Objectifs qualité visés** : [O5 Capacité d'affichage](../../Objectifs%20qualités/Objectifs%20qualités/O5.md), confort de productivité.

Samuel a déployé un enregistreur sur le même point pendant **4 nuits successives** dans le cadre d'un passage Vigie-Chiro. Il vient de récupérer les résultats Tadarida pour les 4 nuits et entame la validation taxonomique (parcours [P7](P7%20-%20Valider%20les%20résultats%20Tadarida.md)). Il sait qu'une espèce détectée et validée sur la nuit 1 sera très probablement aussi présente sur les nuits 2, 3 et 4 - il veut **gagner du temps** en regroupant les nuits.

1. Samuel ouvre la vue des sites et sélectionne un point. Il voit la liste des passages sur ce point pour la saison en cours, ordonnés chronologiquement.
2. Il sélectionne plusieurs passages successifs (4 nuits) avec un Ctrl+clic ou en glissant la sélection.
3. Il clique sur « **Regrouper pour validation** ». L'application ouvre une **vue de validation regroupée** :
    - les observations des 4 nuits sont fusionnées et triées par espèce
    - un compteur indique « 4 nuits, 12 espèces détectées, 2 870 observations au total »
    - Samuel peut alors valider une espèce **une seule fois pour les 4 nuits** : son verdict s'applique aux observations de la même espèce sur toute la période regroupée
4. Le mode regroupé respecte le **mode de validation** choisi (inventaire ou activité, R18) :
    - en mode inventaire, valider une espèce sur la 1re nuit la marque comme présente sur le site, les autres détections (mêmes nuits ou nuits suivantes du regroupement) sont ignorées
    - en mode activité, le regroupement est plus formellement « rouler par espèce » : on valide chaque détection mais le tri par espèce permet d'enchaîner rapidement

## Note de complexité

Samuel le considérait comme « utile mais peut-être trop complexe ». Il **conditionne la productivité** des utilisateurs avec beaucoup de passages (Karim et Samuel) mais reste hors du périmètre MVP strict de la SAE. À promouvoir en SHOULD voire MUST si la vélocité étudiante le permet **et** si la validation Tadarida ([P7](P7%20-%20Valider%20les%20résultats%20Tadarida.md)) est déjà solidement en place.
