# ADR 3350 - C'est la sortie qui désambiguïse, pas le critère qui se restreint

- **Statut** : Accepté - 2026-08-06
- **Chantier** : #3350, suite de la clôture du chantier #3151
- **Vérification** : certaine - `FiltresLieuTest#le_point_est_filtrable`

## Contexte

`FiltresLieu` (#2971) écartait le **point** de `--lieu`, et l'écrivait :

> le schéma pose `UNIQUE(site_id, code)`, donc un code seul (« A1 ») désigne autant de lieux qu'il y a
> de carrés. L'écran s'en tire en l'affichant qualifié (« 640380 · A1 »), ce qui suppose une liste sous
> les yeux ; une ligne de commande n'en a pas.

L'argument est bien construit. Il repose sur une prémisse : *la sortie ne montre pas le carré*.

**L'inventaire l'a démentie.** Toutes les surfaces qui offrent `--lieu` portent le carré **et** le
point :

| Commande | montre le carré | montre le point |
| --- | --- | --- |
| `lister-passages` | oui, chaque ligne | oui, chaque ligne |
| `exporter-sons` | colonne « Carré » | colonne « Point » |
| `exporter-activite` | colonne « Carré » | colonne « Point » |
| `solde-saison` | colonne | colonne |
| `lister-observations` | **non** | **non** |

Un `--lieu A1` qui remonte les A1 de plusieurs carrés se lit donc sans ambiguïté, sur la sortie
elle-même.

Deux incohérences en découlaient. `FiltresSaison` retenait sur le code du point depuis toujours, sans
avoir jamais rencontré cet arbitrage - deux commandes, deux règles sur la même notion. Et
l'échappatoire annoncée, « le point restera atteignable par un croisement `--carre` / `--point` »,
n'existait sur **aucune** commande.

## Décision

**Le point se compare, au même titre que la commune et le carré**, qualifié par son carré
(« 640380 · A1 ») comme l'écran l'écrit. La correspondance étant partielle, `--lieu A1` retient les A1
de tous les carrés et `--lieu "640380 · A1"` n'en désigne qu'un.

La règle générale : **ce n'est pas le critère qui doit se restreindre, c'est la sortie qui
désambiguïse.** Son corollaire est la thèse de l'[ADR 3151](3151-un-ecran-n-offre-pas-ce-qu-il-ne-montre-pas.md)
portée à la ligne de commande - **une commande qui offre `--lieu` doit montrer le lieu**.
`lister-observations` y manquait : elle filtrait sur ce qu'elle taisait entièrement. Elle porte
désormais le lieu du passage en tête, et les champs `carre`, `point`, `commune` en JSON.

## Conséquences

- `FiltresSaison` cesse d'être une exception : elle avait raison, pour une raison qu'elle ignorait ;
- l'échappatoire `--carre` / `--point` **n'est plus nécessaire** : ce qu'elle promettait, `--lieu`
  qualifié le fait déjà. Une option de moins, plutôt qu'une de plus sur quatre commandes ;
- deux tests qui affirmaient l'exclusion ont été **retournés**, avec le motif du retournement inscrit
  à côté : ce sont des décisions, pas des ajustements.

### Ce que le refus énumère n'est pas ce qu'il accepte

Élargir la comparaison a dégradé le **refus**, et la mesure l'a montré avant qu'on ne le livre : sur
quinze carrés, la liste « Lieux présents » passait de 15 à **31** entrées, et sa borne de douze ne
montrait plus que **six carrés** au lieu de douze. Les points évinçaient ce qui sert à corriger une
faute de frappe.

Le refus nomme donc les niveaux qui **discriminent** - commune et carré - et le message le dit
(« Lieux présents (communes et carrés) »), pour ne pas laisser croire à un inventaire de ce qui est
acceptable. D'où deux fonctions par surface : `dimensionsLieu` pour comparer, `dimensionsNommees` pour
énumérer. Un refus qui grossit en disant moins est un refus qui s'use.

## Alternatives écartées

- **Livrer l'échappatoire promise** (`--point` à croiser avec `--carre` sur les quatre commandes) :
  tient la promesse écrite, mais ajoute une option là où la sortie rendait déjà `--lieu` lisible ;
- **Aligner `FiltresSaison` par le bas** (retirer le point de sa recherche) : cohérent et minimal,
  mais retire un usage qui marche et que la sortie justifie. On aurait uniformisé sur l'erreur.

## Note sur la genèse

L'issue #3350 affirmait que `lister-passages --lieu A1` « ne trouve rien, par principe écrit ». C'était
**faux au moment où elle a été écrite** : #3320 y avait ajouté le point qualifié sept heures plus tôt.
L'erreur vient d'avoir lu la doc de `FiltresLieu` en supposant qu'elle gouvernait la commande, alors
que celle-ci passe sa propre fonction de dimensions. Une classe partagée ne dit pas ce que font ses
appelants quand ils lui passent leur règle.
