## 1. Rouge

- [x] 1.1 `LibelleProximiteTest` : une distance de 100 m rend la phrase nue, sans « protocole »
- [x] 1.2 `ScenarioFicheSiteTest` : deux points à cent mètres, la distance se lit et aucune étiquette d'alerte n'existe
- [x] 1.3 `SiteDetailViewModelTest` : la distance est portée par la carte, sans verdict

## 2. Retrait

- [x] 2.1 Retirer `SEUIL_PROXIMITE_METRES` et `tropProche` de `CartePoint`
- [x] 2.2 `CartesPointsSite` : l'étiquette ne garde que sa forme d'information ; retirer le style orphelin
- [x] 2.3 Réécrire le commentaire de `DistanceGeo`

## 3. Ce qui citait la règle

- [x] 3.1 `docs/ecrans/sites.md` : retirer l'encart « Trop rapprochés pour le protocole »
- [x] 3.2 Balayer brief, recette, ADR et specs ; aligner ce qui cite encore le seuil
- [x] 3.3 Régénérer et rouvrir l'aperçu de la fiche d'un site

## 4. Décision

- [x] 4.1 ADR : un seul seuil de voisinage, et pourquoi l'autre est parti
- [x] 4.2 Mutation : remettre l'alerte, la voir tuée
