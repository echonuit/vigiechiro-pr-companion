## Why

Depuis #5631, la date du journal se juge sur les nuits qu'il raconte. Mais une carte dont les enregistrements s'étalent sur plus d'une nuit restait exemptée : un journal d'un autre déploiement, posé sur trois nuits de juillet, n'était signalé que si sa série différait. La clôture de #5629 l'a assumé comme défaut, et le porteur a demandé de le corriger (#5669).

L'exemption protégeait un cas réel, qu'il faut garder : une carte laissée tourner plusieurs nuits, dont le journal circulaire a perdu les dernières entrées (`sd-multi-nuits`).

## What Changes

- Sur une carte de plusieurs nuits, la date du journal est incohérente quand le journal raconte au moins un cycle et qu'**aucune** nuit de la carte n'en fait partie. Une seule nuit racontée suffit à la dire cohérente.
- Sans cycle lisible, une carte de plusieurs nuits n'est toujours pas jugée sur la date : la première ligne ne date qu'une nuit.
- Une carte de recette, `sd-journal-etranger-multi`, et une capture, `apercu-import-journal-etranger.png`, montrent le cas.

## Capabilities

### Modified Capabilities

- `importation/import-d-une-carte` : l'exigence « Le journal est cohérent s'il raconte les nuits de la carte » cesse d'exempter les cartes de plusieurs nuits.

## Impact

`AnalyseCoherence.dateIncoherente`. L'ADR 5629, qui disait « l'exemption multi-nuits reste », est amendée par l'ADR 5669.
