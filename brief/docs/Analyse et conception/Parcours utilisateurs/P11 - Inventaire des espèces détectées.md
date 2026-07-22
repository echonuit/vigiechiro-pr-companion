# P11 - Inventaire des espèces détectées 🪶

[← Retour au sommaire des parcours](index.md) · **Section C - Après le dépôt & exploitation**

> **Personas principaux** : Karim, Samuel. **Prérequis** : des observations Tadarida importées et (idéalement) validées ([P7](P7%20-%20Valider%20les%20résultats%20Tadarida.md)).

La chaîne de production (sites → import → vérification → dépôt) et la validation Tadarida raisonnent **nuit par nuit**. Une fois plusieurs nuits traitées, l'utilisateur veut une lecture **transverse** orientée **biodiversité** : « quelles espèces ai-je détectées, où, quand, combien ? ». L'écran **« Espèces & observations »** répond à cette question en **exploitant toutes les observations**, tous passages confondus.

1. Depuis l'accueil, l'utilisateur ouvre la carte **« Espèces & observations »**.
2. Un **inventaire** récapitule ses espèces détectées. Un sélecteur **« Regrouper »** propose deux angles (le pivot **espèce ↔ lieu**) :
    - **Par espèce** : une ligne par espèce (son groupe taxonomique, ses compteurs, sa période d'activité) - *qu'ai-je détecté ?*
    - **Par carré** : une ligne par carré (sa **richesse** : nombre d'espèces distinctes, volume d'observations) - *où est-ce le plus riche ?*
3. Un **filtre de statut** restreint la lecture (par exemple aux passages déjà déposés) ; tout changement de regroupement ou de filtre **ré-interroge** les données et met à jour le résumé.
4. Sur une espèce qui l'intrigue, l'utilisateur **ouvre sa fiche** (double-clic sur la ligne) pour retrouver ses critères et sa répartition, et **copie** son nom latin ou vernaculaire vers un tableur ou un courriel (clic droit, `Copier ▸`) : l'inventaire est un point de départ vers le reste de son travail, pas un cul-de-sac.

## Règles métier visibles

- Lecture **transverse** : l'écran agrège les [observations](../Modèle%20conceptuel/Règles%20métier.md#r24) de **tous** les passages de l'utilisateur, sans en modifier aucune (consultation seule).
- L'espèce retenue pour une observation suit la même logique qu'ailleurs : taxon **observateur** s'il a été validé, sinon taxon **Tadarida** (cf. [R17](../Modèle%20conceptuel/Règles%20métier.md#r17)).

## Lien avec les autres parcours

Ce parcours **consomme** ce que produisent l'import ([P2](P2%20-%20Importer%20une%20nuit%20d%27enregistrement.md)) et la validation ([P7](P7%20-%20Valider%20les%20résultats%20Tadarida.md)) : plus la validation est avancée, plus l'inventaire est fidèle. Il complète le prisme **collecte & passages** par un prisme **espèces & biodiversité**.

## Enrichissements prévus

> Ces évolutions sont **décidées et maquettées, pas encore livrées**. Elles prolongent ce parcours sans en modifier les étapes actuelles.

- **Ce que l'activité vaut, et pas seulement combien elle compte.** L'inventaire répond à *quelles espèces, où, quand, combien*. Il ne répond pas à *est-ce beaucoup*. La [synthèse de la nuit](../Maquettes/M-Synthese.md) replace chaque comptage dans un référentiel de saison, de région et de milieu, avec les quantiles affichés à côté de la classe et une mise en garde qui voyage jusque dans l'export (#2351).
- **La forme de la nuit.** La [courbe d'activité](../Maquettes/M-Activite.md) trace les contacts par tranche horaire et par espèce sur l'axe nocturne : deux nuits à 300 contacts n'ont rien à voir selon que l'activité s'étale ou tient en quarante minutes (#2352).
- **Les espèces à enjeu** sont repérables et filtrables dans l'inventaire comme dans la validation (#2353).
