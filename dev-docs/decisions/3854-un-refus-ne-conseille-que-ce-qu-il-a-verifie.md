---
type: adr
title: "Un refus ne conseille que ce qu'il a vérifié applicable"
status: stable
article: A13
chantier: "#3854, clôture des suites de #3458"
decided_at: 2026-08-16
verification: certaine
enforced_by:
  - "SynchronisationParticipationTest#refus_ne_tranche_pas_quand_la_plateforme_est_injoignable"
verified:
  - by: machine:ci
    at: 2026-08-16
relations:
  prolonge: ["3458"]
---

# Un refus ne conseille que ce qu'il a vérifié applicable

## Contexte

Le refus qui bloque le dépôt disait :

> Site non rattaché à Vigie-Chiro : connectez-vous et **synchronisez vos sites** avant de créer la
> participation.

Le conseil était **inapplicable à qui le lisait**. La synchronisation dérive les sites de
`/moi/participations` : elle n'atteint que les carrés où une nuit est déjà déposée, et celui qui lit ce
message essaie d'en déposer une première (mesure de la sonde #3669, cf.
[ADR 3806](3806-un-carre-se-rapatrie-par-son-numero-pas-par-ses-participations.md)).

C'est le **jumeau** du défaut que #3806 a corrigé côté fenêtre de déclaration : le verdict « ce carré
existe déjà » renvoyait lui aussi vers un geste inopérant. Corriger l'un sans l'autre laisse le défaut
vivant sous une autre forme - c'est la raison d'être de la recherche de jumeaux au début d'une issue.

## Décision

**Un message d'erreur qui conseille un geste vérifie d'abord que ce geste s'applique**, ou il ne le
nomme pas.

Concrètement, le refus interroge `GET /sites?q=<carré>` et dit l'un des trois :

| Ce que porte la plateforme | Ce que le refus conseille |
|---|---|
| le carré existe **en Point Fixe** | le **récupérer** depuis la fenêtre de déclaration |
| le carré n'y est **pas** en Point Fixe | l'**activer sur le portail** d'abord |
| la plateforme **ne répond pas** | dire qu'on **n'a pas pu vérifier** |

⚠️ **La troisième ligne est la règle, pas une commodité.** Ni « récupérez-le », ni « il n'existe pas » :
affirmer depuis une **ignorance** est le défaut que l'ADR 3458 a fermé côté verdict. Un test l'exige en
interdisant explicitement les mots des deux autres branches.

### Ce que la requête coûte, et où

**Rien sur le chemin nominal.** Un dépôt qui aboutit ne cherche aucun carré, et un test le tient
(`verify(client, never()).chercherCarre(...)`). Le conseil ne se paie que là où il sert - un appel
réseau ajouté à chaque dépôt réussi, pour un message que personne ne lirait, serait un coût pur.

### Ce qu'il a fallu déplacer, et pourquoi ce n'est pas un détail

Le refus vit dans `passage`, qui **ne peut pas dépendre de `sites`** (cycle interdit par ArchUnit).
Deux choses ont donc changé de place :

- **`estPointFixe`** quitte `RapatriementCarre` pour `SiteVigieChiro`, dans le socle : une définition,
  deux appelants. Une seconde copie de « le titre nomme le protocole » aurait divergé sans bruit ;
- **`InfosPoint`** porte le numéro de carré. Il ne sert pas à déposer - le dépôt passe par l'objectid du
  lien - mais à **conseiller**. Un site absent le laisse à `null` et le message retombe sur sa forme
  générique, plutôt que d'échouer.

## Conséquences

- **Le parcours qui a produit #3458 se referme des deux côtés** : l'écran propose le geste, et le refus
  y renvoie.
- **Un message d'erreur peut coûter une requête.** C'est nouveau dans ce dépôt, et borné : sur le chemin
  d'échec, jamais sur le nominal.
- ⚠️ **Le socle porte désormais une règle de protocole plateforme** (`estPointFixe`). C'est le prix de
  l'acyclicité ; l'alternative était deux copies.
- ⚠️ **La ligne de commande ne bénéficie pas de ce refus** : `creer-site` n'interroge rien (#3856).

## Alternatives écartées

- **Un message fixe et générique** (« récupérez ce carré depuis Mes sites ») : correct dans le cas
  courant, faux quand le carré n'existe pas en Point Fixe - où le geste conseillé ne peut pas aboutir.
- **Ne rien changer et documenter le contournement** : la doc n'est pas lue au moment du refus.
- **Interroger la plateforme à chaque dépôt** pour préparer le message : un coût permanent pour un cas
  d'échec.
