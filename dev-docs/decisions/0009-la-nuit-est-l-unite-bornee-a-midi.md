# ADR 0009 — La nuit (soir J → matin J+1, bornée à midi) est l'unité de traitement

- **Statut** : Accepté — rétroactif
- **Chantier** : import multi-nuits #664 ; EPIC #1696 (partition par nuit)
- **Vérification** : humaine — que la nuit soit l'unité bornée à midi est une règle de calcul de fenêtre, non observable dans un motif statique

## Contexte

Un déploiement de terrain couvre souvent **plusieurs nuits** sur une même carte SD, avec parfois **un seul** journal (THLog / LogPR) pour tout le déploiement. L'objet métier pertinent n'est pas « la carte » ni « la date calendaire » : c'est la **nuit** (le protocole nocturne va du soir `J` au matin `J+1`). Il fallait une **frontière** non ambiguë entre deux nuits consécutives, et que **tout** ce qui décrit une nuit lui soit propre.

## Décision

- **Une nuit = un passage.** L'import (#664) crée un passage par nuit.
- **La frontière est midi.** Un enregistrement d'**avant midi** appartient à la nuit de la **veille** ; midi ne produit aucun fichier (acquisition ~21:00→06:30) et sépare donc proprement deux nuits. Cette clé de nuit est **partagée** (extraite dans `commun.model.Nuit`, #1724), utilisée à l'identique par la partition d'import (`PartitionNuits`) et par l'hydratation d'un passage reconstruit.
- **Tout est partitionné par nuit** : mesures climatiques, évènements et anomalies du journal, `heureDebut`/`heureFin` sont filtrés sur la **fenêtre réelle** de la nuit (`NuitDetectee.debut()/fin()`), pas hérités du journal commun.

## Conséquences

- Le diagnostic, le climat et le journal d'un passage ne montrent **que sa nuit**, même quand la carte en porte plusieurs sous un log unique.
- La clé de nuit vivant dans `commun`, `passage` peut l'utiliser sans dépendre de `importation` (cf. [ADR 0004](0004-cross-feature-sans-cycle-ports-commun.md)).
- Correction **future-only** : les imports déjà faits (partition partielle) sont à re-importer pour bénéficier de la partition complète.

## Alternatives écartées

- **Partitionner par date calendaire.** Coupe une nuit en deux à minuit : le petit matin serait rattaché au mauvais jour.
- **Une frontière par calcul solaire (coucher/lever).** Plus « juste » en apparence, mais dépendante du lieu et de la date, et inutile : aucun fichier n'est produit autour de midi, qui tranche sans ambiguïté.
