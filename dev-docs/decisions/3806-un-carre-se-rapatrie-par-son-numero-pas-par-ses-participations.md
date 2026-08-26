---
type: adr
title: "Un carré se rapatrie par son numéro, pas par ses participations"
status: stable
article: A17
chantier: "#3806, suites de #3458"
decided_at: 2026-08-16
verification: certaine
enforced_by:
  - "RapatriementCarreTest#entre_plusieurs_protocoles_on_prend_le_point_fixe"
verified:
  - by: machine:ci
    at: 2026-08-16
relations:
  prolonge: ["3458"]
---

# Un carré se rapatrie par son numéro, pas par ses participations

## Contexte

Préparer une nuit **opportuniste** - le cas majoritaire - commence par déclarer le carré et son point.
Mais le dépôt exige que le site local porte un **lien** vers son homologue plateforme
(`SynchronisationParticipation#creerPour`, sinon « Site non rattaché à Vigie-Chiro », #3463), et la
synchronisation qui pose ce lien part de `GET /moi/participations` : elle ne voit que les carrés où une
nuit est **déjà** déposée.

> Déposer était la seule chose qui aurait créé la participation qui aurait rendu le dépôt possible.

C'est ce qu'a vécu l'observateur à l'origine de #3458 : *« j'ai dû recréer Z1 manuellement en le
positionnant »*, puis son dépôt a échoué loin de sa cause.

## La mesure, qui a retourné le remède

Le remède pressenti était d'**élargir la synchronisation à `/moi/sites`**. La sonde #3669, jouée en
lecture seule sur un compte réel le **2026-08-15**, l'a démenti :

| Requête | Résultat |
|---|---|
| `GET /moi/sites` | **0** - collection vide |
| `GET /moi/participations` | 4 participations, **1** site |
| le site de ces participations | `observateur` = **un autre compte** |

`/moi/sites` filtre sur le **propriétaire**. Or **les possesseurs de carrés sont rares** : la majorité
des observateurs déposent sur des carrés qui ne leur appartiennent pas. L'union des deux sources aurait
donc été vraie sur le cas rare et fausse sur le cas courant.

`GET /sites?q=<carré>` rend en revanche le site **complet** à qui le demande - `_id`, titre, et ses
localités avec coordonnées.

## Décision

**Un carré se rapatrie par son numéro**, depuis la fenêtre de déclaration, via `GET /sites?q=`. Le site
arrive rattaché, avec ses points positionnés, **avant** tout dépôt.

**Et l'on n'élargit pas la synchronisation à `/moi/sites`.** C'est une décision **de ne pas faire**,
et elle est la plus coûteuse à refaire : elle a demandé un jeton, une sonde sur compte réel, et elle a
retourné l'intuition de départ. Sans cette ADR, la piste se rouvrirait au premier lecteur qui remarque
que la route existe.

!!! warning "Ce que la `Vérification` de cette ADR ne couvre pas"
    Le test déclaré en en-tête tient la règle du **Point Fixe**, qui s'exécute. Il ne tient **pas** la
    décision de ne pas faire : aucun test ne peut échouer parce que quelqu'un aura rebranché
    `/moi/sites`. Cette moitié-là est **humaine**, et c'est ce paragraphe qui la porte - le dire vaut
    mieux que laisser croire, par la seule présence d'une `Vérification : certaine`, que l'ensemble de
    l'ADR est sous garde (ADR 2748).

### Ce qui se rapatrie, et ce qui se dit plutôt que de se deviner

Un numéro de carré **ne désigne pas un site** : le même carré peut exister en Point Fixe, en Pédestre et
en Routier. On ne récupère que le **Point Fixe** - le seul protocole que Companion traite - et si le
carré n'existe que sous un autre, on le **dit**. Rattacher au premier site trouvé enverrait la nuit au
mauvais endroit ; dire « inexistant » serait faux.

### La saisie de l'utilisateur survit au rapatriement

Nom convivial et commentaire déjà tapés sont **conservés** ; le titre plateforme ne sert qu'à défaut.
Mesuré avant de trancher : la plateforme ne stocke aucun nom libre, donc les garder ne crée **aucun**
écart avec le portail.

### La règle de blocage est bornée à la déclaration

« Créer » se ferme tant que le verdict « ce carré existe déjà » tient - le clic y produisait le doublon
d'origine. Mais **en édition**, un site déjà connu de la plateforme est le cas **nominal** : y appliquer
la même règle interdirait de modifier un site correctement rattaché.

## Conséquences

- **Le cercle est cassé** : le rattachement s'obtient sans avoir déposé.
- **Un seul mécanisme d'écriture** : `ImportSiteDistant` est **extrait** de `RapprochementSites`, pas
  réécrit. Un carré arrivé par la synchronisation périodique et un carré rapatrié à la demande laissent
  le **même état local**.
- **Une différence est assumée** : la synchronisation connaît **tous** les sites et peut purger les
  correspondances qu'elle ne cite plus ; le rapatriement n'en connaît **qu'un** et se contente d'un
  `upsert`. Purger depuis une vue partielle effacerait des liens valides.
- **Le refus de dépôt s'appuie sur cette route** ([ADR 3854](3854-un-refus-ne-conseille-que-ce-qu-il-a-verifie.md)).
- **Le geste reste absent de la ligne de commande** (#3856) : `creer-site` fabrique encore le doublon
  que l'écran empêche. La parité n'est donc pas tenue, et c'est écrit plutôt que tu.

## Alternatives écartées

- **Élargir la synchronisation à `/moi/sites`** : mesurée fausse sur le cas courant (voir ci-dessus).
- **Rattacher au premier site trouvé** : silencieusement faux dès que le carré existe sous plusieurs
  protocoles, et la nuit part au mauvais endroit.
- **Laisser l'utilisateur créer le doublon puis le réconcilier** : c'est exactement l'incident de #3458,
  et la réconciliation coûte plus que l'empêchement.
