# ADR 0018 — La synchro rapatrie aussi l'identité de la nuit

- **Statut** : Accepté — 2026-07-18
- **Chantier** : #1814 (suite de l'EPIC #1662)
- **Vérification** : humaine — que la synchro pose l'identité réelle de la nuit relève du déroulé réseau, vérifié par les tests de synchro
- **Amende** : [ADR 0016](0016-synchro-rapatrie-des-squelettes-hydrates-a-la-demande.md)

## Contexte

L'ADR 0016 a fait des nuits rapatriées des **squelettes ultra-légers** : structure seule, **aucun appel de détail par nuit**, donc enregistreur « inconnu », sans météo ni micro. À l'usage réel (base neuve, une fois le bouton « Mes sites » réparé par #1808), la fiche du site affiche une **colonne entière de « PR INCONNU »**. Or l'enregistreur qui a produit la nuit est l'une des premières choses qu'un observateur cherche, et la plateforme **la connaît**.

Cette information ne vit pas dans la **liste** des participations que lit la synchro, mais dans le **détail par nuit** (`GET /participations/#id`, dans sa `configuration`) — comme la météo, le micro et la fin de nuit. Seule la **reconstruction complète** la récupérait ; or elle télécharge au passage **toutes les observations**, dont l'enregistreur n'a nul besoin. Récupérer un numéro de série coûtait donc le prix d'une nuit entière.

## Décision

La synchro paie **un appel de détail par nuit nouvelle** et pose l'**identité** réelle de la nuit : numéro de série de l'enregistreur, météo, micro, fin de nuit. La nuit **reste un squelette** (0 séquence, toujours listée « à reconstruire ») : les observations et l'audio restent à la reconstruction.

« Récupérer une nuit » se lit désormais en **trois niveaux** de complétude, alignés sur ce que chacun coûte :

| Niveau | Contenu | Coût | Qui le pose |
|---|---|---|---|
| **Structure** | point, date, n° de passage | la liste, déjà lue | la synchro |
| **Identité** | enregistreur, météo, micro, fin | 1 appel de détail / nuit | la synchro (#1814) |
| **Contenu** | séquences, observations, audio | détail + CSV/pagination | la reconstruction, la réactivation |

Deux garde-fous :

- **Best-effort par nuit** : un détail indisponible (injoignable, refus) retombe sur le **squelette nu** (« INCONNU ») au lieu d'écarter la nuit. La nuit apparaît toujours ; son identité se rattrapera.
- **Une seule fois par nuit** : la synchro reste idempotente (les nuits déjà locales sont sautées), et les appels sont menés **en parallèle** (moteur de #1779, borne d'entrée/sortie à 8).

## Conséquences

- La table des passages est **lisible dès la synchro** (enregistreur, météo) au lieu d'une colonne d'« INCONNU ».
- Le coût est **N appels** à la première synchro d'un historique existant, puis **incrémental** (seules les nuits nouvelles). Parallélisé, il reste de l'ordre de la seconde pour une saison.
- La reconstruction **garde son rôle** : une nuit rapatriée avec identité est toujours listée « à reconstruire », car elle n'a ni séquences ni observations.
- L'appariement nuit → détail devient un **invariant à tenir** (les appels étant parallèles) : le moteur rend les résultats dans l'ordre d'entrée, et un test l'exerce sur deux nuits aux numéros de série distincts.

## Alternatives écartées

- **Laisser tel quel** (l'enregistreur ne revient qu'à la reconstruction complète). C'est ce que faisait 0016 : cela oblige à télécharger toutes les observations d'une nuit pour apprendre quel enregistreur l'a produite.
- **Un geste « compléter les métadonnées » à la demande** (1 appel/nuit, sans observations, en étendant `SynchronisationParticipation#tirerDepuis`). Préserve la synchro ultra-légère, mais la table reste « PR INCONNU » tant qu'on n'a pas agi **nuit par nuit** — précisément la friction qui a motivé le chantier.
- **Re-tirer le détail de toutes les nuits à chaque synchro** (et non des seules nuits nouvelles). Repaierait N appels à chaque fois pour une donnée qui ne bouge pas.
- **Tout rapatrier** (structure + observations + audio), déjà écarté par 0016 : coût prohibitif, et impossible pour l'audio qu'un dépôt ZIP ne restitue pas (ADR 0006).
